package com.stayyoungugly.stomplibrary.provider

import com.stayyoungugly.stomplibrary.model.StompEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseConnectionProvider : ConnectionProvider {

    private val dispatcher = Dispatchers.Default

    private val TAG = BaseConnectionProvider::class.simpleName

    private val lifecycleStream = MutableSharedFlow<StompEvent>()
    private val messagesStream = MutableSharedFlow<String>()

    override fun messages(): Flow<String> = messagesStream

    override suspend fun disconnect() {
        rawDisconnect()
    }

    protected abstract fun createWebSocketConnection()
    protected abstract fun rawDisconnect()

    private fun initSocket(): Flow<String> = flow {
        createWebSocketConnection()
    }

    override fun send(stompMessage: String) {
        if (getSocket() == null) {
            throw IllegalStateException("Not connected")
        } else {
            println("Send STOMP message: $stompMessage")
            rawSend(stompMessage)
        }
    }

    protected abstract fun rawSend(stompMessage: String)

    protected abstract fun getSocket(): Any?

    protected fun emitStompEvent(lifecycleEvent: StompEvent) {
        println("Emit lifecycle event: ${lifecycleEvent.eventType.name}")
        lifecycleStream.tryEmit(lifecycleEvent)
    }

    protected fun emitMessage(stompMessage: String) {
        println("Receive STOMP message: $stompMessage")
        messagesStream.tryEmit(stompMessage)
    }

    override fun lifecycle(): Flow<StompEvent> = lifecycleStream

}
