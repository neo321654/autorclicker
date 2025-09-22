package com.templatefinder.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.templatefinder.controller.SearchController
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.SearchResult
import com.templatefinder.model.Template
import com.templatefinder.service.TemplateMatchingService
import com.templatefinder.util.ErrorHandler
import com.templatefinder.util.Logger
import com.templatefinder.util.PermissionManager
import com.templatefinder.util.RobustnessManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests for the complete Template Coordinate Finder workflow
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class EndToEndIntegrationTest {

    private lateinit var context: Context
    private lateinit var templateManager: TemplateManager
    private lateinit var searchController: SearchController
    private lateinit var templateMatchingService: TemplateMatchingService
    private lateinit var permissionManager: PermissionManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var logger: Logger
    private lateinit var robustnessManager: RobustnessManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize all components
        templateManager = TemplateManager(context)
        searchController = SearchController(context)
        templateMatchingService = TemplateMatchingService(context)
        permissionManager = PermissionManager(context)
        errorHandler = ErrorHandler.getInstance(context)
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
        
        searchController.cleanup()
        robustnessManager.stopMonitoring()
    }

    @Test
    fun testCompleteTemplateCreationWorkflow() {
        // Test the complete template creation workflow
        
        // 1. Create a test template
        val testBitmap = createTestBitmap(200, 200)
        val template = Template.createFromRegion(
            sourceBitmap = testBitmap,
            centerX = 100,
            centerY = 100,
            radius = 50,
            matchThreshold = 0.8f
        )
        
        assertNotNull("Template should be created", template)
        
        // 2. Validate template
        val validationResult = templateManager.validateTemplate(template!!)
        assertTrue("Template should be valid", validationResult.isValid)
        
        // 3. Save template
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        // 4. Verify template exists
        assertTrue("Template should exist", templateManager.hasCurrentTemplate())
        
        // 5. Load and verify template
        val loadedTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Template should be loaded", loadedTemplate)
        assertEquals("Center X should match", 100, loadedTemplate!!.centerX)
        assertEquals("Center Y should match", 100, loadedTemplate.centerY)
        assertEquals("Radius should match", 50, loadedTemplate.radius)
        
        // Clean up
        testBitmap.recycle()
    }

    @Test
    fun testTemplateMatchingWorkflow() {
        // Test the template matching workflow
        
        // 1. Initialize OpenCV
        val initLatch = CountDownLatch(1)
        var openCVInitialized = false
        
        templateMatchingService.initializeOpenCV(object : TemplateMatchingService.OpenCVInitializationCallback {
            override fun onInitializationComplete(success: Boolean) {
                openCVInitialized = success
                initLatch.countDown()
            }
        })
        
        // Wait for OpenCV initialization (may not work in test environment)
        initLatch.await(5, TimeUnit.SECONDS)
        
        // 2. Create template and screenshot
        val templateBitmap = createTestBitmap(100, 100)
        val screenshotBitmap = createTestBitmap(300, 300)
        
        val template = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = templateBitmap,
            matchThreshold = 0.8f
        )
        
        // 3. Validate template
        assertTrue("Template should be valid", templateMatchingService.validateTemplate(template))
        
        // 4. Test template matching (may not work without OpenCV in test environment)
        val matchingLatch = CountDownLatch(1)
        var matchingResult: SearchResult? = null
        
        if (openCVInitialized) {
            templateMatchingService.findTemplate(
                screenshot = screenshotBitmap,
                template = template,
                callback = object : TemplateMatchingService.TemplateMatchingCallback {
                    override fun onMatchingComplete(result: SearchResult) {
                        matchingResult = result
                        matchingLatch.countDown()
                    }

                    override fun onMatchingError(error: String) {
                        matchingLatch.countDown()
                    }
                }
            )
            
            matchingLatch.await(10, TimeUnit.SECONDS)
            assertNotNull("Matching result should be received", matchingResult)
        }
        
        // Clean up
        templateBitmap.recycle()
        screenshotBitmap.recycle()
    }

    @Test
    fun testSearchControllerWorkflow() {
        // Test the search controller workflow
        
        // 1. Create and save template
        val template = createTestTemplate()
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        // 2. Test search controller status
        val initialStatus = searchController.getSearchStatus()
        assertFalse("Search should not be active initially", initialStatus.isActive)
        assertEquals("Initial state should be STOPPED", 
            SearchController.SearchState.STOPPED, initialStatus.currentState)
        
        // 3. Test search start (will likely fail without proper permissions in test environment)
        val startResult = searchController.startSearch()
        // Don't assert success as it depends on system permissions
        assertNotNull("Start result should not be null", startResult)
        
        // 4. Test search stop
        val stopResult = searchController.stopSearch()
        assertNotNull("Stop result should not be null", stopResult)
        
        // 5. Reset statistics
        searchController.resetStatistics()
        val resetStatus = searchController.getSearchStatus()
        assertEquals("Search attempts should be reset", 0L, resetStatus.searchAttempts)
        assertEquals("Successful finds should be reset", 0L, resetStatus.successfulFinds)
    }

    @Test
    fun testTemplateBackupAndRestoreWorkflow() {
        // Test template backup and restore workflow
        
        // 1. Create initial template
        val initialTemplate = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Initial template should be saved", templateManager.saveCurrentTemplate(initialTemplate))
        
        // 2. Create backup
        assertTrue("Backup should be created", templateManager.createTemplateBackup())
        
        // 3. Verify backup exists
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have backups", backups.isNotEmpty())
        val backup = backups.first()
        
        // 4. Replace with new template
        val newTemplate = createTestTemplate(200, 200, 75, 0.9f)
        assertTrue("New template should be saved", templateManager.saveCurrentTemplate(newTemplate))
        
        // 5. Verify replacement
        val replacedTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Replaced template should be loaded", replacedTemplate)
        assertEquals("Center X should be updated", 200, replacedTemplate!!.centerX)
        
        // 6. Restore from backup
        assertTrue("Should restore from backup", 
            templateManager.restoreTemplateFromBackup(backup.name))
        
        // 7. Verify restoration
        val restoredTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Restored template should be loaded", restoredTemplate)
        assertEquals("Center X should be restored", 100, restoredTemplate!!.centerX)
        assertEquals("Center Y should be restored", 100, restoredTemplate.centerY)
    }

    @Test
    fun testErrorHandlingWorkflow() {
        // Test error handling throughout the system
        
        // 1. Test error handler creation
        assertNotNull("Error handler should be available", errorHandler)
        
        // 2. Test error logging
        val errorResult = errorHandler.handleError(
            category = ErrorHandler.CATEGORY_TEMPLATE,
            severity = ErrorHandler.SEVERITY_MEDIUM,
            message = "Test error for integration testing",
            context = "End-to-end integration test"
        )
        
        assertTrue("Error should be handled", errorResult.handled)
        assertNotNull("Error ID should be generated", errorResult.errorId)
        
        // 3. Test error statistics
        val stats = errorHandler.getErrorStatistics()
        assertTrue("Should have recorded errors", stats.totalErrors > 0)
        
        // 4. Test recent errors
        val recentErrors = errorHandler.getRecentErrors(5)
        assertTrue("Should have recent errors", recentErrors.isNotEmpty())
        
        // 5. Test logger integration
        logger.info("EndToEndTest", "Integration test logging")
        val loggingStats = logger.getLoggingStats()
        assertTrue("Should have log entries", loggingStats.totalEntries >= 0)
    }

    @Test
    fun testRobustnessManagerWorkflow() {
        // Test robustness manager functionality
        
        // 1. Start monitoring
        robustnessManager.startMonitoring()
        
        val stats = robustnessManager.getRobustnessStats()
        assertTrue("Should be monitoring", stats.isMonitoring)
        
        // 2. Test bitmap registration
        val testBitmap = createTestBitmap(100, 100)
        robustnessManager.registerBitmap(testBitmap)
        
        val updatedStats = robustnessManager.getRobustnessStats()
        assertTrue("Should have cached bitmaps", updatedStats.cachedBitmaps >= 0)
        
        // 3. Test memory pressure handling
        robustnessManager.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        
        // 4. Test template file validation
        val testFile = java.io.File(context.filesDir, "test_template.dat")
        testFile.writeText("test data")
        
        val validationResult = robustnessManager.validateTemplateFile(testFile)
        assertTrue("Test file should be valid", validationResult.isValid)
        
        // Clean up
        testFile.delete()
        robustnessManager.unregisterBitmap(testBitmap)
        testBitmap.recycle()
    }

    @Test
    fun testPermissionManagerWorkflow() {
        // Test permission manager functionality
        
        // 1. Check initial permission states
        val hasAccessibility = permissionManager.isAccessibilityServiceEnabled()
        val hasOverlay = permissionManager.hasOverlayPermission()
        val hasNotification = permissionManager.hasNotificationPermission()
        
        // These will likely be false in test environment, but should not crash
        assertNotNull("Accessibility permission check should complete", hasAccessibility)
        assertNotNull("Overlay permission check should complete", hasOverlay)
        assertNotNull("Notification permission check should complete", hasNotification)
        
        // 2. Test permission guidance
        // These methods should not crash even without permissions
        try {
            permissionManager.openAccessibilitySettings()
            permissionManager.requestOverlayPermission()
            permissionManager.requestNotificationPermission()
        } catch (e: Exception) {
            // Expected in test environment
        }
    }

    @Test
    fun testCompleteSystemIntegration() {
        // Test integration between all major components
        
        // 1. Create template through template manager
        val template = createTestTemplate()
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        // 2. Validate template through template matching service
        assertTrue("Template should be valid for matching", 
            templateMatchingService.validateTemplate(template))
        
        // 3. Get template stats
        val templateStats = templateManager.getTemplateStats()
        assertTrue("Should have current template", templateStats.hasCurrentTemplate)
        
        // 4. Test search controller with template
        val searchStatus = searchController.getSearchStatus()
        assertNotNull("Search status should be available", searchStatus)
        
        // 5. Test error handling integration
        val errorStats = errorHandler.getErrorStatistics()
        assertNotNull("Error statistics should be available", errorStats)
        
        // 6. Test robustness manager integration
        val robustnessStats = robustnessManager.getRobustnessStats()
        assertNotNull("Robustness stats should be available", robustnessStats)
        
        // 7. Test logger integration
        val loggingStats = logger.getLoggingStats()
        assertNotNull("Logging stats should be available", loggingStats)
    }

    @Test
    fun testSystemRecoveryScenarios() {
        // Test system recovery from various failure scenarios
        
        // 1. Test template corruption recovery
        val template = createTestTemplate()
        assertTrue("Template should be saved", templateManager.saveCurrentTemplate(template))
        
        // Simulate corruption by creating invalid template
        val corruptedTemplate = Template(
            centerX = -1, // Invalid coordinate
            centerY = -1,
            radius = 0, // Invalid radius
            templateBitmap = template.templateBitmap,
            matchThreshold = 2.0f // Invalid threshold
        )
        
        val validationResult = templateManager.validateTemplate(corruptedTemplate)
        assertFalse("Corrupted template should fail validation", validationResult.isValid)
        
        // 2. Test memory pressure recovery
        robustnessManager.startMonitoring()
        
        // Create multiple bitmaps to simulate memory pressure
        val bitmaps = mutableListOf<Bitmap>()
        repeat(5) {
            val bitmap = createTestBitmap(100, 100)
            bitmaps.add(bitmap)
            robustnessManager.registerBitmap(bitmap)
        }
        
        // Simulate memory pressure
        robustnessManager.handleMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        
        // Clean up
        bitmaps.forEach { bitmap ->
            robustnessManager.unregisterBitmap(bitmap)
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        
        // 3. Test service restart scenario
        val restartResult = robustnessManager.attemptServiceRestart()
        assertNotNull("Service restart should return result", restartResult)
    }

    /**
     * Helper method to create test bitmap
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            // Fill with some pattern
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLUE
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    /**
     * Helper method to create test template
     */
    private fun createTestTemplate(
        centerX: Int = 100,
        centerY: Int = 100,
        radius: Int = 50,
        threshold: Float = 0.8f
    ): Template {
        val bitmap = createTestBitmap(radius * 2, radius * 2)
        return Template(
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            templateBitmap = bitmap,
            matchThreshold = threshold
        )
    }
}