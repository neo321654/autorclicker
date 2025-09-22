package com.templatefinder.util

import android.content.Context
import android.content.SharedPreferences
import com.templatefinder.model.AppSettings
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central manager for all app optimizations and polish features
 */
class AppOptimizationManager(private val context: Context) {

    companion object {
        private const val TAG = "AppOptimizationManager"
        private const val PREFS_NAME = "optimization_prefs"
        private const val KEY_OPTIMIZATION_ENABLED = "optimization_enabled"
        private const val KEY_BATTERY_OPTIMIZATION_LEVEL = "battery_optimization_level"
        private const val KEY_PERFORMANCE_OPTIMIZATION_LEVEL = "performance_optimization_level"
        private const val KEY_ACCESSIBILITY_IMPROVEMENTS_ENABLED = "accessibility_improvements_enabled"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        
        @Volatile
        private var instance: AppOptimizationManager? = null
        
        fun getInstance(context: Context): AppOptimizationManager {
            return instance ?: synchronized(this) {
                instance ?: AppOptimizationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logger = Logger.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Optimization managers
    private val batteryOptimizer: com.templatefinder.util.BatteryOptimizer = com.templatefinder.util.BatteryOptimizer.getInstance(context)
    private val performanceOptimizer: com.templatefinder.util.PerformanceOptimizer = com.templatefinder.util.PerformanceOptimizer.getInstance(context)
    private val accessibilityManager: com.templatefinder.util.AccessibilityManager = com.templatefinder.util.AccessibilityManager.getInstance(context)
    private val analyticsManager: com.templatefinder.util.AnalyticsManager = com.templatefinder.util.AnalyticsManager.getInstance(context)
    private val robustnessManager: com.templatefinder.util.RobustnessManager = com.templatefinder.util.RobustnessManager.getInstance(context)
    
    // Optimization state
    private val isOptimizationActive = AtomicBoolean(false)
    
    // Settings
    private var optimizationEnabled = true
    private var batteryOptimizationLevel = BatteryOptimizer.OptimizationLevel.NORMAL
    private var performanceOptimizationLevel = PerformanceOptimizer.OptimizationLevel.BALANCED
    private var accessibilityImprovementsEnabled = true
    private var analyticsEnabled = true

    init {
        loadSettings()
    }

    /**
     * Initialize all optimization systems
     */
    fun initialize() {
        if (isOptimizationActive.compareAndSet(false, true)) {
            logger.info(TAG, "Initializing app optimization systems")
            
            try {
                // Configure battery optimization
                batteryOptimizer.configure(
                    enabled = optimizationEnabled,
                    aggressive = batteryOptimizationLevel == BatteryOptimizer.OptimizationLevel.AGGRESSIVE
                )
                
                // Configure performance optimization
                performanceOptimizer.configure(
                    optimizationLevel = performanceOptimizationLevel,
                    adaptiveInterval = true,
                    backgroundProcessing = true
                )
                
                // Configure analytics
                analyticsManager.setAnalyticsEnabled(analyticsEnabled)
                
                // Start monitoring systems
                if (optimizationEnabled) {
                    batteryOptimizer.startMonitoring()
                    robustnessManager.startMonitoring()
                }
                
                logger.info(TAG, "App optimization systems initialized successfully")
                analyticsManager.trackEvent("optimization_systems_initialized")
                
            } catch (e: Exception) {
                logger.error(TAG, "Error initializing optimization systems", e)
                analyticsManager.trackError("optimization_initialization_error", e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Shutdown all optimization systems
     */
    fun shutdown() {
        if (isOptimizationActive.compareAndSet(true, false)) {
            logger.info(TAG, "Shutting down app optimization systems")
            
            try {
                batteryOptimizer.stopMonitoring()
                robustnessManager.stopMonitoring()
                
                logger.info(TAG, "App optimization systems shut down successfully")
                
            } catch (e: Exception) {
                logger.error(TAG, "Error shutting down optimization systems", e)
            }
        }
    }

    /**
     * Optimize app settings based on current conditions
     */
    fun optimizeAppSettings(currentSettings: AppSettings): AppSettings {
        if (!optimizationEnabled) return currentSettings
        
        return try {
            // Get battery-optimized settings
            val batteryOptimizedSettings = if (batteryOptimizer.shouldPauseSearch()) {
                // Pause search if battery is critical
                currentSettings.copy(isSearchActive = false)
            } else {
                val optimizedInterval = batteryOptimizer.getOptimizedSearchInterval(currentSettings.searchInterval)
                currentSettings.copy(searchInterval = optimizedInterval)
            }
            
            // Get performance-optimized settings
            val performanceOptimizedSettings = performanceOptimizer.optimizeAppSettings(batteryOptimizedSettings)
            
            logger.debug(TAG, "App settings optimized")
            performanceOptimizedSettings
            
        } catch (e: Exception) {
            logger.error(TAG, "Error optimizing app settings", e)
            analyticsManager.trackError("settings_optimization_error", e.message ?: "Unknown error")
            currentSettings
        }
    }

    /**
     * Get optimization recommendations for the user
     */
    fun getOptimizationRecommendations(): List<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()
        
        try {
            // Battery recommendations
            val batteryStats = batteryOptimizer.getBatteryStats()
            if (batteryStats.level < 20 && !batteryStats.isCharging) {
                recommendations.add(
                    OptimizationRecommendation(
                        type = RecommendationType.BATTERY,
                        title = "Low Battery Detected",
                        description = "Consider connecting charger or enabling aggressive battery optimization",
                        priority = RecommendationPriority.HIGH
                    )
                )
            }
            
            // Performance recommendations
            val performanceStats = performanceOptimizer.getPerformanceStats()
            if (performanceStats.isLowMemory) {
                recommendations.add(
                    OptimizationRecommendation(
                        type = RecommendationType.PERFORMANCE,
                        title = "Low Memory Detected",
                        description = "Close other apps or reduce search frequency to improve performance",
                        priority = RecommendationPriority.MEDIUM
                    )
                )
            }
            
            // Accessibility recommendations
            val accessibilityStats = accessibilityManager.getAccessibilityStats()
            if (accessibilityStats.isLargeTextEnabled && !accessibilityImprovementsEnabled) {
                recommendations.add(
                    OptimizationRecommendation(
                        type = RecommendationType.ACCESSIBILITY,
                        title = "Accessibility Improvements Available",
                        description = "Enable accessibility improvements for better large text support",
                        priority = RecommendationPriority.LOW
                    )
                )
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Error generating optimization recommendations", e)
        }
        
        return recommendations
    }

    /**
     * Apply emergency optimizations (e.g., during critical battery or memory situations)
     */
    fun applyEmergencyOptimizations() {
        logger.warning(TAG, "Applying emergency optimizations")
        
        try {
            // Force aggressive battery optimization
            batteryOptimizer.configure(enabled = true, aggressive = true)
            
            // Force memory cleanup
            robustnessManager.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            
            // Reduce performance optimization level
            performanceOptimizer.configure(
                optimizationLevel = PerformanceOptimizer.OptimizationLevel.BATTERY,
                adaptiveInterval = true,
                backgroundProcessing = false
            )
            
            analyticsManager.trackEvent("emergency_optimizations_applied")
            logger.info(TAG, "Emergency optimizations applied successfully")
            
        } catch (e: Exception) {
            logger.error(TAG, "Error applying emergency optimizations", e)
            analyticsManager.trackError("emergency_optimization_error", e.message ?: "Unknown error")
        }
    }

    /**
     * Configure optimization settings
     */
    fun configure(
        optimizationEnabled: Boolean = true,
        batteryOptimizationLevel: BatteryOptimizer.OptimizationLevel = BatteryOptimizer.OptimizationLevel.NORMAL,
        performanceOptimizationLevel: PerformanceOptimizer.OptimizationLevel = PerformanceOptimizer.OptimizationLevel.BALANCED,
        accessibilityImprovementsEnabled: Boolean = true,
        analyticsEnabled: Boolean = true
    ) {
        this.optimizationEnabled = optimizationEnabled
        this.batteryOptimizationLevel = batteryOptimizationLevel
        this.performanceOptimizationLevel = performanceOptimizationLevel
        this.accessibilityImprovementsEnabled = accessibilityImprovementsEnabled
        this.analyticsEnabled = analyticsEnabled
        
        saveSettings()
        
        // Apply new configuration
        if (isOptimizationActive.get()) {
            initialize() // Re-initialize with new settings
        }
        
        logger.info(TAG, "Optimization configuration updated")
        analyticsManager.trackEvent("optimization_configuration_changed")
    }

    /**
     * Get comprehensive optimization statistics
     */
    fun getOptimizationStats(): OptimizationStats {
        return OptimizationStats(
            optimizationEnabled = optimizationEnabled,
            isActive = isOptimizationActive.get(),
            batteryStats = batteryOptimizer.getBatteryStats(),
            performanceStats = performanceOptimizer.getPerformanceStats(),
            accessibilityStats = accessibilityManager.getAccessibilityStats(),
            analyticsStats = analyticsManager.getAnalyticsStats(),
            robustnessStats = robustnessManager.getRobustnessStats()
        )
    }

    /**
     * Load settings from preferences
     */
    private fun loadSettings() {
        optimizationEnabled = prefs.getBoolean(KEY_OPTIMIZATION_ENABLED, true)
        
        val batteryLevelName = prefs.getString(KEY_BATTERY_OPTIMIZATION_LEVEL, BatteryOptimizer.OptimizationLevel.NORMAL.name)
        batteryOptimizationLevel = try {
            BatteryOptimizer.OptimizationLevel.valueOf(batteryLevelName ?: BatteryOptimizer.OptimizationLevel.NORMAL.name)
        } catch (e: Exception) {
            BatteryOptimizer.OptimizationLevel.NORMAL
        }
        
        val performanceLevelName = prefs.getString(KEY_PERFORMANCE_OPTIMIZATION_LEVEL, PerformanceOptimizer.OptimizationLevel.BALANCED.name)
        performanceOptimizationLevel = try {
            PerformanceOptimizer.OptimizationLevel.valueOf(performanceLevelName ?: PerformanceOptimizer.OptimizationLevel.BALANCED.name)
        } catch (e: Exception) {
            PerformanceOptimizer.OptimizationLevel.BALANCED
        }
        
        accessibilityImprovementsEnabled = prefs.getBoolean(KEY_ACCESSIBILITY_IMPROVEMENTS_ENABLED, true)
        analyticsEnabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)
    }

    /**
     * Save settings to preferences
     */
    private fun saveSettings() {
        prefs.edit()
            .putBoolean(KEY_OPTIMIZATION_ENABLED, optimizationEnabled)
            .putString(KEY_BATTERY_OPTIMIZATION_LEVEL, batteryOptimizationLevel.name)
            .putString(KEY_PERFORMANCE_OPTIMIZATION_LEVEL, performanceOptimizationLevel.name)
            .putBoolean(KEY_ACCESSIBILITY_IMPROVEMENTS_ENABLED, accessibilityImprovementsEnabled)
            .putBoolean(KEY_ANALYTICS_ENABLED, analyticsEnabled)
            .apply()
    }

    /**
     * Optimization recommendation data class
     */
    data class OptimizationRecommendation(
        val type: RecommendationType,
        val title: String,
        val description: String,
        val priority: RecommendationPriority
    )

    /**
     * Recommendation types
     */
    enum class RecommendationType {
        BATTERY, PERFORMANCE, ACCESSIBILITY, ANALYTICS
    }

    /**
     * Recommendation priorities
     */
    enum class RecommendationPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Comprehensive optimization statistics
     */
    data class OptimizationStats(
        val optimizationEnabled: Boolean,
        val isActive: Boolean,
        val batteryStats: BatteryOptimizer.BatteryStats,
        val performanceStats: PerformanceOptimizer.PerformanceStats,
        val accessibilityStats: AccessibilityManager.AccessibilityStats,
        val analyticsStats: AnalyticsManager.AnalyticsStats,
        val robustnessStats: RobustnessManager.RobustnessStats
    )
}