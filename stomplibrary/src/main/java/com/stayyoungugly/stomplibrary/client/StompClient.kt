package com.stayyoungugly.stomplibrary.client

import android.util.Log
import com.stayyoungugly.stomplibrary.heartbeat.HeartBeat
import com.stayyoungugly.stomplibrary.matcher.BaseMatcher
import com.stayyoungugly.stomplibrary.matcher.PathMatcher
import com.stayyoungugly.stomplibrary.model.StompEvent
import com.stayyoungugly.stomplibrary.model.StompHeader
import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.EventType
import com.stayyoungugly.stomplibrary.model.enum.FrameType
import com.stayyoungugly.stomplibrary.model.enum.HeaderType
import com.stayyoungugly.stomplibrary.provider.ConnectionProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class StompClient(private val connectionProvider: ConnectionProvider) {

    companion object {
        private const val TAG = "com.stayyoungugly.stomplibrary.client.StompClient"
        const val SUPPORTED_VERSIONS = "1.1, 1.2"
        const val DEFAULT_ACK = "auto"
    }

    private val dispatcher = Dispatchers.Default

    private val scope = CoroutineScope(Dispatchers.Default)

    private var topics = ConcurrentHashMap<String, String>()

    private val streamMap = ConcurrentHashMap<String, Flow<StompMessage>>()

    var pathMatcher: PathMatcher = BaseMatcher()
    var legacyWhitespace = false

    private var lifecycleDisposable: Job? = null

    private var messagesDisposable: Job? = null

    private val lifecycleSharedFlow =
        MutableSharedFlow<StompEvent>(replay = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val messageSharedFlow =
        MutableSharedFlow<StompMessage>(replay = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val connectionSharedFlow = MutableStateFlow(false)

    private var headers: List<StompHeader>? = null

    private val heartBeatTask = HeartBeat(this::sendHeartBeat, this::failListener)

    init {
        heartBeatTask.serverHeartbeatNew = 0
        heartBeatTask.clientHeartbeatNew = 0
    }

    private fun failListener() {
        lifecycleSharedFlow.tryEmit(StompEvent(EventType.FAILED_SERVER_HEARTBEAT))
    }

    fun withServerHeartbeat(ms: Int): StompClient {
        heartBeatTask.serverHeartbeatNew = ms
        return this
    }

    fun withClientHeartbeat(ms: Int): StompClient {
        heartBeatTask.clientHeartbeatNew = ms
        return this
    }

    suspend fun connect(headers: List<StompHeader>?) {
        Log.d(TAG, "Connect")

        this.headers = headers

        if (isConnected()) {
            Log.d(TAG, "Already connected, ignore")
            return
        }

//        lifecycleDisposable?.cancel()
//        messagesDisposable?.cancel()

        lifecycleDisposable = connectionProvider.lifecycle()
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
                        connectionProvider.send(
                            StompMessage(FrameType.CONNECT.name, headers, null).compile(
                                legacyWhitespace
                            )
                        )
                        lifecycleSharedFlow.emit(lifecycleEvent)
                    }
                    EventType.CLOSED -> {
                        Log.d(TAG, "Socket closed")
                        disconnect()
                    }
                    EventType.ERROR -> {
                        Log.d(TAG, "Socket closed with error")
                        lifecycleSharedFlow.emit(lifecycleEvent)
                    }
                }
            }.launchIn(CoroutineScope(dispatcher))

        messagesDisposable = connectionProvider.messages()
            .map { message -> StompMessage.from(message) }
            .filter { stompMessage -> heartBeatTask.consumeHeartBeat(stompMessage) }
            .onEach { getMessageSharedFlow().emit(it) }
            .filter { msg -> msg.stompFrame == FrameType.CONNECTED.name }
            .onEach {
                getConnectionSharedFlow().emit(true)
            }
            .catch { cause ->
                Log.e(TAG, "Error parsing message", cause)
            }.launchIn(CoroutineScope(dispatcher))
    }

    private fun getConnectionSharedFlow(): MutableStateFlow<Boolean> = connectionSharedFlow

    private fun getMessageSharedFlow(): MutableSharedFlow<StompMessage> = messageSharedFlow

    fun send(destination: String, data: String? = null) {
        val headers = listOf(StompHeader(HeaderType.DESTINATION, destination))
        val stompMessage = StompMessage(FrameType.SEND.name, headers, data)
        send(stompMessage)
    }

    fun send(stompMessage: StompMessage) {
        val completable = connectionProvider.send(stompMessage.compile(legacyWhitespace))
    }

    private fun sendHeartBeat(pingMessage: String) {
        val completable = connectionProvider.send(pingMessage)
    }

    fun lifecycle(): Flow<StompEvent> = lifecycleSharedFlow

    suspend fun reconnect() {
        connect(headers)
    }

    suspend fun disconnect() {
        heartBeatTask.shutdown()

        lifecycleDisposable?.cancel()
        messagesDisposable?.cancel()

        Log.d(TAG, "Stomp disconnected")
        getConnectionSharedFlow().collect()
        getMessageSharedFlow().collect()
        lifecycleSharedFlow.emit(StompEvent(EventType.CLOSED))

        return connectionProvider.disconnect()
    }

    fun topic(destPath: String, headerList: List<StompHeader>): Flow<StompMessage> {
        if (destPath == null) {
            return flow {
                throw IllegalArgumentException("Topic path cannot be null")
            }
        } else if (!streamMap.containsKey(destPath)) {
            streamMap[destPath] = flow<String> {
                subscribePath(destPath, headerList)
            }.flatMapConcat {
                getMessageSharedFlow()
                    .filter { msg -> pathMatcher.matches(destPath, msg) }
            }.onCompletion {
                unsubscribePath(destPath)
            }
        }
        return streamMap[destPath] ?: flowOf()
    }

    fun subscribePath(
        destinationPath: String,
        headerList: List<StompHeader>?
    ) {
        val topicId = UUID.randomUUID().toString()

        if (topics == null) topics = ConcurrentHashMap()

        if (topics.containsKey(destinationPath)) {
            Log.d(TAG, "Attempted to subscribe to already-subscribed path!")
            return
        }

        topics[destinationPath] = topicId
        val headers = ArrayList<StompHeader>()
        headers.add(StompHeader(HeaderType.ID, topicId))
        headers.add(StompHeader(HeaderType.DESTINATION, destinationPath))
        headers.add(StompHeader(HeaderType.ACK, DEFAULT_ACK))
        if (headerList != null) headers.addAll(headerList)
        try {
            send(StompMessage(FrameType.SUBSCRIBE.name, headers, null))
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
            unsubscribePath(destinationPath)
        }
    }


    private fun unsubscribePath(dest: String) {
        streamMap.remove(dest)
        val topicId = topics.remove(dest)
        if (topicId != null) {
            send(
                StompMessage(
                    FrameType.UNSUBSCRIBE.name,
                    listOf(StompHeader(HeaderType.ID, topicId)),
                    null
                )
            )
        }
    }


    fun isConnected(): Boolean = getConnectionSharedFlow().value

    fun getTopicId(dest: String): String? = topics[dest]
}
