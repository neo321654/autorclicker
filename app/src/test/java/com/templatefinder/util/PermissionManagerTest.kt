package com.templatefinder.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mockito.*

class PermissionManagerTest {
    
    private lateinit var mockContext: Context
    private lateinit var permissionManager: PermissionManager
    
    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        `when`(mockContext.packageName).thenReturn("com.templatefinder")
        permissionManager = PermissionManager(mockContext)
    }
    
    @Test
    fun testPermissionStatusAllGranted() {
        val status = PermissionStatus(
            accessibilityService = true,
            overlay = true,
            notification = true
        )
        
        assertTrue(status.allGranted())
        assertTrue(status.hasMinimumRequired())
    }
    
    @Test
    fun testPermissionStatusMinimumRequired() {
        val status = PermissionStatus(
            accessibilityService = true,
            overlay = false,
            notification = false
        )
        
        assertFalse(status.allGranted())
        assertTrue(status.hasMinimumRequired())
    }
    
    @Test
    fun testPermissionStatusNotMet() {
        val status = PermissionStatus(
            accessibilityService = false,
            overlay = true,
            notification = true
        )
        
        assertFalse(status.allGranted())
        assertFalse(status.hasMinimumRequired())
    }
    
    @Test
    fun testPermissionResults() {
        val accessibilityResult = PermissionResult.AccessibilityService(true)
        assertTrue(accessibilityResult.granted)
        
        val overlayResult = PermissionResult.Overlay(false)
        assertFalse(overlayResult.granted)
        
        val notificationResult = PermissionResult.Notification(true)
        assertTrue(notificationResult.granted)
    }
    
    @Test
    fun testHasOverlayPermissionOlderAndroid() {
        // For Android versions < M, overlay permission should always return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            assertTrue(permissionManager.hasOverlayPermission())
        }
    }
    
    @Test
    fun testHasNotificationPermissionOlderAndroid() {
        // For Android versions < TIRAMISU, notification permission should always return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            assertTrue(permissionManager.hasNotificationPermission())
        }
    }
}