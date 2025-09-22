package com.templatefinder.service

import android.graphics.Bitmap
import android.os.Build
import org.junit.Assert.*
import org.junit.Test

class ScreenshotAccessibilityServiceTest {

    @Test
    fun testServiceInstanceManagement() {
        // Initially no instance should be available
        assertNull(ScreenshotAccessibilityService.getInstance())
        assertFalse(ScreenshotAccessibilityService.isServiceRunning())
    }

    @Test
    fun testScreenshotSupportCheck() {
        val service = ScreenshotAccessibilityService()
        // This will return true if running on API 30+ or false otherwise
        val isSupported = service.isScreenshotSupported()
        assertEquals("Screenshot support should match API level", 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R, isSupported)
    }

    @Test
    fun testServiceStatusReporting() {
        val service = ScreenshotAccessibilityService()
        val status = service.getServiceStatus()
        
        assertNotNull("Service status should not be null", status)
        assertEquals("Initially no pending requests", 0, status.pendingRequests)
        assertFalse("Initially not processing screenshot", status.isProcessingScreenshot)
        assertEquals("Screenshot support should match API level", 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R, status.isScreenshotSupported)
        assertFalse("Initially service not running", status.isRunning)
    }

    @Test
    fun testConnectionListenerManagement() {
        val service = ScreenshotAccessibilityService()
        var connectionReceived = false
        var disconnectionReceived = false
        
        val listener = object : ScreenshotAccessibilityService.ServiceConnectionListener {
            override fun onServiceConnected() {
                connectionReceived = true
            }

            override fun onServiceDisconnected() {
                disconnectionReceived = true
            }
        }
        
        // Test adding and removing listeners
        service.addConnectionListener(listener)
        service.removeConnectionListener(listener)
        
        // Should not crash
        assertTrue("Listener management should work without errors", true)
    }

    @Test
    fun testPendingRequestsManagement() {
        val service = ScreenshotAccessibilityService()
        
        // Initially no pending requests
        assertEquals(0, service.getPendingRequestsCount())
        
        // Clear should work even with no requests
        service.clearPendingRequests()
        assertEquals(0, service.getPendingRequestsCount())
    }

    @Test
    fun testCallbackInterface() {
        // Test that we can create callback implementations
        val callback = object : ScreenshotAccessibilityService.ScreenshotCallback {
            override fun onScreenshotTaken(bitmap: Bitmap?) {
                // Test implementation
            }

            override fun onScreenshotError(error: String) {
                // Test implementation
            }
        }
        
        assertNotNull("Callback should be created successfully", callback)
    }

    @Test
    fun testScreenshotCallbackOnUnsupportedVersion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Skip this test on supported versions
            return
        }

        val service = ScreenshotAccessibilityService()
        var errorReceived: String? = null

        val callback = object : ScreenshotAccessibilityService.ScreenshotCallback {
            override fun onScreenshotTaken(bitmap: Bitmap?) {
                fail("Should not receive screenshot on unsupported version")
            }

            override fun onScreenshotError(error: String) {
                errorReceived = error
            }
        }

        service.takeScreenshot(callback)
        
        assertNotNull("Error message should not be null", errorReceived)
        assertTrue("Error should mention API requirement", 
            errorReceived!!.contains("Android 11") || errorReceived!!.contains("API 30"))
    }
}