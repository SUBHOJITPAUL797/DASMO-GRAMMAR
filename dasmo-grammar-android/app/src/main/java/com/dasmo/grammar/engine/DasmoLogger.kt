package com.dasmo.grammar.engine

import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enterprise-grade logging and diagnostics system for Dasmo Grammar.
 * Keeps an in-memory circular buffer of logs for immediate debugging on any device (Samsung, Pixel, Xiaomi, etc.)
 */
object DasmoLogger {

    private const val MAX_LOG_ENTRIES = 500
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun format(): String {
            val time = dateFormat.format(Date(timestamp))
            val base = "[$time] [$level/$tag] $message"
            return if (throwable != null) {
                "$base\n${Log.getStackTraceString(throwable)}"
            } else {
                base
            }
        }
    }

    private fun addEntry(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        logQueue.add(entry)
        while (logQueue.size > MAX_LOG_ENTRIES) {
            logQueue.poll()
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addEntry("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry("INFO", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        addEntry("WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        addEntry("ERROR", tag, message, throwable)
    }

    fun getLogs(): List<String> {
        return logQueue.map { it.format() }
    }

    fun getSystemDiagnosticsReport(engineReady: Boolean, dialect: String, dictCount: Int): String {
        val sb = StringBuilder()
        sb.append("=== DASMO GRAMMAR SYSTEM DIAGNOSTICS ===\n")
        sb.append("Generated At: ${dateFormat.format(Date())}\n")
        sb.append("Device Manufacturer: ${Build.MANUFACTURER}\n")
        sb.append("Device Model: ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android OS Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Hardware / Board: ${Build.BOARD} / ${Build.HARDWARE}\n")
        sb.append("Engine Status: ${if (engineReady) "READY (Online & Active)" else "INITIALIZING / PENDING"}\n")
        sb.append("Active Dialect: $dialect\n")
        sb.append("Personal Dictionary Size: $dictCount words\n")
        sb.append("Max Memory Allocated: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB\n")
        sb.append("Free Memory: ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB\n")
        sb.append("========================================\n\n")
        sb.append("--- RECENT EXECUTION LOGS (${logQueue.size} entries) ---\n")
        for (entry in logQueue) {
            sb.append(entry.format()).append("\n")
        }
        return sb.toString()
    }

    fun clear() {
        logQueue.clear()
    }
}
