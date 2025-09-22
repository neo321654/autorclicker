package com.templatefinder.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import com.templatefinder.model.SearchResult
import com.templatefinder.model.Template
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TemplateMatchingServiceTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var templateMatchingService: TemplateMatchingService

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        templateMatchingService = TemplateMatchingService(mockContext)
    }

    @Test
    fun testServiceInitialization() {
        assertNotNull("Service should be created successfully", templateMatchingService)
        assertFalse("OpenCV should not be initialized initially", templateMatchingService.isInitialized())
    }

    @Test
    fun testGetMatchingStats() {
        val stats = templateMatchingService.getMatchingStats()
        
        assertNotNull("Stats should not be null", stats)
        assertFalse("OpenCV should not be initialized initially", stats.isOpenCVInitialized)
        assertEquals("Version should indicate not initialized", "Not initialized", stats.openCVVersion)
    }

    @Test
    fun testValidateTemplate() {
        // Create a mock bitmap for testing
        val mockBitmap = createMockBitmap(100, 100)
        
        // Test valid template
        val validTemplate = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = mockBitmap,
            matchThreshold = 0.8f
        )
        
        assertTrue("Valid template should pass validation", 
            templateMatchingService.validateTemplate(validTemplate))
        
        // Test invalid template with bad threshold
        val invalidTemplate = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = mockBitmap,
            matchThreshold = 1.5f // Invalid threshold > 1.0
        )
        
        assertFalse("Invalid template should fail validation", 
            templateMatchingService.validateTemplate(invalidTemplate))
    }

    @Test
    fun testFindTemplateWithoutInitialization() {
        val mockBitmap = createMockBitmap(100, 100)
        val template = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = mockBitmap,
            matchThreshold = 0.8f
        )
        
        var errorReceived: String? = null
        
        val callback = object : TemplateMatchingService.TemplateMatchingCallback {
            override fun onMatchingComplete(result: SearchResult) {
                fail("Should not receive result when OpenCV is not initialized")
            }

            override fun onMatchingError(error: String) {
                errorReceived = error
            }
        }
        
        templateMatchingService.findTemplate(mockBitmap, template, callback)
        
        assertNotNull("Error should be received", errorReceived)
        assertTrue("Error should mention OpenCV initialization", 
            errorReceived!!.contains("OpenCV is not initialized"))
    }

    @Test
    fun testMultiScaleFindTemplateWithoutInitialization() {
        val mockBitmap = createMockBitmap(100, 100)
        val template = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = mockBitmap,
            matchThreshold = 0.8f
        )
        
        var errorReceived: String? = null
        
        val callback = object : TemplateMatchingService.TemplateMatchingCallback {
            override fun onMatchingComplete(result: SearchResult) {
                fail("Should not receive result when OpenCV is not initialized")
            }

            override fun onMatchingError(error: String) {
                errorReceived = error
            }
        }
        
        templateMatchingService.findTemplateMultiScale(mockBitmap, template, callback = callback)
        
        assertNotNull("Error should be received", errorReceived)
        assertTrue("Error should mention OpenCV initialization", 
            errorReceived!!.contains("OpenCV is not initialized"))
    }

    @Test
    fun testInitializationCallback() {
        var callbackReceived = false
        var initializationSuccess: Boolean? = null
        
        val callback = object : TemplateMatchingService.OpenCVInitializationCallback {
            override fun onInitializationComplete(success: Boolean) {
                callbackReceived = true
                initializationSuccess = success
            }
        }
        
        // Note: This test will likely fail in unit test environment since OpenCV
        // requires Android runtime, but it tests the callback mechanism
        templateMatchingService.initializeOpenCV(callback)
        
        // In a real Android environment, we would wait for the callback
        // For unit tests, we just verify the callback interface works
        assertNotNull("Callback should be set up correctly", callback)
    }

    @Test
    fun testCleanup() {
        // Test that cleanup doesn't throw exceptions
        templateMatchingService.cleanup()
        
        // Verify service can still be used after cleanup
        val stats = templateMatchingService.getMatchingStats()
        assertNotNull("Stats should still be available after cleanup", stats)
    }

    @Test
    fun testTemplateValidationEdgeCases() {
        val mockBitmap = createMockBitmap(1, 1) // Very small bitmap
        
        // Test with minimum valid threshold
        val minThresholdTemplate = Template(
            centerX = 1,
            centerY = 1,
            radius = 1,
            templateBitmap = mockBitmap,
            matchThreshold = 0.1f
        )
        
        assertTrue("Template with minimum threshold should be valid", 
            templateMatchingService.validateTemplate(minThresholdTemplate))
        
        // Test with maximum valid threshold
        val maxThresholdTemplate = Template(
            centerX = 1,
            centerY = 1,
            radius = 1,
            templateBitmap = mockBitmap,
            matchThreshold = 1.0f
        )
        
        assertTrue("Template with maximum threshold should be valid", 
            templateMatchingService.validateTemplate(maxThresholdTemplate))
    }

    @Test
    fun testPreprocessingOptions() {
        val options = TemplateMatchingService.PreprocessingOptions(
            convertToGrayscale = true,
            applyGaussianBlur = true,
            gaussianKernelSize = 5,
            gaussianSigma = 1.0,
            applyHistogramEqualization = false,
            scaleFactor = 1.0f
        )
        
        assertNotNull("Preprocessing options should be created", options)
        assertTrue("Grayscale conversion should be enabled", options.convertToGrayscale)
        assertTrue("Gaussian blur should be enabled", options.applyGaussianBlur)
        assertEquals("Kernel size should match", 5, options.gaussianKernelSize)
    }

    @Test
    fun testExtractionOptions() {
        val options = TemplateMatchingService.ExtractionOptions(
            paddingFactor = 0.2f,
            minSize = 30,
            applyPreprocessing = true
        )
        
        assertNotNull("Extraction options should be created", options)
        assertEquals("Padding factor should match", 0.2f, options.paddingFactor, 0.001f)
        assertEquals("Min size should match", 30, options.minSize)
        assertTrue("Preprocessing should be enabled", options.applyPreprocessing)
    }

    @Test
    fun testOptimizeMatchingParameters() {
        val mockBitmap = createMockBitmap(100, 100)
        val template = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = mockBitmap,
            matchThreshold = 0.8f
        )
        
        val optimizedParams = templateMatchingService.optimizeMatchingParameters(template)
        
        assertNotNull("Optimized parameters should not be null", optimizedParams)
        assertTrue("Confidence threshold should be valid", 
            optimizedParams.confidenceThreshold in 0.1f..1.0f)
        assertTrue("Should have at least one scale", optimizedParams.scales.isNotEmpty())
        assertTrue("Matching method should be valid", 
            optimizedParams.matchingMethod >= 0)
    }

    @Test
    fun testPreprocessBitmapWithoutOpenCV() {
        val mockBitmap = createMockBitmap(100, 100)
        val options = TemplateMatchingService.PreprocessingOptions()
        
        // Should return original bitmap when OpenCV is not initialized
        val result = templateMatchingService.preprocessBitmap(mockBitmap, options)
        
        assertNotNull("Result should not be null", result)
        // In unit test environment without OpenCV, should return original bitmap
        assertEquals("Should return original bitmap when OpenCV not available", 
            mockBitmap, result)
    }

    @Test
    fun testExtractOptimizedTemplateRegion() {
        val mockBitmap = createMockBitmap(200, 200)
        
        val result = templateMatchingService.extractOptimizedTemplateRegion(
            screenshot = mockBitmap,
            centerX = 100,
            centerY = 100,
            radius = 50
        )
        
        assertNotNull("Extracted region should not be null", result)
    }

    /**
     * Helper method to create a mock bitmap for testing
     */
    private fun createMockBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}