package com.templatefinder.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.templatefinder.model.AppSettings
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Battery optimization manager for improving power efficiency
 */
class BatteryOptimizer(private val context: Context) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "BatteryOptimizer"
        private const val LOW_BATTERY_THRESHOLD = 15
        private const val CRITICAL_BATTERY_THRESHOLD = 5
        
        @Volatile
        private var instance: BatteryOptimizer? = null
        
        fun getInstance(context: Context): BatteryOptimizer {
            return instance ?: synchronized(this) {
                instance ?: BatteryOptimizer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logger = Logger.getInstance(context)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val isMonitoring = AtomicBoolean(false)
    
    // Battery state
    private var currentBatteryLevel = 100
    private var isCharging = false
    private var isPowerSaveMode = false
    private var isDozeMode = false
    
    // Optimization settings
    private var batteryOptimizationEnabled = true
    private var aggressiveOptimization = false
    
    // Listeners
    private val batteryChangeListeners = mutableSetOf<BatteryChangeListener>()
    
    // Battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    handleBatteryChanged(intent)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    handlePowerConnected()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    handlePowerDisconnected()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    handlePowerSaveModeChanged()
                }
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Start battery monitoring
     */
    fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            logger.info(TAG, "Starting battery monitoring")
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            
            context.registerReceiver(batteryReceiver, filter)
            
            // Get initial battery state
            updateBatteryState()
        }
    }

    /**
     * Stop battery monitoring
     */
    fun stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            logger.info(TAG, "Stopping battery monitoring")
            
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                logger.warning(TAG, "Error unregistering battery receiver", e)
            }
        }
    }

    /**
     * Handle battery level changes
     */
    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        
        if (level >= 0 && scale > 0) {
            currentBatteryLevel = (level * 100) / scale
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            
            logger.debug(TAG, "Battery changed: ${currentBatteryLevel}%, charging: $isCharging")
            
            // Notify listeners
            notifyBatteryChanged()
            
            // Apply optimizations based on battery level
            applyBatteryOptimizations()
        }
    }

    /**
     * Handle power connected
     */
    private fun handlePowerConnected() {
        isCharging = true
        logger.info(TAG, "Power connected")
        notifyBatteryChanged()
        applyBatteryOptimizations()
    }

    /**
     * Handle power disconnected
     */
    private fun handlePowerDisconnected() {
        isCharging = false
        logger.info(TAG, "Power disconnected")
        notifyBatteryChanged()
        applyBatteryOptimizations()
    }

    /**
     * Handle power save mode changes
     */
    private fun handlePowerSaveModeChanged() {
        isPowerSaveMode = powerManager.isPowerSaveMode
        logger.info(TAG, "Power save mode: $isPowerSaveMode")
        notifyBatteryChanged()
        applyBatteryOptimizations()
    }

    /**
     * Update battery state
     */
    private fun updateBatteryState() {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryIntent?.let { handleBatteryChanged(it) }
            
            isPowerSaveMode = powerManager.isPowerSaveMode
            isDozeMode = powerManager.isDeviceIdleMode
            
        } catch (e: Exception) {
            logger.warning(TAG, "Error updating battery state", e)
        }
    }

    /**
     * Apply battery optimizations based on current state
     */
    private fun applyBatteryOptimizations() {
        if (!batteryOptimizationEnabled) return
        
        val optimizationLevel = when {
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> OptimizationLevel.CRITICAL
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> OptimizationLevel.AGGRESSIVE
            isPowerSaveMode -> OptimizationLevel.AGGRESSIVE
            isDozeMode -> OptimizationLevel.AGGRESSIVE
            !isCharging && currentBatteryLevel < 30 -> OptimizationLevel.MODERATE
            else -> OptimizationLevel.NORMAL
        }
        
        logger.info(TAG, "Applying battery optimization level: $optimizationLevel")
        
        // Notify listeners about optimization level change
        batteryChangeListeners.forEach { listener ->
            try {
                listener.onOptimizationLevelChanged(optimizationLevel)
            } catch (e: Exception) {
                logger.warning(TAG, "Error notifying battery change listener", e)
            }
        }
    }

    /**
     * Get optimized search interval based on battery state
     */
    fun getOptimizedSearchInterval(baseInterval: Long): Long {
        if (!batteryOptimizationEnabled) return baseInterval
        
        val multiplier = when {
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> 5.0f
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> 3.0f
            isPowerSaveMode -> 2.5f
            isDozeMode -> 4.0f
            !isCharging && currentBatteryLevel < 30 -> 1.5f
            isCharging -> 0.8f
            else -> 1.0f
        }
        
        return (baseInterval * multiplier).toLong()
    }

    /**
     * Check if search should be paused due to battery conditions
     */
    fun shouldPauseSearch(): Boolean {
        return batteryOptimizationEnabled && (
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD ||
            (isPowerSaveMode && aggressiveOptimization) ||
            (isDozeMode && aggressiveOptimization)
        )
    }

    /**
     * Get battery optimization recommendations
     */
    fun getBatteryOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        when {
            currentBatteryLevel <= CRITICAL_BATTERY_THRESHOLD -> {
                recommendations.add("Critical battery level - consider charging device")
                recommendations.add("Search frequency has been reduced significantly")
                recommendations.add("Consider pausing search until charging")
            }
            currentBatteryLevel <= LOW_BATTERY_THRESHOLD -> {
                recommendations.add("Low battery level - search frequency reduced")
                recommendations.add("Consider enabling power save mode")
            }
            isPowerSaveMode -> {
                recommendations.add("Power save mode active - optimizations applied")
            }
            !isCharging && currentBatteryLevel < 50 -> {
                recommendations.add("Consider connecting charger for optimal performance")
            }
        }
        
        return recommendations
    }

    /**
     * Configure battery optimization settings
     */
    fun configure(
        enabled: Boolean = true,
        aggressive: Boolean = false
    ) {
        batteryOptimizationEnabled = enabled
        aggressiveOptimization = aggressive
        
        logger.info(TAG, "Battery optimization configured: enabled=$enabled, aggressive=$aggressive")
        
        if (enabled) {
            applyBatteryOptimizations()
        }
    }

    /**
     * Add battery change listener
     */
    fun addBatteryChangeListener(listener: BatteryChangeListener) {
        batteryChangeListeners.add(listener)
    }

    /**
     * Remove battery change listener
     */
    fun removeBatteryChangeListener(listener: BatteryChangeListener) {
        batteryChangeListeners.remove(listener)
    }

    /**
     * Notify battery change listeners
     */
    private fun notifyBatteryChanged() {
        val batteryInfo = BatteryInfo(
            level = currentBatteryLevel,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            isDozeMode = isDozeMode
        )
        
        batteryChangeListeners.forEach { listener ->
            try {
                listener.onBatteryChanged(batteryInfo)
            } catch (e: Exception) {
                logger.warning(TAG, "Error notifying battery change listener", e)
            }
        }
    }

    /**
     * Get current battery statistics
     */
    fun getBatteryStats(): BatteryStats {
        return BatteryStats(
            level = currentBatteryLevel,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            isDozeMode = isDozeMode,
            optimizationEnabled = batteryOptimizationEnabled,
            aggressiveOptimization = aggressiveOptimization,
            shouldPauseSearch = shouldPauseSearch()
        )
    }

    // Lifecycle callbacks
    override fun onStart(owner: LifecycleOwner) {
        startMonitoring()
    }

    override fun onStop(owner: LifecycleOwner) {
        stopMonitoring()
    }

    /**
     * Battery change listener interface
     */
    interface BatteryChangeListener {
        fun onBatteryChanged(batteryInfo: BatteryInfo)
        fun onOptimizationLevelChanged(level: OptimizationLevel)
    }

    /**
     * Battery optimization levels
     */
    enum class OptimizationLevel {
        NORMAL,     // No special optimizations
        MODERATE,   // Some optimizations applied
        AGGRESSIVE, // Aggressive power saving
        CRITICAL    // Emergency power saving
    }

    /**
     * Battery information data class
     */
    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val isPowerSaveMode: Boolean,
        val isDozeMode: Boolean
    )

    /**
     * Battery statistics data class
     */
    data class BatteryStats(
        val level: Int,
        val isCharging: Boolean,
        val isPowerSaveMode: Boolean,
        val isDozeMode: Boolean,
        val optimizationEnabled: Boolean,
        val aggressiveOptimization: Boolean,
        val shouldPauseSearch: Boolean
    )
}