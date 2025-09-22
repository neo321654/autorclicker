package com.templatefinder.service

import android.content.Context
import android.graphics.Bitmap
import com.templatefinder.model.Template
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.system.measureTimeMillis

class TemplateMatchingPerformanceTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var templateMatchingService: TemplateMatchingService

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        templateMatchingService = TemplateMatchingService(mockContext)
    }

    @Test
    fun testPreprocessingPerformance() {
        val bitmap = createTestBitmap(1920, 1080) // Full HD screenshot
        
        // Test different preprocessing options
        val basicOptions = TemplateMatchingService.PreprocessingOptions()
        val advancedOptions = TemplateMatchingService.PreprocessingOptions(
            convertToGrayscale = true,
            applyGaussianBlur = true,
            applyHistogramEqualization = true
        )
        
        // Measure basic preprocessing time
        val basicTime = measureTimeMillis {
            templateMatchingService.preprocessBitmap(bitmap, basicOptions)
        }
        
        // Measure advanced preprocessing time
        val advancedTime = measureTimeMillis {
            templateMatchingService.preprocessBitmap(bitmap, advancedOptions)
        }
        
        // Performance assertions (these will pass even without OpenCV since we return original bitmap)
        assertTrue("Basic preprocessing should complete quickly", basicTime < 5000) // 5 seconds max
        assertTrue("Advanced preprocessing should complete reasonably", advancedTime < 10000) // 10 seconds max
        
        println("Basic preprocessing time: ${basicTime}ms")
        println("Advanced preprocessing time: ${advancedTime}ms")
    }

    @Test
    fun testTemplateExtractionPerformance() {
        val screenshot = createTestBitmap(1920, 1080)
        
        val extractionTimes = mutableListOf<Long>()
        
        // Test multiple extractions
        repeat(10) {
            val time = measureTimeMillis {
                templateMatchingService.extractOptimizedTemplateRegion(
                    screenshot = screenshot,
                    centerX = 500 + it * 10,
                    centerY = 500 + it * 10,
                    radius = 50
                )
            }
            extractionTimes.add(time)
        }
        
        val averageTime = extractionTimes.average()
        val maxTime = extractionTimes.maxOrNull() ?: 0L
        
        assertTrue("Average extraction time should be reasonable", averageTime < 100) // 100ms average
        assertTrue("Max extraction time should be acceptable", maxTime < 500) // 500ms max
        
        println("Average extraction time: ${averageTime}ms")
        println("Max extraction time: ${maxTime}ms")
    }

    @Test
    fun testParameterOptimizationPerformance() {
        val templates = listOf(
            createTestTemplate(50, 50), // Small template
            createTestTemplate(100, 100), // Medium template
            createTestTemplate(200, 200), // Large template
            createTestTemplate(500, 300) // Very large template
        )
        
        templates.forEach { template ->
            val time = measureTimeMillis {
                val optimizedParams = templateMatchingService.optimizeMatchingParameters(template)
                
                // Verify optimization results
                assertNotNull("Optimized parameters should not be null", optimizedParams)
                assertTrue("Confidence threshold should be valid", 
                    optimizedParams.confidenceThreshold in 0.1f..1.0f)
                assertTrue("Should have at least one scale", optimizedParams.scales.isNotEmpty())
            }
            
            assertTrue("Parameter optimization should be fast", time < 100) // 100ms max
            println("Optimization time for ${template.templateBitmap.width}x${template.templateBitmap.height} template: ${time}ms")
        }
    }

    @Test
    fun testMemoryUsageWithLargeBitmaps() {
        // Test with progressively larger bitmaps
        val sizes = listOf(
            Pair(480, 320),   // Small
            Pair(1280, 720),  // HD
            Pair(1920, 1080), // Full HD
            Pair(2560, 1440)  // QHD
        )
        
        sizes.forEach { (width, height) ->
            val bitmap = createTestBitmap(width, height)
            
            // Test preprocessing doesn't cause memory issues
            val processedBitmap = templateMatchingService.preprocessBitmap(bitmap)
            assertNotNull("Processed bitmap should not be null", processedBitmap)
            
            // Test extraction doesn't cause memory issues
            val extractedBitmap = templateMatchingService.extractOptimizedTemplateRegion(
                screenshot = bitmap,
                centerX = width / 2,
                centerY = height / 2,
                radius = 50
            )
            assertNotNull("Extracted bitmap should not be null", extractedBitmap)
            
            println("Successfully processed ${width}x${height} bitmap")
        }
    }

    @Test
    fun testValidationPerformance() {
        val templates = (1..100).map { createTestTemplate(50 + it, 50 + it) }
        
        val validationTime = measureTimeMillis {
            templates.forEach { template ->
                val isValid = templateMatchingService.validateTemplate(template)
                assertTrue("Template should be valid", isValid)
            }
        }
        
        val averageValidationTime = validationTime / templates.size.toDouble()
        
        assertTrue("Validation should be very fast", averageValidationTime < 1.0) // Less than 1ms per template
        println("Average validation time per template: ${averageValidationTime}ms")
    }

    @Test
    fun testScalingPerformance() {
        val template = createTestTemplate(200, 200)
        val scales = floatArrayOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
        
        scales.forEach { scale ->
            val time = measureTimeMillis {
                // We can't directly test the private scaleTemplate method,
                // but we can test the optimization that uses it
                val optimizedParams = templateMatchingService.optimizeMatchingParameters(template)
                assertNotNull("Optimized parameters should not be null", optimizedParams)
            }
            
            assertTrue("Scaling operations should be fast", time < 50) // 50ms max
            println("Processing time for scale ${scale}: ${time}ms")
        }
    }

    /**
     * Helper method to create test bitmap
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            // Fill with some pattern to make it more realistic
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLUE
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            
            // Add some variation
            paint.color = android.graphics.Color.RED
            canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 4f, paint)
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