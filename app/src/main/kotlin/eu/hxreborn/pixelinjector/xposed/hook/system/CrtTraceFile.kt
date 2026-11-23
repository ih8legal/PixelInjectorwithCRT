package eu.hxreborn.pixelinjector.xposed.hook.system

import android.os.Process
import eu.hxreborn.pixelinjector.ModuleConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_BYTES = 64 * 1024

/**
 * Plain file the CRT hook appends diagnostics to, so they survive even where the framework's
 * module log is not captured. Lines mimic logcat so the app's log screen can parse them.
 */
internal object CrtTraceFile {
    private val lock = Any()
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun append(message: String) {
        runCatching {
            synchronized(lock) {
                val file = File(ModuleConstants.CRT_TRACE_FILE)
                if (file.length() > MAX_BYTES) file.delete()
                val stamp = clock.format(Date())
                val ids = "${Process.myPid()}  ${Process.myTid()}"
                file.appendText("$stamp  $ids I ${ModuleConstants.LOG_TAG}: $message\n")
            }
        }
    }
}
