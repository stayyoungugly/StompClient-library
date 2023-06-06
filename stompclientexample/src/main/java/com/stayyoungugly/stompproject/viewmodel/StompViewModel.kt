package com.stayyoungugly.stompproject.viewmodel

import androidx.lifecycle.*
import com.stayyoungugly.stomplibrary.client.StompClient
import com.stayyoungugly.stomplibrary.client.StompCreator
import com.stayyoungugly.stomplibrary.model.StompEvent
import com.stayyoungugly.stomplibrary.model.StompHeader
import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.ConnectionProviderType
import com.stayyoungugly.stomplibrary.model.enum.HeaderType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber

class StompViewModel : ViewModel() {
    private val clientHeartBeat = 0
    private val serverHeartBeat = 0

    private val login = "LOGIN"

    private val password = "PASSWORD"

    private val _error: SingleLiveEvent<Throwable> = SingleLiveEvent()
    val error: LiveData<Throwable> = _error

    private val _client: MutableLiveData<Result<StompClient>> = MutableLiveData()
    val client: LiveData<Result<StompClient>> = _client

    private lateinit var stompClient: StompClient

    private val _wasSent: MutableLiveData<Result<Boolean>> = MutableLiveData()
    val wasSent: LiveData<Result<Boolean>> = _wasSent

    private val _wasSubscribed: MutableLiveData<Result<Boolean>> = MutableLiveData()
    val wasSubscribed: LiveData<Result<Boolean>> = _wasSubscribed

    private val _wasUnsubscribed: MutableLiveData<Result<Boolean>> = MutableLiveData()
    val wasUnsubscribed: LiveData<Result<Boolean>> = _wasUnsubscribed

    private val _wasConnected: MutableLiveData<Result<Boolean>> = MutableLiveData()
    val wasConnected: LiveData<Result<Boolean>> = _wasConnected

    private val _wasDisconnected: MutableLiveData<Result<Boolean>> = MutableLiveData()
    val wasDisconnected: LiveData<Result<Boolean>> = _wasDisconnected

    private var _lifecycleEvents: MutableLiveData<StompEvent> = MutableLiveData()
    val lifecycleEvents: LiveData<StompEvent> = _lifecycleEvents

    private var _messages: MutableLiveData<StompMessage> = MutableLiveData()
    val messages: LiveData<StompMessage> = _messages


    fun createClient(
        connectionProvider: ConnectionProviderType,
        uri: String,
        connectHttpHeaders: Map<String, String>? = null,
        okHttpClient: OkHttpClient? = null
    ) {
        viewModelScope.launch {
            try {
                val clientStomp =
                    StompCreator().createClient(
                        connectionProvider,
                        uri,
                        connectHttpHeaders,
                        okHttpClient
                    )
                _client.value = Result.success(clientStomp)
                stompClient = clientStomp
            } catch (ex: Exception) {
                _client.value = Result.failure(ex)
                _error.value = ex
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            try {
                val headers = mutableListOf<StompHeader>()
                headers.add(StompHeader(HeaderType.LOGIN, login))
                headers.add(StompHeader(HeaderType.PASSCODE, password))

                stompClient.setClientHeartbeatInMs(clientHeartBeat)
                    .setServerHeartbeatInMs(serverHeartBeat)

                val flagConnected = stompClient.connect(headers)
                _wasConnected.value = Result.success(flagConnected == true)
                getLifecycle()

            } catch (ex: Exception) {
                _wasConnected.value = Result.failure(ex)
                _error.value = ex
            }
        }
    }

    fun unsubscribe(destination: String) {
        viewModelScope.launch {
            try {
                val flagUnsubscribed = stompClient.unsubscribe(destination)
                _wasUnsubscribed.value = Result.success(flagUnsubscribed == true)
            } catch (ex: Exception) {
                _wasUnsubscribed.value = Result.failure(ex)
                _error.value = ex

            }
        }
    }

    fun getLifecycle() {
        viewModelScope.launch {
            try {
                stompClient.sessionLifecycleFlow().collect {
                    _lifecycleEvents.value = it
                }
            } catch (ex: Exception) {
                _error.value = ex
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                val flagDisconnected = stompClient.disconnect()
                _wasDisconnected.value = Result.success(flagDisconnected == true)

            } catch (ex: Exception) {
                _wasDisconnected.value = Result.failure(ex)
                _error.value = ex
            }
        }
    }

    fun subscribe(destination: String) {
        viewModelScope.launch {
            try {
                _wasSubscribed.value = Result.success(true)
                stompClient.subscribe(destination).collect {
                    _messages.value = it
                }
            } catch (ex: Exception) {
                _wasSubscribed.value = Result.failure(ex)
                _error.value = ex
            }
        }
    }

    fun sendMessage(destination: String, data: String? = null) {
        viewModelScope.launch {
            try {
                val flagSent = stompClient.send(destination, data)
                _wasSent.value = Result.success(flagSent == true)
            } catch (ex: Exception) {
                _wasSent.value = Result.failure(ex)
                _error.value = ex
            }
        }
    }

}
