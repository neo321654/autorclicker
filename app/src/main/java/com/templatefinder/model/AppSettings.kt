package com.templatefinder.model

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val searchInterval: Long,
    val matchThreshold: Float,
    val templateRadius: Int,
    val isSearchActive: Boolean,
    val maxResults: Int,
    val notificationsEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val autoOpenEnabled: Boolean,
    val loggingEnabled: Boolean,
    val autoClickEnabled: Boolean,
    val clickOffsetX: Int,
    val clickOffsetY: Int,
    val language: String,
    val showClickMarker: Boolean // Added this field
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
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SHOW_CLICK_MARKER = "show_click_marker"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettings(
                searchInterval = prefs.getLong(KEY_SEARCH_INTERVAL, 2000L),
                matchThreshold = prefs.getFloat(KEY_MATCH_THRESHOLD, 0.8f),
                templateRadius = prefs.getInt(KEY_TEMPLATE_RADIUS, 50),
                isSearchActive = prefs.getBoolean(KEY_IS_SEARCH_ACTIVE, false),
                maxResults = prefs.getInt(KEY_MAX_RESULTS, 10),
                notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
                vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
                autoOpenEnabled = prefs.getBoolean(KEY_AUTO_OPEN_ENABLED, false),
                loggingEnabled = prefs.getBoolean(KEY_LOGGING_ENABLED, false),
                autoClickEnabled = prefs.getBoolean(KEY_AUTO_CLICK_ENABLED, false),
                clickOffsetX = prefs.getInt(KEY_CLICK_OFFSET_X, 0),
                clickOffsetY = prefs.getInt(KEY_CLICK_OFFSET_Y, 0),
                language = prefs.getString(KEY_LANGUAGE, "en") ?: "en",
                showClickMarker = prefs.getBoolean(KEY_SHOW_CLICK_MARKER, false)
            )
        }
    }

    fun save(context: Context) {
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
            putString(KEY_LANGUAGE, language)
            putBoolean(KEY_SHOW_CLICK_MARKER, showClickMarker)
            apply()
        }
    }