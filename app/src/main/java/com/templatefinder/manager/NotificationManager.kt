package com.templatefinder.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.templatefinder.MainActivity
import com.templatefinder.R
import com.templatefinder.model.AppSettings
import com.templatefinder.model.SearchResult
import com.templatefinder.service.CoordinateFinderService
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for handling result notifications and alerts
 */
class NotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"
        
        // Notification channels
        private const val CHANNEL_RESULTS_VIBRATE = "coordinate_results_vibrate"
        private const val CHANNEL_RESULTS_NO_VIBRATE = "coordinate_results_no_vibrate"
        private const val CHANNEL_ALERTS = "coordinate_alerts"
        
        // Notification IDs
        private const val NOTIFICATION_ID_RESULT = 2001
        private const val NOTIFICATION_ID_ALERT = 2002
        
        // Vibration patterns
        private val VIBRATION_PATTERN_RESULT = longArrayOf(0, 300, 100, 300)
        private val VIBRATION_PATTERN_ALERT = longArrayOf(0, 100, 50, 100, 50, 100)
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var appSettings = AppSettings.load(context)
    
    // Result history for multiple results
    private val resultHistory = mutableListOf<SearchResult>()
    private val maxHistorySize = 10

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Results channel with vibration
            val resultsChannelVibrate = NotificationChannel(
                CHANNEL_RESULTS_VIBRATE,
                "Coordinate Results (Vibration)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for found coordinates with vibration"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN_RESULT
                setShowBadge(true)
            }

            // Results channel without vibration
            val resultsChannelNoVibrate = NotificationChannel(
                CHANNEL_RESULTS_NO_VIBRATE,
                "Coordinate Results (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for found coordinates without vibration"
                enableVibration(false)
                setShowBadge(true)
            }
            
            // Alerts channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "System Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "System alerts and status notifications"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN_ALERT
                setShowBadge(false)
            }
            
            notificationManager.createNotificationChannel(resultsChannelVibrate)
            notificationManager.createNotificationChannel(resultsChannelNoVibrate)
            notificationManager.createNotificationChannel(alertsChannel)
            
            Log.d(TAG, "Notification channels created")
        }
    }

    /**
     * Show notification when coordinates are found
     */
    fun showResultNotification(result: SearchResult) {
        if (!result.found || !shouldShowNotifications()) {
            return
        }

        try {
            // Add to history
            addToHistory(result)
            
            // Create and show notification on the correct channel
            val notification = createResultNotification(result)
            // Create and show notification on the correct channel
            notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
            
            Log.d(TAG, "Result notification shown: ${result.getFormattedCoordinates()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing result notification", e)
        }
    }

    /**
     * Show alert notification for system messages
     */
    fun showAlertNotification(title: String, message: String, isError: Boolean = false) {
        if (!shouldShowNotifications()) {
            return
        }

        try {
            val notification = createAlertNotification(title, message, isError)
            notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
            
            // Trigger vibration for errors
            if (isError && shouldVibrate()) {
                triggerVibration(VIBRATION_PATTERN_ALERT)
            }
            
            Log.d(TAG, "Alert notification shown: $title - $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing alert notification", e)
        }
    }

    /**
     * Create notification for coordinate results
     */
    private fun createResultNotification(result: SearchResult): android.app.Notification {
        val channelId = if (shouldVibrate()) CHANNEL_RESULTS_VIBRATE else CHANNEL_RESULTS_NO_VIBRATE

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_result", true)
            putExtra("result_x", result.coordinates?.x ?: 0)
            putExtra("result_y", result.coordinates?.y ?: 0)
            putExtra("result_confidence", result.confidence)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeString = timeFormat.format(Date(result.timestamp))
        
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Coordinates Found!")
            .setContentText(result.getFormattedCoordinates())
            .setSubText("Confidence: ${result.getConfidencePercentage()}% at $timeString")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(createViewAction())
            .addAction(createShareAction(result))
            .addAction(createStopServiceAction())
            .setStyle(createExpandedStyle(result))
            .build()
    }

    /**
     * Create action to stop the search service
     */
    private fun createStopServiceAction(): NotificationCompat.Action {
        val stopIntent = Intent(context, CoordinateFinderService::class.java).apply {
            action = CoordinateFinderService.ACTION_STOP_SEARCH
        }
        
        val pendingIntent = PendingIntent.getService(
            context, 3, stopIntent, // Use a unique request code
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop Service",
            pendingIntent
        ).build()
    }

    /**
     * Create notification for system alerts
     */
    private fun createAlertNotification(title: String, message: String, isError: Boolean): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val iconRes = if (isError) {
            android.R.drawable.ic_dialog_alert
        } else {
            R.drawable.ic_launcher_foreground
        }
        
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isError) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    /**
     * Create expanded notification style with result details
     */
    private fun createExpandedStyle(result: SearchResult): NotificationCompat.Style {
        val historyText = StringBuilder()
        
        // Add current result
        historyText.append("Latest: ${result.getFormattedCoordinates()}\n")
        historyText.append("Confidence: ${result.getConfidencePercentage()}%\n")
        historyText.append("Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.timestamp))}\n")
        
        // Add recent history
        if (resultHistory.size > 1) {
            historyText.append("\nRecent Results:\n")
            resultHistory.takeLast(3).reversed().drop(1).forEach { historyResult ->
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(historyResult.timestamp))
                historyText.append("• ${historyResult.getFormattedCoordinates()} ($time)\n")
            }
        }
        
        return NotificationCompat.BigTextStyle()
            .bigText(historyText.toString().trim())
            .setSummaryText("${resultHistory.size} results found")
    }

    /**
     * Create action to view results in main activity
     */
    private fun createViewAction(): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_results", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "View",
            pendingIntent
        ).build()
    }

    /**
     * Create action to share result
     */
    private fun createShareAction(result: SearchResult): NotificationCompat.Action {
        val shareText = "Coordinates found: ${result.getFormattedCoordinates()} " +
                "with ${result.getConfidencePercentage()}% confidence"
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Template Coordinate Finder Result")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 2, Intent.createChooser(shareIntent, "Share Result"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_share,
            "Share",
            pendingIntent
        ).build()
    }

    /**
     * Trigger vibration with pattern
     */
    private fun triggerVibration(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            
            Log.d(TAG, "Vibration triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering vibration", e)
        }
    }

    /**
     * Add result to history
     */
    private fun addToHistory(result: SearchResult) {
        resultHistory.add(result)
        
        // Keep only recent results
        if (resultHistory.size > maxHistorySize) {
            resultHistory.removeAt(0)
        }
        
        Log.d(TAG, "Result added to history. Total: ${resultHistory.size}")
    }

    /**
     * Get result history
     */
    fun getResultHistory(): List<SearchResult> = resultHistory.toList()

    /**
     * Clear result history
     */
    fun clearHistory() {
        resultHistory.clear()
        Log.d(TAG, "Result history cleared")
    }

    /**
     * Clear all notifications
     */
    fun clearAllNotifications() {
        notificationManager.cancelAll()
        Log.d(TAG, "All notifications cleared")
    }

    /**
     * Clear specific notification
     */
    fun clearResultNotification() {
        notificationManager.cancel(NOTIFICATION_ID_RESULT)
        Log.d(TAG, "Result notification cleared")
    }

    /**
     * Check if notifications should be shown based on settings
     */
    private fun shouldShowNotifications(): Boolean {
        // For now, always show notifications
        // This can be made configurable with app settings
        return true
    }

    /**
     * Check if vibration should be triggered based on settings
     */
    private fun shouldVibrate(): Boolean {
        return appSettings.vibrationEnabled
    }

    /**
     * Update settings and reload configuration
     */
    fun updateSettings() {
        appSettings = AppSettings.load(context)
        Log.d(TAG, "Notification settings updated")
    }

    /**
     * Get notification statistics
     */
    fun getNotificationStats(): NotificationStats {
        return NotificationStats(
            totalResults = resultHistory.size,
            lastResultTime = resultHistory.lastOrNull()?.timestamp ?: 0L,
            notificationsEnabled = shouldShowNotifications(),
            vibrationEnabled = shouldVibrate()
        )
    }

    /**
     * Data class for notification statistics
     */
    data class NotificationStats(
        val totalResults: Int,
        val lastResultTime: Long,
        val notificationsEnabled: Boolean,
        val vibrationEnabled: Boolean
    )
}