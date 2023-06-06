package com.stayyoungugly.stompproject.activity

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.stayyoungugly.stomplibrary.client.StompClient
import com.stayyoungugly.stomplibrary.model.enum.ConnectionProviderType
import com.stayyoungugly.stomplibrary.model.enum.EventType
import com.stayyoungugly.stompproject.R
import com.stayyoungugly.stompproject.viewmodel.StompViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "com.stayyoungugly.stompproject.activity.MainActivity"
    }

    private val stompViewModel: StompViewModel by viewModels()

    private val uri = "http://192.168.3.8:80/guide/websocket"
    private lateinit var mStompClient: StompClient
    private val mTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initObservers()
        stompViewModel.createClient(ConnectionProviderType.OKHTTP, uri)
        //  sendStomp("/topic/hello", "Echo STOMP ${mTimeFormat.format(Date())}")
        findViewById<Button>(R.id.btn_send).setOnClickListener {
            // sendStomp("/app/hello", "Echo STOMP ${mTimeFormat.format(Date())}")
            stompViewModel.check()
            subscribeStomp("/topic/greetings")
        }
    }

    private fun connectStomp() {
        stompViewModel.connect()
    }

    private fun disconnectStomp() {
        stompViewModel.disconnect()
    }

    private fun sendStomp(destination: String, data: String? = null) {
        stompViewModel.sendMessage(
            destination, data
        )
    }

    private fun subscribeStomp(destination: String) {
        stompViewModel.subscribe(destination)
    }

    private fun unsubscribeStomp(destination: String) {
        stompViewModel.unsubscribe(destination)
    }

    private fun toast(text: String?) {
        Timber.i(text ?: "")
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun initObservers() {
        stompViewModel.client.observe(this) { client ->
            client.fold(onSuccess = {
                toast("Соединение было установлено")
                mStompClient = it
                connectStomp()
            }, onFailure = {
                toast("Ошибка подключения")
                Timber.e(it)
            })
        }

        stompViewModel.wasSent.observe(this) { flag ->
            flag.fold(onSuccess = {
                toast("Сообщение было отправлено")
            }, onFailure = {
                toast("Ошибка отправки")
                Timber.e(it)
            })
        }

        stompViewModel.wasConnected.observe(this) { flag ->
            flag.fold(onSuccess = {
                toast("Запрос на подключение был отправлен")
                //  stompViewModel.getLifecycle()
            }, onFailure = {
                toast("Ошибка подключения")
                Timber.e(it)
            })
        }

        stompViewModel.wasDisconnected.observe(this) { flag ->
            flag.fold(onSuccess = {
                toast("Запрос на отключение был отправлен")
            }, onFailure = {
                toast("Ошибка отключения")
                Timber.e(it)
            })
        }

        stompViewModel.wasUnsubscribed.observe(this) { flag ->
            flag.fold(onSuccess = {
                toast("Запрос на отписку был отправлен")
            }, onFailure = {
                toast("Ошибка отправки запроса на отписку")
                Timber.e(it)
            })
        }

        stompViewModel.wasSubscribed.observe(this) { flag ->
            flag.fold(onSuccess = {
                toast("Запрос на подписку был отправлен")
            }, onFailure = {
                toast("Ошибка отправки запроса на подписку")
                Timber.e(it)
            })
        }

        stompViewModel.messages.observe(this) { message ->
            toast(message.payload)
            Timber.i("Received ${message.payload}")
        }

        stompViewModel.lifecycleEvents.observe(this) { lifecycleEvent ->
            when (lifecycleEvent.eventType) {
                EventType.OPENED -> toast("Stomp connection opened")
                EventType.ERROR -> {
                    Timber.e("Stomp connection error: ${lifecycleEvent.eventException}")
                    toast("Stomp connection error")
                }
                EventType.CLOSED -> {
                    toast("Stomp connection closed")
                }
                EventType.FAILED_SERVER_HEARTBEAT -> toast("Stomp failed server heartbeat")
            }
        }

        stompViewModel.error.observe(this) {
            Timber.e(it.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
