package com.stayyoungugly.stomplibrary.heartbeat

import android.util.Log
import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.HeaderType
import com.stayyoungugly.stomplibrary.model.enum.FrameType
import kotlinx.coroutines.*

class HeartBeat(
    private val sendCallback: SendCallback,
    private val failedListener: FailedListener?
) {

    private val dispatcher = Dispatchers.Default
    private val TAG = HeartBeat::class.simpleName

    private var serverHeartbeat = 0
    private var clientHeartbeat = 0

    var serverHeartbeatNew = 0
    var clientHeartbeatNew = 0

    private var lastServerHeartBeat: Long = 0

    private var clientSendHeartBeatJob: Job? = null
    private var serverCheckHeartBeatJob: Job? = null

    suspend fun consumeHeartBeat(message: StompMessage): Boolean {
        when (message.stompFrame) {
            FrameType.CONNECTED.name -> heartBeatHandshake(message.findHeader(HeaderType.HEART_BEAT.text))
            FrameType.SEND.name -> abortClientHeartBeatSend()
            FrameType.MESSAGE.name -> abortServerHeartBeatCheck()
            FrameType.UNKNOWN.name -> {
                if ("\n" == message.payload) {
                    Log.d(TAG, "<<< PONG")
                    abortServerHeartBeatCheck()
                    return false
                }
            }
        }
        return true
    }

    fun shutdown() {
        clientSendHeartBeatJob?.cancel()
        serverCheckHeartBeatJob?.cancel()
        lastServerHeartBeat = 0
    }

    private suspend fun heartBeatHandshake(heartBeatHeader: String?) {
        if (heartBeatHeader != null) {
            val heartbeats = heartBeatHeader.split(",")
            if (clientHeartbeatNew > 0) {
                clientHeartbeat = maxOf(clientHeartbeatNew, heartbeats[1].toInt())
            }
            if (serverHeartbeatNew > 0) {
                serverHeartbeat = maxOf(serverHeartbeatNew, heartbeats[0].toInt())
            }
        }

        if (clientHeartbeat > 0 || serverHeartbeat > 0) {
            if (clientHeartbeat > 0) {
                Log.d(TAG, "Client will send heart-beat every $clientHeartbeat ms")
                scheduleClientHeartBeat()
            }
            if (serverHeartbeat > 0) {
                Log.d(TAG, "Client will listen to server heart-beat every $serverHeartbeat ms")
                scheduleServerHeartBeatCheck()
                lastServerHeartBeat = System.currentTimeMillis()
            }
        }
    }

    private suspend fun scheduleServerHeartBeatCheck() {
        if (serverHeartbeat > 0) {
            serverCheckHeartBeatJob = withContext(dispatcher) {
                launch {
                    while (isActive) {
                        delay(serverHeartbeat.toLong())
                        checkServerHeartBeat()
                    }
                }
            }
        }
    }

    private fun checkServerHeartBeat() {
        if (serverHeartbeat > 0) {
            val now = System.currentTimeMillis()
            val boundary = now - (3 * serverHeartbeat)
            if (lastServerHeartBeat < boundary) {
                Log.d(
                    TAG,
                    "It's a sad day ;( Server didn't send heart-beat on time. Last received at '$lastServerHeartBeat' and now is '$now'"
                )
                failedListener?.onServerHeartBeatFailed()
            } else {
                Log.d(
                    TAG,
                    "We were checking and server sent heart-beat on time. So well-behaved :)"
                )
                lastServerHeartBeat = System.currentTimeMillis()
            }
        }
    }

    private suspend fun scheduleClientHeartBeat() {
        if (clientHeartbeat > 0) {
            clientSendHeartBeatJob = withContext(dispatcher) {
                launch {
                    while (isActive) {
                        delay(clientHeartbeat.toLong())
                        sendClientHeartBeat()
                    }
                }
            }
        }
    }

    private suspend fun sendClientHeartBeat() {
        sendCallback.sendClientHeartBeat("\r\n")
        Log.d(TAG, "PING >>>")
    }

    private suspend fun abortClientHeartBeatSend() {
        clientSendHeartBeatJob?.cancel()
        scheduleClientHeartBeat()
    }

    private suspend fun abortServerHeartBeatCheck() {
        lastServerHeartBeat = System.currentTimeMillis()
        Log.d(
            TAG,
            "Aborted last check because server sent heart-beat on time ('$lastServerHeartBeat'). So well-behaved :)"
        )
        serverCheckHeartBeatJob?.cancel()
        scheduleServerHeartBeatCheck()
    }

    fun interface FailedListener {
        fun onServerHeartBeatFailed()
    }

    fun interface SendCallback {
        fun sendClientHeartBeat(pingMessage: String)
    }
}
