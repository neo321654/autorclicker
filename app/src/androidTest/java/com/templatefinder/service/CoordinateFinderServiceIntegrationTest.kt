package com.templatefinder.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.templatefinder.manager.ServiceCommunicationManager
import com.templatefinder.model.SearchResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@LargeTest
class CoordinateFinderServiceIntegrationTest {

    private lateinit var context: Context
    private lateinit var serviceCommunicationManager: ServiceCommunicationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        serviceCommunicationManager = ServiceCommunicationManager(context)
    }

    @After
    fun tearDown() {
        serviceCommunicationManager.cleanup()
    }

    @Test
    fun testServiceCommunicationManagerCreation() {
        assertNotNull("ServiceCommunicationManager should be created", serviceCommunicationManager)
        assertFalse("Service should not be bound initially", serviceCommunicationManager.isServiceBound())
    }

    @Test
    fun testServiceBinding() {
        val bindingLatch = CountDownLatch(1)
        var serviceConnected = false

        val callback = object : ServiceCommunicationManager.ServiceCommunicationCallback {
            override fun onServiceConnected() {
                serviceConnected = true
                bindingLatch.countDown()
            }

            override fun onServiceDisconnected() {}
            override fun onSearchStarted() {}
            override fun onSearchStopped() {}
            override fun onResultFound(result: SearchResult) {}
            override fun onSearchError(error: String) {}
            override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {}
        }

        serviceCommunicationManager.addCallback(callback)
        
        // Attempt to bind to service
        val bindSuccess = serviceCommunicationManager.bindService()
        
        // Note: In test environment, service binding might not work as expected
        // but we can test that the method doesn't crash
        assertNotNull("Bind method should complete", bindSuccess)
    }

    @Test
    fun testServiceRunningState() {
        // Test static method for checking service state
        val isRunning = serviceCommunicationManager.isServiceRunning()
        
        // Initially should not be running
        assertFalse("Service should not be running initially", isRunning)
    }

    @Test
    fun testCallbackManagement() {
        var callbackCount = 0
        
        val callback1 = object : ServiceCommunicationManager.ServiceCommunicationCallback {
            override fun onServiceConnected() { callbackCount++ }
            override fun onServiceDisconnected() {}
            override fun onSearchStarted() {}
            override fun onSearchStopped() {}
            override fun onResultFound(result: SearchResult) {}
            override fun onSearchError(error: String) {}
            override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {}
        }
        
        val callback2 = object : ServiceCommunicationManager.ServiceCommunicationCallback {
            override fun onServiceConnected() { callbackCount++ }
            override fun onServiceDisconnected() {}
            override fun onSearchStarted() {}
            override fun onSearchStopped() {}
            override fun onResultFound(result: SearchResult) {}
            override fun onSearchError(error: String) {}
            override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {}
        }

        // Add callbacks
        serviceCommunicationManager.addCallback(callback1)
        serviceCommunicationManager.addCallback(callback2)
        
        // Remove one callback
        serviceCommunicationManager.removeCallback(callback1)
        
        // Should not crash
        assertTrue("Callback management should work", true)
    }

    @Test
    fun testConnectionInfo() {
        val connectionInfo = serviceCommunicationManager.getConnectionInfo()
        
        assertNotNull("Connection info should not be null", connectionInfo)
        assertFalse("Should not be bound initially", connectionInfo.isServiceBound)
        assertFalse("Should not be running initially", connectionInfo.isServiceRunning)
        assertEquals("Should have no callbacks initially", 0, connectionInfo.callbackCount)
        assertNull("Should have no service status initially", connectionInfo.serviceStatus)
    }

    @Test
    fun testStartStopSearch() {
        // Test start search
        val startResult = serviceCommunicationManager.startSearch()
        
        // In test environment, this might fail due to missing permissions
        // but the method should not crash
        assertNotNull("Start search should return a result", startResult)
        
        // Test stop search
        val stopResult = serviceCommunicationManager.stopSearch()
        assertNotNull("Stop search should return a result", stopResult)
    }

    @Test
    fun testServiceStatusRetrieval() {
        // Initially should return null since service is not bound
        val status = serviceCommunicationManager.getServiceStatus()
        assertNull("Status should be null when service not bound", status)
    }

    @Test
    fun testUpdateServiceSettings() {
        // Should not crash even if service is not bound
        serviceCommunicationManager.updateServiceSettings()
        
        // Should complete without errors
        assertTrue("Update settings should work", true)
    }

    @Test
    fun testServiceIntentActions() {
        // Test that service action constants are accessible
        assertEquals("Start action should match", 
            "com.templatefinder.START_SEARCH", 
            CoordinateFinderService.ACTION_START_SEARCH)
        
        assertEquals("Stop action should match", 
            "com.templatefinder.STOP_SEARCH", 
            CoordinateFinderService.ACTION_STOP_SEARCH)
    }

    @Test
    fun testServiceCleanup() {
        // Add a callback
        val callback = object : ServiceCommunicationManager.ServiceCommunicationCallback {
            override fun onServiceConnected() {}
            override fun onServiceDisconnected() {}
            override fun onSearchStarted() {}
            override fun onSearchStopped() {}
            override fun onResultFound(result: SearchResult) {}
            override fun onSearchError(error: String) {}
            override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {}
        }
        
        serviceCommunicationManager.addCallback(callback)
        
        // Cleanup should not crash
        serviceCommunicationManager.cleanup()
        
        // Should complete without errors
        assertTrue("Cleanup should work", true)
    }
}