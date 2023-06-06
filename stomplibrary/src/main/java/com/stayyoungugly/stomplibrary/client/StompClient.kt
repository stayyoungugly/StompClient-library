package com.stayyoungugly.stomplibrary.client

import com.stayyoungugly.stomplibrary.heartbeat.HeartBeatService
import com.stayyoungugly.stomplibrary.util.PathMatcher
import com.stayyoungugly.stomplibrary.util.SimpleBrokerMatcher
import com.stayyoungugly.stomplibrary.model.StompEvent
import com.stayyoungugly.stomplibrary.model.StompHeader
import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.EventType
import com.stayyoungugly.stomplibrary.model.enum.FrameType
import com.stayyoungugly.stomplibrary.model.enum.HeaderType
import com.stayyoungugly.stomplibrary.provider.ConnectionProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*

class StompClient(private val connectionProvider: ConnectionProvider) {

    companion object {
        const val SUPPORTED_VERSIONS = "1.1, 1.2"
        const val DEFAULT_ACK = "auto"
    }

    private val dispatcher = Dispatchers.Default

    private val scope = CoroutineScope(Dispatchers.Default)

    private var topics = HashMap<String, String>()

    private val streamMap = HashMap<String, Flow<StompMessage>>()

    var pathMatcher: PathMatcher = SimpleBrokerMatcher()
    var legacyWhitespace = false

    private var lifecycleJob: Job? = null

    private var messagesJob: Job? = null

    private val lifecycleSharedFlow =
        MutableSharedFlow<StompEvent>(replay = 5)

    private val messageSharedFlow =
        MutableSharedFlow<StompMessage>(replay = 5)

    private val connectionSharedFlow = MutableStateFlow(false)

    private var headers: List<StompHeader>? = null

    private val heartBeatTask = HeartBeatService(this::sendHeartbeat, this::failListener)

    init {
        heartBeatTask.serverHeartbeatNew = 0
        heartBeatTask.clientHeartbeatNew = 0
    }

    private fun failListener() {
        lifecycleSharedFlow.tryEmit(StompEvent(EventType.FAILED_SERVER_HEARTBEAT))
    }

    fun setServerHeartbeatInMs(ms: Int): StompClient {
        heartBeatTask.serverHeartbeatNew = ms
        return this
    }

    fun setClientHeartbeatInMs(ms: Int): StompClient {
        heartBeatTask.clientHeartbeatNew = ms
        return this
    }

    suspend fun connect(headers: List<StompHeader>?): Boolean? {
        this.headers = headers

        if (isConnected()) {
            Timber.d("Clien was already connected")
            return false
        }

        lifecycleJob?.cancel()
        messagesJob?.cancel()

        var flag: Boolean? = false
        lifecycleJob = connectionProvider.sessionLifecycle()
            .onEach { lifecycleEvent ->
                when (lifecycleEvent.eventType) {
                    EventType.OPENED -> {
                        val headers = ArrayList<StompHeader>().apply {
                            add(StompHeader(HeaderType.VERSION, SUPPORTED_VERSIONS))
                            add(
                                StompHeader(
                                    HeaderType.HEART_BEAT,
                                    "${heartBeatTask.clientHeartbeatNew},${heartBeatTask.serverHeartbeatNew}"
                                )
                            )
                            headers?.let { addAll(it) }
                        }
                        flag = connectionProvider.send(
                            StompMessage(FrameType.CONNECT.name, headers, null).compile(
                                legacyWhitespace
                            )
                        )
                        lifecycleSharedFlow.emit(lifecycleEvent)
                    }
                    EventType.CLOSED -> {
                        Timber.d("Socket was CLOSED")
                        flag = false
                        disconnect()
                    }
                    EventType.ERROR -> {
                        Timber.d("Socket was CLOSED with ERROR")
                        flag = false
                        lifecycleSharedFlow.emit(lifecycleEvent)
                    }
                    EventType.FAILED_SERVER_HEARTBEAT -> TODO()
                }
            }.launchIn(CoroutineScope(dispatcher))

        messagesJob = connectionProvider.messages()
            .map { message -> StompMessage.from(message) }
            .filter { stompMessage -> heartBeatTask.consumeHeartBeat(stompMessage) }
            .onEach { getMessageSharedFlow().emit(it) }
            .filter { msg -> msg.stompFrame == FrameType.CONNECTED.name }
            .onEach {
                getConnectionSharedFlow().emit(true)
            }
            .catch { cause ->
                Timber.e("Error while parsing message: $cause")
            }.launchIn(CoroutineScope(dispatcher))

        return flag
    }

