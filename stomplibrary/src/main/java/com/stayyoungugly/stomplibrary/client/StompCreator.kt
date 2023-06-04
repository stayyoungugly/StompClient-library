package com.stayyoungugly.stomplibrary.client

import com.stayyoungugly.stomplibrary.model.enum.ConnectionProviderType
import com.stayyoungugly.stomplibrary.provider.ConnectionProvider
import com.stayyoungugly.stomplibrary.provider.OkHttpConnectionProvider
import okhttp3.OkHttpClient

class StompCreator {

    fun create(
        connectionProvider: ConnectionProviderType,
        uri: String,
        connectHttpHeaders: Map<String, String>? = null,
        okHttpClient: OkHttpClient? = null
    ): StompClient {

        if (connectionProvider == ConnectionProviderType.OKHTTP) {
            return createStompClient(
                OkHttpConnectionProvider(
                    uri,
                    connectHttpHeaders,
                    okHttpClient ?: OkHttpClient()
                )
            )
        }
        throw IllegalArgumentException("ConnectionProvider type not supported: $connectionProvider")
    }

    private fun createStompClient(connectionProvider: ConnectionProvider): StompClient {
        return StompClient(connectionProvider)
    }
}
