package com.stayyoungugly.stompproject.activity

import android.content.Context
import com.stayyoungugly.stompproject.dto.SocketMessage
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.stayyoungugly.stomplibrary.client.StompClient
import com.stayyoungugly.stomplibrary.model.enum.ConnectionProviderType
import com.stayyoungugly.stomplibrary.model.enum.EventType
import com.stayyoungugly.stompproject.R
import com.stayyoungugly.stompproject.databinding.ActivityMainBinding
import com.stayyoungugly.stompproject.viewmodel.StompViewModel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val stompViewModel: StompViewModel by viewModels()

    private val uri = "http://192.168.3.8:80/guide/websocket"
    private lateinit var stompClient: StompClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setFields()
        initObservers()
        stompViewModel.createClient(ConnectionProviderType.OKHTTP, uri)
        with(binding) {
            btnSend.setOnClickListener {
                val text = etMessage.editText?.text
                val path = etPath.editText?.text
                if (!path.isNullOrEmpty()) {
                    if (!text.isNullOrEmpty()) {
                        sendStomp(
                            "/app/hello",
                            Gson().toJson(SocketMessage(name = text.toString()))
                        )
                        etMessage.editText?.setText("")
                    } else toast("Введите сообщение для отправки")
                } else toast("Введите необходимый путь для отправки")
            }
            btnConnect.setOnClickListener {
                connectStomp()
            }
            btnDisconnect.setOnClickListener {
                disconnectStomp()
                setFields()
            }
            btnSubscribe.setOnClickListener {
                val path = etPath.editText?.text
                if (!path.isNullOrEmpty()) {
                    subscribeStomp(path.toString())
                } else toast("Введите необходимый путь для подписки")
            }
            btnUnsubscribe.setOnClickListener {
                val path = etPath.editText?.text
                if (!path.isNullOrEmpty()) {
                    unsubscribeStomp(path.toString())
                } else toast("Введите необходимый путь для отписки")
            }
        }
    }

    private fun setConnectedFields() {
        with(binding) {
            btnConnect.visibility = View.INVISIBLE
            etClienthtbt.visibility = View.INVISIBLE
            etServerhtbt.visibility = View.INVISIBLE
            etPath.visibility = View.VISIBLE
            etMessage.visibility = View.VISIBLE
            tvServerhtbt.visibility = View.INVISIBLE
            tvClienthtbt.visibility = View.INVISIBLE
            btnDisconnect.visibility = View.VISIBLE
            btnSubscribe.visibility = View.VISIBLE
            btnUnsubscribe.visibility = View.VISIBLE
            btnSend.visibility = View.VISIBLE
            tvMessage.visibility = View.VISIBLE
            tvMessages.visibility = View.VISIBLE
            tvGet.visibility = View.VISIBLE
            tvPath.visibility = View.VISIBLE
        }
    }

    private fun setFields() {
        with(binding) {
            btnConnect.visibility = View.VISIBLE
            etClienthtbt.visibility = View.VISIBLE
            etServerhtbt.visibility = View.VISIBLE
            etPath.visibility = View.INVISIBLE
            etMessage.visibility = View.INVISIBLE
            tvServerhtbt.visibility = View.VISIBLE
            tvClienthtbt.visibility = View.VISIBLE
            btnDisconnect.visibility = View.INVISIBLE
            btnSubscribe.visibility = View.INVISIBLE
            btnUnsubscribe.visibility = View.INVISIBLE
            btnSend.visibility = View.INVISIBLE
            tvMessage.visibility = View.INVISIBLE
            tvMessages.visibility = View.INVISIBLE
            tvGet.visibility = View.INVISIBLE
            tvPath.visibility = View.INVISIBLE
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
                toast("Клиент был создан")
                stompClient = it
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
                toast("Вы отписались от сообщений")
            }, onFailure = {
                toast("Ошибка отправки запроса на отписку")
                Timber.e(it)
            })
        }

        stompViewModel.wasSubscribed.observe(this) { flag ->
            flag.fold(onSuccess = {
                toast("Вы подписались на сообщения")
            }, onFailure = {
                toast("Ошибка отправки запроса на подписку")
                Timber.e(it)
            })
        }

        stompViewModel.messages.observe(this) { message ->
            binding.tvGet.text = message.payload
            Timber.i("Received ${message.payload}")
        }

        stompViewModel.lifecycleEvents.observe(this) { lifecycleEvent ->
            when (lifecycleEvent.eventType) {
                EventType.OPENED -> {
                    toast("Соединение было успешно открыто")
                    setConnectedFields()
                }
                EventType.ERROR -> {
                    Timber.e("Stomp connection error: ${lifecycleEvent.eventException}")
                    toast("Ошибка соединения")
                }
                EventType.CLOSED -> {
                    setFields()
                    toast("Соединение было закрыто")
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
        _binding = null
    }
}