    private fun getConnectionSharedFlow(): MutableStateFlow<Boolean> = connectionSharedFlow

    fun getMessageSharedFlow(): MutableSharedFlow<StompMessage> = messageSharedFlow

    fun send(destination: String, data: String? = null): Boolean? {
        val headers = listOf(StompHeader(HeaderType.DESTINATION, destination))
        val stompMessage = StompMessage(FrameType.SEND.name, headers, data)
        return send(stompMessage)
    }

    fun send(stompMessage: StompMessage): Boolean? {
        return connectionProvider.send(stompMessage.compile(legacyWhitespace))
    }

    private fun sendHeartbeat(pingMessage: String): Boolean? {
        return connectionProvider.send(pingMessage)
    }

    fun sessionLifecycleFlow(): Flow<StompEvent> = lifecycleSharedFlow

    suspend fun reconnect(): Boolean? {
        return connect(headers)
    }

    suspend fun disconnect(): Boolean? {
        heartBeatTask.shutdown()

        lifecycleJob?.cancel()
        messagesJob?.cancel()

        Timber.d("Stomp Disconnected")
        getConnectionSharedFlow().collect()
        getMessageSharedFlow().collect()
        lifecycleSharedFlow.emit(StompEvent(EventType.CLOSED))

        return connectionProvider.disconnect()
    }

    @OptIn(FlowPreview::class)
    fun subscribe(destPath: String, headerList: List<StompHeader>? = null): Flow<StompMessage> {
        if (!streamMap.containsKey(destPath)) {
            try {
                subscribeOnPath(destPath, headerList)
                streamMap[destPath] = getMessageSharedFlow()
                    .filter { msg -> pathMatcher.matches(destPath, msg) }
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }
        return streamMap[destPath] ?: flowOf()
    }

    private fun subscribeOnPath(
        destinationPath: String,
        headerList: List<StompHeader>?
    ): Boolean? {
        val topicId = UUID.randomUUID().toString()
        if (topics == null) topics = HashMap()
        if (topics.containsKey(destinationPath)) {
            Timber.d("You already subscribed on this path")
            return false
        }

        topics[destinationPath] = topicId
        val headers = ArrayList<StompHeader>()
        headers.add(StompHeader(HeaderType.ID, topicId))
        headers.add(StompHeader(HeaderType.DESTINATION, destinationPath))
        headers.add(StompHeader(HeaderType.ACK, DEFAULT_ACK))

        if (headerList != null) headers.addAll(headerList)

        return try {
            send(StompMessage(FrameType.SUBSCRIBE.name, headers, null))
        } catch (e: Exception) {
            Timber.d(e.toString())
            unsubscribe(destinationPath)
            false
        }
    }

    fun unsubscribe(dest: String): Boolean? {
        streamMap.remove(dest)
        val topicId = topics.remove(dest)
        if (topicId != null) {
            return send(
                StompMessage(
                    FrameType.UNSUBSCRIBE.name,
                    listOf(StompHeader(HeaderType.ID, topicId)),
                    null
                )
            )
        }
        return false
    }

    fun isConnected(): Boolean = getConnectionSharedFlow().value

    fun getTopicId(dest: String): String? = topics[dest]
}
