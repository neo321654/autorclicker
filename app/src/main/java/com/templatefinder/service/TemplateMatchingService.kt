package com.templatefinder.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.templatefinder.model.SearchResult
import com.templatefinder.model.Template
import com.templatefinder.util.ErrorHandler
import java.util.concurrent.atomic.AtomicBoolean

class TemplateMatchingService(private val context: Context) {

    companion object {
        private const val TAG = "TemplateMatchingService"
    }

    private val isOpenCVInitialized = AtomicBoolean(false)
    private var initializationCallback: OpenCVInitializationCallback? = null
    private val errorHandler = ErrorHandler.getInstance(context)

    interface OpenCVInitializationCallback {
        fun onInitializationComplete(success: Boolean)
    }

    interface TemplateMatchingCallback {
        fun onMatchingComplete(result: SearchResult)
        fun onMatchingError(error: String)
    }

    fun initializeOpenCV(callback: OpenCVInitializationCallback? = null) {
        this.initializationCallback = callback
        Log.d(TAG, "OpenCV is not used in this version.")
        callback?.onInitializationComplete(true)
        isOpenCVInitialized.set(true)
    }

    fun isInitialized(): Boolean = isOpenCVInitialized.get()

    fun findTemplate(
        screenshot: Bitmap,
        template: Template,
        callback: TemplateMatchingCallback
    ) {
        Log.d(TAG, "Starting template matching without OpenCV")
        
        try {
            val result = performBasicTemplateMatching(screenshot, template)
            callback.onMatchingComplete(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in template matching", e)
            callback.onMatchingError("Template matching failed: ${e.message}")
        }
    }

    fun findTemplateMultiScale(
        screenshot: Bitmap,
        template: Template,
        scales: FloatArray = floatArrayOf(1.0f),
        callback: TemplateMatchingCallback
    ) {
        Log.d(TAG, "Starting multi-scale template matching without OpenCV")
        
        try {
            val result = performBasicTemplateMatching(screenshot, template)
            callback.onMatchingComplete(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in multi-scale template matching", e)
            callback.onMatchingError("Multi-scale template matching failed: ${e.message}")
        }
    }

    fun preprocessBitmap(
        bitmap: Bitmap,
        options: PreprocessingOptions = PreprocessingOptions()
    ): Bitmap {
        Log.d(TAG, "preprocessBitmap called, but OpenCV is not used.")
        return bitmap
    }

    fun extractOptimizedTemplateRegion(
        screenshot: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int,
        options: ExtractionOptions = ExtractionOptions()
    ): Bitmap? {
        Log.d(TAG, "extractOptimizedTemplateRegion called, but OpenCV is not used.")
        return null
    }

    fun optimizeMatchingParameters(template: Template): OptimizedParameters {
        Log.d(TAG, "optimizeMatchingParameters called, but OpenCV is not used.")
        return OptimizedParameters(0.8f, floatArrayOf(1.0f), 0, false)
    }

    fun validateTemplate(template: Template): Boolean {
        return true
    }

    fun getMatchingStats(): MatchingStats {
        return MatchingStats(true, "Not applicable")
    }

    data class MatchingStats(
        val isOpenCVInitialized: Boolean,
        val openCVVersion: String
    )

    data class PreprocessingOptions(
        val convertToGrayscale: Boolean = true,
        val applyGaussianBlur: Boolean = false,
        val gaussianKernelSize: Int = 5,
        val gaussianSigma: Double = 1.0,
        val applyHistogramEqualization: Boolean = false,
        val scaleFactor: Float = 1.0f
    )

    data class ExtractionOptions(
        val paddingFactor: Float = 0.1f,
        val minSize: Int = 20,
        val applyPreprocessing: Boolean = true,
        val preprocessingOptions: PreprocessingOptions = PreprocessingOptions()
    )

    data class OptimizedParameters(
        val confidenceThreshold: Float,
        val scales: FloatArray,
        val matchingMethod: Int,
        val usePreprocessing: Boolean
    )

    /**
     * Basic template matching algorithm without OpenCV
     * Uses simple pixel comparison within the template region
     */
    private fun performBasicTemplateMatching(screenshot: Bitmap, template: Template): SearchResult {
        Log.d(TAG, "Performing basic template matching")
        Log.d(TAG, "Screenshot: ${screenshot.width}x${screenshot.height}, config: ${screenshot.config}")
        Log.d(TAG, "Template: ${template.templateBitmap.width}x${template.templateBitmap.height}, config: ${template.templateBitmap.config}")
        Log.d(TAG, "Template center: (${template.centerX}, ${template.centerY}), radius: ${template.radius}")
        
        // Convert HARDWARE bitmaps to SOFTWARE for pixel access
        val screenshotSoftware = convertToSoftwareBitmap(screenshot)
        val templateBitmap = convertToSoftwareBitmap(template.templateBitmap)
        val templateWidth = templateBitmap.width
        val templateHeight = templateBitmap.height
        
        // Validate dimensions
        if (templateWidth > screenshot.width || templateHeight > screenshot.height) {
            Log.w(TAG, "Template is larger than screenshot")
            return SearchResult.failure()
        }
        
        var bestMatch = 0.0
        var bestX = -1
        var bestY = -1
        
        // Calculate search area
        val searchWidth = screenshotSoftware.width - templateWidth + 1
        val searchHeight = screenshotSoftware.height - templateHeight + 1
        
        // Limit search area for performance - search in center region and around template center
        val maxSearchWidth = minOf(searchWidth, 400) // Limit to 400px width
        val maxSearchHeight = minOf(searchHeight, 600) // Limit to 600px height
        
        // Start search from center of screen
        val startX = maxOf(0, (screenshotSoftware.width - maxSearchWidth) / 2)
        val startY = maxOf(0, (screenshotSoftware.height - maxSearchHeight) / 2)
        val endX = minOf(searchWidth, startX + maxSearchWidth)
        val endY = minOf(searchHeight, startY + maxSearchHeight)
        
        Log.d(TAG, "Full search area: ${searchWidth}x${searchHeight}")
        Log.d(TAG, "Limited search area: ${maxSearchWidth}x${maxSearchHeight}")
        Log.d(TAG, "Search bounds: ($startX,$startY) to ($endX,$endY)")
        
        var iterationCount = 0
        val maxIterations = 2000 // Limit total iterations
        
        // Perform template matching by sliding the template over the screenshot
        for (y in startY until endY step 10) { // Increased step for better performance
            for (x in startX until endX step 10) {
                iterationCount++
                if (iterationCount > maxIterations) {
                    Log.w(TAG, "Reached maximum iterations ($maxIterations), stopping search")
                    break
                }
                
                val similarity = calculateSimilarity(screenshotSoftware, templateBitmap, x, y)
                
                if (similarity > bestMatch) {
                    bestMatch = similarity
                    bestX = x + templateWidth / 2  // Center of template
                    bestY = y + templateHeight / 2
                    Log.d(TAG, "New best match: $bestMatch at ($bestX, $bestY)")
                }
                
                // Early exit if we find a very good match
                if (similarity > 0.9) {
                    Log.d(TAG, "Found excellent match at ($bestX, $bestY) with similarity $similarity")
                    return SearchResult.success(bestX, bestY, similarity.toFloat())
                }
                
                // Log progress every 100 iterations
                if (iterationCount % 100 == 0) {
                    Log.d(TAG, "Search progress: $iterationCount iterations, best match: $bestMatch")
                }
            }
            if (iterationCount > maxIterations) {
                break
            }
        }
        
        Log.d(TAG, "Best match: similarity=$bestMatch at ($bestX, $bestY)")
        
        // Check if the best match meets the threshold
        if (bestMatch >= template.matchThreshold) {
            Log.d(TAG, "Match found above threshold: $bestMatch >= ${template.matchThreshold}")
            return SearchResult.success(bestX, bestY, bestMatch.toFloat())
        } else {
            Log.d(TAG, "No match found above threshold: $bestMatch < ${template.matchThreshold}")
            return SearchResult.failure()
        }
    }
    
    /**
     * Calculate similarity between template and screenshot region
     * Returns a value between 0.0 (no match) and 1.0 (perfect match)
     */
    private fun calculateSimilarity(screenshot: Bitmap, template: Bitmap, startX: Int, startY: Int): Double {
        val templateWidth = template.width
        val templateHeight = template.height
        
        var totalPixels = 0
        var matchingPixels = 0
        
        // Sample pixels for comparison (every 5th pixel for better performance)
        for (y in 0 until templateHeight step 5) {
            for (x in 0 until templateWidth step 5) {
                val screenshotX = startX + x
                val screenshotY = startY + y
                
                // Bounds check
                if (screenshotX >= screenshot.width || screenshotY >= screenshot.height) {
                    continue
                }
                
                val templatePixel = template.getPixel(x, y)
                val screenshotPixel = screenshot.getPixel(screenshotX, screenshotY)
                
                // Compare RGB values with tolerance
                val similarity = comparePixels(templatePixel, screenshotPixel)
                matchingPixels += similarity
                totalPixels++
            }
        }
        
        return if (totalPixels > 0) {
            matchingPixels.toDouble() / totalPixels.toDouble()
        } else {
            0.0
        }
    }
    
    /**
     * Convert HARDWARE bitmap to SOFTWARE bitmap for pixel access
     */
    private fun convertToSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && 
                   bitmap.config == Bitmap.Config.HARDWARE) {
            Log.d(TAG, "Converting HARDWARE bitmap to SOFTWARE")
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    /**
     * Compare two pixels and return similarity (0 or 1)
     * Uses color distance with tolerance
     */
    private fun comparePixels(pixel1: Int, pixel2: Int): Int {
        val r1 = (pixel1 shr 16) and 0xFF
        val g1 = (pixel1 shr 8) and 0xFF
        val b1 = pixel1 and 0xFF
        
        val r2 = (pixel2 shr 16) and 0xFF
        val g2 = (pixel2 shr 8) and 0xFF
        val b2 = pixel2 and 0xFF
        
        // Calculate color distance
        val rDiff = Math.abs(r1 - r2)
        val gDiff = Math.abs(g1 - g2)
        val bDiff = Math.abs(b1 - b2)
        
        // Use tolerance of 50 for each color channel (more permissive)
        val tolerance = 50
        
        return if (rDiff <= tolerance && gDiff <= tolerance && bDiff <= tolerance) {
            1 // Match
        } else {
            0 // No match
        }
    }

    fun findAllMatches(screenshot: Bitmap, template: Template): List<SearchResult> {
        Log.d(TAG, "Finding all matches in the image")

        val screenshotSoftware = convertToSoftwareBitmap(screenshot)
        val templateBitmap = convertToSoftwareBitmap(template.templateBitmap)
        val templateWidth = templateBitmap.width
        val templateHeight = templateBitmap.height

        if (templateWidth > screenshotSoftware.width || templateHeight > screenshotSoftware.height) {
            Log.w(TAG, "Template is larger than screenshot")
            return emptyList()
        }

        val foundResults = mutableListOf<SearchResult>()
        val searchWidth = screenshotSoftware.width - templateWidth
        val searchHeight = screenshotSoftware.height - templateHeight

        for (y in 0 until searchHeight step 10) {
            for (x in 0 until searchWidth step 10) {
                val similarity = calculateSimilarity(screenshotSoftware, templateBitmap, x, y)

                if (similarity >= template.matchThreshold) {
                    val centerX = x + templateWidth / 2
                    val centerY = y + templateHeight / 2

                    // Non-maximum suppression
                    var isOverlapping = false
                    for (foundResult in foundResults) {
                        val dx = (foundResult.coordinates?.x ?: 0) - centerX
                        val dy = (foundResult.coordinates?.y ?: 0) - centerY
                        val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                        if (distance < template.radius) {
                            isOverlapping = true
                            if (similarity > foundResult.confidence) {
                                foundResults.remove(foundResult)
                                foundResults.add(SearchResult.success(centerX, centerY, similarity.toFloat()))
                            }
                            break
                        }
                    }

                    if (!isOverlapping) {
                        foundResults.add(SearchResult.success(centerX, centerY, similarity.toFloat()))
                    }
                }
            }
        }

        Log.d(TAG, "Found ${foundResults.size} matches")
        return foundResults
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up TemplateMatchingService")
        initializationCallback = null
    }
}
