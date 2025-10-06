package com.templatefinder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.templatefinder.MainActivity
import com.templatefinder.R
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.AppSettings
import com.templatefinder.model.SearchResult
import com.templatefinder.model.Template
import com.templatefinder.util.ErrorHandler
import com.templatefinder.util.RobustnessManager
import com.templatefinder.util.PermissionManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service for continuous coordinate finding in the background
 */
class CoordinateFinderService : Service() {

    companion object {
        private const val TAG = "CoordinateFinderService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "coordinate_finder_channel"
        private const val CHANNEL_NAME = "Coordinate Finder"
        private const val DEFAULT_SEARCH_INTERVAL = 2000L // 2 seconds
        
        // Service actions
        const val ACTION_START_SEARCH = "com.templatefinder.START_SEARCH"
        const val ACTION_STOP_SEARCH = "com.templatefinder.STOP_SEARCH"
        const val ACTION_PAUSE_SEARCH = "com.templatefinder.PAUSE_SEARCH"
        const val ACTION_RESUME_SEARCH = "com.templatefinder.RESUME_SEARCH"
        
        // Service state
        @Volatile
        private var isServiceRunning = false
        
        fun isRunning(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (CoordinateFinderService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
        
        /**
         * Start the coordinate finder service
         */
        fun startService(context: Context) {
            val intent = Intent(context, CoordinateFinderService::class.java).apply {
                action = ACTION_START_SEARCH
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop the coordinate finder service
         */
        fun stopService(context: Context) {
            val intent = Intent(context, CoordinateFinderService::class.java).apply {
                action = ACTION_STOP_SEARCH
            }
            context.startService(intent)
        }
    }

    // Service components
    private lateinit var templateManager: TemplateManager
    private lateinit var templateMatchingService: TemplateMatchingService
    private lateinit var notificationManager: com.templatefinder.manager.NotificationManager
    private lateinit var autoOpenManager: com.templatefinder.manager.AutoOpenManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var robustnessManager: RobustnessManager
    private lateinit var permissionManager: PermissionManager
    private var appSettings: AppSettings? = null
    
    // Service state
    private val isSearchActive = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val searchCount = AtomicLong(0)
    private val lastSearchTime = AtomicLong(0)
    private var lastAction: String? = null
    private val shouldStopOnUnbind = AtomicBoolean(false)
    
    // Coroutine management
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job? = null
    
    // Handler for UI updates
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Current template and settings
    private var currentTemplate: Template? = null
    private var searchInterval: Long = DEFAULT_SEARCH_INTERVAL
    
    // Service callbacks
    private val callbacks = mutableSetOf<ServiceCallback>()
    
    /**
     * Interface for service callbacks
     */
    interface ServiceCallback {
        fun onSearchStarted()
        fun onSearchStopped()
        fun onSearchPaused()
        fun onSearchResumed()
        fun onResultFound(result: SearchResult)
        fun onSearchError(error: String)
        fun onStatusUpdate(status: ServiceStatus)
    }
    
    /**
     * Binder for local service binding
     */
    inner class LocalBinder : Binder() {
        fun getService(): CoordinateFinderService = this@CoordinateFinderService
    }
    
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        
        initializeComponents()
        createNotificationChannel()
        
        Log.d(TAG, "CoordinateFinderService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service unbound")
        if (shouldStopOnUnbind.get()) {
            stopSelf()
        }
        return super.onUnbind(intent)
    }

    fun prepareToStop() {
        shouldStopOnUnbind.set(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: lastAction
        lastAction = action

        when (action) {
            ACTION_START_SEARCH -> startSearch()
            ACTION_STOP_SEARCH -> stopSearch()
            ACTION_PAUSE_SEARCH -> pauseSearch()
            ACTION_RESUME_SEARCH -> resumeSearch()
        }
        
        return START_STICKY // Restart service if killed
    }

    private fun initializeComponents() {
        try {
            templateManager = TemplateManager(this)
            templateMatchingService = TemplateMatchingService(this)
            notificationManager = com.templatefinder.manager.NotificationManager(this)
            autoOpenManager = com.templatefinder.manager.AutoOpenManager(this)
            errorHandler = ErrorHandler.getInstance(this)
            robustnessManager = RobustnessManager.getInstance(this)
            permissionManager = PermissionManager(this)
            appSettings = AppSettings.load(this)

            // Start robustness monitoring
            robustnessManager.startMonitoring()
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error initializing service components", e)
            // Use basic error handling if ErrorHandler fails to initialize
            throw e
        }
        
        // Initialize OpenCV
        templateMatchingService.initializeOpenCV(object : TemplateMatchingService.OpenCVInitializationCallback {
            override fun onInitializationComplete(success: Boolean) {
                if (success) {
                    Log.d(TAG, "OpenCV initialized successfully")
                } else {
                    val error = "OpenCV initialization failed"
                    errorHandler.handleError(
                        category = ErrorHandler.CATEGORY_SYSTEM,
                        severity = ErrorHandler.SEVERITY_CRITICAL,
                        message = error,
                        context = "Service initialization",
                        recoverable = false
                    )
                    notifyError(error)
                }
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for coordinate finder service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startSearch() {
        if (isSearchActive.get()) {
            Log.d(TAG, "Search already active")
            return
        }
        
        Log.d(TAG, "Starting coordinate search")
        
        // Start foreground notification immediately
        startForeground(NOTIFICATION_ID, createNotification("Starting search..."))

        // Load current template
        try {
            currentTemplate = templateManager.loadCurrentTemplate()
            if (currentTemplate == null) {
                val error = "No template available for search"
                Log.e(TAG, error)
                errorHandler.handleError(
                    category = ErrorHandler.CATEGORY_TEMPLATE,
                    severity = ErrorHandler.SEVERITY_HIGH,
                    message = error,
                    context = "Starting search",
                    recoverable = false
                )
                notifyError(error)
                return
            } else {
                Log.d(TAG, "Template loaded successfully: ${currentTemplate!!.templateBitmap.width}x${currentTemplate!!.templateBitmap.height}")
                Log.d(TAG, "Template center: (${currentTemplate!!.centerX}, ${currentTemplate!!.centerY}), radius: ${currentTemplate!!.radius}")
                Log.d(TAG, "Template threshold: ${currentTemplate!!.matchThreshold}")
            }
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_TEMPLATE,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Failed to load template",
                throwable = e,
                context = "Starting search",
                recoverable = false
            )
            notifyError("Failed to load template: ${e.message}")
            return
        }
        
        // Load settings
        loadSettings()
        
        // Start search loop
        isSearchActive.set(true)
        isPaused.set(false)
        
        searchJob = serviceScope.launch {
            performSearchLoop()
        }
        
        notifyCallbacks { it.onSearchStarted() }
        updateNotification("Search active")
    }

    private fun stopSearch() {
        Log.d(TAG, "Stopping coordinate search")
        
        mainHandler.post {
            Toast.makeText(applicationContext, getString(R.string.search_service_stopped), Toast.LENGTH_SHORT).show()
        }

        isSearchActive.set(false)
        isPaused.set(false)
        
        searchJob?.cancel()
        searchJob = null
        
        notifyCallbacks { it.onSearchStopped() }
        
        // Stop foreground service and remove notification
        stopForeground(true)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        
        stopSelf()
    }

    private fun pauseSearch() {
        if (!isSearchActive.get()) {
            Log.d(TAG, "Search not active, cannot pause")
            return
        }
        
        Log.d(TAG, "Pausing coordinate search")
        isPaused.set(true)
        
        notifyCallbacks { it.onSearchPaused() }
        updateNotification("Search paused")
    }

    private fun resumeSearch() {
        if (!isSearchActive.get()) {
            Log.d(TAG, "Search not active, cannot resume")
            return
        }
        
        if (!isPaused.get()) {
            Log.d(TAG, "Search not paused")
            return
        }
        
        Log.d(TAG, "Resuming coordinate search")
        isPaused.set(false)
        
        notifyCallbacks { it.onSearchResumed() }
        updateNotification("Search active")
    }

    private suspend fun performSearchLoop() {
        while (isSearchActive.get() && !searchJob?.isCancelled!!) {
            try {
                if (!isPaused.get()) {
                    performSingleSearch()
                }
                
                // Wait for next search interval
                delay(searchInterval)
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Search loop cancelled")
                break
            } catch (e: Exception) {
                errorHandler.handleError(
                    category = ErrorHandler.CATEGORY_SERVICE,
                    severity = ErrorHandler.SEVERITY_CRITICAL,
                    message = "Error in search loop, stopping search.",
                    throwable = e,
                    context = "Search loop iteration ${searchCount.get()}",
                    recoverable = false
                )
                mainHandler.post {
                    notifyError("Search error: ${e.message}")
                    stopSearch()
                }
            }
        }
    }

    private suspend fun performSingleSearch() {
        val template = currentTemplate ?: return
        
        try {
            // Validate template before search
            if (!templateMatchingService.validateTemplate(template)) {
                notifyError("Invalid template for matching")
                return
            }
            
            // Get screenshot from accessibility service with retry logic
            val screenshot = captureScreenshotWithRetry(maxRetries = 30)
            
            // Preprocess screenshot if needed
            val processedScreenshot = preprocessScreenshotForMatching(screenshot)
            
            // Perform template matching with optimized parameters
            val result = performTemplateMatchingWithOptimization(processedScreenshot, template)
            
            // Update search statistics
            searchCount.incrementAndGet()
            lastSearchTime.set(System.currentTimeMillis())
            
            // Process and handle result
            processSearchResult(result)
            
            // Clean up bitmaps to prevent memory leaks
            if (processedScreenshot != screenshot) {
                robustnessManager.unregisterBitmap(processedScreenshot)
                processedScreenshot.recycle()
            }
            robustnessManager.unregisterBitmap(screenshot)
            screenshot.recycle()
            
        } catch (e: CancellationException) {
            Log.d(TAG, "Single search cancelled")
            throw e // Re-throw to be handled by the outer loop
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_MATCHING,
                severity = ErrorHandler.SEVERITY_MEDIUM,
                message = "Error in single search",
                throwable = e,
                context = "Search attempt ${searchCount.get()}",
                recoverable = true
            )
            throw e
        }
    }

    /**
     * Capture screenshot with retry logic
     */
    /**
     * Capture screenshot with retry logic, checking for accessibility service availability.
     */
    private suspend fun captureScreenshotWithRetry(maxRetries: Int, retryDelay: Long = 1000L): Bitmap {
        var attempt = 0
        val timeout = maxRetries * retryDelay
        
        while (attempt < maxRetries) {
            // 1. Check if the accessibility service is enabled by the user
            if (!permissionManager.isAccessibilityServiceEnabled()) {
                throw IllegalStateException("Screenshot Accessibility Service is not enabled. Please enable it in settings.")
            }

            // 2. Try to get the service instance
            val screenshotService = ScreenshotAccessibilityService.getInstance()
            if (screenshotService != null) {
                // 3. If instance is available, try to take a screenshot
                val screenshot = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Bitmap?> { continuation ->
                        screenshotService.takeScreenshot(object : ScreenshotAccessibilityService.ScreenshotCallback {
                            override fun onScreenshotTaken(bitmap: Bitmap?) {
                                continuation.resumeWith(Result.success(bitmap))
                            }

                            override fun onScreenshotError(error: String) {
                                Log.e(TAG, "Screenshot error on attempt ${attempt + 1}: $error")
                                continuation.resumeWith(Result.success(null))
                            }
                        })
                        
                        continuation.invokeOnCancellation {
                            Log.d(TAG, "Screenshot request cancelled")
                        }
                    }
                }
                if (screenshot != null) {
                    Log.d(TAG, "Screenshot captured successfully on attempt ${attempt + 1}")
                    robustnessManager.registerBitmap(screenshot)
                    return screenshot
                }
            }

            // 4. If service is not available or screenshot failed, wait and retry
            Log.w(TAG, "Attempt ${attempt + 1} to get screenshot service failed. Retrying in ${retryDelay}ms...")
            attempt++
            delay(retryDelay)
        }
        
        throw IllegalStateException("Failed to capture screenshot after $maxRetries retries (timeout: ${timeout}ms). The accessibility service might be crashing or unavailable.")
    }

    /**
     * Preprocess screenshot for optimal template matching
     */
    private fun preprocessScreenshotForMatching(screenshot: Bitmap): Bitmap {
        return try {
            // Use template matching service preprocessing if available
            val options = TemplateMatchingService.PreprocessingOptions(
                convertToGrayscale = true,
                applyGaussianBlur = false,
                applyHistogramEqualization = false,
                scaleFactor = 1.0f
            )
            
            templateMatchingService.preprocessBitmap(screenshot, options)
        } catch (e: Exception) {
            Log.w(TAG, "Error preprocessing screenshot, using original", e)
            screenshot
        }
    }

    /**
     * Perform template matching with optimization
     */
    private suspend fun performTemplateMatchingWithOptimization(
        screenshot: Bitmap,
        template: Template
    ): SearchResult {
        return withContext(Dispatchers.Default) {
            suspendCancellableCoroutine<SearchResult> { continuation ->
                try {
                    // Get optimized parameters for this template
                    val optimizedParams = templateMatchingService.optimizeMatchingParameters(template)
                    
                    // Use multi-scale matching for better accuracy
                    templateMatchingService.findTemplateMultiScale(
                        screenshot = screenshot,
                        template = template,
                        scales = optimizedParams.scales,
                        callback = object : TemplateMatchingService.TemplateMatchingCallback {
                            override fun onMatchingComplete(result: SearchResult) {
                                continuation.resumeWith(Result.success(result))
                            }

                            override fun onMatchingError(error: String) {
                                Log.e(TAG, "Template matching error: $error")
                                continuation.resumeWith(Result.success(SearchResult.failure()))
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during template matching", e)
                    continuation.resumeWith(Result.success(SearchResult.failure()))
                }
                
                continuation.invokeOnCancellation {
                    Log.d(TAG, "Template matching cancelled")
                }
            }
        }
    }

    /**
     * Process search result and update UI
     */
    private fun processSearchResult(result: SearchResult) {
        if (result.found) {
            Log.d(TAG, "Coordinates found: ${result.getFormattedCoordinates()} " +
                    "with confidence: ${result.getConfidencePercentage()}%")
            
            // Validate result quality
            val threshold = currentTemplate?.matchThreshold ?: 0.8f
            Log.d(TAG, "[CONFIDENCE DEBUG] Confidence: ${result.confidence}, Threshold: $threshold")

            val epsilon = 0.001f
            val isMatch = if (threshold >= 1.0f) {
                (1.0f - result.confidence) < epsilon
            } else {
                result.confidence >= threshold
            }

            if (isMatch) {
                notifyCallbacks { it.onResultFound(result) }
                updateNotification("Found: ${result.getFormattedCoordinates()}")
                
                // Show result notification
                notificationManager.showResultNotification(result)

                serviceScope.launch {
                    // delay(2000) // Temporarily removed delay to make click immediate
                    result.coordinates?.let {
                        val accessibilityService = ScreenshotAccessibilityService.getInstance()
                        if (accessibilityService != null) {
                            val clickX = it.x
                            val clickY = it.y

                            Log.d(TAG, "[CLICK DEBUG] Original Coords: (${it.x}, ${it.y})")
                            Log.d(TAG, "[CLICK DEBUG] Final Click Coords: (${clickX}, ${clickY})")

                            // Show a visual marker for debugging if enabled
                            if (appSettings?.showClickMarker == true) {
                                autoOpenManager.showClickMarker(clickX, clickY)
                            }
                            accessibilityService.performClick(clickX, clickY)
                        } else {
                            Log.w(TAG, "Accessibility service not available for auto-click.")
                        }
                    }
                }
                
                // Show overlay and auto-open app if configured
                // autoOpenManager.showResultOverlay(result)
                // autoOpenManager.bringAppToForeground(result)
                
                // For now, continue searching after finding result
                // This can be made configurable in future versions
                Log.d(TAG, "Result found, continuing search")
            } else {
                Log.d(TAG, "Result confidence too low: ${result.getConfidencePercentage()}%")
                updateNotification("Low confidence result (${searchCount.get()} attempts)")
            }
        } else {
            Log.d(TAG, "No coordinates found in search ${searchCount.get()}")
            updateNotification("Searching... (${searchCount.get()} attempts)")
        }
        
        // Notify status update
        notifyStatusUpdate()
    }

    private fun loadSettings() {
        appSettings = AppSettings.load(this)
        searchInterval = appSettings?.searchInterval ?: DEFAULT_SEARCH_INTERVAL
        Log.d(TAG, "Loaded search interval: ${searchInterval}ms")
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CoordinateFinderService::class.java).apply {
            action = ACTION_STOP_SEARCH
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Coordinate Finder")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Prevent sound on update
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyCallbacks(action: (ServiceCallback) -> Unit) {
        mainHandler.post {
            callbacks.forEach { callback ->
                try {
                    action(callback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying callback", e)
                }
            }
        }
    }

    private fun notifyError(error: String) {
        Log.e(TAG, error)
        notifyCallbacks { it.onSearchError(error) }
        
        // Show a dismissible alert notification for the error
        notificationManager.showAlertNotification("Search Error", error, isError = true)
    }

    private fun notifyStatusUpdate() {
        val status = ServiceStatus(
            isRunning = isSearchActive.get(),
            isPaused = isPaused.get(),
            searchCount = searchCount.get(),
            lastSearchTime = lastSearchTime.get(),
            searchInterval = searchInterval,
            hasTemplate = currentTemplate != null
        )
        
        notifyCallbacks { it.onStatusUpdate(status) }
    }

    /**
     * Add service callback
     */
    fun addCallback(callback: ServiceCallback) {
        callbacks.add(callback)
    }

    /**
     * Remove service callback
     */
    fun removeCallback(callback: ServiceCallback) {
        callbacks.remove(callback)
    }

    /**
     * Get current service status
     */
    fun getStatus(): ServiceStatus {
        return ServiceStatus(
            isRunning = isSearchActive.get(),
            isPaused = isPaused.get(),
            searchCount = searchCount.get(),
            lastSearchTime = lastSearchTime.get(),
            searchInterval = searchInterval,
            hasTemplate = currentTemplate != null
        )
    }

    /**
     * Update search settings
     */
    fun updateSettings() {
        loadSettings()
        notificationManager.updateSettings()
        Log.d(TAG, "Settings updated")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "CoordinateFinderService destroying")
        
        // Stop search
        isSearchActive.set(false)
        searchJob?.cancel()
        
        // Clean up
        serviceScope.cancel()
        callbacks.clear()
        templateMatchingService.cleanup()
        autoOpenManager.cleanup()
        robustnessManager.stopMonitoring()

        // Ensure notification is removed
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        
        Log.d(TAG, "CoordinateFinderService destroyed")
    }

    /**
     * Data class for service status
     */
    data class ServiceStatus(
        val isRunning: Boolean,
        val isPaused: Boolean,
        val searchCount: Long,
        val lastSearchTime: Long,
        val searchInterval: Long,
        val hasTemplate: Boolean
    )
}