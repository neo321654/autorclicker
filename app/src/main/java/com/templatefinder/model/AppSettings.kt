package com.templatefinder.model

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val searchInterval: Long = 5000L, // 5 seconds
    val matchThreshold: Float = 0.8f,
    val templateRadius: Int = 50,
    val isSearchActive: Boolean = false,
    val maxResults: Int = 10,
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoOpenEnabled: Boolean = false,
    val loggingEnabled: Boolean = true,
    val autoClickEnabled: Boolean = false,
    val clickOffsetX: Int = 0,
    val clickOffsetY: Int = 0
) {
    
    companion object {
        private const val PREFS_NAME = "template_finder_settings"
        private const val KEY_SEARCH_INTERVAL = "search_interval"
        private const val KEY_MATCH_THRESHOLD = "match_threshold"
        private const val KEY_TEMPLATE_RADIUS = "template_radius"
        private const val KEY_IS_SEARCH_ACTIVE = "is_search_active"
        private const val KEY_MAX_RESULTS = "max_results"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_AUTO_OPEN_ENABLED = "auto_open_enabled"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_AUTO_CLICK_ENABLED = "auto_click_enabled"
        private const val KEY_CLICK_OFFSET_X = "click_offset_x"
        private const val KEY_CLICK_OFFSET_Y = "click_offset_y"
        
        /**
         * Loads settings from SharedPreferences
         */
        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettings(
                searchInterval = prefs.getLong(KEY_SEARCH_INTERVAL, 5000L),
                matchThreshold = prefs.getFloat(KEY_MATCH_THRESHOLD, 0.8f),
                templateRadius = prefs.getInt(KEY_TEMPLATE_RADIUS, 50),
                isSearchActive = prefs.getBoolean(KEY_IS_SEARCH_ACTIVE, false),
                maxResults = prefs.getInt(KEY_MAX_RESULTS, 10),
                notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
                vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
                autoOpenEnabled = prefs.getBoolean(KEY_AUTO_OPEN_ENABLED, false),
                loggingEnabled = prefs.getBoolean(KEY_LOGGING_ENABLED, true),
                autoClickEnabled = prefs.getBoolean(KEY_AUTO_CLICK_ENABLED, false),
                clickOffsetX = prefs.getInt(KEY_CLICK_OFFSET_X, 0),
                clickOffsetY = prefs.getInt(KEY_CLICK_OFFSET_Y, 0)
            )
        }
    }
    
    /**
     * Saves settings to SharedPreferences
     */
    fun save(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong(KEY_SEARCH_INTERVAL, searchInterval)
                putFloat(KEY_MATCH_THRESHOLD, matchThreshold)
                putInt(KEY_TEMPLATE_RADIUS, templateRadius)
                putBoolean(KEY_IS_SEARCH_ACTIVE, isSearchActive)
                putInt(KEY_MAX_RESULTS, maxResults)
                putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled)
                putBoolean(KEY_VIBRATION_ENABLED, vibrationEnabled)
                putBoolean(KEY_AUTO_OPEN_ENABLED, autoOpenEnabled)
                putBoolean(KEY_LOGGING_ENABLED, loggingEnabled)
                putBoolean(KEY_AUTO_CLICK_ENABLED, autoClickEnabled)
                putInt(KEY_CLICK_OFFSET_X, clickOffsetX)
                putInt(KEY_CLICK_OFFSET_Y, clickOffsetY)
                apply()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Updates search active status
     */
    fun updateSearchActive(context: Context, isActive: Boolean): AppSettings {
        val updated = copy(isSearchActive = isActive)
        updated.save(context)
        return updated
    }
    
    /**
     * Updates search interval
     */
    fun updateSearchInterval(context: Context, intervalMs: Long): AppSettings {
        val updated = copy(searchInterval = intervalMs)
        updated.save(context)
        return updated
    }
    
    /**
     * Updates match threshold
     */
    fun updateMatchThreshold(context: Context, threshold: Float): AppSettings {
        val updated = copy(matchThreshold = threshold)
        updated.save(context)
        return updated
    }
    
    /**
     * Updates template radius
     */
    fun updateTemplateRadius(context: Context, radius: Int): AppSettings {
        val updated = copy(templateRadius = radius)
        updated.save(context)
        return updated
    }
    
    /**
     * Validates settings values
     */
    fun isValid(): Boolean {
        return searchInterval > 0 &&
                matchThreshold in 0.1f..1.0f &&
                templateRadius > 0 &&
                maxResults > 0
    }
    
    // Getter methods for compatibility (using different names to avoid conflicts)
    fun getSearchIntervalMs(): Long = searchInterval
    fun getMatchThresholdValue(): Float = matchThreshold
    fun getTemplateRadiusPixels(): Int = templateRadius
    fun getMaxResultsCount(): Int = maxResults
    fun isNotificationsEnabled(): Boolean = notificationsEnabled
    fun isVibrationEnabled(): Boolean = vibrationEnabled
    fun isAutoOpenEnabled(): Boolean = autoOpenEnabled
    fun isLoggingEnabled(): Boolean = loggingEnabled
    fun isAutoClickEnabled(): Boolean = autoClickEnabled
}