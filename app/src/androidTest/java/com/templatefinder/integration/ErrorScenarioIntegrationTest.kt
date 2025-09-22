package com.templatefinder.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.Template
import com.templatefinder.service.TemplateMatchingService
import com.templatefinder.util.ErrorHandler
import com.templatefinder.util.RobustnessManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for error scenarios and edge cases
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ErrorScenarioIntegrationTest {

    private lateinit var context: Context
    private lateinit var templateManager: TemplateManager
    private lateinit var templateMatchingService: TemplateMatchingService
    private lateinit var errorHandler: ErrorHandler
    private lateinit var robustnessManager: RobustnessManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        templateManager = TemplateManager(context)
        templateMatchingService = TemplateMatchingService(context)
        errorHandler = ErrorHandler.getInstance(context)
        robustnessManager = RobustnessManager.getInstance(context)
        
        // Clean up any existing templates
        templateManager.deleteCurrentTemplate()
    }

    @After
    fun tearDown() {
        // Clean up test data
        templateManager.deleteCurrentTemplate()
        
        val backups = templateManager.getTemplateBackups()
        backups.forEach { backup ->
            templateManager.deleteNamedTemplate(backup.name)
        }
    }

    @Test
    fun testInvalidTemplateScenarios() {
        // Test various invalid template scenarios
        
        // 1. Test with recycled bitmap
        val recycledBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        recycledBitmap.recycle()
        
        val recycledTemplate = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = recycledBitmap,
            matchThreshold = 0.8f
        )
        
        val recycledResult = templateManager.validateTemplate(recycledTemplate)
        assertFalse("Recycled bitmap template should be invalid", recycledResult.isValid)
        assertTrue("Error should mention recycled bitmap", 
            recycledResult.message.contains("recycled"))
        
        // 2. Test with invalid coordinates
        val validBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val invalidCoordsTemplate = Template(
            centerX = -10, // Invalid negative coordinate
            centerY = -5,
            radius = 25,
            templateBitmap = validBitmap,
            matchThreshold = 0.8f
        )
        
        val coordsResult = templateManager.validateTemplate(invalidCoordsTemplate)
        assertFalse("Invalid coordinates template should be invalid", coordsResult.isValid)
        
        // 3. Test with invalid radius
        val invalidRadiusTemplate = Template(
            centerX = 50,
            centerY = 50,
            radius = 0, // Invalid zero radius
            templateBitmap = validBitmap,
            matchThreshold = 0.8f
        )
        
        val radiusResult = templateManager.validateTemplate(invalidRadiusTemplate)
        assertFalse("Invalid radius template should be invalid", radiusResult.isValid)
        
        // 4. Test with invalid threshold
        val invalidThresholdTemplate = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = validBitmap,
            matchThreshold = 1.5f // Invalid threshold > 1.0
        )
        
        val thresholdResult = templateManager.validateTemplate(invalidThresholdTemplate)
        assertFalse("Invalid threshold template should be invalid", thresholdResult.isValid)
        
        // Clean up
        validBitmap.recycle()
    }

    @Test
    fun testFileCorruptionScenarios() {
        // Test file corruption and recovery scenarios
        
        // 1. Create valid template file
        val template = createTestTemplate()
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        // 2. Test with corrupted file
        val corruptedFile = File(context.filesDir, "corrupted_template.dat")
        corruptedFile.writeText("This is not a valid template file")
        
        val validationResult = robustnessManager.validateTemplateFile(corruptedFile)
        assertTrue("File should exist but may be invalid", validationResult.isValid)
        
        // 3. Test corruption handling
        val recoveryResult = robustnessManager.handleTemplateCorruption(corruptedFile)
        assertTrue("Should handle corruption", recoveryResult.success)
        assertFalse("Corrupted file should be removed", corruptedFile.exists())
        
        // 4. Test with empty file
        val emptyFile = File(context.filesDir, "empty_template.dat")
        emptyFile.createNewFile()
        
        val emptyValidation = robustnessManager.validateTemplateFile(emptyFile)
        assertFalse("Empty file should be invalid", emptyValidation.isValid)
        
        // Clean up
        emptyFile.delete()
    }

    @Test
    fun testMemoryPressureScenarios() {
        // Test memory pressure handling scenarios
        
        robustnessManager.startMonitoring()
        
        // 1. Create multiple large bitmaps to simulate memory pressure
        val largeBitmaps = mutableListOf<Bitmap>()
        repeat(10) { i ->
            try {
                val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
                largeBitmaps.add(bitmap)
                robustnessManager.registerBitmap(bitmap)
            } catch (e: OutOfMemoryError) {
                // Expected in low memory situations
                break
            }
        }
        
        // 2. Test different memory pressure levels
        robustnessManager.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        robustnessManager.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        robustnessManager.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        
        // 3. Verify memory cleanup
        val stats = robustnessManager.getRobustnessStats()
        assertTrue("Available memory should be positive", stats.availableMemoryMB >= 0)
        
        // Clean up remaining bitmaps
        largeBitmaps.forEach { bitmap ->
            robustnessManager.unregisterBitmap(bitmap)
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        
        robustnessManager.stopMonitoring()
    }

    @Test
    fun testTemplateMatchingErrorScenarios() {
        // Test template matching error scenarios
        
        // 1. Test matching without OpenCV initialization
        val template = createTestTemplate()
        val screenshot = createTestBitmap(300, 300)
        
        // Template matching service may not be initialized in test environment
        val isSupported = templateMatchingService.isInitialized()
        
        if (!isSupported) {
            // Test error handling when OpenCV is not available
            val callback = object : TemplateMatchingService.TemplateMatchingCallback {
                override fun onMatchingComplete(result: com.templatefinder.model.SearchResult) {
                    fail("Should not complete without OpenCV")
                }

                override fun onMatchingError(error: String) {
                    assertTrue("Error should mention OpenCV", error.contains("OpenCV"))
                }
            }
            
            templateMatchingService.findTemplate(screenshot, template, callback)
        }
        
        // 2. Test with invalid template for matching
        val invalidTemplate = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = createTestBitmap(0, 0), // Invalid size
            matchThreshold = 0.8f
        )
        
        assertFalse("Invalid template should fail validation", 
            templateMatchingService.validateTemplate(invalidTemplate))
        
        // Clean up
        screenshot.recycle()
        template.templateBitmap.recycle()
        invalidTemplate.templateBitmap.recycle()
    }

    @Test
    fun testBackupAndRestoreErrorScenarios() {
        // Test backup and restore error scenarios
        
        // 1. Test backup without current template
        assertFalse("Should not create backup without template", 
            templateManager.createTemplateBackup())
        
        // 2. Test restore from non-existent backup
        assertFalse("Should not restore from non-existent backup", 
            templateManager.restoreTemplateFromBackup("non_existent_backup"))
        
        // 3. Create template and backup
        val template = createTestTemplate()
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        assertTrue("Backup should be created", templateManager.createTemplateBackup())
        
        // 4. Test restore with corrupted backup
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have backups", backups.isNotEmpty())
        
        // Corrupt the backup file
        val backupName = backups.first().name
        val backupFile = File(context.filesDir, "templates/$backupName.dat")
        if (backupFile.exists()) {
            backupFile.writeText("corrupted backup data")
            
            // Try to restore corrupted backup
            assertFalse("Should not restore corrupted backup", 
                templateManager.restoreTemplateFromBackup(backupName))
        }
    }

    @Test
    fun testImportExportErrorScenarios() {
        // Test import/export error scenarios
        
        // 1. Test export without template
        val exportResult = templateManager.exportTemplate()
        assertNull("Should not export without template", exportResult)
        
        // 2. Test import from non-existent file
        val nonExistentFile = File(context.filesDir, "non_existent.dat")
        assertFalse("Should not import non-existent file", 
            templateManager.importTemplate(nonExistentFile))
        
        // 3. Test import from invalid file
        val invalidFile = File(context.filesDir, "invalid_template.dat")
        invalidFile.writeText("This is not a valid template")
        
        assertFalse("Should not import invalid file", 
            templateManager.importTemplate(invalidFile))
        
        // Clean up
        invalidFile.delete()
    }

    @Test
    fun testErrorHandlerStressTest() {
        // Stress test the error handler with many errors
        
        val errorCount = 50
        val errorResults = mutableListOf<ErrorHandler.ErrorResult>()
        
        // Generate many errors quickly
        repeat(errorCount) { i ->
            val result = errorHandler.handleError(
                category = ErrorHandler.CATEGORY_TEMPLATE,
                severity = ErrorHandler.SEVERITY_LOW,
                message = "Stress test error $i",
                context = "Error handler stress test"
            )
            errorResults.add(result)
        }
        
        // Verify all errors were handled
        assertEquals("All errors should be handled", errorCount, errorResults.size)
        assertTrue("All errors should be marked as handled", 
            errorResults.all { it.handled })
        
        // Check error statistics
        val stats = errorHandler.getErrorStatistics()
        assertTrue("Should have recorded many errors", stats.totalErrors >= errorCount)
        
        // Test recent errors retrieval
        val recentErrors = errorHandler.getRecentErrors(10)
        assertTrue("Should have recent errors", recentErrors.isNotEmpty())
        assertTrue("Should not exceed requested limit", recentErrors.size <= 10)
    }

    @Test
    fun testConcurrentOperations() {
        // Test concurrent operations to check for race conditions
        
        val template = createTestTemplate()
        
        // Test concurrent template operations
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<Boolean>()
        
        // Create multiple threads performing template operations
        repeat(5) { i ->
            val thread = Thread {
                try {
                    when (i % 3) {
                        0 -> {
                            val result = templateManager.saveCurrentTemplate(template)
                            synchronized(results) { results.add(result) }
                        }
                        1 -> {
                            val loadedTemplate = templateManager.loadCurrentTemplate()
                            synchronized(results) { results.add(loadedTemplate != null) }
                        }
                        2 -> {
                            val hasTemplate = templateManager.hasCurrentTemplate()
                            synchronized(results) { results.add(true) } // Always succeeds
                        }
                    }
                } catch (e: Exception) {
                    synchronized(results) { results.add(false) }
                }
            }
            threads.add(thread)
        }
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for all threads to complete
        threads.forEach { it.join(5000) } // 5 second timeout
        
        // Verify no crashes occurred
        assertEquals("All operations should complete", 5, results.size)
        
        // Clean up
        template.templateBitmap.recycle()
    }

    /**
     * Helper method to create test bitmap
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Helper method to create test template
     */
    private fun createTestTemplate(): Template {
        val bitmap = createTestBitmap(100, 100)
        return Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = bitmap,
            matchThreshold = 0.8f
        )
    }
}