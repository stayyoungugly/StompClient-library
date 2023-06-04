import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stayyoungugly.stomplibrary.client.StompClient
import com.stayyoungugly.stomplibrary.client.StompCreator
import com.stayyoungugly.stomplibrary.model.StompHeader
import com.stayyoungugly.stomplibrary.model.enum.ConnectionProviderType
import com.stayyoungugly.stompproject.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "MainActivity"
        private const val LOGIN = "login"
        private const val PASSCODE = "passcode"
    }

    private lateinit var mStompClient: StompClient
    private var mRestPingJob: Job? = null
    private var stompGenerator = StompCreator()
    private val mTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private lateinit var mRecyclerView: RecyclerView

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mStompClient = stompGenerator.create(
            ConnectionProviderType.OKHTTP,
            "/example-endpoint/websocket"
        )

        resetSubscriptions()
    }

    fun disconnectStomp(view: View) {
        mStompClient.disconnect()
    }

    fun connectStomp(view: View) {
        val headers = mutableListOf<StompHeader>()
        headers.add(StompHeader(LOGIN, "guest"))
        headers.add(StompHeader(PASSCODE, "guest"))

        mStompClient.withClientHeartbeat(1000).withServerHeartbeat(1000)

        resetSubscriptions()

        val dispLifecycle = mStompClient.lifecycle().onEach { lifecycleEvent ->
            when (lifecycleEvent.type) {
                Stomp.LifecycleEvent.Type.OPENED -> toast("Stomp connection opened")
                Stomp.LifecycleEvent.Type.ERROR -> {
                    Log.e(TAG, "Stomp connection error", lifecycleEvent.exception)
                    toast("Stomp connection error")
                }
                Stomp.LifecycleEvent.Type.CLOSED -> {
                    toast("Stomp connection closed")
                    resetSubscriptions()
                }
                Stomp.LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> toast("Stomp failed server heartbeat")
            }
        }.launchIn(scope)

        compositeDisposable.add(dispLifecycle)

        val dispTopic = mStompClient.topic("/topic/greetings").onEach { topicMessage ->
            Log.d(TAG, "Received ${topicMessage.payload}")
            addItem(mGson.fromJson(topicMessage.payload, EchoModel::class.java))
        }.onEachError { throwable ->
            Log.e(TAG, "Error on subscribe topic", throwable)
        }.launchIn(scope)

        compositeDisposable.add(dispTopic)

        mStompClient.connect(headers)
    }

    suspend fun sendEchoViaStomp(view: View) {
        mStompClient.send("/topic/hello-msg-mapping", "Echo STOMP ${mTimeFormat.format(Date())}")
            .onEach {
                Log.d(TAG, "STOMP echo send successfully")
            }.onEachError { throwable ->
                Log.e(TAG, "Error send STOMP echo", throwable)
                toast(throwable.message)
            }.launchIn(scope)
    }

    private fun toast(text: String?) {
        Log.i(TAG, text ?: "")
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun resetSubscriptions() {
        compositeDisposable.clear()
    }

    override fun onDestroy() {
//        mStompClient.disconnect()
        mRestPingJob?.cancel()
        compositeDisposable.dispose()
        super.onDestroy()
    }
}
