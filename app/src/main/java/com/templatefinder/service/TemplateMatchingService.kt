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
        Log.d(TAG, "findTemplate called, but OpenCV is not used.")
        callback.onMatchingError("OpenCV is not used in this version.")
    }

    fun findTemplateMultiScale(
        screenshot: Bitmap,
        template: Template,
        scales: FloatArray = floatArrayOf(1.0f),
        callback: TemplateMatchingCallback
    ) {
        Log.d(TAG, "findTemplateMultiScale called, but OpenCV is not used.")
        callback.onMatchingError("OpenCV is not used in this version.")
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

    fun cleanup() {
        Log.d(TAG, "Cleaning up TemplateMatchingService")
        initializationCallback = null
    }
}
