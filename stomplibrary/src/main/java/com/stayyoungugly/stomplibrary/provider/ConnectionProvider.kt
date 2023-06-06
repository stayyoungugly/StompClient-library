package com.stayyoungugly.stomplibrary.provider

import com.stayyoungugly.stomplibrary.model.StompEvent
import kotlinx.coroutines.flow.Flow

interface ConnectionProvider {
    suspend fun messages(): Flow<String>

    fun send(stompMessage: String): Boolean?

    fun sessionLifecycle(): Flow<StompEvent>

    suspend fun disconnect(): Boolean?
}
