package com.templatefinder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
// FLAG_TAKE_SCREENSHOT is available from API 30+
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.accessibilityservice.GestureDescription
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Accessibility Service for taking screenshots without root permissions.
 * Uses AccessibilityService.takeScreenshot() API available from Android 11 (API 30).
 */
class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenshotAccessibilityService"
        private const val MAX_QUEUE_SIZE = 10
        private const val REQUEST_TIMEOUT_MS = 30000L // 30 seconds
        private const val MIN_SCREENSHOT_INTERVAL_MS = 1000L // 1 second minimum between screenshots
        
        @Volatile
        private var instance: ScreenshotAccessibilityService? = null
        
        /**
         * Get the current instance of the service if it's running
         */
        fun getInstance(): ScreenshotAccessibilityService? = instance
        
        /**
         * Check if the service is currently running
         */
        fun isServiceRunning(): Boolean = instance != null
    }

    // Queue for managing screenshot requests
    private val screenshotRequestQueue = ConcurrentLinkedQueue<ScreenshotRequest>()
    private val isProcessingScreenshot = AtomicBoolean(false)
    private val lastScreenshotTime = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Service lifecycle listeners
    private val connectionListeners = mutableSetOf<ServiceConnectionListener>()
    
    /**
     * Interface for service connection events
     */
    interface ServiceConnectionListener {
        fun onServiceConnected()
        fun onServiceDisconnected()
    }

    /**
     * Data class for screenshot requests
     */
    private data class ScreenshotRequest(
        val callback: ScreenshotCallback,
        val timestamp: Long = System.currentTimeMillis(),
        val id: String = "screenshot_${System.nanoTime()}"
    )

    /**
     * Interface for screenshot callbacks
     */
    interface ScreenshotCallback {
        fun onScreenshotTaken(bitmap: Bitmap?)
        fun onScreenshotError(error: String)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        Log.d(TAG, "ScreenshotAccessibilityService connected")
        
        try {
            // Configure service info
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.DEFAULT
                
                // Add capabilities
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    this.capabilities = this.capabilities or AccessibilityServiceInfo.CAPABILITY_CAN_DISPATCH_GESTURES
                }
                
                // Enable screenshot capability if supported
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    flags = flags or 0x00000040 // FLAG_TAKE_SCREENSHOT value
                }
                
                // Set notification timeout
                notificationTimeout = 100
            }
            
            serviceInfo = info
            
            Log.d(TAG, "Service configured successfully")
            
            // Notify listeners
            notifyConnectionListeners(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring accessibility service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "ScreenshotAccessibilityService destroying...")
        
        // Clear pending requests and notify with errors
        clearPendingRequestsWithError("Service is being destroyed")
        
        // Notify listeners
        notifyConnectionListeners(false)
        
        // Clear listeners
        connectionListeners.clear()
        
        instance = null
        
        Log.d(TAG, "ScreenshotAccessibilityService destroyed")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "ScreenshotAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for screenshot functionality
        // This method is required by AccessibilityService but can be empty for our use case
    }

    override fun onInterrupt() {
        Log.w(TAG, "ScreenshotAccessibilityService interrupted")
    }

    /**
     * Add a service connection listener
     */
    fun addConnectionListener(listener: ServiceConnectionListener) {
        connectionListeners.add(listener)
    }

    /**
     * Remove a service connection listener
     */
    fun removeConnectionListener(listener: ServiceConnectionListener) {
        connectionListeners.remove(listener)
    }

    /**
     * Notify connection listeners
     */
    private fun notifyConnectionListeners(connected: Boolean) {
        connectionListeners.forEach { listener ->
            try {
                if (connected) {
                    listener.onServiceConnected()
                } else {
                    listener.onServiceDisconnected()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying connection listener", e)
            }
        }
    }

    /**
     * Take a screenshot using the AccessibilityService API
     * @param callback Callback to receive the screenshot result
     */
    fun takeScreenshot(callback: ScreenshotCallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback.onScreenshotError("Screenshot API requires Android 11 (API 30) or higher")
            return
        }

        // Check if queue is full
        if (screenshotRequestQueue.size >= MAX_QUEUE_SIZE) {
            callback.onScreenshotError("Screenshot request queue is full. Please try again later.")
            return
        }

        // Check minimum interval between screenshots
        val currentTime = System.currentTimeMillis()
        val timeSinceLastScreenshot = currentTime - lastScreenshotTime.get()
        
        if (timeSinceLastScreenshot < MIN_SCREENSHOT_INTERVAL_MS) {
            val waitTime = MIN_SCREENSHOT_INTERVAL_MS - timeSinceLastScreenshot
            Log.d(TAG, "Screenshot requested too soon, delaying by ${waitTime}ms")
            
            mainHandler.postDelayed({
                addScreenshotRequest(callback)
            }, waitTime)
        } else {
            addScreenshotRequest(callback)
        }
    }

    /**
     * Add screenshot request to queue and process
     */
    private fun addScreenshotRequest(callback: ScreenshotCallback) {
        val request = ScreenshotRequest(callback)
        screenshotRequestQueue.offer(request)
        
        Log.d(TAG, "Screenshot request added to queue. Queue size: ${screenshotRequestQueue.size}")
        
        // Clean up expired requests
        cleanupExpiredRequests()
        
        processNextScreenshotRequest()
    }

    /**
     * Process the next screenshot request in the queue
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun processNextScreenshotRequest() {
        if (isProcessingScreenshot.get()) {
            Log.d(TAG, "Screenshot already in progress, request queued")
            return
        }

        val request = screenshotRequestQueue.poll() ?: return
        
        if (!isProcessingScreenshot.compareAndSet(false, true)) {
            // Another thread started processing, re-queue the request
            screenshotRequestQueue.offer(request)
            return
        }

        try {
            Log.d(TAG, "Taking screenshot for request: ${request.id}")
            lastScreenshotTime.set(System.currentTimeMillis())
            
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )
                            
                            Log.d(TAG, "Screenshot taken successfully for request ${request.id}: ${bitmap?.width}x${bitmap?.height}")
                            request.callback.onScreenshotTaken(bitmap)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot result for request ${request.id}", e)
                            request.callback.onScreenshotError("Failed to process screenshot: ${e.message}")
                        } finally {
                            try {
                                screenshotResult.hardwareBuffer.close()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error closing hardware buffer", e)
                            }
                            
                            isProcessingScreenshot.set(false)
                            
                            // Process next request if any
                            if (screenshotRequestQueue.isNotEmpty()) {
                                mainHandler.post { processNextScreenshotRequest() }
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val errorMessage = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Internal error occurred"
                            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Invalid display"
                            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "No accessibility access"
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Screenshot interval too short"
                            else -> "Unknown error (code: $errorCode)"
                        }
                        
                        Log.e(TAG, "Screenshot failed for request ${request.id}: $errorMessage")
                        request.callback.onScreenshotError(errorMessage)
                        
                        isProcessingScreenshot.set(false)
                        
                        // Process next request if any with a small delay to avoid rapid failures
                        if (screenshotRequestQueue.isNotEmpty()) {
                            mainHandler.postDelayed({ processNextScreenshotRequest() }, 500)
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception while taking screenshot", e)
            request.callback.onScreenshotError("Exception: ${e.message}")
            isProcessingScreenshot.set(false)
            
            // Process next request if any
            if (screenshotRequestQueue.isNotEmpty()) {
                processNextScreenshotRequest()
            }
        }
    }

    /**
     * Clean up expired requests from the queue
     */
    private fun cleanupExpiredRequests() {
        val currentTime = System.currentTimeMillis()
        val iterator = screenshotRequestQueue.iterator()
        var removedCount = 0
        
        while (iterator.hasNext()) {
            val request = iterator.next()
            if (currentTime - request.timestamp > REQUEST_TIMEOUT_MS) {
                iterator.remove()
                request.callback.onScreenshotError("Screenshot request timed out")
                removedCount++
            }
        }
        
        if (removedCount > 0) {
            Log.d(TAG, "Removed $removedCount expired screenshot requests")
        }
    }

    /**
     * Get the number of pending screenshot requests
     */
    fun getPendingRequestsCount(): Int = screenshotRequestQueue.size

    /**
     * Clear all pending screenshot requests
     */
    fun clearPendingRequests() {
        screenshotRequestQueue.clear()
        Log.d(TAG, "Cleared all pending screenshot requests")
    }

    /**
     * Clear all pending requests with error notification
     */
    private fun clearPendingRequestsWithError(errorMessage: String) {
        val requests = mutableListOf<ScreenshotRequest>()
        
        // Drain the queue
        while (screenshotRequestQueue.isNotEmpty()) {
            screenshotRequestQueue.poll()?.let { requests.add(it) }
        }
        
        // Notify all requests with error
        requests.forEach { request ->
            try {
                request.callback.onScreenshotError(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying callback about service destruction", e)
            }
        }
        
        Log.d(TAG, "Cleared ${requests.size} pending screenshot requests with error: $errorMessage")
    }

    /**
     * Check if screenshot functionality is available on this device
     */
    fun isScreenshotSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Performs a click at the specified coordinates.
     */
    /**
     * Performs a multi-click gesture at the specified coordinates.
     */
    fun performMultiClick(x: Int, y: Int, radius: Int, count: Int, delay: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Gesture dispatch is not supported on this API level.")
            return
        }

        Log.d(TAG, "Performing multi-click at ($x, $y) with radius $radius")

        val gestureBuilder = GestureDescription.Builder()
        val clickDuration = 50L // Duration of each tap

        val points = listOf(
            Point(x, y),
            Point(x + radius, y),
            Point(x - radius, y),
            Point(x, y + radius),
            Point(x, y - radius)
        ).take(count)

        var startTime = 0L
        for (point in points) {
            val path = Path()
            path.moveTo(point.x.toFloat(), point.y.toFloat())
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, startTime, clickDuration))
            startTime += delay
        }

        val gesture = gestureBuilder.build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Multi-click gesture completed.")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Multi-click gesture cancelled.")
            }
        }, null)

        if (!dispatched) {
            Log.e(TAG, "Gesture dispatch failed immediately.")
        }
    }

    /**
     * Get service status information
     */
    fun getServiceStatus(): ServiceStatus {
        return ServiceStatus(
            isRunning = isServiceRunning(),
            isScreenshotSupported = isScreenshotSupported(),
            pendingRequests = getPendingRequestsCount(),
            isProcessingScreenshot = isProcessingScreenshot.get(),
            lastScreenshotTime = lastScreenshotTime.get()
        )
    }

    /**
     * Data class for service status
     */
    data class ServiceStatus(
        val isRunning: Boolean,
        val isScreenshotSupported: Boolean,
        val pendingRequests: Int,
        val isProcessingScreenshot: Boolean,
        val lastScreenshotTime: Long
    )
}