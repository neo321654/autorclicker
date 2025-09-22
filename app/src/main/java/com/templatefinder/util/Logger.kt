package com.templatefinder.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import com.templatefinder.BuildConfig

/**
 * Enhanced logging system with file output and debugging capabilities
 */
class Logger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "Logger"
        private const val LOG_FILE_NAME = "app_log.txt"
        private const val MAX_LOG_FILE_SIZE = 2 * 1024 * 1024 // 2MB
        private const val MAX_LOG_ENTRIES = 5000
        
        @Volatile
        private var instance: Logger? = null
        
        fun getInstance(context: Context): Logger {
            return instance ?: synchronized(this) {
                instance ?: Logger(context.applicationContext).also { instance = it }
            }
        }
        
        // Convenience methods
        fun d(tag: String, message: String, context: Context? = null) {
            context?.let { getInstance(it).debug(tag, message) } ?: Log.d(tag, message)
        }
        
        fun i(tag: String, message: String, context: Context? = null) {
            context?.let { getInstance(it).info(tag, message) } ?: Log.i(tag, message)
        }
        
        fun w(tag: String, message: String, throwable: Throwable? = null, context: Context? = null) {
            context?.let { getInstance(it).warning(tag, message, throwable) } ?: Log.w(tag, message, throwable)
        }
        
        fun e(tag: String, message: String, throwable: Throwable? = null, context: Context? = null) {
            context?.let { getInstance(it).error(tag, message, throwable) } ?: Log.e(tag, message, throwable)
        }
    }

    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    // Logging configuration
    private var isFileLoggingEnabled = true
    private var isDebugLoggingEnabled = BuildConfig.DEBUG
    private var logLevel = LogLevel.DEBUG

    enum class LogLevel(val value: Int) {
        DEBUG(1),
        INFO(2),
        WARNING(3),
        ERROR(4)
    }

    /**
     * Log debug message
     */
    fun debug(tag: String, message: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            val entry = LogEntry(LogLevel.DEBUG, tag, message, null, System.currentTimeMillis())
            processLogEntry(entry)
        }
    }

    /**
     * Log info message
     */
    fun info(tag: String, message: String) {
        if (shouldLog(LogLevel.INFO)) {
            val entry = LogEntry(LogLevel.INFO, tag, message, null, System.currentTimeMillis())
            processLogEntry(entry)
        }
    }

    /**
     * Log warning message
     */
    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.WARNING)) {
            val entry = LogEntry(LogLevel.WARNING, tag, message, throwable, System.currentTimeMillis())
            processLogEntry(entry)
        }
    }

    /**
     * Log error message
     */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR)) {
            val entry = LogEntry(LogLevel.ERROR, tag, message, throwable, System.currentTimeMillis())
            processLogEntry(entry)
        }
    }

    /**
     * Process log entry
     */
    private fun processLogEntry(entry: LogEntry) {
        // Log to Android system log
        when (entry.level) {
            LogLevel.DEBUG -> Log.d(entry.tag, entry.message, entry.throwable)
            LogLevel.INFO -> Log.i(entry.tag, entry.message, entry.throwable)
            LogLevel.WARNING -> Log.w(entry.tag, entry.message, entry.throwable)
            LogLevel.ERROR -> Log.e(entry.tag, entry.message, entry.throwable)
        }
        
        // Add to queue
        logQueue.offer(entry)
        
        // Log to file if enabled
        if (isFileLoggingEnabled) {
            logToFile(entry)
        }
        
        // Clean up old entries
        cleanupOldEntries()
    }

    /**
     * Log entry to file
     */
    private fun logToFile(entry: LogEntry) {
        try {
            // Check file size and rotate if necessary
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                rotateLogFile()
            }
            
            FileWriter(logFile, true).use { writer ->
                PrintWriter(writer).use { printer ->
                    printer.println(formatLogEntry(entry))
                    entry.throwable?.printStackTrace(printer)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log to file", e)
        }
    }

    /**
     * Format log entry for file output
     */
    private fun formatLogEntry(entry: LogEntry): String {
        return "${dateFormat.format(Date(entry.timestamp))} " +
                "[${entry.level.name}] " +
                "[${entry.tag}] " +
                entry.message
    }

    /**
     * Check if should log based on level
     */
    private fun shouldLog(level: LogLevel): Boolean {
        return level.value >= logLevel.value && 
               (level != LogLevel.DEBUG || isDebugLoggingEnabled)
    }

    /**
     * Clean up old log entries
     */
    private fun cleanupOldEntries() {
        while (logQueue.size > MAX_LOG_ENTRIES) {
            logQueue.poll()
        }
    }

    /**
     * Rotate log file
     */
    private fun rotateLogFile() {
        try {
            val backupFile = File(context.filesDir, "${LOG_FILE_NAME}.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log file", e)
        }
    }

    /**
     * Get recent log entries
     */
    fun getRecentLogs(limit: Int = 100): List<LogEntry> {
        return logQueue.toList().takeLast(limit)
    }

    /**
     * Export log file
     */
    fun exportLogFile(): File? {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "app_log_export.txt")
            if (logFile.exists()) {
                logFile.copyTo(exportFile, overwrite = true)
                exportFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export log file", e)
            null
        }
    }

    /**
     * Clear log file and queue
     */
    fun clearLogs() {
        try {
            logQueue.clear()
            if (logFile.exists()) {
                logFile.delete()
            }
            Log.d(TAG, "Logs cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }

    /**
     * Configure logging settings
     */
    fun configure(
        fileLoggingEnabled: Boolean = true,
        debugLoggingEnabled: Boolean = true,
        logLevel: LogLevel = LogLevel.DEBUG
    ) {
        this.isFileLoggingEnabled = fileLoggingEnabled
        this.isDebugLoggingEnabled = debugLoggingEnabled
        this.logLevel = logLevel
        
        Log.d(TAG, "Logger configured: file=$fileLoggingEnabled, debug=$debugLoggingEnabled, level=$logLevel")
    }

    /**
     * Get logging statistics
     */
    fun getLoggingStats(): LoggingStats {
        val logCounts = mutableMapOf<LogLevel, Int>()
        logQueue.forEach { entry ->
            logCounts[entry.level] = (logCounts[entry.level] ?: 0) + 1
        }
        
        return LoggingStats(
            totalEntries = logQueue.size,
            logCounts = logCounts,
            fileSize = if (logFile.exists()) logFile.length() else 0L,
            isFileLoggingEnabled = isFileLoggingEnabled,
            isDebugLoggingEnabled = isDebugLoggingEnabled,
            currentLogLevel = logLevel
        )
    }

    /**
     * Data class for log entries
     */
    data class LogEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
        val timestamp: Long
    )

    /**
     * Data class for logging statistics
     */
    data class LoggingStats(
        val totalEntries: Int,
        val logCounts: Map<LogLevel, Int>,
        val fileSize: Long,
        val isFileLoggingEnabled: Boolean,
        val isDebugLoggingEnabled: Boolean,
        val currentLogLevel: LogLevel
    )
}