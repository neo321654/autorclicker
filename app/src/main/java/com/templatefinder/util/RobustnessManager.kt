package com.templatefinder.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.templatefinder.service.CoordinateFinderService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Manager for handling robustness, edge cases, and system recovery
 */
class RobustnessManager(private val context: Context) {

    companion object {
        private const val TAG = "RobustnessManager"
        private const val MEMORY_THRESHOLD_MB = 50 // Low memory threshold in MB
        private const val MAX_RESTART_ATTEMPTS = 3
        private const val RESTART_DELAY_MS = 5000L // 5 seconds
        private const val HEALTH_CHECK_INTERVAL_MS = 30000L // 30 seconds
        
        @Volatile
        private var instance: RobustnessManager? = null
        
        fun getInstance(context: Context): RobustnessManager {
            return instance ?: synchronized(this) {
                instance ?: RobustnessManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val errorHandler = ErrorHandler.getInstance(context)
    private val logger = Logger.getInstance(context)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // Robustness state
    private val isMonitoring = AtomicBoolean(false)
    private val lastHealthCheck = AtomicLong(0)
    private val restartAttempts = AtomicLong(0)
    private val lastRestartTime = AtomicLong(0)
    
    // Memory management
    private val bitmapCache = mutableSetOf<Bitmap>()
    private var lastMemoryWarning = 0L

    /**
     * Start robustness monitoring
     */
    fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            logger.info(TAG, "Starting robustness monitoring")
            
            // Start periodic health checks
            scheduleHealthCheck()
        }
    }

    /**
     * Stop robustness monitoring
     */
    fun stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            logger.info(TAG, "Stopping robustness monitoring")
            
            // Clean up resources
            cleanupBitmapCache()
        }
    }

    /**
     * Handle memory pressure situation
     */
    fun handleMemoryPressure(level: Int) {
        logger.warning(TAG, "Memory pressure detected: level $level")
        
        try {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                    // Moderate memory pressure - clean up non-essential resources
                    cleanupNonEssentialResources()
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    // Low memory - more aggressive cleanup
                    cleanupBitmapCache()
                    System.gc() // Suggest garbage collection
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    // Critical memory - emergency cleanup
                    handleCriticalMemoryPressure()
                }
            }
            
            lastMemoryWarning = System.currentTimeMillis()
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SYSTEM,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Error handling memory pressure",
                throwable = e,
                context = "Memory pressure level: $level"
            )
        }
    }

    /**
     * Handle critical memory pressure
     */
    private fun handleCriticalMemoryPressure() {
        logger.error(TAG, "Critical memory pressure - emergency cleanup")
        
        // Clean up all cached bitmaps
        cleanupBitmapCache()
        
        // Force garbage collection
        System.gc()
        
        // Notify error handler
        errorHandler.handleError(
            category = ErrorHandler.CATEGORY_SYSTEM,
            severity = ErrorHandler.SEVERITY_CRITICAL,
            message = "Critical memory pressure detected",
            context = "Available memory below threshold"
        )
        
        // Consider pausing non-essential operations
        pauseNonEssentialOperations()
    }

    /**
     * Register bitmap for memory management
     */
    fun registerBitmap(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmapCache.add(bitmap)
            
            // Check if we should clean up
            if (shouldCleanupMemory()) {
                cleanupOldBitmaps()
            }
        }
    }

    /**
     * Unregister bitmap from memory management
     */
    fun unregisterBitmap(bitmap: Bitmap) {
        bitmapCache.remove(bitmap)
    }

    /**
     * Clean up bitmap cache
     */
    private fun cleanupBitmapCache() {
        logger.info(TAG, "Cleaning up bitmap cache: ${bitmapCache.size} bitmaps")
        
        val iterator = bitmapCache.iterator()
        var recycledCount = 0
        
        while (iterator.hasNext()) {
            val bitmap = iterator.next()
            if (!bitmap.isRecycled) {
                bitmap.recycle()
                recycledCount++
            }
            iterator.remove()
        }
        
        logger.info(TAG, "Recycled $recycledCount bitmaps")
    }

    /**
     * Clean up old bitmaps to free memory
     */
    private fun cleanupOldBitmaps() {
        val iterator = bitmapCache.iterator()
        var cleanedCount = 0
        val maxToClean = bitmapCache.size / 2 // Clean up half
        
        while (iterator.hasNext() && cleanedCount < maxToClean) {
            val bitmap = iterator.next()
            if (!bitmap.isRecycled) {
                bitmap.recycle()
                cleanedCount++
            }
            iterator.remove()
        }
        
        if (cleanedCount > 0) {
            logger.info(TAG, "Cleaned up $cleanedCount old bitmaps")
        }
    }

    /**
     * Check if memory cleanup is needed
     */
    private fun shouldCleanupMemory(): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
        return availableMemoryMB < MEMORY_THRESHOLD_MB || memoryInfo.lowMemory
    }

    /**
     * Clean up non-essential resources
     */
    private fun cleanupNonEssentialResources() {
        logger.info(TAG, "Cleaning up non-essential resources")
        
        // Clean up some cached bitmaps
        if (bitmapCache.size > 5) {
            cleanupOldBitmaps()
        }
        
        // Clear log cache if too large
        val loggingStats = logger.getLoggingStats()
        if (loggingStats.totalEntries > 1000) {
            logger.clearLogs()
        }
    }

    /**
     * Pause non-essential operations during critical situations
     */
    private fun pauseNonEssentialOperations() {
        logger.warning(TAG, "Pausing non-essential operations due to critical memory pressure")
        
        // Could pause template matching, reduce search frequency, etc.
        // This would require integration with the service
    }

    /**
     * Attempt to restart crashed service
     */
    fun attemptServiceRestart(): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Check if we've exceeded restart attempts
        if (restartAttempts.get() >= MAX_RESTART_ATTEMPTS) {
            val timeSinceLastRestart = currentTime - lastRestartTime.get()
            if (timeSinceLastRestart < RESTART_DELAY_MS * MAX_RESTART_ATTEMPTS) {
                logger.error(TAG, "Max restart attempts exceeded, not restarting service")
                return false
            } else {
                // Reset attempts after sufficient time has passed
                restartAttempts.set(0)
            }
        }
        
        try {
            logger.info(TAG, "Attempting to restart CoordinateFinderService")
            
            // Stop existing service
            CoordinateFinderService.stopService(context)
            
            // Wait a moment
            Thread.sleep(1000)
            
            // Start service again
            CoordinateFinderService.startService(context)
            
            restartAttempts.incrementAndGet()
            lastRestartTime.set(currentTime)
            
            logger.info(TAG, "Service restart attempted (attempt ${restartAttempts.get()})")
            return true
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SERVICE,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Failed to restart service",
                throwable = e,
                context = "Restart attempt ${restartAttempts.get()}"
            )
            return false
        }
    }

    /**
     * Validate template file integrity
     */
    fun validateTemplateFile(file: File): ValidationResult {
        return try {
            if (!file.exists()) {
                ValidationResult(false, "Template file does not exist")
            } else if (file.length() == 0L) {
                ValidationResult(false, "Template file is empty")
            } else if (file.length() > 10 * 1024 * 1024) { // 10MB limit
                ValidationResult(false, "Template file is too large")
            } else {
                // Try to read the file header
                val header = file.readBytes().take(100)
                if (header.isEmpty()) {
                    ValidationResult(false, "Cannot read template file")
                } else {
                    ValidationResult(true, "Template file is valid")
                }
            }
        } catch (e: Exception) {
            ValidationResult(false, "Error validating template file: ${e.message}")
        }
    }

    /**
     * Handle template file corruption
     */
    fun handleTemplateCorruption(file: File): RecoveryResult {
        logger.error(TAG, "Template file corruption detected: ${file.absolutePath}")
        
        return try {
            // Try to create backup if possible
            val backupFile = File(file.parent, "${file.name}.corrupted")
            if (file.exists()) {
                file.copyTo(backupFile, overwrite = true)
            }
            
            // Delete corrupted file
            file.delete()
            
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_TEMPLATE,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Template file corrupted and removed",
                context = "File: ${file.absolutePath}"
            )
            
            RecoveryResult(true, "Corrupted template file removed, backup created")
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_TEMPLATE,
                severity = ErrorHandler.SEVERITY_CRITICAL,
                message = "Failed to handle template corruption",
                throwable = e,
                context = "File: ${file.absolutePath}"
            )
            
            RecoveryResult(false, "Failed to recover from template corruption")
        }
    }

    /**
     * Perform health check
     */
    private fun performHealthCheck() {
        try {
            val currentTime = System.currentTimeMillis()
            lastHealthCheck.set(currentTime)
            
            // Check memory status
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            if (memoryInfo.lowMemory) {
                handleMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            }
            
            // Check service status
            if (!CoordinateFinderService.isRunning()) {
                logger.warning(TAG, "Service not running during health check")
                // Could attempt restart here if needed
            }
            
            // Log health status
            logger.debug(TAG, "Health check completed - Memory: ${memoryInfo.availMem / (1024 * 1024)}MB available")
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SYSTEM,
                severity = ErrorHandler.SEVERITY_MEDIUM,
                message = "Error during health check",
                throwable = e,
                context = "Periodic health check"
            )
        }
    }

    /**
     * Schedule next health check
     */
    private fun scheduleHealthCheck() {
        if (isMonitoring.get()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                performHealthCheck()
                scheduleHealthCheck() // Schedule next check
            }, HEALTH_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Get robustness statistics
     */
    fun getRobustnessStats(): RobustnessStats {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return RobustnessStats(
            isMonitoring = isMonitoring.get(),
            lastHealthCheck = lastHealthCheck.get(),
            restartAttempts = restartAttempts.get(),
            lastRestartTime = lastRestartTime.get(),
            cachedBitmaps = bitmapCache.size,
            availableMemoryMB = memoryInfo.availMem / (1024 * 1024),
            isLowMemory = memoryInfo.lowMemory,
            lastMemoryWarning = lastMemoryWarning
        )
    }

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )

    /**
     * Data class for recovery results
     */
    data class RecoveryResult(
        val success: Boolean,
        val message: String
    )

    /**
     * Data class for robustness statistics
     */
    data class RobustnessStats(
        val isMonitoring: Boolean,
        val lastHealthCheck: Long,
        val restartAttempts: Long,
        val lastRestartTime: Long,
        val cachedBitmaps: Int,
        val availableMemoryMB: Long,
        val isLowMemory: Boolean,
        val lastMemoryWarning: Long
    )
}