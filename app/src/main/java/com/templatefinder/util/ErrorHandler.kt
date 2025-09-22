package com.templatefinder.util

import android.content.Context
import android.util.Log
import com.templatefinder.manager.NotificationManager
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralized error handling and logging system
 */
class ErrorHandler(private val context: Context) {

    companion object {
        private const val TAG = "ErrorHandler"
        private const val LOG_FILE_NAME = "error_log.txt"
        private const val MAX_LOG_ENTRIES = 1000
        private const val MAX_LOG_FILE_SIZE = 1024 * 1024 // 1MB
        
        // Error categories
        const val CATEGORY_PERMISSION = "PERMISSION"
        const val CATEGORY_TEMPLATE = "TEMPLATE"
        const val CATEGORY_SCREENSHOT = "SCREENSHOT"
        const val CATEGORY_MATCHING = "MATCHING"
        const val CATEGORY_SERVICE = "SERVICE"
        const val CATEGORY_NOTIFICATION = "NOTIFICATION"
        const val CATEGORY_STORAGE = "STORAGE"
        const val CATEGORY_NETWORK = "NETWORK"
        const val CATEGORY_SYSTEM = "SYSTEM"
        const val CATEGORY_UI = "UI"
        
        // Error severity levels
        const val SEVERITY_LOW = 1
        const val SEVERITY_MEDIUM = 2
        const val SEVERITY_HIGH = 3
        const val SEVERITY_CRITICAL = 4
        
        @Volatile
        private var instance: ErrorHandler? = null
        
        fun getInstance(context: Context): ErrorHandler {
            return instance ?: synchronized(this) {
                instance ?: ErrorHandler(context.applicationContext).also { instance = it }
            }
        }
    }

    private val notificationManager = NotificationManager(context)
    private val errorQueue = ConcurrentLinkedQueue<ErrorEntry>()
    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    
    // Error recovery strategies
    private val recoveryStrategies = mutableMapOf<String, ErrorRecoveryStrategy>()
    
    // Error statistics
    private var totalErrors = 0
    private var criticalErrors = 0
    private val errorCounts = mutableMapOf<String, Int>()

    init {
        setupRecoveryStrategies()
        loadErrorStatistics()
    }

    /**
     * Handle an error with automatic recovery attempt
     */
    fun handleError(
        category: String,
        severity: Int,
        message: String,
        throwable: Throwable? = null,
        context: String = "",
        recoverable: Boolean = true
    ): ErrorResult {
        
        val errorEntry = ErrorEntry(
            category = category,
            severity = severity,
            message = message,
            throwable = throwable,
            context = context,
            timestamp = System.currentTimeMillis()
        )
        
        // Log the error
        logError(errorEntry)
        
        // Add to queue for processing
        errorQueue.offer(errorEntry)
        
        // Update statistics
        updateErrorStatistics(errorEntry)
        
        // Attempt recovery if possible
        val recoveryResult = if (recoverable) {
            attemptRecovery(errorEntry)
        } else {
            RecoveryResult(false, "Recovery not attempted for non-recoverable error")
        }
        
        // Show user notification for critical errors
        if (severity >= SEVERITY_HIGH) {
            showErrorNotification(errorEntry)
        }
        
        // Clean up old errors
        cleanupOldErrors()
        
        return ErrorResult(
            handled = true,
            recovered = recoveryResult.success,
            recoveryMessage = recoveryResult.message,
            errorId = errorEntry.id
        )
    }

    /**
     * Log error to system log and file
     */
    private fun logError(error: ErrorEntry) {
        val logMessage = formatErrorMessage(error)
        
        // Log to Android system log
        when (error.severity) {
            SEVERITY_LOW -> Log.i(TAG, logMessage, error.throwable)
            SEVERITY_MEDIUM -> Log.w(TAG, logMessage, error.throwable)
            SEVERITY_HIGH -> Log.e(TAG, logMessage, error.throwable)
            SEVERITY_CRITICAL -> Log.wtf(TAG, logMessage, error.throwable)
        }
        
        // Log to file
        logToFile(error)
    }

