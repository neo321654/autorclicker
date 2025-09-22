package com.templatefinder.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class AnalyticsManager(private val context: Context) {

    companion object {
        private const val TAG = "AnalyticsManager"
        private const val PREFS_NAME = "analytics_prefs"
        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_CRASH_TIME = "last_crash_time"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"

        @Volatile
        private var instance: AnalyticsManager? = null

        fun getInstance(context: Context): AnalyticsManager {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logger = Logger.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var analyticsEnabled = true
    private var sessionStartTime = 0L
    private val eventCounts = ConcurrentHashMap<String, AtomicLong>()
    private val performanceMetrics = ConcurrentHashMap<String, MutableList<Long>>()

    init {
        analyticsEnabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)
        initializeCrashReporting()
    }

    private fun initializeCrashReporting() {
        Log.d(TAG, "Crash reporting is disabled in this version.")
    }

    fun startSession() {
        if (!analyticsEnabled) return
        sessionStartTime = System.currentTimeMillis()
        val sessionCount = prefs.getLong(KEY_SESSION_COUNT, 0) + 1
        prefs.edit().putLong(KEY_SESSION_COUNT, sessionCount).apply()
        if (!prefs.contains(KEY_FIRST_LAUNCH)) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH, sessionStartTime).apply()
            trackEvent("app_first_launch")
        }
        trackEvent("session_start")
        trackDeviceInfo()
        logger.info(TAG, "Analytics session started (session #$sessionCount)")
    }

    fun endSession() {
        if (!analyticsEnabled || sessionStartTime == 0L) return
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        trackPerformanceMetric("session_duration", sessionDuration)
        trackEvent("session_end")
        logger.info(TAG, "Analytics session ended (duration: ${sessionDuration}ms)")
        sessionStartTime = 0L
    }

    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        if (!analyticsEnabled) return
        eventCounts.computeIfAbsent(eventName) { AtomicLong(0) }.incrementAndGet()
        val propertiesStr = if (properties.isNotEmpty()) properties.entries.joinToString(", ") { "${it.key}=${it.value}" } else "no properties"
        logger.info(TAG, "Event tracked: $eventName ($propertiesStr)")
        storeEventLocally(eventName, properties)
    }

    fun trackPerformanceMetric(metricName: String, value: Long) {
        if (!analyticsEnabled) return
        performanceMetrics.computeIfAbsent(metricName) { mutableListOf() }.add(value)
        logger.debug(TAG, "Performance metric tracked: $metricName = $value")
    }

    fun trackTemplateCreation(success: Boolean, templateSize: Int = 0) {
        trackEvent("template_created", mapOf("success" to success, "template_size" to templateSize))
    }

    fun trackSearchOperation(duration: Long, success: Boolean, matchFound: Boolean = false, confidence: Float = 0f) {
        val properties = mapOf("duration_ms" to duration, "success" to success, "match_found" to matchFound, "confidence" to confidence)
        trackEvent("search_operation", properties)
        trackPerformanceMetric("search_duration", duration)
    }

    fun trackError(errorType: String, errorMessage: String, isFatal: Boolean = false) {
        val properties = mapOf("error_type" to errorType, "error_message" to errorMessage, "is_fatal" to isFatal)
        trackEvent("error_occurred", properties)
        if (isFatal) {
            prefs.edit().putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis()).apply()
        }
    }

    fun trackUserAction(action: String, screen: String = "") {
        val properties = if (screen.isNotEmpty()) mapOf("screen" to screen) else emptyMap()
        trackEvent("user_action_$action", properties)
    }

    private fun trackDeviceInfo() {
        val properties = mapOf(
            "device_model" to Build.MODEL,
            "device_manufacturer" to Build.MANUFACTURER,
            "android_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "app_version" to getAppVersion()
        )
        trackEvent("device_info", properties)
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun storeEventLocally(eventName: String, properties: Map<String, Any>) {
        logger.debug(TAG, "Event stored locally: $eventName")
    }

    fun getAnalyticsStats(): AnalyticsStats {
        val sessionCount = prefs.getLong(KEY_SESSION_COUNT, 0)
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, 0)
        val lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0)
        return AnalyticsStats(analyticsEnabled, sessionCount, firstLaunch, lastCrashTime, eventCounts.toMap().mapValues { it.value.get() }, getPerformanceMetricsSummary())
    }

    private fun getPerformanceMetricsSummary(): Map<String, PerformanceMetricSummary> {
        return performanceMetrics.mapValues { (_, values) ->
            if (values.isEmpty()) {
                PerformanceMetricSummary(0, 0.0, 0, 0)
            } else {
                PerformanceMetricSummary(values.size, values.average(), values.minOrNull() ?: 0, values.maxOrNull() ?: 0)
            }
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analyticsEnabled = enabled
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, enabled).apply()
        logger.info(TAG, "Analytics ${if (enabled) "enabled" else "disabled"}")
        if (enabled) {
            trackEvent("analytics_enabled")
        }
    }

    fun clearAnalyticsData() {
        eventCounts.clear()
        performanceMetrics.clear()
        prefs.edit().clear().apply()
        logger.info(TAG, "Analytics data cleared")
        trackEvent("analytics_data_cleared")
    }

    fun sendCrashReport(exception: Throwable, additionalInfo: String = "") {
        Log.d(TAG, "sendCrashReport called, but ACRA is disabled.")
    }

    data class AnalyticsStats(
        val analyticsEnabled: Boolean,
        val sessionCount: Long,
        val firstLaunchTime: Long,
        val lastCrashTime: Long,
        val eventCounts: Map<String, Long>,
        val performanceMetrics: Map<String, PerformanceMetricSummary>
    )

    data class PerformanceMetricSummary(
        val count: Int,
        val average: Double,
        val min: Long,
        val max: Long
    )
}
