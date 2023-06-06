package com.stayyoungugly.stomplibrary.heartbeat

import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.HeaderType
import com.stayyoungugly.stomplibrary.model.enum.FrameType
import kotlinx.coroutines.*
import timber.log.Timber

class HeartBeatService(
    private val sendCallback: SendCallback,
    private val failedListener: FailedListener?
) {

    private val dispatcher = Dispatchers.Default

    private var serverHeartbeat = 0
    private var clientHeartbeat = 0

    var serverHeartbeatNew = 0
    var clientHeartbeatNew = 0

    private var lastServerHeartbeat: Long = 0

    private var clientSendHeartbeatJob: Job? = null
    private var serverCheckHeartbeatJob: Job? = null

    suspend fun consumeHeartBeat(message: StompMessage): Boolean {
        println(message)
        when (message.stompFrame) {
            FrameType.CONNECTED.name -> initHandshake(message.findHeader(HeaderType.HEART_BEAT.text))
            FrameType.SEND.name -> stopClientHeartbeatSending()
            FrameType.MESSAGE.name -> stopServerHeartbeatChecking()
            FrameType.UNKNOWN.name -> {
                if ("\n" == message.payload) {
                    Timber.d("< PONG from server >")
                    stopServerHeartbeatChecking()
                    return false
                }
            }
        }
        return true
    }

    fun shutdown() {
        clientSendHeartbeatJob?.cancel()
        serverCheckHeartbeatJob?.cancel()
        lastServerHeartbeat = 0
    }

    private suspend fun initHandshake(heartBeatHeader: String?) {
        if (heartBeatHeader != null) {
            val heartbeats = heartBeatHeader.split(",")
            if (clientHeartbeatNew > 0) {
                clientHeartbeat = maxOf(clientHeartbeatNew, heartbeats[1].toInt())
            }
            if (serverHeartbeatNew > 0) {
                serverHeartbeat = maxOf(serverHeartbeatNew, heartbeats[0].toInt())
            }
            Timber.e("ServerHeartBeat = $serverHeartbeat")
            Timber.e("ServerHeartBeat = $serverHeartbeat")
        }

        if (clientHeartbeat > 0 || serverHeartbeat > 0) {
            if (clientHeartbeat > 0) {
                Timber.d("Client will send messages every $clientHeartbeat MS")
                setScheduleClientHeartbeat()
            }
            if (serverHeartbeat > 0) {
                Timber.d("Client will get messages from server every $serverHeartbeat ms")
                setScheduleServerHeartbeat()
                lastServerHeartbeat = System.currentTimeMillis()
            }
        }
    }

    private suspend fun setScheduleServerHeartbeat() {
        if (serverHeartbeat > 0) {
            serverCheckHeartbeatJob = withContext(dispatcher) {
                launch {
                    while (isActive) {
                        delay(serverHeartbeat.toLong())
                        listenServerHeartbeat()
                    }
                }
            }
        }
    }

    private fun listenServerHeartbeat() {
        if (serverHeartbeat > 0) {
            val now = System.currentTimeMillis()
            val boundary = now - (3 * serverHeartbeat)
            if (lastServerHeartbeat < boundary) {
                Timber.d(
                    "Server not responding. Last received at '$lastServerHeartbeat'"
                )
                failedListener?.onServerHeartBeatFailed()
            } else {
                Timber.d(
                    "Server sent heartbeat on time"
                )
                lastServerHeartbeat = System.currentTimeMillis()
            }
        }
    }

    private suspend fun setScheduleClientHeartbeat() {
        if (clientHeartbeat > 0) {
            clientSendHeartbeatJob = withContext(dispatcher) {
                launch {
                    while (isActive) {
                        delay(clientHeartbeat.toLong())
                        sendClientHeartbeat()
                    }
                }
            }
        }
    }

    private fun sendClientHeartbeat() {
        sendCallback.sendClientHeartBeat("\r\n")
        Timber.d("< PING from client >")
    }

    private suspend fun stopClientHeartbeatSending() {
        clientSendHeartbeatJob?.cancel()
        setScheduleClientHeartbeat()
    }

    private suspend fun stopServerHeartbeatChecking() {
        lastServerHeartbeat = System.currentTimeMillis()
        Timber.d(
            "Server sent message early, ('$lastServerHeartbeat')."
        )
        serverCheckHeartbeatJob?.cancel()
        setScheduleServerHeartbeat()
    }

    fun interface FailedListener {
        fun onServerHeartBeatFailed()
    }

    fun interface SendCallback {
        fun sendClientHeartBeat(pingMessage: String)
    }
}
