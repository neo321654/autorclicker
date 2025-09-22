package com.templatefinder.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.Template
import com.templatefinder.service.TemplateMatchingService
import com.templatefinder.util.Logger
import com.templatefinder.util.RobustnessManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance validation tests for the Template Coordinate Finder system
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PerformanceValidationTest {

    private lateinit var context: Context
    private lateinit var templateManager: TemplateManager
    private lateinit var templateMatchingService: TemplateMatchingService
    private lateinit var logger: Logger
    private lateinit var robustnessManager: RobustnessManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        templateManager = TemplateManager(context)
        templateMatchingService = TemplateMatchingService(context)
        logger = Logger.getInstance(context)
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
    fun testTemplateSaveLoadPerformance() {
        // Test template save/load performance
        
        val templateSizes = listOf(
            Pair(50, 50),    // Small
            Pair(100, 100),  // Medium
            Pair(200, 200),  // Large
            Pair(400, 400)   // Very Large
        )
        
        templateSizes.forEach { (width, height) ->
            val template = createTestTemplate(width, height)
            
            // Measure save time
            val saveTime = measureTimeMillis {
                assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
            }
            
            // Measure load time
            val loadTime = measureTimeMillis {
                val loadedTemplate = templateManager.loadCurrentTemplate()
                assertNotNull("Template should be loaded", loadedTemplate)
            }
            
            logger.info("PerformanceTest", 
                "Template ${width}x${height}: Save=${saveTime}ms, Load=${loadTime}ms")
            
            // Performance assertions (reasonable limits)
            assertTrue("Save time should be reasonable for ${width}x${height}", saveTime < 5000)
            assertTrue("Load time should be reasonable for ${width}x${height}", loadTime < 3000)
            
            // Clean up
            template.templateBitmap.recycle()
            templateManager.deleteCurrentTemplate()
        }
    }

    @Test
    fun testTemplateValidationPerformance() {
        // Test template validation performance
        
        val templates = mutableListOf<Template>()
        
        // Create various templates
        repeat(20) { i ->
            val size = 50 + (i * 10)
            templates.add(createTestTemplate(size, size))
        }
        
        // Measure validation time for all templates
        val totalValidationTime = measureTimeMillis {
            templates.forEach { template ->
                val result = templateManager.validateTemplate(template)
                assertTrue("Template should be valid", result.isValid)
            }
        }
        
        val averageValidationTime = totalValidationTime / templates.size.toDouble()
        
        logger.info("PerformanceTest", 
            "Average validation time: ${averageValidationTime}ms per template")
        
        // Performance assertion
        assertTrue("Average validation time should be reasonable", averageValidationTime < 100)
        
        // Clean up
        templates.forEach { it.templateBitmap.recycle() }
    }

    @Test
    fun testBackupCreationPerformance() {
        // Test backup creation performance
        
        val template = createTestTemplate(200, 200)
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        val backupTimes = mutableListOf<Long>()
        
        // Create multiple backups and measure time
        repeat(5) {
            val backupTime = measureTimeMillis {
                assertTrue("Backup should be created", templateManager.createTemplateBackup())
            }
            backupTimes.add(backupTime)
            
            // Small delay to ensure different timestamps
            Thread.sleep(100)
        }
        
        val averageBackupTime = backupTimes.average()
        val maxBackupTime = backupTimes.maxOrNull() ?: 0L
        
        logger.info("PerformanceTest", 
            "Backup creation - Average: ${averageBackupTime}ms, Max: ${maxBackupTime}ms")
        
        // Performance assertions
        assertTrue("Average backup time should be reasonable", averageBackupTime < 2000)
        assertTrue("Max backup time should be reasonable", maxBackupTime < 5000)
        
        // Verify backups were created
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have created backups", backups.size >= 5)
        
        // Clean up
        template.templateBitmap.recycle()
    }

    @Test
    fun testMemoryUsageUnderLoad() {
        // Test memory usage under load
        
        robustnessManager.startMonitoring()
        
        val initialStats = robustnessManager.getRobustnessStats()
        val initialMemory = initialStats.availableMemoryMB
        
        val bitmaps = mutableListOf<Bitmap>()
        
        try {
            // Create many bitmaps to test memory management
            repeat(20) { i ->
                val bitmap = createTestBitmap(200, 200)
                bitmaps.add(bitmap)
                robustnessManager.registerBitmap(bitmap)
                
                // Check memory every 5 bitmaps
                if (i % 5 == 0) {
                    val currentStats = robustnessManager.getRobustnessStats()
                    logger.info("PerformanceTest", 
                        "Memory after ${i + 1} bitmaps: ${currentStats.availableMemoryMB}MB")
                    
                    // Trigger cleanup if memory is getting low
                    if (currentStats.isLowMemory) {
                        robustnessManager.handleMemoryPressure(
                            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                    }
                }
            }
            
        } catch (e: OutOfMemoryError) {
            logger.warning("PerformanceTest", "Out of memory with ${bitmaps.size} bitmaps")
        }
        
        val finalStats = robustnessManager.getRobustnessStats()
        
        logger.info("PerformanceTest", 
            "Memory usage - Initial: ${initialMemory}MB, Final: ${finalStats.availableMemoryMB}MB")
        
        // Clean up
        bitmaps.forEach { bitmap ->
            robustnessManager.unregisterBitmap(bitmap)
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        
        robustnessManager.stopMonitoring()
        
        // Memory should not have decreased dramatically
        assertTrue("Memory should not decrease too much", 
            finalStats.availableMemoryMB > initialMemory * 0.5) // Allow 50% decrease
    }

    @Test
    fun testConcurrentOperationPerformance() {
        // Test performance under concurrent operations
        
        val template = createTestTemplate(150, 150)
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        val operationTimes = mutableListOf<Long>()
        val threads = mutableListOf<Thread>()
        
        // Create multiple threads performing operations
        repeat(10) { i ->
            val thread = Thread {
                val operationTime = measureTimeMillis {
                    when (i % 4) {
                        0 -> templateManager.loadCurrentTemplate()
                        1 -> templateManager.hasCurrentTemplate()
                        2 -> templateManager.getTemplateStats()
                        3 -> templateManager.validateTemplate(template)
                    }
                }
                
                synchronized(operationTimes) {
                    operationTimes.add(operationTime)
                }
            }
            threads.add(thread)
        }
        
        // Measure total time for all concurrent operations
        val totalTime = measureTimeMillis {
            threads.forEach { it.start() }
            threads.forEach { it.join(5000) } // 5 second timeout
        }
        
        val averageOperationTime = operationTimes.average()
        
        logger.info("PerformanceTest", 
            "Concurrent operations - Total: ${totalTime}ms, Average per operation: ${averageOperationTime}ms")
        
        // Performance assertions
        assertTrue("Total concurrent time should be reasonable", totalTime < 10000)
        assertTrue("Average operation time should be reasonable", averageOperationTime < 1000)
        assertEquals("All operations should complete", 10, operationTimes.size)
        
        // Clean up
        template.templateBitmap.recycle()
    }

    @Test
    fun testLargeDatasetPerformance() {
        // Test performance with large datasets
        
        val largeTemplates = mutableListOf<Template>()
        
        // Create many templates
        val creationTime = measureTimeMillis {
            repeat(50) { i ->
                val template = createTestTemplate(100, 100)
                largeTemplates.add(template)
            }
        }
        
        // Test batch validation
        val validationTime = measureTimeMillis {
            largeTemplates.forEach { template ->
                templateManager.validateTemplate(template)
            }
        }
        
        // Test batch save operations (save as named templates)
        val saveTime = measureTimeMillis {
            largeTemplates.forEachIndexed { index, template ->
                templateManager.saveNamedTemplate(template, "test_template_$index")
            }
        }
        
        // Test batch load operations
        val loadTime = measureTimeMillis {
            repeat(50) { i ->
                templateManager.loadNamedTemplate("test_template_$i")
            }
        }
        
        logger.info("PerformanceTest", 
            "Large dataset (50 templates) - Creation: ${creationTime}ms, " +
            "Validation: ${validationTime}ms, Save: ${saveTime}ms, Load: ${loadTime}ms")
        
        // Performance assertions
        assertTrue("Creation time should be reasonable", creationTime < 5000)
        assertTrue("Validation time should be reasonable", validationTime < 10000)
        assertTrue("Save time should be reasonable", saveTime < 15000)
        assertTrue("Load time should be reasonable", loadTime < 10000)
        
        // Clean up
        largeTemplates.forEach { it.templateBitmap.recycle() }
        repeat(50) { i ->
            templateManager.deleteNamedTemplate("test_template_$i")
        }
    }

    @Test
    fun testSystemResourceUsage() {
        // Test system resource usage during operations
        
        val initialStats = robustnessManager.getRobustnessStats()
        
        // Perform resource-intensive operations
        val template = createTestTemplate(300, 300)
        
        // Multiple save/load cycles
        repeat(10) {
            templateManager.saveCurrentTemplate(template)
            templateManager.loadCurrentTemplate()
            templateManager.createTemplateBackup()
        }
        
        val finalStats = robustnessManager.getRobustnessStats()
        
        logger.info("PerformanceTest", 
            "Resource usage - Initial memory: ${initialStats.availableMemoryMB}MB, " +
            "Final memory: ${finalStats.availableMemoryMB}MB")
        
        // Verify system stability
        assertTrue("System should remain stable", finalStats.availableMemoryMB > 0)
        assertFalse("Should not be in low memory state", finalStats.isLowMemory)
        
        // Clean up
        template.templateBitmap.recycle()
    }

    /**
     * Helper method to create test bitmap
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            // Fill with pattern for more realistic data
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint()
            
            // Create a simple pattern
            for (x in 0 until width step 10) {
                for (y in 0 until height step 10) {
                    paint.color = if ((x + y) % 20 == 0) 
                        android.graphics.Color.RED else android.graphics.Color.BLUE
                    canvas.drawRect(x.toFloat(), y.toFloat(), 
                        (x + 5).toFloat(), (y + 5).toFloat(), paint)
                }
            }
        }
    }

    /**
     * Helper method to create test template
     */
    private fun createTestTemplate(width: Int, height: Int): Template {
        val bitmap = createTestBitmap(width, height)
        return Template(
            centerX = width / 2,
            centerY = height / 2,
            radius = minOf(width, height) / 4,
            templateBitmap = bitmap,
            matchThreshold = 0.8f
        )
    }
}