package com.templatefinder

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.templatefinder.model.AppSettings
import com.templatefinder.util.ErrorHandler
import com.templatefinder.util.Logger
import com.templatefinder.util.AppOptimizationManager
import com.templatefinder.util.AnalyticsManager

import com.templatefinder.util.BatteryOptimizer
import com.templatefinder.util.AccessibilityManager
import com.templatefinder.util.PerformanceOptimizer
import org.opencv.android.OpenCVLoader

/**
 * Application class for handling system-wide events and initialization
 */
class TemplateFinderApplication : Application() {

    companion object {
        private const val TAG = "TemplateFinderApp"
    }

    private lateinit var errorHandler: ErrorHandler
    private lateinit var logger: Logger
    private lateinit var appOptimizationManager: AppOptimizationManager
    private lateinit var analyticsManager: AnalyticsManager

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Apply the saved language setting first
            applyLanguageSetting()

            // Initialize core utilities
            errorHandler = ErrorHandler.getInstance(this)
            logger = Logger.getInstance(this)
            appOptimizationManager = AppOptimizationManager.getInstance(this)
            analyticsManager = AnalyticsManager.getInstance(this)
            
            // Configure logger for release builds
            logger.configure(
                fileLoggingEnabled = true,
                debugLoggingEnabled = BuildConfig.DEBUG,
                logLevel = if (true) Logger.LogLevel.DEBUG else Logger.LogLevel.INFO
            )
            
            // Initialize all optimization systems
            appOptimizationManager.initialize()

            // Initialize OpenCV
            if (!OpenCVLoader.initLocal()) {
                logger.error(TAG, "OpenCV initialization failed!")
            } else {
                logger.info(TAG, "OpenCV loaded successfully")
            }
            
            logger.info(TAG, "TemplateFinderApplication initialized with comprehensive optimizations")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing application", e)
            try {
                AnalyticsManager.getInstance(this).trackError("app_initialization_error", e.message ?: "Unknown error", true)
            } catch (analyticsError: Exception) {
                Log.e(TAG, "Error tracking initialization error", analyticsError)
            }
        }
    }

    private fun applyLanguageSetting() {
        val settings = AppSettings.load(this)
        val appLocale = LocaleListCompat.forLanguageTags(settings.language)
        AppCompatDelegate.setApplicationLocales(appLocale)
        Log.d(TAG, "Applied language setting: ${settings.language}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logger.info(TAG, "Configuration changed")
        analyticsManager.trackEvent("configuration_changed")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        logger.warning(TAG, "Low memory callback received")
        
        try {
            appOptimizationManager.applyEmergencyOptimizations()
            AnalyticsManager.getInstance(this).trackEvent("low_memory_warning")
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SYSTEM,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Error handling low memory callback",
                throwable = e,
                context = "Application onLowMemory"
            )
            AnalyticsManager.getInstance(this).trackError("low_memory_handling_error", e.message ?: "Unknown error")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        val levelName = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "UNKNOWN($level)"
        }
        
        logger.warning(TAG, "Memory trim requested: $levelName")
        
        try {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                appOptimizationManager.applyEmergencyOptimizations()
            }
            AnalyticsManager.getInstance(this).trackEvent("memory_trim", mapOf("level" to levelName))
        } catch (e: Exception) {
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SYSTEM,
                severity = ErrorHandler.SEVERITY_HIGH,
                message = "Error handling memory trim",
                throwable = e,
                context = "Application onTrimMemory level: $levelName"
            )
            AnalyticsManager.getInstance(this).trackError("memory_trim_handling_error", e.message ?: "Unknown error")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        logger.info(TAG, "Application terminating")
        
        try {
            // Shutdown optimization systems
            appOptimizationManager.shutdown()
            
            // End analytics session
            AnalyticsManager.getInstance(this).endSession()
            AnalyticsManager.getInstance(this).trackEvent("app_terminated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during application termination", e)
        }
    }

    /**
     * Get application-wide managers for use by activities and services
     */
    fun getAppOptimizationManager(): AppOptimizationManager = appOptimizationManager
    fun getBatteryOptimizer(): com.templatefinder.util.BatteryOptimizer = com.templatefinder.util.BatteryOptimizer.getInstance(this)
    fun getAnalyticsManager(): com.templatefinder.util.AnalyticsManager = com.templatefinder.util.AnalyticsManager.getInstance(this)
    fun getAccessibilityManager(): com.templatefinder.util.AccessibilityManager = com.templatefinder.util.AccessibilityManager.getInstance(this)
    fun getPerformanceOptimizer(): com.templatefinder.util.PerformanceOptimizer = com.templatefinder.util.PerformanceOptimizer.getInstance(this)
}