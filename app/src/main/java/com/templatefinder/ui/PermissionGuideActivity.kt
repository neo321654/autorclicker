package com.templatefinder.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.templatefinder.R
import com.templatefinder.util.PermissionManager
import com.templatefinder.util.PermissionResult

class PermissionGuideActivity : AppCompatActivity() {
    
    private lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_guide)
        
        permissionManager = PermissionManager(this)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        findViewById<View>(R.id.btnEnableAccessibility).setOnClickListener {
            permissionManager.openAccessibilitySettings()
        }
        
        findViewById<View>(R.id.btnEnableOverlay).setOnClickListener {
            permissionManager.requestOverlayPermission(this)
        }
        
        findViewById<View>(R.id.btnEnableNotifications).setOnClickListener {
            permissionManager.requestNotificationPermission(this)
        }
        
        findViewById<View>(R.id.btnContinue).setOnClickListener {
            checkPermissionsAndContinue()
        }
        
        findViewById<View>(R.id.btnSkip).setOnClickListener {
            finishWithResult(false)
        }
    }
    
    private fun checkPermissions() {
        val status = permissionManager.checkAllPermissions()
        updateUI(status)
    }
    
    private fun updateUI(status: com.templatefinder.util.PermissionStatus) {
        // Update accessibility service status
        val accessibilityIcon = findViewById<View>(R.id.iconAccessibility)
        val accessibilityButton = findViewById<View>(R.id.btnEnableAccessibility)
        if (status.accessibilityService) {
            accessibilityIcon.setBackgroundResource(R.drawable.ic_check_green)
            accessibilityButton.visibility = View.GONE
        } else {
            accessibilityIcon.setBackgroundResource(R.drawable.ic_warning_red)
            accessibilityButton.visibility = View.VISIBLE
        }
        
        // Update overlay permission status
        val overlayIcon = findViewById<View>(R.id.iconOverlay)
        val overlayButton = findViewById<View>(R.id.btnEnableOverlay)
        if (status.overlay) {
            overlayIcon.setBackgroundResource(R.drawable.ic_check_green)
            overlayButton.visibility = View.GONE
        } else {
            overlayIcon.setBackgroundResource(R.drawable.ic_warning_orange)
            overlayButton.visibility = View.VISIBLE
        }
        
        // Update notification permission status
        val notificationIcon = findViewById<View>(R.id.iconNotification)
        val notificationButton = findViewById<View>(R.id.btnEnableNotifications)
        if (status.notification) {
            notificationIcon.setBackgroundResource(R.drawable.ic_check_green)
            notificationButton.visibility = View.GONE
        } else {
            notificationIcon.setBackgroundResource(R.drawable.ic_warning_orange)
            notificationButton.visibility = View.VISIBLE
        }
        
        // Update continue button
        val continueButton = findViewById<View>(R.id.btnContinue)
        continueButton.isEnabled = status.hasMinimumRequired()
    }
    
    private fun checkPermissionsAndContinue() {
        val status = permissionManager.checkAllPermissions()
        if (status.hasMinimumRequired()) {
            finishWithResult(true)
        } else {
            showMinimumRequirementDialog()
        }
    }
    
    private fun showMinimumRequirementDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.permission_missing_title)
            .setMessage(R.string.permission_missing_message)
            .setPositiveButton("Enable") { _, _ ->
                permissionManager.openAccessibilitySettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun finishWithResult(success: Boolean) {
        val resultIntent = Intent().apply {
            putExtra("permissions_granted", success)
        }
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultIntent)
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        // Recheck permissions when returning from settings
        checkPermissions()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults) { result ->
            when (result) {
                is PermissionResult.Notification -> {
                    checkPermissions()
                }
                else -> { /* Handle other results if needed */ }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        permissionManager.handleActivityResult(requestCode, resultCode) { result ->
            when (result) {
                is PermissionResult.Overlay -> {
                    checkPermissions()
                }
                is PermissionResult.AccessibilityService -> {
                    checkPermissions()
                }
                else -> { /* Handle other results if needed */ }
            }
        }
    }
}