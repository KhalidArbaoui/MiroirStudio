package studio.miroir.tether.tether

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import studio.miroir.tether.ptp.TetherLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app log the photographer can share with support when a camera misbehaves. */
object AppLog : TetherLog {
    private const val MAX_LINES = 1500
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lines = ArrayDeque<String>()
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    @Synchronized
    override fun log(message: String) {
        Log.i("MiroirTether", message)
        lines.addLast("${time.format(Date())}  $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
        _text.value = lines.joinToString("\n")
    }

    fun error(message: String, t: Throwable) {
        log("ERROR $message: ${t.javaClass.simpleName}: ${t.message}")
        t.stackTrace.take(6).forEach { log("    at $it") }
    }
}
