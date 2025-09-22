package com.templatefinder.manager

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.templatefinder.model.Template
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class TemplateManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var templateManager: TemplateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        templateManager = TemplateManager(context)
        
        // Clean up any existing templates
        templateManager.deleteCurrentTemplate()
    }

    @After
    fun tearDown() {
        // Clean up test templates
        templateManager.deleteCurrentTemplate()
        
        val backups = templateManager.getTemplateBackups()
        backups.forEach { backup ->
            templateManager.deleteNamedTemplate(backup.name)
        }
    }

    @Test
    fun testTemplateReplacement() {
        // Create initial template
        val initialTemplate = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Should save initial template", templateManager.saveCurrentTemplate(initialTemplate))
        
        // Create replacement template
        val replacementTemplate = createTestTemplate(200, 200, 75, 0.9f)
        
        // Replace template
        assertTrue("Should replace template", templateManager.replaceCurrentTemplate(replacementTemplate))
        
        // Verify replacement
        val loadedTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Should load replaced template", loadedTemplate)
        assertEquals("Center X should match", 200, loadedTemplate!!.centerX)
        assertEquals("Center Y should match", 200, loadedTemplate.centerY)
        assertEquals("Radius should match", 75, loadedTemplate.radius)
        assertEquals("Threshold should match", 0.9f, loadedTemplate.matchThreshold, 0.001f)
        
        // Verify backup was created
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have created backup", backups.isNotEmpty())
    }

    @Test
    fun testTemplateUpdate() {
        // Create initial template
        val initialTemplate = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Should save initial template", templateManager.saveCurrentTemplate(initialTemplate))
        
        // Update template properties
        assertTrue("Should update template", 
            templateManager.updateTemplate(matchThreshold = 0.9f, radius = 75))
        
        // Verify update
        val updatedTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Should load updated template", updatedTemplate)
        assertEquals("Threshold should be updated", 0.9f, updatedTemplate!!.matchThreshold, 0.001f)
        assertEquals("Radius should be updated", 75, updatedTemplate.radius)
        assertEquals("Center X should remain unchanged", 100, updatedTemplate.centerX)
        assertEquals("Center Y should remain unchanged", 100, updatedTemplate.centerY)
    }

    @Test
    fun testTemplateValidation() {
        // Test valid template
        val validTemplate = createTestTemplate(100, 100, 50, 0.8f)
        val validResult = templateManager.validateTemplate(validTemplate)
        assertTrue("Valid template should pass validation", validResult.isValid)
        
        // Test invalid template - recycled bitmap
        val invalidTemplate = createTestTemplate(100, 100, 50, 0.8f)
        invalidTemplate.templateBitmap.recycle()
        val invalidResult = templateManager.validateTemplate(invalidTemplate)
        assertFalse("Recycled bitmap should fail validation", invalidResult.isValid)
        assertTrue("Error should mention recycled bitmap", 
            invalidResult.message.contains("recycled"))
    }

    @Test
    fun testTemplateBackupAndRestore() {
        // Create initial template
        val initialTemplate = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Should save initial template", templateManager.saveCurrentTemplate(initialTemplate))
        
        // Create backup
        assertTrue("Should create backup", templateManager.createTemplateBackup())
        
        // Verify backup exists
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have backup", backups.isNotEmpty())
        val backup = backups.first()
        
        // Replace with new template
        val newTemplate = createTestTemplate(200, 200, 75, 0.9f)
        assertTrue("Should save new template", templateManager.saveCurrentTemplate(newTemplate))
        
        // Restore from backup
        assertTrue("Should restore from backup", 
            templateManager.restoreTemplateFromBackup(backup.name))
        
        // Verify restoration
        val restoredTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Should load restored template", restoredTemplate)
        assertEquals("Center X should match original", 100, restoredTemplate!!.centerX)
        assertEquals("Center Y should match original", 100, restoredTemplate.centerY)
        assertEquals("Radius should match original", 50, restoredTemplate.radius)
    }

    @Test
    fun testTemplateExportImport() {
        // Create template
        val originalTemplate = createTestTemplate(150, 150, 60, 0.85f)
        assertTrue("Should save original template", templateManager.saveCurrentTemplate(originalTemplate))
        
        // Export template
        val exportFile = templateManager.exportTemplate()
        assertNotNull("Should export template", exportFile)
        assertTrue("Export file should exist", exportFile!!.exists())
        
        // Clear current template
        assertTrue("Should delete current template", templateManager.deleteCurrentTemplate())
        assertFalse("Should not have current template", templateManager.hasCurrentTemplate())
        
        // Import template
        assertTrue("Should import template", templateManager.importTemplate(exportFile))
        
        // Verify import
        val importedTemplate = templateManager.loadCurrentTemplate()
        assertNotNull("Should load imported template", importedTemplate)
        assertEquals("Center X should match", 150, importedTemplate!!.centerX)
        assertEquals("Center Y should match", 150, importedTemplate.centerY)
        assertEquals("Radius should match", 60, importedTemplate.radius)
        assertEquals("Threshold should match", 0.85f, importedTemplate.matchThreshold, 0.001f)
        
        // Clean up export file
        exportFile.delete()
    }

    @Test
    fun testTemplateStats() {
        // Initially no template
        val initialStats = templateManager.getTemplateStats()
        assertFalse("Should not have current template", initialStats.hasCurrentTemplate)
        assertEquals("Should have no backups", 0, initialStats.backupCount)
        
        // Create template
        val template = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Should save template", templateManager.saveCurrentTemplate(template))
        
        // Create backup
        assertTrue("Should create backup", templateManager.createTemplateBackup())
        
        // Check updated stats
        val updatedStats = templateManager.getTemplateStats()
        assertTrue("Should have current template", updatedStats.hasCurrentTemplate)
        assertTrue("Should have backups", updatedStats.backupCount > 0)
        assertTrue("Current template size should be positive", updatedStats.currentTemplateSize > 0)
    }

    @Test
    fun testBackupCleanup() {
        // Create template
        val template = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Should save template", templateManager.saveCurrentTemplate(template))
        
        // Create multiple backups (more than MAX_BACKUP_FILES)
        repeat(7) {
            assertTrue("Should create backup $it", templateManager.createTemplateBackup())
            Thread.sleep(100) // Ensure different timestamps
        }
        
        // Check that old backups were cleaned up
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have limited number of backups", backups.size <= 5)
    }

    @Test
    fun testBackupInfo() {
        // Create template and backup
        val template = createTestTemplate(100, 100, 50, 0.8f)
        assertTrue("Should save template", templateManager.saveCurrentTemplate(template))
        assertTrue("Should create backup", templateManager.createTemplateBackup())
        
        // Get backup info
        val backups = templateManager.getTemplateBackups()
        assertTrue("Should have backup", backups.isNotEmpty())
        
        val backup = backups.first()
        assertNotNull("Backup name should not be null", backup.name)
        assertNotNull("Backup display name should not be null", backup.displayName)
        assertTrue("Backup created time should be positive", backup.createdAt > 0)
        assertTrue("Backup file size should be positive", backup.fileSize > 0)
        assertTrue("Display name should be formatted", backup.displayName.contains("Backup"))
    }

    @Test
    fun testInvalidTemplateHandling() {
        // Test with invalid threshold
        val invalidTemplate = createTestTemplate(100, 100, 50, 1.5f) // Invalid threshold > 1.0
        val result = templateManager.validateTemplate(invalidTemplate)
        assertFalse("Invalid threshold should fail validation", result.isValid)
        
        // Test replacement with invalid template
        assertFalse("Should not replace with invalid template", 
            templateManager.replaceCurrentTemplate(invalidTemplate))
    }

    /**
     * Helper method to create test template
     */
    private fun createTestTemplate(centerX: Int, centerY: Int, radius: Int, threshold: Float): Template {
        val bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888)
        return Template(
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            templateBitmap = bitmap,
            matchThreshold = threshold
        )
    }
}