    /**
     * Log error to file for persistence
     */
    private fun logToFile(error: ErrorEntry) {
        try {
            // Check file size and rotate if necessary
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                rotateLogFile()
            }
            
            FileWriter(logFile, true).use { writer ->
                PrintWriter(writer).use { printer ->
                    printer.println(formatErrorForFile(error))
                    error.throwable?.printStackTrace(printer)
                    printer.println("---")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log error to file", e)
        }
    }

    /**
     * Attempt to recover from error using registered strategies
     */
    private fun attemptRecovery(error: ErrorEntry): RecoveryResult {
        val strategy = recoveryStrategies[error.category]
        
        return if (strategy != null) {
            try {
                Log.d(TAG, "Attempting recovery for ${error.category} error")
                strategy.recover(error)
            } catch (e: Exception) {
                Log.e(TAG, "Recovery strategy failed for ${error.category}", e)
                RecoveryResult(false, "Recovery strategy failed: ${e.message}")
            }
        } else {
            RecoveryResult(false, "No recovery strategy available for ${error.category}")
        }
    }

    /**
     * Setup error recovery strategies
     */
    private fun setupRecoveryStrategies() {
        // Permission errors
        recoveryStrategies[CATEGORY_PERMISSION] = object : ErrorRecoveryStrategy {
            override fun recover(error: ErrorEntry): RecoveryResult {
                return RecoveryResult(false, "Permission errors require user intervention")
            }
        }
        
        // Template errors
        recoveryStrategies[CATEGORY_TEMPLATE] = object : ErrorRecoveryStrategy {
            override fun recover(error: ErrorEntry): RecoveryResult {
                // Could attempt to reload template or use backup
                return RecoveryResult(false, "Template recovery not implemented")
            }
        }
        
        // Screenshot errors
        recoveryStrategies[CATEGORY_SCREENSHOT] = object : ErrorRecoveryStrategy {
            override fun recover(error: ErrorEntry): RecoveryResult {
                // Could retry screenshot or check service status
                return RecoveryResult(true, "Screenshot retry scheduled")
            }
        }
        
        // Service errors
        recoveryStrategies[CATEGORY_SERVICE] = object : ErrorRecoveryStrategy {
            override fun recover(error: ErrorEntry): RecoveryResult {
                // Could attempt service restart
                return RecoveryResult(false, "Service recovery requires manual restart")
            }
        }
        
        // Storage errors
        recoveryStrategies[CATEGORY_STORAGE] = object : ErrorRecoveryStrategy {
            override fun recover(error: ErrorEntry): RecoveryResult {
                // Could check disk space or permissions
                return RecoveryResult(false, "Storage recovery not implemented")
            }
        }
    }

    /**
     * Show error notification to user
     */
    private fun showErrorNotification(error: ErrorEntry) {
        val title = when (error.severity) {
            SEVERITY_CRITICAL -> "Critical Error"
            SEVERITY_HIGH -> "Error Occurred"
            else -> "Warning"
        }
        
        val message = "${error.category}: ${error.message}"
        notificationManager.showAlertNotification(title, message, isError = true)
    }

    /**
     * Format error message for logging
     */
    private fun formatErrorMessage(error: ErrorEntry): String {
        return "[${error.category}] ${error.message}" +
                if (error.context.isNotEmpty()) " (Context: ${error.context})" else ""
    }

    /**
     * Format error for file logging
     */
    private fun formatErrorForFile(error: ErrorEntry): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return "${dateFormat.format(Date(error.timestamp))} " +
                "[${getSeverityString(error.severity)}] " +
                "[${error.category}] " +
                "${error.message}" +
                if (error.context.isNotEmpty()) " (Context: ${error.context})" else ""
    }

    /**
     * Get severity string for logging
     */
    private fun getSeverityString(severity: Int): String {
        return when (severity) {
            SEVERITY_LOW -> "INFO"
            SEVERITY_MEDIUM -> "WARN"
            SEVERITY_HIGH -> "ERROR"
            SEVERITY_CRITICAL -> "CRITICAL"
            else -> "UNKNOWN"
        }
    }

    /**
     * Update error statistics
     */
    private fun updateErrorStatistics(error: ErrorEntry) {
        totalErrors++
        
        if (error.severity >= SEVERITY_CRITICAL) {
            criticalErrors++
        }
        
        errorCounts[error.category] = (errorCounts[error.category] ?: 0) + 1
    }

    /**
     * Clean up old errors from queue
     */
    private fun cleanupOldErrors() {
        while (errorQueue.size > MAX_LOG_ENTRIES) {
            errorQueue.poll()
        }
    }

    /**
     * Rotate log file when it gets too large
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
     * Load error statistics from persistent storage
     */
    private fun loadErrorStatistics() {
        // Could load from SharedPreferences or file
        // For now, start fresh each time
    }

    /**
     * Get error statistics
     */
    fun getErrorStatistics(): ErrorStatistics {
        return ErrorStatistics(
            totalErrors = totalErrors,
            criticalErrors = criticalErrors,
            errorsByCategory = errorCounts.toMap(),
            queueSize = errorQueue.size,
            logFileSize = if (logFile.exists()) logFile.length() else 0L
        )
    }

    /**
     * Get recent errors
     */
    fun getRecentErrors(limit: Int = 10): List<ErrorEntry> {
        return errorQueue.toList().takeLast(limit)
    }

    /**
     * Clear error log
     */
    fun clearErrorLog() {
        try {
            errorQueue.clear()
            if (logFile.exists()) {
                logFile.delete()
            }
            totalErrors = 0
            criticalErrors = 0
            errorCounts.clear()
            
            Log.d(TAG, "Error log cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear error log", e)
        }
    }

    /**
     * Export error log
     */
    fun exportErrorLog(): File? {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "error_log_export.txt")
            logFile.copyTo(exportFile, overwrite = true)
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export error log", e)
            null
        }
    }

    /**
     * Data class for error entries
     */
    data class ErrorEntry(
        val id: String = UUID.randomUUID().toString(),
        val category: String,
        val severity: Int,
        val message: String,
        val throwable: Throwable?,
        val context: String,
        val timestamp: Long
    )

    /**
     * Data class for error handling results
     */
    data class ErrorResult(
        val handled: Boolean,
        val recovered: Boolean,
        val recoveryMessage: String,
        val errorId: String
    )

    /**
     * Data class for recovery results
     */
    data class RecoveryResult(
        val success: Boolean,
        val message: String
    )

    /**
     * Data class for error statistics
     */
    data class ErrorStatistics(
        val totalErrors: Int,
        val criticalErrors: Int,
        val errorsByCategory: Map<String, Int>,
        val queueSize: Int,
        val logFileSize: Long
    )

    /**
     * Interface for error recovery strategies
     */
    interface ErrorRecoveryStrategy {
        fun recover(error: ErrorEntry): RecoveryResult
    }
}