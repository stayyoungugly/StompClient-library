package com.stayyoungugly.stomplibrary.provider

import com.stayyoungugly.stomplibrary.model.StompEvent
import com.stayyoungugly.stomplibrary.model.enum.EventType
import kotlinx.coroutines.coroutineScope
import okhttp3.*
import okio.ByteString
import timber.log.Timber
import java.util.*

class OkHttpConnectionProvider(
    private val uri: String,
    private val connectHttpHeaders: Map<String, String>?,
    private val okHttpClient: OkHttpClient
) : BaseConnectionProvider() {

    private var openSocket: WebSocket? = null

    override fun rawDisconnect(): Boolean? {
        return openSocket?.close(1000, "")
    }

    override fun createWebSocketConnection() {
        val requestBuilder = Request.Builder()
            .url(uri)

        addConnectionHeadersToBuilder(requestBuilder, connectHttpHeaders)

        openSocket = okHttpClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val openEvent = StompEvent(EventType.OPENED)
                    openEvent.responseHeaders = response.headers.toMap()
                    emitStompEvent(openEvent)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    emitMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    emitMessage(bytes.utf8())
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    openSocket = null
                    emitStompEvent(StompEvent(EventType.CLOSED))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    emitStompEvent(StompEvent(EventType.ERROR, Exception(t)))
                    openSocket = null
                    emitStompEvent(StompEvent(EventType.CLOSED))
                    Timber.e(t)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            }
        )
    }

    override fun rawSend(stompMessage: String): Boolean? {
        return openSocket?.send(stompMessage)
    }

    override fun getSocket(): Any? {
        return openSocket
    }

    private fun addConnectionHeadersToBuilder(
        requestBuilder: Request.Builder,
        connectHttpHeaders: Map<String, String>?
    ) {
        connectHttpHeaders?.let {
            for ((key, value) in it) {
                requestBuilder.addHeader(key, value)
            }
        }
    }
}
