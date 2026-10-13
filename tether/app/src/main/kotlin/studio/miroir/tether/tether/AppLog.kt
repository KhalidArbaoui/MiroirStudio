package studio.miroir.tether.tether

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import studio.miroir.tether.ptp.TetherLog
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app log the photographer can share with support when a camera misbehaves.
 * Also written to a file, and a crash is saved so the next launch can show what happened.
 */
object AppLog : TetherLog {
    private const val MAX_LINES = 1500
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lines = ArrayDeque<String>()
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text
    private var writer: Writer? = null

    /**
     * Starts file logging and crash capture. Returns true when the app crashed last time;
     * the crash and the end of that session's log are then copied into this log.
     */
    @Synchronized
    fun init(dir: File): Boolean {
        if (writer != null) return false
        val current = File(dir, "tether.log")
        val previous = File(dir, "tether-previous.log")
        val crashFile = File(dir, "last-crash.txt")
        runCatching { if (current.exists()) { previous.delete(); current.renameTo(previous) } }
        writer = runCatching { current.bufferedWriter() }.getOrNull()

        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                crashFile.writeText("${time.format(Date())}  CRASH in thread ${thread.name}\n${e.stackTraceToString().take(6000)}")
                log("CRASH in ${thread.name}: $e")
            }
            system?.uncaughtException(thread, e)
        }

        val crash = runCatching { crashFile.takeIf { it.exists() }?.readText() }.getOrNull() ?: return false
        crashFile.delete()
        log("The app closed unexpectedly last time. End of that session:")
        runCatching { previous.readLines().takeLast(80).forEach { log("  | $it") } }
        log("Crash details:")
        crash.lines().take(40).forEach { log("  ! $it") }
        log("---- new session ----")
        return true
    }

    @Synchronized
    override fun log(message: String) {
        Log.i("MiroirTether", message)
        val line = "${time.format(Date())}  $message"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        _text.value = lines.joinToString("\n")
        writer?.let { runCatching { it.write(line); it.write("\n"); it.flush() } }
    }

    fun error(message: String, t: Throwable) {
        log("ERROR $message: ${t.javaClass.simpleName}: ${t.message}")
        t.stackTrace.take(6).forEach { log("    at $it") }
    }
}
