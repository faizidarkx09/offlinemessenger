package com.offlinemessenger

import android.app.Application
import android.util.Log
import com.offlinemessenger.di.ServiceLocator
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Application entry point. Builds the [ServiceLocator] graph once so every
 * component (BLE, crypto, database, repositories) shares one instance.
 */
class OfflineMessengerApp : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashReporter()
        locator = ServiceLocator(this)
    }

    /**
     * Diagnostic crash reporter: writes the full stack trace of any uncaught
     * exception to internal storage before the process dies, so MainActivity
     * can show it on the next launch. Temporary diagnostic aid.
     */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                var cause = throwable.cause
                while (cause != null) {
                    pw.println("Caused by:")
                    cause.printStackTrace(pw)
                    cause = cause.cause
                }
                pw.flush()
                File(filesDir, CRASH_FILE).writeText("${Date()}\n\n${sw}")
                Log.e("OfflineMessenger", "Crash report saved", throwable)
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_FILE = "om_crash.txt"

        fun readCrashReport(app: Application): String? {
            val f = File(app.filesDir, CRASH_FILE)
            return if (f.exists()) f.readText() else null
        }

        fun clearCrashReport(app: Application) {
            try {
                File(app.filesDir, CRASH_FILE).delete()
            } catch (_: Exception) {
            }
        }
    }
}
