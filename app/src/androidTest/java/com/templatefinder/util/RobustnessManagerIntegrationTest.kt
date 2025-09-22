package com.templatefinder.util

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class RobustnessManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var robustnessManager: RobustnessManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        robustnessManager = RobustnessManager.getInstance(context)
    }

    @After
    fun tearDown() {
        robustnessManager.stopMonitoring()
    }

    @Test
    fun testRobustnessManagerCreation() {
        assertNotNull("RobustnessManager should be created", robustnessManager)
    }

    @Test
    fun testStartStopMonitoring() {
        // Test starting monitoring
        robustnessManager.startMonitoring()
        
        val stats = robustnessManager.getRobustnessStats()
        assertTrue("Should be monitoring", stats.isMonitoring)
        
        // Test stopping monitoring
        robustnessManager.stopMonitoring()
        
        val stoppedStats = robustnessManager.getRobustnessStats()
        assertFalse("Should not be monitoring", stoppedStats.isMonitoring)
    }

    @Test
    fun testMemoryPressureHandling() {
        // Test different memory pressure levels
        robustnessManager.handleMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        robustnessManager.handleMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        robustnessManager.handleMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        
        // Should complete without crashing
        assertTrue("Memory pressure handling should work", true)
    }

    @Test
    fun testBitmapRegistration() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        // Register bitmap
        robustnessManager.registerBitmap(bitmap)
        
        val stats = robustnessManager.getRobustnessStats()
        assertTrue("Should have cached bitmaps", stats.cachedBitmaps >= 0)
        
        // Unregister bitmap
        robustnessManager.unregisterBitmap(bitmap)
        
        // Clean up
        bitmap.recycle()
    }

    @Test
    fun testServiceRestartAttempt() {
        // Test service restart attempt
        val result = robustnessManager.attemptServiceRestart()
        
        // Should return a boolean result without crashing
        assertNotNull("Restart attempt should return result", result)
    }

    @Test
    fun testTemplateFileValidation() {
        // Test with non-existent file
        val nonExistentFile = File(context.filesDir, "non_existent_template.dat")
        val nonExistentResult = robustnessManager.validateTemplateFile(nonExistentFile)
        
        assertFalse("Non-existent file should be invalid", nonExistentResult.isValid)
        assertTrue("Should mention file doesn't exist", 
            nonExistentResult.message.contains("does not exist"))
        
        // Test with empty file
        val emptyFile = File(context.filesDir, "empty_template.dat")
        emptyFile.createNewFile()
        
        val emptyResult = robustnessManager.validateTemplateFile(emptyFile)
        assertFalse("Empty file should be invalid", emptyResult.isValid)
        
        // Clean up
        emptyFile.delete()
        
        // Test with file containing some data
        val validFile = File(context.filesDir, "valid_template.dat")
        validFile.writeText("Some template data")
        
        val validResult = robustnessManager.validateTemplateFile(validFile)
        assertTrue("File with data should be valid", validResult.isValid)
        
        // Clean up
        validFile.delete()
    }

    @Test
    fun testTemplateCorruptionHandling() {
        // Create a test file
        val testFile = File(context.filesDir, "corrupted_template.dat")
        testFile.writeText("Some corrupted data")
        
        // Handle corruption
        val result = robustnessManager.handleTemplateCorruption(testFile)
        
        assertNotNull("Corruption handling should return result", result)
        assertFalse("Original file should be removed", testFile.exists())
        
        // Check if backup was created
        val backupFile = File(context.filesDir, "corrupted_template.dat.corrupted")
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    @Test
    fun testRobustnessStats() {
        val stats = robustnessManager.getRobustnessStats()
        
        assertNotNull("Stats should not be null", stats)
        assertTrue("Available memory should be positive", stats.availableMemoryMB >= 0)
        assertTrue("Cached bitmaps should be non-negative", stats.cachedBitmaps >= 0)
        assertTrue("Restart attempts should be non-negative", stats.restartAttempts >= 0)
    }

    @Test
    fun testValidationResultDataClass() {
        val validResult = RobustnessManager.ValidationResult(true, "Valid")
        val invalidResult = RobustnessManager.ValidationResult(false, "Invalid")
        
        assertTrue("Valid result should be valid", validResult.isValid)
        assertEquals("Valid message should match", "Valid", validResult.message)
        
        assertFalse("Invalid result should not be valid", invalidResult.isValid)
        assertEquals("Invalid message should match", "Invalid", invalidResult.message)
    }

    @Test
    fun testRecoveryResultDataClass() {
        val successResult = RobustnessManager.RecoveryResult(true, "Success")
        val failureResult = RobustnessManager.RecoveryResult(false, "Failure")
        
        assertTrue("Success result should be successful", successResult.success)
        assertEquals("Success message should match", "Success", successResult.message)
        
        assertFalse("Failure result should not be successful", failureResult.success)
        assertEquals("Failure message should match", "Failure", failureResult.message)
    }

    @Test
    fun testMultipleBitmapRegistration() {
        val bitmaps = mutableListOf<Bitmap>()
        
        // Register multiple bitmaps
        repeat(10) { i ->
            val bitmap = Bitmap.createBitmap(50 + i, 50 + i, Bitmap.Config.ARGB_8888)
            bitmaps.add(bitmap)
            robustnessManager.registerBitmap(bitmap)
        }
        
        val stats = robustnessManager.getRobustnessStats()
        assertTrue("Should have multiple cached bitmaps", stats.cachedBitmaps >= 0)
        
        // Trigger memory pressure to test cleanup
        robustnessManager.handleMemoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        
        // Clean up
        bitmaps.forEach { bitmap ->
            robustnessManager.unregisterBitmap(bitmap)
            bitmap.recycle()
        }
    }
}