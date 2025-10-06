package com.templatefinder.controller

import android.content.Context
import android.util.Log
import com.templatefinder.manager.ServiceCommunicationManager
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.SearchResult
import com.templatefinder.service.CoordinateFinderService
import com.templatefinder.util.ErrorHandler
import com.templatefinder.util.Logger
import com.templatefinder.util.PermissionManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Controller for managing search lifecycle and state
 */
class SearchController(private val context: Context) {

    companion object {
        private const val TAG = "SearchController"
    }

    private val permissionManager = PermissionManager(context)
    private val templateManager = TemplateManager(context)
    private val serviceCommunicationManager = ServiceCommunicationManager(context)
    private val errorHandler = ErrorHandler.getInstance(context)
    private val logger = Logger.getInstance(context)

    // Search state
    private val isSearchActive = AtomicBoolean(false)
    private val isSearchPaused = AtomicBoolean(false)
    private val searchStartTime = AtomicLong(0)
    private val searchPauseTime = AtomicLong(0)
    private val totalPausedTime = AtomicLong(0)

    // Search statistics
    private val searchAttempts = AtomicLong(0)
    private val successfulFinds = AtomicLong(0)
    private var lastResult: SearchResult? = null

    // Listeners
    private val stateChangeListeners = mutableSetOf<SearchStateChangeListener>()

    /**
     * Interface for search state change notifications
     */
    interface SearchStateChangeListener {
        fun onSearchStarted()
        fun onSearchStopped()
        fun onSearchPaused()
        fun onSearchResumed()
        fun onSearchError(error: String)
        fun onResultFound(result: SearchResult)
        fun onStateChanged(state: SearchState)
    }

    /**
     * Enum for search states
     */
    enum class SearchState {
        STOPPED,
        STARTING,
        ACTIVE,
        PAUSING,
        PAUSED,
        RESUMING,
        STOPPING,
        ERROR
    }

    private var currentState = SearchState.STOPPED

    private val serviceCommunicationCallback = object : ServiceCommunicationManager.ServiceCommunicationCallback {
        override fun onServiceConnected() {
            logger.debug(TAG, "Service connected")
        }

        override fun onServiceDisconnected() {
            logger.debug(TAG, "Service disconnected")
            if (isSearchActive.get()) {
                handleUnexpectedStop()
            }
        }

        override fun onSearchStarted() {
            isSearchActive.set(true)
            isSearchPaused.set(false)
            searchStartTime.set(System.currentTimeMillis())
            totalPausedTime.set(0)
            
            updateState(SearchState.ACTIVE)
            notifyListeners { it.onSearchStarted() }
            
            logger.info(TAG, "Search started successfully")
        }

        override fun onSearchStopped() {
            isSearchActive.set(false)
            isSearchPaused.set(false)
            
            updateState(SearchState.STOPPED)
            notifyListeners { it.onSearchStopped() }
            
            logger.info(TAG, "Search stopped")
        }

        override fun onResultFound(result: SearchResult) {
            lastResult = result
            searchAttempts.incrementAndGet()
            
            if (result.found) {
                successfulFinds.incrementAndGet()
            }
            
            notifyListeners { it.onResultFound(result) }
            
            logger.info(TAG, "Search result: ${if (result.found) "Found" else "Not found"}")
        }

        override fun onSearchError(error: String) {
            updateState(SearchState.ERROR)
            notifyListeners { it.onSearchError(error) }
            
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SERVICE,
                severity = ErrorHandler.SEVERITY_MEDIUM,
                message = "Search error reported by service",
                context = error
            )
        }

