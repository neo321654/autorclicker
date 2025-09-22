package com.templatefinder.ui

import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.templatefinder.R
import com.templatefinder.databinding.ActivitySettingsBinding
import com.templatefinder.model.AppSettings
import kotlinx.coroutines.launch

/**
 * Activity for configuring application settings and search parameters
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        
        // Default values
        private const val DEFAULT_SEARCH_INTERVAL = 2000L // 2 seconds
        private const val DEFAULT_MATCH_THRESHOLD = 0.8f
        private const val DEFAULT_TEMPLATE_RADIUS = 50
        private const val DEFAULT_MAX_RESULTS = 10
        
        // Ranges
        private const val MIN_SEARCH_INTERVAL = 500L // 0.5 seconds
        private const val MAX_SEARCH_INTERVAL = 30000L // 30 seconds
        private const val MIN_MATCH_THRESHOLD = 0.1f
        private const val MAX_MATCH_THRESHOLD = 1.0f
        private const val MIN_TEMPLATE_RADIUS = 20
        private const val MAX_TEMPLATE_RADIUS = 200
        private const val MIN_MAX_RESULTS = 1
        private const val MAX_MAX_RESULTS = 100
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appSettings: AppSettings
    
    // Current settings values
    private var searchInterval: Long = DEFAULT_SEARCH_INTERVAL
    private var matchThreshold: Float = DEFAULT_MATCH_THRESHOLD
    private var templateRadius: Int = DEFAULT_TEMPLATE_RADIUS
    private var maxResults: Int = DEFAULT_MAX_RESULTS
    private var enableNotifications: Boolean = true
    private var enableVibration: Boolean = true
    private var enableAutoOpen: Boolean = false
    private var enableLogging: Boolean = false
    private var enableAutoClick: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        initializeSettings()
        setupEventListeners()
        loadSettings()

        Log.d(TAG, "SettingsActivity created")
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun initializeSettings() {
        appSettings = AppSettings.load(this)
    }

    private fun setupEventListeners() {
        // Search Interval SeekBar
        binding.searchIntervalSeekBar.apply {
            min = (MIN_SEARCH_INTERVAL / 100).toInt() // Scale down for SeekBar
            max = (MAX_SEARCH_INTERVAL / 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        searchInterval = (progress * 100).toLong()
                        updateSearchIntervalText()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Match Threshold SeekBar
        binding.matchThresholdSeekBar.apply {
            min = (MIN_MATCH_THRESHOLD * 100).toInt()
            max = (MAX_MATCH_THRESHOLD * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        matchThreshold = progress / 100f
                        updateMatchThresholdText()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Template Radius SeekBar
        binding.templateRadiusSeekBar.apply {
            min = MIN_TEMPLATE_RADIUS
            max = MAX_TEMPLATE_RADIUS
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        templateRadius = progress
                        updateTemplateRadiusText()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Max Results SeekBar
        binding.maxResultsSeekBar.apply {
            min = MIN_MAX_RESULTS
            max = MAX_MAX_RESULTS
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        maxResults = progress
                        updateMaxResultsText()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Notification Settings
        binding.enableNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableNotifications = isChecked
        }

        binding.enableVibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableVibration = isChecked
        }

        binding.enableAutoOpenSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableAutoOpen = isChecked
        }

        binding.enableLoggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableLogging = isChecked
        }

        binding.enableAutoClickSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableAutoClick = isChecked
        }

        // Action Buttons
        binding.saveButton.setOnClickListener {
            saveSettings()
        }

        binding.resetButton.setOnClickListener {
            resetToDefaults()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                // Load settings from AppSettings
                searchInterval = appSettings.searchInterval
                matchThreshold = appSettings.matchThreshold
                templateRadius = appSettings.templateRadius
                maxResults = appSettings.maxResults
                enableNotifications = appSettings.notificationsEnabled
                enableVibration = appSettings.vibrationEnabled
                enableAutoOpen = appSettings.autoOpenEnabled
                enableLogging = appSettings.loggingEnabled
                enableAutoClick = appSettings.autoClickEnabled

                // Update UI
                updateUI()

                Log.d(TAG, "Settings loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
                Toast.makeText(this@SettingsActivity, "Error loading settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        // Update SeekBars
        binding.searchIntervalSeekBar.progress = (searchInterval / 100).toInt()
        binding.matchThresholdSeekBar.progress = (matchThreshold * 100).toInt()
        binding.templateRadiusSeekBar.progress = templateRadius
        binding.maxResultsSeekBar.progress = maxResults

        // Update Switches
        binding.enableNotificationsSwitch.isChecked = enableNotifications
        binding.enableVibrationSwitch.isChecked = enableVibration
        binding.enableAutoOpenSwitch.isChecked = enableAutoOpen
        binding.enableLoggingSwitch.isChecked = enableLogging
        binding.enableAutoClickSwitch.isChecked = enableAutoClick

        // Update text displays
        updateSearchIntervalText()
        updateMatchThresholdText()
        updateTemplateRadiusText()
        updateMaxResultsText()
    }

    private fun updateSearchIntervalText() {
        val seconds = searchInterval / 1000.0
        binding.searchIntervalText.text = "Search Interval: ${seconds}s"
    }

    private fun updateMatchThresholdText() {
        val percentage = (matchThreshold * 100).toInt()
        binding.matchThresholdText.text = "Match Threshold: ${percentage}%"
    }

    private fun updateTemplateRadiusText() {
        binding.templateRadiusText.text = "Template Radius: ${templateRadius}px"
    }

    private fun updateMaxResultsText() {
        binding.maxResultsText.text = "Max Results: $maxResults"
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                // Validate settings
                if (!validateSettings()) {
                    return@launch
                }

                // Save settings using AppSettings
                val newSettings = AppSettings(
                    searchInterval = searchInterval,
                    matchThreshold = matchThreshold,
                    templateRadius = templateRadius,
                    isSearchActive = appSettings.isSearchActive,
                    maxResults = maxResults,
                    notificationsEnabled = enableNotifications,
                    vibrationEnabled = enableVibration,
                    autoOpenEnabled = enableAutoOpen,
                    loggingEnabled = enableLogging,
                    autoClickEnabled = enableAutoClick
                )
                newSettings.save(this@SettingsActivity)

                Log.d(TAG, "Settings saved successfully")
                Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()

                // Return to previous activity
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
                Toast.makeText(this@SettingsActivity, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateSettings(): Boolean {
        // Validate search interval
        if (searchInterval < MIN_SEARCH_INTERVAL || searchInterval > MAX_SEARCH_INTERVAL) {
            showError("Search interval must be between ${MIN_SEARCH_INTERVAL/1000}s and ${MAX_SEARCH_INTERVAL/1000}s")
            return false
        }

        // Validate match threshold
        if (matchThreshold < MIN_MATCH_THRESHOLD || matchThreshold > MAX_MATCH_THRESHOLD) {
            showError("Match threshold must be between ${(MIN_MATCH_THRESHOLD*100).toInt()}% and ${(MAX_MATCH_THRESHOLD*100).toInt()}%")
            return false
        }

        // Validate template radius
        if (templateRadius < MIN_TEMPLATE_RADIUS || templateRadius > MAX_TEMPLATE_RADIUS) {
            showError("Template radius must be between ${MIN_TEMPLATE_RADIUS}px and ${MAX_TEMPLATE_RADIUS}px")
            return false
        }

        // Validate max results
        if (maxResults < MIN_MAX_RESULTS || maxResults > MAX_MAX_RESULTS) {
            showError("Max results must be between $MIN_MAX_RESULTS and $MAX_MAX_RESULTS")
            return false
        }

        return true
    }

    private fun resetToDefaults() {
        searchInterval = DEFAULT_SEARCH_INTERVAL
        matchThreshold = DEFAULT_MATCH_THRESHOLD
        templateRadius = DEFAULT_TEMPLATE_RADIUS
        maxResults = DEFAULT_MAX_RESULTS
        enableNotifications = true
        enableVibration = true
        enableAutoOpen = false
        enableLogging = false
        enableAutoClick = false

        updateUI()

        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Settings reset to defaults")
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SettingsActivity destroyed")
    }
}