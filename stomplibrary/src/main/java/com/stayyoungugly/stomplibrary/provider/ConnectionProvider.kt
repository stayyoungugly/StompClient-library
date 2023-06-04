package com.stayyoungugly.stomplibrary.provider

import com.stayyoungugly.stomplibrary.model.StompEvent
import kotlinx.coroutines.flow.Flow

interface ConnectionProvider {
    fun messages(): Flow<String>

    fun send(stompMessage: String)

    fun lifecycle(): Flow<StompEvent>

    suspend fun disconnect()
}