        override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {
            // Update internal state based on service status
            if (status.isRunning != isSearchActive.get()) {
                isSearchActive.set(status.isRunning)
                
                if (status.isRunning && currentState == SearchState.STOPPED) {
                    updateState(SearchState.ACTIVE)
                } else if (!status.isRunning && currentState == SearchState.ACTIVE) {
                    updateState(SearchState.STOPPED)
                }
            }
        }
    }

    init {
        // Set up service communication
        serviceCommunicationManager.addCallback(serviceCommunicationCallback)
    }

    /**
     * Start the search
     */
    fun startSearch(): SearchControlResult {
        logger.info(TAG, "Starting search...")
        
        // Check if already active
        if (isSearchActive.get()) {
            return SearchControlResult(false, "Search is already active")
        }

        // Validate preconditions
        val validationResult = validateSearchPreconditions()
        if (!validationResult.isValid) {
            return SearchControlResult(false, validationResult.message)
        }

        try {
            updateState(SearchState.STARTING)
            
            // Bind to service first
            serviceCommunicationManager.bindService()
            
            // Start the search service
            val success = serviceCommunicationManager.startSearch()
            
            if (success) {
                logger.info(TAG, "Search start request sent successfully")
                return SearchControlResult(true, "Search started")
            } else {
                updateState(SearchState.ERROR)
                val error = "Failed to start search service"
                errorHandler.handleError(
                    category = ErrorHandler.CATEGORY_SERVICE,
                    severity = ErrorHandler.SEVERITY_HIGH,
                    message = error,
                    context = "Starting search"
                )
                return SearchControlResult(false, error)
            }
            
        } catch (e: Exception) {
            updateState(SearchState.ERROR)
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SERVICE,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Exception starting search",
                throwable = e,
                context = "SearchController.startSearch"
            )
            return SearchControlResult(false, "Error starting search: ${e.message}")
        }
    }

    /**
     * Stop the search
     */
    fun stopSearch(): SearchControlResult {
        logger.info(TAG, "Stopping search...")
        
        if (!isSearchActive.get()) {
            return SearchControlResult(false, "Search is not active")
        }

        try {
            updateState(SearchState.STOPPING)
            
            // Use the static intent-based method for robust stopping
            CoordinateFinderService.stopService(context)
            
            // Since the intent is asynchronous, we assume success and let the callbacks handle the state change
            logger.info(TAG, "Search stop request sent successfully")
            return SearchControlResult(true, "Search stopped")
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SERVICE,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Exception stopping search",
                throwable = e,
                context = "SearchController.stopSearch"
            )
            return SearchControlResult(false, "Error stopping search: ${e.message}")
        }
    }

    /**
     * Pause the search
     */
    fun pauseSearch(): SearchControlResult {
        logger.info(TAG, "Pausing search...")
        
        if (!isSearchActive.get()) {
            return SearchControlResult(false, "Search is not active")
        }
        
        if (isSearchPaused.get()) {
            return SearchControlResult(false, "Search is already paused")
        }

        try {
            updateState(SearchState.PAUSING)
            
            // Send pause command to service
            val intent = android.content.Intent(context, CoordinateFinderService::class.java).apply {
                action = CoordinateFinderService.ACTION_PAUSE_SEARCH
            }
            context.startService(intent)
            
            isSearchPaused.set(true)
            searchPauseTime.set(System.currentTimeMillis())
            
            updateState(SearchState.PAUSED)
            notifyListeners { it.onSearchPaused() }
            
            logger.info(TAG, "Search paused successfully")
            return SearchControlResult(true, "Search paused")
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SERVICE,
                severity = ErrorHandler.SEVERITY_MEDIUM,
                message = "Exception pausing search",
                throwable = e,
                context = "SearchController.pauseSearch"
            )
            return SearchControlResult(false, "Error pausing search: ${e.message}")
        }
    }

    /**
     * Resume the search
     */
    fun resumeSearch(): SearchControlResult {
        logger.info(TAG, "Resuming search...")
        
        if (!isSearchActive.get()) {
            return SearchControlResult(false, "Search is not active")
        }
        
        if (!isSearchPaused.get()) {
            return SearchControlResult(false, "Search is not paused")
        }

        try {
            updateState(SearchState.RESUMING)
            
            // Send resume command to service
            val intent = android.content.Intent(context, CoordinateFinderService::class.java).apply {
                action = CoordinateFinderService.ACTION_RESUME_SEARCH
            }
            context.startService(intent)
            
            // Update pause time tracking
            val pauseDuration = System.currentTimeMillis() - searchPauseTime.get()
            totalPausedTime.addAndGet(pauseDuration)
            
            isSearchPaused.set(false)
            searchPauseTime.set(0)
            
            updateState(SearchState.ACTIVE)
            notifyListeners { it.onSearchResumed() }
            
            logger.info(TAG, "Search resumed successfully")
            return SearchControlResult(true, "Search resumed")
            
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SERVICE,
                severity = ErrorHandler.SEVERITY_MEDIUM,
                message = "Exception resuming search",
                throwable = e,
                context = "SearchController.resumeSearch"
            )
            return SearchControlResult(false, "Error resuming search: ${e.message}")
        }
    }

    /**
     * Validate search preconditions
     */
    private fun validateSearchPreconditions(): ValidationResult {
        // Check permissions
        if (!permissionManager.isAccessibilityServiceEnabled()) {
            return ValidationResult(false, "Accessibility service is not enabled")
        }
        
        if (!permissionManager.hasOverlayPermission()) {
            return ValidationResult(false, "Overlay permission is required")
        }
        
        // Check template availability
        if (!templateManager.hasCurrentTemplate()) {
            return ValidationResult(false, "No template is available for search")
        }
        
        return ValidationResult(true, "All preconditions met")
    }

    /**
     * Handle unexpected search stop
     */
    private fun handleUnexpectedStop() {
        logger.warning(TAG, "Unexpected search stop detected")
        
        isSearchActive.set(false)
        isSearchPaused.set(false)
        updateState(SearchState.ERROR)
        
        errorHandler.handleError(
            category = ErrorHandler.CATEGORY_SERVICE,
            severity = ErrorHandler.SEVERITY_HIGH,
            message = "Search stopped unexpectedly",
            context = "Service disconnection"
        )
        
        notifyListeners { it.onSearchError("Search stopped unexpectedly") }
    }

    /**
     * Update current state and notify listeners
     */
    private fun updateState(newState: SearchState) {
        if (currentState != newState) {
            val oldState = currentState
            currentState = newState
            
            logger.debug(TAG, "State changed: $oldState -> $newState")
            notifyListeners { it.onStateChanged(newState) }
        }
    }

    /**
     * Notify all listeners
     */
    private fun notifyListeners(action: (SearchStateChangeListener) -> Unit) {
        stateChangeListeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                errorHandler.handleError(
                    category = ErrorHandler.CATEGORY_UI,
                    severity = ErrorHandler.SEVERITY_LOW,
                    message = "Error notifying search state listener",
                    throwable = e,
                    context = "Listener notification"
                )
            }
        }
    }

    /**
     * Add state change listener
     */
    fun addStateChangeListener(listener: SearchStateChangeListener) {
        stateChangeListeners.add(listener)
    }

    /**
     * Remove state change listener
     */
    fun removeStateChangeListener(listener: SearchStateChangeListener) {
        stateChangeListeners.remove(listener)
    }

    /**
     * Get current search status
     */
    fun getSearchStatus(): SearchStatus {
        val currentTime = System.currentTimeMillis()
        val activeTime = if (isSearchActive.get()) {
            val startTime = searchStartTime.get()
            val pausedTime = totalPausedTime.get()
            val currentPauseTime = if (isSearchPaused.get()) {
                currentTime - searchPauseTime.get()
            } else {
                0L
            }
            currentTime - startTime - pausedTime - currentPauseTime
        } else {
            0L
        }

        return SearchStatus(
            isActive = isSearchActive.get(),
            isPaused = isSearchPaused.get(),
            currentState = currentState,
            searchStartTime = searchStartTime.get(),
            activeTime = activeTime,
            totalPausedTime = totalPausedTime.get(),
            searchAttempts = searchAttempts.get(),
            successfulFinds = successfulFinds.get(),
            lastResult = lastResult
        )
    }

    /**
     * Reset search statistics
     */
    fun resetStatistics() {
        searchAttempts.set(0)
        successfulFinds.set(0)
        totalPausedTime.set(0)
        lastResult = null
        
        logger.info(TAG, "Search statistics reset")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        logger.info(TAG, "Cleaning up SearchController")
        
        serviceCommunicationManager.removeCallback(serviceCommunicationCallback)
        serviceCommunicationManager.cleanup()
        stateChangeListeners.clear()
    }

    /**
     * Data class for search control results
     */
    data class SearchControlResult(
        val success: Boolean,
        val message: String
    )

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )

    /**
     * Data class for search status
     */
    data class SearchStatus(
        val isActive: Boolean,
        val isPaused: Boolean,
        val currentState: SearchState,
        val searchStartTime: Long,
        val activeTime: Long,
        val totalPausedTime: Long,
        val searchAttempts: Long,
        val successfulFinds: Long,
        val lastResult: SearchResult?
    )
}