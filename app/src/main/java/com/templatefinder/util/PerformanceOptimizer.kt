package com.templatefinder.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.templatefinder.model.AppSettings
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Performance optimizer for battery usage and system efficiency
 */
class PerformanceOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceOptimizer"
        
        @Volatile
        private var instance: PerformanceOptimizer? = null
        
        fun getInstance(context: Context): PerformanceOptimizer {
            return instance ?: synchronized(this) {
                instance ?: PerformanceOptimizer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logger = Logger.getInstance(context)
    private val isOptimizationEnabled = AtomicBoolean(true)
    
    // Performance settings
    private var batteryOptimizationLevel = OptimizationLevel.BALANCED
    private var adaptiveIntervalEnabled = true
    private var backgroundProcessingEnabled = true

    enum class OptimizationLevel {
        PERFORMANCE,  // Prioritize speed and accuracy
        BALANCED,     // Balance between performance and battery
        BATTERY       // Prioritize battery life
    }

    /**
     * Optimize search interval based on battery level and usage patterns
     */
    fun getOptimizedSearchInterval(baseInterval: Long): Long {
        if (!isOptimizationEnabled.get() || !adaptiveIntervalEnabled) {
            return baseInterval
        }

        return try {
            val batteryLevel = getBatteryLevel()
            val optimizationFactor = when (batteryOptimizationLevel) {
                OptimizationLevel.PERFORMANCE -> 1.0f
                OptimizationLevel.BALANCED -> when {
                    batteryLevel > 50 -> 1.0f
                    batteryLevel > 20 -> 1.5f
                    else -> 2.0f
                }
                OptimizationLevel.BATTERY -> when {
                    batteryLevel > 50 -> 1.5f
                    batteryLevel > 20 -> 2.0f
                    else -> 3.0f
                }
            }
            
            val optimizedInterval = (baseInterval * optimizationFactor).toLong()
            
            logger.debug(TAG, "Optimized search interval: ${baseInterval}ms -> ${optimizedInterval}ms " +
                    "(battery: ${batteryLevel}%, level: $batteryOptimizationLevel)")
            
            optimizedInterval
            
        } catch (e: Exception) {
            logger.warning(TAG, "Error optimizing search interval", e)
            baseInterval
        }
    }

    /**
     * Get current battery level
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            logger.warning(TAG, "Error getting battery level", e)
            100 // Assume full battery on error
        }
    }

    /**
     * Optimize template matching parameters based on device capabilities
     */
    fun getOptimizedMatchingParameters(baseThreshold: Float): OptimizedMatchingParams {
        return try {
            val devicePerformance = getDevicePerformanceLevel()
            
            val optimizedThreshold = when (batteryOptimizationLevel) {
                OptimizationLevel.PERFORMANCE -> baseThreshold
                OptimizationLevel.BALANCED -> baseThreshold + 0.05f
                OptimizationLevel.BATTERY -> baseThreshold + 0.1f
            }.coerceIn(0.1f, 1.0f)
            
            val useMultiScale = when (batteryOptimizationLevel) {
                OptimizationLevel.PERFORMANCE -> true
                OptimizationLevel.BALANCED -> devicePerformance >= DevicePerformance.MEDIUM
                OptimizationLevel.BATTERY -> false
            }
            
            val preprocessingLevel = when (batteryOptimizationLevel) {
                OptimizationLevel.PERFORMANCE -> PreprocessingLevel.FULL
                OptimizationLevel.BALANCED -> PreprocessingLevel.BASIC
                OptimizationLevel.BATTERY -> PreprocessingLevel.MINIMAL
            }
            
            OptimizedMatchingParams(
                threshold = optimizedThreshold,
                useMultiScale = useMultiScale,
                preprocessingLevel = preprocessingLevel,
                maxScales = if (useMultiScale) 5 else 1
            )
            
        } catch (e: Exception) {
            logger.warning(TAG, "Error optimizing matching parameters", e)
            OptimizedMatchingParams(baseThreshold, false, PreprocessingLevel.MINIMAL, 1)
        }
    }

    /**
     * Get device performance level
     */
    private fun getDevicePerformanceLevel(): DevicePerformance {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
            
            when {
                totalMemoryMB > 6000 && availableMemoryMB > 2000 -> DevicePerformance.HIGH
                totalMemoryMB > 3000 && availableMemoryMB > 1000 -> DevicePerformance.MEDIUM
                else -> DevicePerformance.LOW
            }
        } catch (e: Exception) {
            logger.warning(TAG, "Error determining device performance", e)
            DevicePerformance.MEDIUM
        }
    }

    /**
     * Configure optimization settings
     */
    fun configure(
        optimizationLevel: OptimizationLevel = OptimizationLevel.BALANCED,
        adaptiveInterval: Boolean = true,
        backgroundProcessing: Boolean = true
    ) {
        batteryOptimizationLevel = optimizationLevel
        adaptiveIntervalEnabled = adaptiveInterval
        backgroundProcessingEnabled = backgroundProcessing
        
        logger.info(TAG, "Performance optimizer configured: level=$optimizationLevel, " +
                "adaptive=$adaptiveInterval, background=$backgroundProcessing")
    }

    /**
     * Enable or disable optimization
     */
    fun setOptimizationEnabled(enabled: Boolean) {
        isOptimizationEnabled.set(enabled)
        logger.info(TAG, "Performance optimization ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): PerformanceStats {
        return try {
            val batteryLevel = getBatteryLevel()
            val devicePerformance = getDevicePerformanceLevel()
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.getMemoryInfo(memoryInfo)
            
            PerformanceStats(
                isOptimizationEnabled = isOptimizationEnabled.get(),
                optimizationLevel = batteryOptimizationLevel,
                batteryLevel = batteryLevel,
                devicePerformance = devicePerformance,
                availableMemoryMB = memoryInfo.availMem / (1024 * 1024),
                totalMemoryMB = memoryInfo.totalMem / (1024 * 1024),
                isLowMemory = memoryInfo.lowMemory,
                adaptiveIntervalEnabled = adaptiveIntervalEnabled,
                backgroundProcessingEnabled = backgroundProcessingEnabled
            )
        } catch (e: Exception) {
            logger.warning(TAG, "Error getting performance stats", e)
            PerformanceStats(
                isOptimizationEnabled.get(), batteryOptimizationLevel, 100,
                DevicePerformance.MEDIUM, 1000, 4000, false, 
                adaptiveIntervalEnabled, backgroundProcessingEnabled
            )
        }
    }

    /**
     * Optimize app settings based on current conditions
     */
    fun optimizeAppSettings(currentSettings: AppSettings): AppSettings {
        return try {
            val optimizedInterval = getOptimizedSearchInterval(currentSettings.searchInterval)
            val optimizedParams = getOptimizedMatchingParameters(currentSettings.matchThreshold)
            
            currentSettings.copy(
                searchInterval = optimizedInterval,
                matchThreshold = optimizedParams.threshold
            )
        } catch (e: Exception) {
            logger.warning(TAG, "Error optimizing app settings", e)
            currentSettings
        }
    }

    enum class DevicePerformance {
        LOW, MEDIUM, HIGH
    }

    enum class PreprocessingLevel {
        MINIMAL, BASIC, FULL
    }

    /**
     * Data class for optimized matching parameters
     */
    data class OptimizedMatchingParams(
        val threshold: Float,
        val useMultiScale: Boolean,
        val preprocessingLevel: PreprocessingLevel,
        val maxScales: Int
    )

    /**
     * Data class for performance statistics
     */
    data class PerformanceStats(
        val isOptimizationEnabled: Boolean,
        val optimizationLevel: OptimizationLevel,
        val batteryLevel: Int,
        val devicePerformance: DevicePerformance,
        val availableMemoryMB: Long,
        val totalMemoryMB: Long,
        val isLowMemory: Boolean,
        val adaptiveIntervalEnabled: Boolean,
        val backgroundProcessingEnabled: Boolean
    )
}