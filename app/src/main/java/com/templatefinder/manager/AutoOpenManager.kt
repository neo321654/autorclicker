package com.templatefinder.manager

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.templatefinder.MainActivity
import com.templatefinder.R
import com.templatefinder.model.SearchResult
import com.templatefinder.util.PermissionManager
import androidx.appcompat.view.ContextThemeWrapper
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for auto-opening application and showing overlay windows when results are found
 */
class AutoOpenManager(private val context: Context) {

    companion object {
        private const val TAG = "AutoOpenManager"
        private const val OVERLAY_DISPLAY_DURATION = 5000L // 5 seconds
    }

    private val permissionManager = PermissionManager(context)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false

    /**
     * Bring application to foreground when result is found
     */
    fun bringAppToForeground(result: SearchResult) {
        if (!result.found) {
            Log.d(TAG, "No result to show, skipping auto-open")
            return
        }

        try {
            Log.d(TAG, "Bringing app to foreground for result: ${result.getFormattedCoordinates()}")
            
            // Check if app is already in foreground
            if (isAppInForeground()) {
                Log.d(TAG, "App already in foreground")
                return
            }
            
            // Create intent to bring MainActivity to front
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("auto_opened", true)
                putExtra("result_x", result.coordinates?.x ?: 0)
                putExtra("result_y", result.coordinates?.y ?: 0)
                putExtra("result_confidence", result.confidence)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "App brought to foreground successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error bringing app to foreground", e)
        }
    }

    /**
     * Show overlay window with result information
     */
    fun showResultOverlay(result: SearchResult) {
        if (!result.found) {
            Log.d(TAG, "No result to show in overlay")
            return
        }

        if (!permissionManager.hasOverlayPermission()) {
            Log.w(TAG, "No overlay permission, cannot show result overlay")
            return
        }

        try {
            // Remove existing overlay if present
            hideResultOverlay()
            
            // Create overlay view
            overlayView = createOverlayView(result)
            
            // Add overlay to window manager
            val params = createOverlayLayoutParams()
            windowManager.addView(overlayView, params)
            isOverlayShowing = true
            
            Log.d(TAG, "Result overlay shown: ${result.getFormattedCoordinates()}")
            
            // Auto-hide overlay after duration
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                hideResultOverlay()
            }, OVERLAY_DISPLAY_DURATION)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing result overlay", e)
        }
    }

    /**
     * Hide result overlay window
     */
    fun hideResultOverlay() {
        try {
            overlayView?.let { view ->
                windowManager.removeView(view)
                overlayView = null
                isOverlayShowing = false
                Log.d(TAG, "Result overlay hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding result overlay", e)
        }
    }

    /**
     * Create overlay view with result information
     */
    private fun createOverlayView(result: SearchResult): View {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_TemplateCoordinateFinder)
        val inflater = LayoutInflater.from(themedContext)
        val overlayView = inflater.inflate(R.layout.overlay_result, null)
        
        // Set result information
        overlayView.findViewById<TextView>(R.id.coordinatesText)?.text = 
            result.getFormattedCoordinates()
        
        overlayView.findViewById<TextView>(R.id.confidenceText)?.text = 
            "Confidence: ${result.getConfidencePercentage()}%"
        
        overlayView.findViewById<TextView>(R.id.timestampText)?.text = 
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.timestamp))
        
        // Add click listener to open main activity
        overlayView.setOnClickListener {
            bringAppToForeground(result)
            hideResultOverlay()
        }
        
        // Add close button
        overlayView.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            hideResultOverlay()
        }
        
        return overlayView
    }

    /**
     * Create layout parameters for overlay window
     */
    private fun createOverlayLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Offset from top
        }
    }

    /**
     * Check if the app is currently in foreground
     */
    private fun isAppInForeground(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, use different approach
                val runningTasks = activityManager.appTasks
                runningTasks.any { task ->
                    task.taskInfo.topActivity?.packageName == context.packageName
                }
            } else {
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                runningTasks.isNotEmpty() && 
                runningTasks[0].topActivity?.packageName == context.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is in foreground", e)
            false
        }
    }

    /**
     * Request overlay permission if not granted
     */
    fun requestOverlayPermission() {
        if (!permissionManager.hasOverlayPermission()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Overlay permission request initiated")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting overlay permission", e)
            }
        }
    }

    /**
     * Show persistent result display that stays until dismissed
     */
    fun showPersistentResultDisplay(results: List<SearchResult>) {
        if (!permissionManager.hasOverlayPermission()) {
            Log.w(TAG, "No overlay permission for persistent display")
            return
        }

        try {
            // Remove existing overlay
            hideResultOverlay()
            
            // Create persistent overlay with multiple results
            overlayView = createPersistentOverlayView(results)
            
            val params = createPersistentOverlayLayoutParams()
            windowManager.addView(overlayView, params)
            isOverlayShowing = true
            
            Log.d(TAG, "Persistent result display shown with ${results.size} results")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing persistent result display", e)
        }
    }

    /**
     * Create persistent overlay view with multiple results
     */
    private fun createPersistentOverlayView(results: List<SearchResult>): View {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_TemplateCoordinateFinder)
        val inflater = LayoutInflater.from(themedContext)
        val overlayView = inflater.inflate(R.layout.overlay_persistent_results, null)
        
        // Set results information
        val resultsText = results.takeLast(5).joinToString("\n") { result ->
            "${result.getFormattedCoordinates()} (${result.getConfidencePercentage()}%)"
        }
        
        overlayView.findViewById<TextView>(R.id.resultsListText)?.text = resultsText
        overlayView.findViewById<TextView>(R.id.resultCountText)?.text = 
            "${results.size} results found"
        
        // Add click listener to open main activity
        overlayView.findViewById<View>(R.id.openAppButton)?.setOnClickListener {
            bringAppToForeground(results.lastOrNull() ?: SearchResult.failure())
            hideResultOverlay()
        }
        
        // Add close button
        overlayView.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            hideResultOverlay()
        }
        
        return overlayView
    }

    /**
     * Create layout parameters for persistent overlay
     */
    private fun createPersistentOverlayLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 50
        }
    }

    /**
     * Check if overlay is currently showing
     */
    fun isOverlayShowing(): Boolean = isOverlayShowing

    /**
     * Get auto-open manager status
     */
    fun getStatus(): AutoOpenStatus {
        return AutoOpenStatus(
            hasOverlayPermission = permissionManager.hasOverlayPermission(),
            isOverlayShowing = isOverlayShowing,
            isAppInForeground = isAppInForeground()
        )
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        hideResultOverlay()
        Log.d(TAG, "AutoOpenManager cleaned up")
    }

    /**
     * Data class for auto-open manager status
     */
    data class AutoOpenStatus(
        val hasOverlayPermission: Boolean,
        val isOverlayShowing: Boolean,
        val isAppInForeground: Boolean
    )
}