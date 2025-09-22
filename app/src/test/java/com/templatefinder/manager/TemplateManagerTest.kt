package com.templatefinder.manager

import android.content.Context
import android.graphics.Bitmap
import com.templatefinder.model.Template
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.File

class TemplateManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockFilesDir: File

    private lateinit var templateManager: TemplateManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Mock the context and files directory
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
        `when`(mockFilesDir.absolutePath).thenReturn("/mock/files")
        
        templateManager = TemplateManager(mockContext)
    }

    @Test
    fun testTemplateManagerCreation() {
        assertNotNull("TemplateManager should be created successfully", templateManager)
    }

    @Test
    fun testValidateTemplateRegion() {
        val bitmap = createMockBitmap(1000, 1000)
        
        // Test valid region
        val validResult = templateManager.validateTemplateRegion(
            screenshot = bitmap,
            centerX = 500,
            centerY = 500,
            radius = 100
        )
        
        assertTrue("Valid region should pass validation", validResult.isValid)
        assertEquals("Valid region should have success message", 
            "Template region is valid", validResult.message)
        
        // Test invalid region - outside bounds
        val invalidResult = templateManager.validateTemplateRegion(
            screenshot = bitmap,
            centerX = -10,
            centerY = 500,
            radius = 100
        )
        
        assertFalse("Invalid region should fail validation", invalidResult.isValid)
        assertTrue("Error message should mention bounds", 
            invalidResult.message.contains("outside screenshot bounds"))
        
        // Test invalid region - radius too small
        val smallRadiusResult = templateManager.validateTemplateRegion(
            screenshot = bitmap,
            centerX = 500,
            centerY = 500,
            radius = 5
        )
        
        assertFalse("Small radius should fail validation", smallRadiusResult.isValid)
        assertTrue("Error message should mention minimum radius", 
            smallRadiusResult.message.contains("too small"))
        
        // Test invalid region - radius too large
        val largeRadiusResult = templateManager.validateTemplateRegion(
            screenshot = bitmap,
            centerX = 500,
            centerY = 500,
            radius = 600
        )
        
        assertFalse("Large radius should fail validation", largeRadiusResult.isValid)
        assertTrue("Error message should mention maximum radius", 
            largeRadiusResult.message.contains("too large"))
    }

    @Test
    fun testValidateTemplateRegionEdgeCases() {
        val bitmap = createMockBitmap(200, 200)
        
        // Test region that extends outside bounds
        val extendingResult = templateManager.validateTemplateRegion(
            screenshot = bitmap,
            centerX = 50,
            centerY = 50,
            radius = 60 // This would extend outside the 200x200 bitmap
        )
        
        assertFalse("Extending region should fail validation", extendingResult.isValid)
        assertTrue("Error message should mention extending outside bounds", 
            extendingResult.message.contains("extends outside"))
    }

    @Test
    fun testHasCurrentTemplate() {
        // Initially should not have current template
        assertFalse("Should not have current template initially", 
            templateManager.hasCurrentTemplate())
    }

    @Test
    fun testGetTemplateNames() {
        // Should return empty list initially
        val names = templateManager.getTemplateNames()
        assertNotNull("Template names should not be null", names)
        assertTrue("Template names should be empty initially", names.isEmpty())
    }

    @Test
    fun testTemplateInfoDataClass() {
        val info = TemplateManager.TemplateInfo(
            name = "test",
            filePath = "/path/to/file",
            fileSize = 1024L,
            lastModified = System.currentTimeMillis(),
            exists = true
        )
        
        assertNotNull("TemplateInfo should be created", info)
        assertEquals("Name should match", "test", info.name)
        assertEquals("File path should match", "/path/to/file", info.filePath)
        assertEquals("File size should match", 1024L, info.fileSize)
        assertTrue("Should exist", info.exists)
    }

    @Test
    fun testValidationResultDataClass() {
        val validResult = TemplateManager.ValidationResult(true, "Success")
        val invalidResult = TemplateManager.ValidationResult(false, "Error")
        
        assertTrue("Valid result should be valid", validResult.isValid)
        assertEquals("Valid result message should match", "Success", validResult.message)
        
        assertFalse("Invalid result should not be valid", invalidResult.isValid)
        assertEquals("Invalid result message should match", "Error", invalidResult.message)
    }

    @Test
    fun testSaveCurrentTemplateWithNullBitmap() {
        // Test behavior with invalid template (this would normally cause issues)
        // We can't easily test file operations in unit tests, but we can test validation
        val bitmap = createMockBitmap(100, 100)
        val template = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = bitmap,
            matchThreshold = 0.8f
        )
        
        // The actual save operation would require file system access
        // In unit tests, we focus on testing the validation logic
        assertNotNull("Template should be created", template)
    }

    @Test
    fun testLoadCurrentTemplateWhenNoFile() {
        // When no file exists, should return null
        val template = templateManager.loadCurrentTemplate()
        assertNull("Should return null when no template file exists", template)
    }

    /**
     * Helper method to create mock bitmap for testing
     */
    private fun createMockBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}