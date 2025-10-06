package com.templatefinder.model

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val searchInterval: Long,
    val matchThreshold: Float,
    val templateRadius: Int,
    val maxResults: Int,
    val notificationsEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val autoOpenEnabled: Boolean,
    val loggingEnabled: Boolean,
    val autoClickEnabled: Boolean,
    val language: String
) {

    companion object {
        private const val PREFS_NAME = "template_finder_settings"
        private const val KEY_SEARCH_INTERVAL = "search_interval"
        private const val KEY_MATCH_THRESHOLD = "match_threshold"
        private const val KEY_TEMPLATE_RADIUS = "template_radius"
        private const val KEY_MAX_RESULTS = "max_results"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_AUTO_OPEN_ENABLED = "auto_open_enabled"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_AUTO_CLICK_ENABLED = "auto_click_enabled"
        private const val KEY_LANGUAGE = "language"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettings(
                searchInterval = prefs.getLong(KEY_SEARCH_INTERVAL, 2000L),
                matchThreshold = prefs.getFloat(KEY_MATCH_THRESHOLD, 0.8f),
                templateRadius = prefs.getInt(KEY_TEMPLATE_RADIUS, 50),
                maxResults = prefs.getInt(KEY_MAX_RESULTS, 10),
                notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
                vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
                autoOpenEnabled = prefs.getBoolean(KEY_AUTO_OPEN_ENABLED, false),
                loggingEnabled = prefs.getBoolean(KEY_LOGGING_ENABLED, false),
                autoClickEnabled = prefs.getBoolean(KEY_AUTO_CLICK_ENABLED, false),
                language = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
            )
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong(KEY_SEARCH_INTERVAL, searchInterval)
            putFloat(KEY_MATCH_THRESHOLD, matchThreshold)
            putInt(KEY_TEMPLATE_RADIUS, templateRadius)
            putInt(KEY_MAX_RESULTS, maxResults)
            putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putBoolean(KEY_VIBRATION_ENABLED, vibrationEnabled)
            putBoolean(KEY_AUTO_OPEN_ENABLED, autoOpenEnabled)
            putBoolean(KEY_LOGGING_ENABLED, loggingEnabled)
            putBoolean(KEY_AUTO_CLICK_ENABLED, autoClickEnabled)
            putString(KEY_LANGUAGE, language)
            apply()
        }
    }}