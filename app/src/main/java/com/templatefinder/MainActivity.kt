package com.templatefinder

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.templatefinder.databinding.ActivityMainBinding
import com.templatefinder.controller.SearchController
import com.templatefinder.manager.ServiceCommunicationManager
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.SearchResult
import com.templatefinder.service.CoordinateFinderService
import com.templatefinder.service.ScreenshotAccessibilityService
import com.templatefinder.ui.PermissionGuideActivity
import com.templatefinder.ui.SettingsActivity
import com.templatefinder.ui.TemplateCreationActivity
import com.templatefinder.ui.TemplateTestActivity
import com.templatefinder.util.PermissionManager
import com.templatefinder.util.BatteryOptimizer
import com.templatefinder.util.AppOptimizationManager
import com.templatefinder.TemplateFinderApplication
import com.templatefinder.util.AccessibilityManager
import kotlinx.coroutines.launch

/**
 * Main activity providing core application controls and status display
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var templateManager: TemplateManager
    private lateinit var serviceCommunicationManager: ServiceCommunicationManager
    private lateinit var searchController: SearchController
    
    // Application state
    private var isSearchActive = false
    private var hasTemplate = false
    private var lastSearchResult: SearchResult? = null
    
    // Service callback for coordinate finder service
    private val serviceCallback = object : CoordinateFinderService.ServiceCallback {
        override fun onSearchStarted() {
            runOnUiThread {
                isSearchActive = true
                updateUI()
                Log.d(TAG, "Search started callback received")
            }
        }

        override fun onSearchStopped() {
            runOnUiThread {
                isSearchActive = false
                updateUI()
                Log.d(TAG, "Search stopped callback received")
            }
        }

        override fun onSearchPaused() {
            runOnUiThread {
                Log.d(TAG, "Search paused callback received")
            }
        }

        override fun onSearchResumed() {
            runOnUiThread {
                Log.d(TAG, "Search resumed callback received")
            }
        }

        override fun onResultFound(result: SearchResult) {
            runOnUiThread {
                lastSearchResult = result
                updateResultsDisplay()
                Log.d(TAG, "Result found: ${result.getFormattedCoordinates()}")
            }
        }

        override fun onSearchError(error: String) {
            runOnUiThread {
                showError("Search error: $error")
            }
        }

        override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {
            runOnUiThread {
                // Update UI based on service status
                Log.d(TAG, "Service status update: searches=${status.searchCount}")
            }
        }
    }

    // Activity result launchers
    private val templateCreationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Template creation completed successfully")
            checkTemplateStatus()
            updateUI()
        }
    }

    private val permissionGuideLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Permission guide completed")
        checkPermissions()
        updateUI()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Settings updated")
            // Reload any settings-dependent components
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Check if onboarding should be shown
            if (com.templatefinder.ui.OnboardingActivity.shouldShowOnboarding(this)) {
                val intent = Intent(this, com.templatefinder.ui.OnboardingActivity::class.java)
                startActivity(intent)
                finish()
                return
            }
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            initializeComponents()
            setupEventListeners()
            checkInitialState()
            handleNotificationIntent(intent)
            
            // Apply accessibility improvements
            val app = application as TemplateFinderApplication
            app.getAccessibilityManager().applyActivityAccessibilityImprovements(binding.root)
            
            // Track analytics
            app.getAnalyticsManager().trackUserAction("main_activity_opened")
            
            // Check for optimization recommendations
            checkOptimizationRecommendations()

            Log.d(TAG, "MainActivity created successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // Show error to user and finish activity
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        
        try {
            checkPermissions()
            checkTemplateStatus()
            checkServiceStatus()
            
            // Bind to service for communication (check if initialized)
            if (::serviceCommunicationManager.isInitialized) {
                serviceCommunicationManager.bindService()
            }
            
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        
        try {
            // Unbind from service (check if initialized)
            if (::serviceCommunicationManager.isInitialized) {
                serviceCommunicationManager.unbindService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPause", e)
        }
    }

    private fun initializeComponents() {
        permissionManager = PermissionManager(this)
        templateManager = TemplateManager(this)
        serviceCommunicationManager = ServiceCommunicationManager(this)
        searchController = SearchController(this)
        
        // Add service communication callback
        serviceCommunicationManager.addCallback(serviceCommunicationCallback)
        
        // Add search controller listener
        searchController.addStateChangeListener(searchStateChangeListener)
        
        // Add battery optimization listener
        val app = application as TemplateFinderApplication
        app.getBatteryOptimizer().addBatteryChangeListener(batteryChangeListener)
    }
    
    // Service communication callback
    private val serviceCommunicationCallback = object : ServiceCommunicationManager.ServiceCommunicationCallback {
        override fun onServiceConnected() {
            Log.d(TAG, "Service communication established")
        }

        override fun onServiceDisconnected() {
            Log.d(TAG, "Service communication lost")
        }

        override fun onSearchStarted() {
            isSearchActive = true
            updateUI()
        }

        override fun onSearchStopped() {
            isSearchActive = false
            updateUI()
        }

        override fun onResultFound(result: SearchResult) {
            lastSearchResult = result
            updateResultsDisplay()
        }

        override fun onSearchError(error: String) {
            showError("Search error: $error")
        }

        override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {
            // Update UI based on service status if needed
        }
    }

    // Battery change listener
    private val batteryChangeListener = object : BatteryOptimizer.BatteryChangeListener {
        override fun onBatteryChanged(batteryInfo: BatteryOptimizer.BatteryInfo) {
            runOnUiThread {
                updateBatteryStatus(batteryInfo)
            }
        }

        override fun onOptimizationLevelChanged(level: BatteryOptimizer.OptimizationLevel) {
            runOnUiThread {
                showBatteryOptimizationMessage(level)
            }
        }
    }

    // Search controller state change listener
    private val searchStateChangeListener = object : SearchController.SearchStateChangeListener {
        override fun onSearchStarted() {
            runOnUiThread {
                isSearchActive = true
                updateUI()
                Log.d(TAG, "Search started via SearchController")
            }
        }

        override fun onSearchStopped() {
            runOnUiThread {
                isSearchActive = false
                updateUI()
                Log.d(TAG, "Search stopped via SearchController")
            }
        }

        override fun onSearchPaused() {
            runOnUiThread {
                Log.d(TAG, "Search paused via SearchController")
                updateUI()
            }
        }

        override fun onSearchResumed() {
            runOnUiThread {
                Log.d(TAG, "Search resumed via SearchController")
                updateUI()
            }
        }

        override fun onSearchError(error: String) {
            runOnUiThread {
                showError("Search error: $error")
            }
        }

        override fun onResultFound(result: SearchResult) {
            runOnUiThread {
                lastSearchResult = result
                updateResultsDisplay()
            }
        }

        override fun onStateChanged(state: SearchController.SearchState) {
            runOnUiThread {
                updateSearchStateDisplay(state)
            }
        }
    }

    /**
     * Handle intents from notifications and auto-open
     */
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            when {
                it.getBooleanExtra("show_result", false) -> {
                    // Show specific result from notification
                    val x = it.getIntExtra("result_x", 0)
                    val y = it.getIntExtra("result_y", 0)
                    val confidence = it.getFloatExtra("result_confidence", 0f)
                    
                    val result = SearchResult.success(x, y, confidence)
                    lastSearchResult = result
                    updateResultsDisplay()
                    
                    Log.d(TAG, "Showing result from notification: ${result.getFormattedCoordinates()}")
                }
                it.getBooleanExtra("show_results", false) -> {
                    // Show results area
                    Log.d(TAG, "Showing results area from notification")
                    // Could scroll to results or expand results view
                }
                it.getBooleanExtra("auto_opened", false) -> {
                    // App was auto-opened due to result found
                    val x = it.getIntExtra("result_x", 0)
                    val y = it.getIntExtra("result_y", 0)
                    val confidence = it.getFloatExtra("result_confidence", 0f)
                    
                    val result = SearchResult.success(x, y, confidence)
                    lastSearchResult = result
                    updateResultsDisplay()
                    
                    // Show toast to indicate auto-open
                    Toast.makeText(this, "App opened automatically - coordinates found!", Toast.LENGTH_LONG).show()
                    
                    Log.d(TAG, "App auto-opened for result: ${result.getFormattedCoordinates()}")
                }
 else -> {}
            }
        }
    }

    private fun setupEventListeners() {
        // Create Template button
        binding.createTemplateButton.setOnClickListener {
            createTemplate()
        }

        // Start Search button
        binding.startSearchButton.setOnClickListener {
            startSearch()
        }

        // Stop Search button
        binding.stopSearchButton.setOnClickListener {
            stopSearch()
        }

        // Pause Search button
        binding.pauseSearchButton.setOnClickListener {
            pauseSearch()
        }

        // Resume Search button
        binding.resumeSearchButton.setOnClickListener {
            resumeSearch()
        }

        // Settings button
        binding.settingsButton.setOnClickListener {
            openSettings()
        }

        // Permission Setup button
        binding.permissionSetupButton.setOnClickListener {
            openPermissionGuide()
        }

        // Results area click (for expanding details)
        binding.resultsCard.setOnClickListener {
            toggleResultsDetails()
        }

        binding.testTemplateButton.setOnClickListener {
            val intent = Intent(this, TemplateTestActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkInitialState() {
        checkPermissions()
        checkTemplateStatus()
        updateUI()
    }

    private fun checkPermissions() {
        lifecycleScope.launch {
            val hasAccessibilityPermission = permissionManager.isAccessibilityServiceEnabled()
            val hasOverlayPermission = permissionManager.hasOverlayPermission()
            val hasNotificationPermission = permissionManager.hasNotificationPermission()

            Log.d(TAG, "Permissions - Accessibility: $hasAccessibilityPermission, " +
                    "Overlay: $hasOverlayPermission, Notification: $hasNotificationPermission")

            updatePermissionStatus(hasAccessibilityPermission, hasOverlayPermission, hasNotificationPermission)
        }
    }

    private fun checkTemplateStatus() {
        val previousStatus = hasTemplate
        hasTemplate = templateManager.hasCurrentTemplate()
        
        Log.d(TAG, "Checking template status: previous=$previousStatus, current=$hasTemplate")
        
        if (hasTemplate) {
            val templateInfo = templateManager.getTemplateInfo()
            Log.d(TAG, "Template found: ${templateInfo?.name}")
        } else {
            Log.d(TAG, "No template found")
        }
    }

    private fun checkServiceStatus() {
        isSearchActive = CoordinateFinderService.isRunning(this)
        Log.d(TAG, "Service running: $isSearchActive")
    }

    private fun updatePermissionStatus(accessibility: Boolean, overlay: Boolean, notification: Boolean) {
        val allPermissionsGranted = accessibility && overlay && notification
        
        binding.apply {
            permissionStatusText.text = if (allPermissionsGranted) {
                "All permissions granted"
            } else {
                "Permissions required"
            }
            
            permissionStatusIcon.setImageResource(
                if (allPermissionsGranted) {
                    android.R.drawable.ic_dialog_info
                } else {
                    android.R.drawable.ic_dialog_alert
                }
            )
            
            permissionSetupButton.visibility = if (allPermissionsGranted) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    private fun updateUI() {
        val hasPermissions = permissionManager.isAccessibilityServiceEnabled() && 
                           permissionManager.hasOverlayPermission()
        
        val searchStatus = searchController.getSearchStatus()
        
        binding.apply {
            // Template status
            templateStatusText.text = if (hasTemplate) {
                "Template ready"
            } else {
                "No template created"
            }
            
            templateStatusIcon.setImageResource(
                if (hasTemplate) {
                    android.R.drawable.ic_dialog_info
                } else {
                    android.R.drawable.ic_dialog_alert
                }
            )
            
            // Search controls
            val canStartSearch = hasPermissions && hasTemplate && !searchStatus.isActive
            val canStopSearch = searchStatus.isActive
            val canPauseSearch = searchStatus.isActive && !searchStatus.isPaused
            val canResumeSearch = searchStatus.isActive && searchStatus.isPaused
            
            startSearchButton.isEnabled = canStartSearch
            stopSearchButton.isEnabled = canStopSearch
            pauseSearchButton.isEnabled = canPauseSearch
            resumeSearchButton.isEnabled = canResumeSearch
            
            startSearchButton.text = if (searchStatus.isActive) {
                if (searchStatus.isPaused) "Search Paused" else "Search Running..."
            } else {
                "Start Search"
            }
            
            // Template creation
            createTemplateButton.isEnabled = hasPermissions
            
            // Search status
            searchStatusText.text = when {
                !hasPermissions -> "Permissions required"
                !hasTemplate -> "Template required"
                searchStatus.isPaused -> "Search paused"
                searchStatus.isActive -> "Search active"
                else -> "Ready to search"
            }
            
            // Results display
            updateResultsDisplay()
        }
    }

    /**
     * Update search state display
     */
    private fun updateSearchStateDisplay(state: SearchController.SearchState) {
        val stateText = when (state) {
            SearchController.SearchState.STOPPED -> "Stopped"
            SearchController.SearchState.STARTING -> "Starting..."
            SearchController.SearchState.ACTIVE -> "Active"
            SearchController.SearchState.PAUSING -> "Pausing..."
            SearchController.SearchState.PAUSED -> "Paused"
            SearchController.SearchState.RESUMING -> "Resuming..."
            SearchController.SearchState.STOPPING -> "Stopping..."
            SearchController.SearchState.ERROR -> "Error"
            else -> "Unknown state"
        }
        
        binding.searchStatusText.text = stateText
    }

    private fun updateResultsDisplay() {
        val result = lastSearchResult
        
        binding.apply {
            if (result != null && result.found) {
                resultsCard.visibility = View.VISIBLE
                coordinatesText.text = result.getFormattedCoordinates()
                confidenceText.text = "Confidence: ${result.getConfidencePercentage()}%"
                timestampText.text = "Found at: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(result.timestamp))}"
            } else {
                resultsCard.visibility = View.GONE
            }
            
            noResultsText.visibility = if (result == null || !result.found) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun createTemplate() {
        if (!permissionManager.isAccessibilityServiceEnabled()) {
            showError("Accessibility service is required to capture screenshots")
            return
        }

        Log.d(TAG, "Starting template creation")
        val intent = Intent(this, TemplateCreationActivity::class.java)
        templateCreationLauncher.launch(intent)
    }

    private fun startSearch() {
        Log.d(TAG, "Starting coordinate search")
        
        val result = searchController.startSearch()
        if (result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        } else {
            showError(result.message)
        }
    }

    private fun stopSearch() {
        Log.d(TAG, "Stopping coordinate search")
        
        val result = searchController.stopSearch()
        if (result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        } else {
            showError(result.message)
        }
    }

    private fun pauseSearch() {
        Log.d(TAG, "Pausing coordinate search")
        
        val result = searchController.pauseSearch()
        if (result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        } else {
            showError(result.message)
        }
    }

    private fun resumeSearch() {
        Log.d(TAG, "Resuming coordinate search")
        
        val result = searchController.resumeSearch()
        if (result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        } else {
            showError(result.message)
        }
    }

    private fun openSettings() {
        Log.d(TAG, "Opening settings")
        val intent = Intent(this, SettingsActivity::class.java)
        settingsLauncher.launch(intent)
    }

    private fun openPermissionGuide() {
        Log.d(TAG, "Opening permission guide")
        val intent = Intent(this, PermissionGuideActivity::class.java)
        permissionGuideLauncher.launch(intent)
    }

    private fun toggleResultsDetails() {
        // TODO: Implement expandable results details
        val result = lastSearchResult ?: return
        
        val message = """
            Coordinates: ${result.getFormattedCoordinates()}
            Confidence: ${result.getConfidencePercentage()}%
            Timestamp: ${java.util.Date(result.timestamp)}
        """.trimIndent()
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Update search result from external source (e.g., service)
     */
    fun updateSearchResult(result: SearchResult) {
        runOnUiThread {
            lastSearchResult = result
            updateResultsDisplay()
            
            if (result.found) {
                Log.d(TAG, "Search result updated: ${result.getFormattedCoordinates()}")
            }
        }
    }

    /**
     * Update search status from external source
     */
    fun updateSearchStatus(active: Boolean) {
        runOnUiThread {
            isSearchActive = active
            updateUI()
        }
    }

    /**
     * Update battery status display
     */
    private fun updateBatteryStatus(batteryInfo: BatteryOptimizer.BatteryInfo) {
        // Update UI to show battery status if needed
        val statusText = when {
            batteryInfo.level <= 5 -> "Critical battery (${batteryInfo.level}%)"
            batteryInfo.level <= 15 -> "Low battery (${batteryInfo.level}%)"
            batteryInfo.isPowerSaveMode -> "Power save mode active"
            else -> null
        }
        
        statusText?.let {
            // Could show in a status bar or as a subtle indicator
            Log.d(TAG, "Battery status: $it")
        }
    }

    /**
     * Show battery optimization message
     */
    private fun showBatteryOptimizationMessage(level: BatteryOptimizer.OptimizationLevel) {
        val message = when (level) {
            BatteryOptimizer.OptimizationLevel.CRITICAL -> 
                getString(R.string.battery_critical_warning)
            BatteryOptimizer.OptimizationLevel.AGGRESSIVE -> 
                getString(R.string.battery_low_warning)
            else -> null
        }
        
        message?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Check for optimization recommendations and show them to the user
     */
    private fun checkOptimizationRecommendations() {
        try {
            val app = application as TemplateFinderApplication
            val recommendations = app.getAppOptimizationManager().getOptimizationRecommendations()
            
            // Show high priority recommendations
            val highPriorityRecommendations = recommendations.filter { 
                it.priority == AppOptimizationManager.RecommendationPriority.HIGH ||
                it.priority == AppOptimizationManager.RecommendationPriority.CRITICAL
            }
            
            if (highPriorityRecommendations.isNotEmpty()) {
                val recommendation = highPriorityRecommendations.first()
                showOptimizationRecommendation(recommendation)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking optimization recommendations", e)
        }
    }

    /**
     * Show optimization recommendation to user
     */
    private fun showOptimizationRecommendation(recommendation: AppOptimizationManager.OptimizationRecommendation) {
        // For now, show as toast. In a real app, this could be a more prominent notification
        val message = "${recommendation.title}: ${recommendation.description}"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        
        Log.i(TAG, "Optimization recommendation: $message")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // Clean up service communication (check if initialized)
            if (::serviceCommunicationManager.isInitialized) {
                serviceCommunicationManager.removeCallback(serviceCommunicationCallback)
                serviceCommunicationManager.cleanup()
            }
            
            // Clean up search controller (check if initialized)
            if (::searchController.isInitialized) {
                searchController.removeStateChangeListener(searchStateChangeListener)
                searchController.cleanup()
            }
            
            // Clean up battery optimizer
            try {
                val app = application as TemplateFinderApplication
                app.getBatteryOptimizer().removeBatteryChangeListener(batteryChangeListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up battery optimizer", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
        
        Log.d(TAG, "MainActivity destroyed")
    }
}