package com.templatefinder.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.templatefinder.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissionManager"
        const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
        const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1002
        const val REQUEST_CODE_ACCESSIBILITY_SETTINGS = 1003
    }
    
    /**
     * Checks if the specific accessibility service is enabled for this app.
     * This is the most reliable method.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "${context.packageName}/${com.templatefinder.service.ScreenshotAccessibilityService::class.java.canonicalName}"
        try {
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabledServices?.contains(serviceId, ignoreCase = false) ?: false
        } catch (e: Exception) {
            // On some devices, reading secure settings can fail. Fallback to the AccessibilityManager method.
            Log.w(TAG, "Error reading secure settings, falling back to AccessibilityManager.", e)
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return enabledServices.any { it.id == serviceId }
        }
    }
    
    /**
     * Opens accessibility settings for user to enable the service
     */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Checks if overlay permission is granted
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Not required for older versions
        }
    }
    
    /**
     * Requests overlay permission
     */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
        }
    }
    
    /**
     * Checks if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
    }
    
    /**
     * Requests notification permission (Android 13+)
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSION
            )
        }
    }
    
    /**
     * Checks all required permissions
     */
    fun checkAllPermissions(): PermissionStatus {
        return PermissionStatus(
            accessibilityService = isAccessibilityServiceEnabled(),
            overlay = hasOverlayPermission(),
            notification = hasNotificationPermission()
        )
    }
    
    /**
     * Requests all missing permissions
     */
    fun requestMissingPermissions(activity: Activity) {
        val status = checkAllPermissions()
        
        if (!status.accessibilityService) {
            // Show dialog explaining accessibility service requirement
            showAccessibilityServiceDialog(activity)
        }
        
        if (!status.overlay) {
            requestOverlayPermission(activity)
        }
        
        if (!status.notification) {
            requestNotificationPermission(activity)
        }
    }
    
    /**
     * Shows dialog explaining accessibility service requirement
     */
    private fun showAccessibilityServiceDialog(activity: Activity) {
        android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.accessibility_service_required_title)
            .setMessage(R.string.accessibility_service_required_message)
            .setPositiveButton("Open Settings") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Opens app settings page
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }
    
    /**
     * Handles permission request results
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onResult: (PermissionResult) -> Unit
    ) {
        when (requestCode) {
            REQUEST_CODE_NOTIFICATION_PERMISSION -> {
                val granted = grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                onResult(PermissionResult.Notification(granted))
            }
        }
    }
    
    /**
     * Handles activity results for permission requests
     */
    fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        onResult: (PermissionResult) -> Unit
    ) {
        when (requestCode) {
            REQUEST_CODE_OVERLAY_PERMISSION -> {
                onResult(PermissionResult.Overlay(hasOverlayPermission()))
            }
            REQUEST_CODE_ACCESSIBILITY_SETTINGS -> {
                onResult(PermissionResult.AccessibilityService(isAccessibilityServiceEnabled()))
            }
        }
    }
}

/**
 * Data class representing permission status
 */
data class PermissionStatus(
    val accessibilityService: Boolean,
    val overlay: Boolean,
    val notification: Boolean
) {
    fun allGranted(): Boolean = accessibilityService && overlay && notification
    fun hasMinimumRequired(): Boolean = accessibilityService // Minimum requirement
}

/**
 * Sealed class for permission results
 */
sealed class PermissionResult {
    data class AccessibilityService(val granted: Boolean) : PermissionResult()
    data class Overlay(val granted: Boolean) : PermissionResult()
    data class Notification(val granted: Boolean) : PermissionResult()
}