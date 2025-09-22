package com.templatefinder.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.templatefinder.model.SearchResult
import com.templatefinder.model.Template
import com.templatefinder.util.ErrorHandler
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt

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
        if (OpenCVLoader.initLocal()) {
            Log.d(TAG, "OpenCV loaded successfully")
            isOpenCVInitialized.set(true)
            callback?.onInitializationComplete(true)
        } else {
            val error = "OpenCV initialization failed"
            Log.e(TAG, error)
            isOpenCVInitialized.set(false)
            callback?.onInitializationComplete(false)
            errorHandler.handleError(
                category = ErrorHandler.CATEGORY_SYSTEM,
                severity = ErrorHandler.SEVERITY_CRITICAL,
                message = error,
                context = "OpenCVLoader.initLocal() returned false"
            )
        }
    }

    fun isInitialized(): Boolean = isOpenCVInitialized.get()

    fun findTemplate(
        screenshot: Bitmap,
        template: Template,
        callback: TemplateMatchingCallback
    ) {
        if (!isInitialized()) {
            callback.onMatchingError("OpenCV is not initialized")
            return
        }
        try {
            val result = performOpenCVTemplateMatching(screenshot, template)
            callback.onMatchingComplete(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error in template matching", e)
            callback.onMatchingError("Template matching failed: ${e.message}")
        }
    }

    fun findTemplateMultiScale(
        screenshot: Bitmap,
        template: Template,
        scales: FloatArray = floatArrayOf(1.0f, 0.9f, 1.1f),
        callback: TemplateMatchingCallback
    ) {
        if (!isInitialized()) {
            callback.onMatchingError("OpenCV is not initialized")
            return
        }
        // This is a more complex implementation, for now, we just call the single scale version
        findTemplate(screenshot, template, callback)
    }
    
    fun findAllMatches(screenshot: Bitmap, template: Template): List<SearchResult> {
        if (!isInitialized()) {
            Log.e(TAG, "OpenCV is not initialized")
            return emptyList()
        }

        val screenshotMat = Mat()
        Utils.bitmapToMat(screenshot, screenshotMat)
        val templateMat = Mat()
        Utils.bitmapToMat(template.templateBitmap, templateMat)

        if (screenshotMat.empty() || templateMat.empty()) {
            Log.e(TAG, "Screenshot or template Mat is empty")
            return emptyList()
        }

        Imgproc.cvtColor(screenshotMat, screenshotMat, Imgproc.COLOR_BGRA2GRAY)
        Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_BGRA2GRAY)

        val resultWidth = screenshotMat.width() - templateMat.width() + 1
        val resultHeight = screenshotMat.height() - templateMat.height() + 1
        if (resultWidth <= 0 || resultHeight <= 0) {
            Log.w(TAG, "Template is larger than the screenshot")
            return emptyList()
        }
        val result = Mat(resultHeight, resultWidth, CvType.CV_32FC1)

        Imgproc.matchTemplate(screenshotMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

        val foundResults = mutableListOf<SearchResult>()
        val threshold = template.matchThreshold.toDouble()

        for (y in 0 until result.rows()) {
            for (x in 0 until result.cols()) {
                if (result.get(y, x)[0] >= threshold) {
                    val confidence = result.get(y, x)[0]
                    val matchLoc = Point(x.toDouble(), y.toDouble())
                    
                    val centerX = (matchLoc.x + templateMat.width() / 2).toInt()
                    val centerY = (matchLoc.y + templateMat.height() / 2).toInt()

                    if (!isOverlapping(centerX, centerY, foundResults, template.radius)) {
                        foundResults.add(SearchResult.success(centerX, centerY, confidence.toFloat()))
                    }
                }
            }
        }

        screenshotMat.release()
        templateMat.release()
        result.release()

        return nonMaxSuppression(foundResults, template.radius.toDouble())
    }

    private fun isOverlapping(x: Int, y: Int, results: List<SearchResult>, radius: Int): Boolean {
        results.forEach {
            val dx = it.coordinates!!.x - x
            val dy = it.coordinates.y - y
            if (dx * dx + dy * dy < radius * radius) {
                return true
            }
        }
        return false
    }
    
    private fun nonMaxSuppression(results: List<SearchResult>, overlapThresh: Double): List<SearchResult> {
        val sortedResults = results.sortedByDescending { it.confidence }
        val finalResults = mutableListOf<SearchResult>()

        for (res in sortedResults) {
            var keep = true
            for (finalRes in finalResults) {
                val dx = res.coordinates!!.x - finalRes.coordinates!!.x
                val dy = res.coordinates.y - finalRes.coordinates.y
                val distance = sqrt(dx.toDouble().pow(2) + dy.toDouble().pow(2))
                if (distance < overlapThresh) {
                    keep = false
                    break
                }
            }
            if (keep) {
                finalResults.add(res)
            }
        }
        return finalResults
    }

    private fun performOpenCVTemplateMatching(screenshot: Bitmap, template: Template): SearchResult {
        val screenshotMat = Mat()
        Utils.bitmapToMat(screenshot, screenshotMat)
        val templateMat = Mat()
        Utils.bitmapToMat(template.templateBitmap, templateMat)

        Imgproc.cvtColor(screenshotMat, screenshotMat, Imgproc.COLOR_BGRA2GRAY)
        Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_BGRA2GRAY)

        val resultWidth = screenshotMat.width() - templateMat.width() + 1
        val resultHeight = screenshotMat.height() - templateMat.height() + 1
        if (resultWidth <= 0 || resultHeight <= 0) {
            return SearchResult.failure()
        }
        val result = Mat(resultHeight, resultWidth, CvType.CV_32FC1)

        Imgproc.matchTemplate(screenshotMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

        val mmr = Core.minMaxLoc(result)
        val maxVal = mmr.maxVal
        val maxLoc = mmr.maxLoc

        screenshotMat.release()
        templateMat.release()
        result.release()

        return if (maxVal >= template.matchThreshold) {
            val centerX = (maxLoc.x + template.templateBitmap.width / 2).toInt()
            val centerY = (maxLoc.y + template.templateBitmap.height / 2).toInt()
            SearchResult.success(centerX, centerY, maxVal.toFloat())
        } else {
            SearchResult.failure()
        }
    }

    fun preprocessBitmap(
        bitmap: Bitmap,
        options: PreprocessingOptions = PreprocessingOptions()
    ): Bitmap {
        if (!isInitialized()) {
            Log.w(TAG, "OpenCV not initialized, returning original bitmap")
            return bitmap
        }
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val dst = Mat()

        if (options.convertToGrayscale) {
            Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2GRAY)
        }
        if (options.applyGaussianBlur) {
            Imgproc.GaussianBlur(dst, dst, org.opencv.core.Size(options.gaussianKernelSize.toDouble(), options.gaussianKernelSize.toDouble()), options.gaussianSigma)
        }
        
        val resultBitmap = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, resultBitmap)
        
        src.release()
        dst.release()
        
        return resultBitmap
    }

    fun getMatchingStats(): MatchingStats {
        return MatchingStats(isInitialized(), if(isInitialized()) OpenCVLoader.OPENCV_VERSION else "Not Initialized")
    }

    fun validateTemplate(template: Template): Boolean {
        return !template.templateBitmap.isRecycled && template.templateBitmap.width > 0 && template.templateBitmap.height > 0
    }

    fun optimizeMatchingParameters(template: Template): OptimizedParameters {
        return OptimizedParameters(template.matchThreshold, floatArrayOf(1.0f), Imgproc.TM_CCOEFF_NORMED, true)
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

    fun cleanup() {
        Log.d(TAG, "Cleaning up TemplateMatchingService")
        initializationCallback = null
    }
}