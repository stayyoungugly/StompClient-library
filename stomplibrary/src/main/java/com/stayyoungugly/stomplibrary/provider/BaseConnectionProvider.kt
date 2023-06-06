package com.stayyoungugly.stomplibrary.provider

import com.stayyoungugly.stomplibrary.model.StompEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import timber.log.Timber

abstract class BaseConnectionProvider : ConnectionProvider {

    private val dispatcher = Dispatchers.Default

    private val lifecycleStream = MutableSharedFlow<StompEvent>(replay = 5)
    private val messagesStream = MutableSharedFlow<String>(replay = 5)
    override suspend fun messages(): Flow<String> = messagesStream

    override suspend fun disconnect(): Boolean? {
        return rawDisconnect()
    }

    protected abstract fun createWebSocketConnection()
    protected abstract fun rawDisconnect(): Boolean?

    override fun initSocket() {
        createWebSocketConnection()
    }

    override fun send(stompMessage: String): Boolean? {
        if (getSocket() == null) {
            throw IllegalStateException("No CONNECTION")
        } else {
            println("Send STOMP message: $stompMessage")
            return rawSend(stompMessage)
        }
    }

    protected abstract fun rawSend(stompMessage: String): Boolean?

    protected abstract fun getSocket(): Any?

    protected fun emitStompEvent(lifecycleEvent: StompEvent) {
        Timber.i("Emit STOMP event during session: ${lifecycleEvent.eventType.name}")
        lifecycleStream.tryEmit(lifecycleEvent)

    }

    protected fun emitMessage(stompMessage: String) {
        Timber.i("Received message from STOMP Server: $stompMessage")
        messagesStream.tryEmit(stompMessage)
    }

    override fun sessionLifecycle(): Flow<StompEvent> = lifecycleStream

}
