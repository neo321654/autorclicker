package com.templatefinder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
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
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.util.ArrayDeque
import java.util.Deque
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

        fun getInstance(): ScreenshotAccessibilityService? = instance

        fun isServiceRunning(): Boolean = instance != null
    }

    // Queue for managing screenshot requests
    private val screenshotRequestQueue = ConcurrentLinkedQueue<ScreenshotRequest>()
    private val isProcessingScreenshot = AtomicBoolean(false)
    private val lastScreenshotTime = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    
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
        
        // Send broadcast
        val intent = Intent("com.easyclicker.ACCESSIBILITY_SERVICE_CONNECTED")
        sendBroadcast(intent)

        try {
            // Configure service info
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            var newFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityServiceInfo.DEFAULT or 0x00000040 // FLAG_TAKE_SCREENSHOT value
            } else {
                AccessibilityServiceInfo.DEFAULT
            }
            newFlags = newFlags or FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.flags = newFlags

            info.notificationTimeout = 100
            
            serviceInfo = info
            
            Log.d(TAG, "Service configured successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring accessibility service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "ScreenshotAccessibilityService destroying...")
        
        // Send broadcast
        val intent = Intent("com.easyclicker.ACCESSIBILITY_SERVICE_DISCONNECTED")
        sendBroadcast(intent)

        // Clear pending requests and notify with errors
        clearPendingRequestsWithError("Service is being destroyed")
        
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            processNextScreenshotRequest()
        } else {
            Log.w(TAG, "Screenshot API not supported on this API level.")
            callback.onScreenshotError("Screenshot API requires Android 11 (API 30) or higher")
        }
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
    /**
     * Performs a single, simple click at the specified coordinates.
     */
    /**
     * Performs a click using AccessibilityNodeInfo.ACTION_CLICK.
     * This method finds the node at the specified coordinates, traverses up to find a clickable parent,
     * and then performs the click action on that node. This is more reliable than coordinate-based gestures.
     * @param x The x-coordinate of the click.
     * @param y The y-coordinate of the click.
     */
    fun performClick(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.i(TAG, "GESTURE CLICK: Attempting realistic click via GestureDescription at ($x, $y)")
            
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            // Add a tiny, random movement
            val random = java.util.Random()
            val moveX = x + random.nextInt(3) - 1 // -1, 0, or 1
            val moveY = y + random.nextInt(3) - 1 // -1, 0, or 1
            path.lineTo(moveX.toFloat(), moveY.toFloat())

            val gestureBuilder = GestureDescription.Builder()
            // The gesture will have a duration of 100ms
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            
            dispatchGesture(gestureBuilder.build(), null, null)
        } else {
            Log.i(TAG, "NODE CLICK: Attempting click via AccessibilityNodeInfo at ($x, $y)")
            val root = rootInActiveWindow ?: run {
                Log.e(TAG, "NODE CLICK: Cannot perform action, rootInActiveWindow is null.")
                return
            }

            try {
                val node = findSmallestNodeAt(root, x, y)
                if (node == null) {
                    Log.w(TAG, "NODE CLICK: No node found at coordinates ($x, $y).")
                    return
                }

                var clickableNode: AccessibilityNodeInfo? = node
                while (clickableNode != null && !clickableNode.isClickable) {
                    clickableNode = clickableNode.parent
                }

                if (clickableNode != null) {
                    Log.i(TAG, "NODE CLICK: Found clickable node: ${clickableNode.className}. Performing ACTION_CLICK.")
                    val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "NODE CLICK: performAction(ACTION_CLICK) result: $success")
                } else {
                    Log.w(TAG, "NODE CLICK: No clickable node found at or above coordinates.")
                }

            } finally {
                root.recycle()
            }
        }
    }

    private fun findSmallestNodeAt(rootNode: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rootBounds = android.graphics.Rect()
        rootNode.getBoundsInScreen(rootBounds)
        if (!rootBounds.contains(x, y)) {
            return null
        }

        // DFS to find the smallest node containing the point
        val stack: Deque<AccessibilityNodeInfo> = ArrayDeque()
        stack.push(rootNode)
        var smallestNode: AccessibilityNodeInfo? = null

        while (stack.isNotEmpty()) {
            val currentNode = stack.pop()
            val bounds = android.graphics.Rect()
            currentNode.getBoundsInScreen(bounds)

            if (bounds.contains(x, y)) {
                smallestNode = currentNode // This is a better candidate
                // Search deeper
                for (i in 0 until currentNode.childCount) {
                    stack.push(currentNode.getChild(i))
                }
            }
        }
        return smallestNode
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