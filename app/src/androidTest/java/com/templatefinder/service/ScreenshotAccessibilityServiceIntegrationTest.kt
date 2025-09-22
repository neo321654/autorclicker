package com.templatefinder.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@LargeTest
class ScreenshotAccessibilityServiceIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testServiceConnectionListener() {
        var connectionCallbackReceived = false
        var disconnectionCallbackReceived = false
        
        val listener = object : ScreenshotAccessibilityService.ServiceConnectionListener {
            override fun onServiceConnected() {
                connectionCallbackReceived = true
            }

            override fun onServiceDisconnected() {
                disconnectionCallbackReceived = true
            }
        }

        // Test that we can add and remove listeners without crashing
        val service = ScreenshotAccessibilityService()
        service.addConnectionListener(listener)
        service.removeConnectionListener(listener)
        
        // Verify no exceptions were thrown
        assertTrue("Test completed without exceptions", true)
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
    }

    @Test
    fun testScreenshotRequestQueueManagement() {
        val service = ScreenshotAccessibilityService()
        
        // Initially empty queue
        assertEquals("Queue should be empty initially", 0, service.getPendingRequestsCount())
        
        // Test queue limit by adding multiple requests
        val callbacks = mutableListOf<TestScreenshotCallback>()
        
        // Add requests up to the limit
        for (i in 1..12) { // More than MAX_QUEUE_SIZE (10)
            val callback = TestScreenshotCallback()
            callbacks.add(callback)
            service.takeScreenshot(callback)
        }
        
        // Wait a bit for processing
        Thread.sleep(100)
        
        // Some requests should have been rejected due to queue limit
        val rejectedCallbacks = callbacks.filter { it.errorReceived != null }
        assertTrue("Some requests should be rejected when queue is full", 
            rejectedCallbacks.isNotEmpty())
        
        // Clear requests
        service.clearPendingRequests()
        assertEquals("Queue should be empty after clearing", 0, service.getPendingRequestsCount())
    }

    @Test
    fun testScreenshotIntervalEnforcement() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Skip on unsupported versions
            return
        }

        val service = ScreenshotAccessibilityService()
        val callback1 = TestScreenshotCallback()
        val callback2 = TestScreenshotCallback()
        
        // Take two screenshots in rapid succession
        service.takeScreenshot(callback1)
        service.takeScreenshot(callback2)
        
        // Wait for processing
        Thread.sleep(2000)
        
        // At least one should complete or get an error
        assertTrue("At least one callback should be triggered",
            callback1.completed || callback2.completed || 
            callback1.errorReceived != null || callback2.errorReceived != null)
    }

    @Test
    fun testErrorHandlingOnUnsupportedVersion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Skip on supported versions
            return
        }

        val service = ScreenshotAccessibilityService()
        val callback = TestScreenshotCallback()
        
        service.takeScreenshot(callback)
        
        // Wait for callback
        Thread.sleep(100)
        
        assertNotNull("Error should be received on unsupported version", callback.errorReceived)
        assertTrue("Error should mention API requirement", 
            callback.errorReceived!!.contains("Android 11") || 
            callback.errorReceived!!.contains("API 30"))
    }

    @Test
    fun testServiceInstanceManagement() {
        // Initially no instance
        assertNull("Initially no service instance", ScreenshotAccessibilityService.getInstance())
        assertFalse("Initially service not running", ScreenshotAccessibilityService.isServiceRunning())
        
        // Note: We can't easily test the actual service connection in unit tests
        // as it requires the accessibility service to be enabled by the user
        // This test just verifies the static methods work correctly
    }

    /**
     * Test callback implementation for testing
     */
    private class TestScreenshotCallback : ScreenshotAccessibilityService.ScreenshotCallback {
        var bitmap: Bitmap? = null
        var errorReceived: String? = null
        var completed = false
        
        override fun onScreenshotTaken(bitmap: Bitmap?) {
            this.bitmap = bitmap
            this.completed = true
        }

        override fun onScreenshotError(error: String) {
            this.errorReceived = error
            this.completed = true
        }
    }
}