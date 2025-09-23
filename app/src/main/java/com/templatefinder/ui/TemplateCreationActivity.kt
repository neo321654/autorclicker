package com.templatefinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.templatefinder.R
import com.templatefinder.databinding.ActivityTemplateCreationBinding
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.Template
import com.templatefinder.service.ScreenshotAccessibilityService
import kotlin.math.max
import kotlin.math.min

/**
 * Activity for creating templates by selecting points on screenshots
 */
class TemplateCreationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TemplateCreationActivity"
        private const val MIN_RADIUS = 20
        private const val MAX_RADIUS = 200
        private const val DEFAULT_RADIUS = 50
        private const val DEFAULT_THRESHOLD = 0.8f
    }

    private lateinit var binding: ActivityTemplateCreationBinding
    private lateinit var templateManager: TemplateManager
    
    // Template creation state
    private var currentScreenshot: Bitmap? = null
    private var selectedPoint: Point? = null
    private var currentRadius: Int = DEFAULT_RADIUS
    private var currentThreshold: Float = DEFAULT_THRESHOLD
    
    // UI state
    private var isCapturingScreenshot = false
    
    // Activity result launcher for gallery template creation
    private val galleryTemplateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Template created from gallery successfully")
            Toast.makeText(this, "Template created from gallery!", Toast.LENGTH_LONG).show()
            
            // Return success and finish
            setResult(RESULT_OK)
            finish()
        }
    }
    
    // Paint objects for drawing
    private val circlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val centerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateCreationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        templateManager = TemplateManager(this)
        
        setupUI()
        setupEventListeners()
        
        Log.d(TAG, "TemplateCreationActivity created")
    }

    private fun setupUI() {
        // Configure radius SeekBar
        binding.radiusSeekBar.apply {
            min = MIN_RADIUS
            max = MAX_RADIUS
            progress = DEFAULT_RADIUS
        }
        
        // Configure threshold SeekBar (0.1 to 1.0, scaled to 10-100)
        binding.thresholdSeekBar.apply {
            min = 10
            max = 100
            progress = (DEFAULT_THRESHOLD * 100).toInt()
        }
        
        updateRadiusText()
        updateThresholdText()
        updateUI()
    }

    private fun setupEventListeners() {
        // Screenshot capture button
        binding.captureScreenshotButton.setOnClickListener {
            captureScreenshot()
        }
        
        // Gallery selection button
        binding.selectFromGalleryButton.setOnClickListener {
            openGalleryTemplateActivity()
        }
        
        // Save template button
        binding.saveTemplateButton.setOnClickListener {
            saveTemplate()
        }
        
        // Cancel button
        binding.cancelButton.setOnClickListener {
            finish()
        }
        
        // Radius SeekBar
        binding.radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRadius = progress
                    updateRadiusText()
                    updatePreview()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Threshold SeekBar
        binding.thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentThreshold = progress / 100f
                    updateThresholdText()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // ImageView touch handling
        binding.screenshotImageView.setOnTouchListener { _, event ->
            handleImageTouch(event)
        }
    }

    private fun openGalleryTemplateActivity() {
        try {
            val intent = android.content.Intent(this, GalleryTemplateActivity::class.java)
            galleryTemplateLauncher.launch(intent)
            Log.d(TAG, "Opening gallery template activity")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery template activity", e)
            showError("Error opening gallery: ${e.message}")
        }
    }

    private fun captureScreenshot() {
        val service = ScreenshotAccessibilityService.getInstance()
        
        if (service == null) {
            showError("Accessibility service is not running. Please enable it in Settings.")
            return
        }
        
        if (!service.isScreenshotSupported()) {
            showError("Screenshot functionality is not supported on this device.")
            return
        }
        
        isCapturingScreenshot = true
        updateUI()
        
        Log.d(TAG, "Requesting screenshot...")
        
        service.takeScreenshot(object : ScreenshotAccessibilityService.ScreenshotCallback {
            override fun onScreenshotTaken(bitmap: Bitmap?) {
                runOnUiThread {
                    isCapturingScreenshot = false
                    
                    if (bitmap != null) {
                        Log.d(TAG, "Screenshot received: ${bitmap.width}x${bitmap.height}")
                        currentScreenshot = bitmap
                        binding.screenshotImageView.setImageBitmap(bitmap)
                        selectedPoint = null // Reset selection
                        updateUI()
                    } else {
                        showError("Failed to capture screenshot: bitmap is null")
                        updateUI()
                    }
                }
            }

            override fun onScreenshotError(error: String) {
                runOnUiThread {
                    isCapturingScreenshot = false
                    showError("Screenshot failed: $error")
                    updateUI()
                }
            }
        })
    }

    private fun handleImageTouch(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && currentScreenshot != null) {
            val imageView = binding.screenshotImageView
            
            // Calculate the actual image bounds within the ImageView
            val drawable = imageView.drawable ?: return false
            val imageMatrix = imageView.imageMatrix
            
            // Get the scale and translation from the matrix
            val values = FloatArray(9)
            imageMatrix.getValues(values)
            
            val scaleX = values[0]
            val scaleY = values[4]
            val transX = values[2]
            val transY = values[5]
            
            // Convert touch coordinates to image coordinates
            val imageX = ((event.x - transX) / scaleX).toInt()
            val imageY = ((event.y - transY) / scaleY).toInt()
            
            // Validate coordinates are within image bounds
            val screenshot = currentScreenshot!!
            if (imageX >= 0 && imageX < screenshot.width && imageY >= 0 && imageY < screenshot.height) {
                selectedPoint = Point(imageX, imageY)
                Log.d(TAG, "Point selected: ($imageX, $imageY)")
                updatePreview()
                updateUI()
                return true
            }
        }
        return false
    }

    private fun updatePreview() {
        val screenshot = currentScreenshot ?: return
        val point = selectedPoint ?: return
        
        // Create a copy of the screenshot with overlay
        val previewBitmap = screenshot.copy(screenshot.config, true)
        val canvas = Canvas(previewBitmap)
        
        // Draw circle overlay
        canvas.drawCircle(
            point.x.toFloat(),
            point.y.toFloat(),
            currentRadius.toFloat(),
            circlePaint
        )
        
        // Draw center point
        canvas.drawCircle(
            point.x.toFloat(),
            point.y.toFloat(),
            8f,
            centerPaint
        )
        
        // Update ImageView
        binding.screenshotImageView.setImageBitmap(previewBitmap)
    }

    private fun saveTemplate() {
        val screenshot = currentScreenshot
        val point = selectedPoint
        
        if (screenshot == null) {
            showError("No screenshot available. Please capture a screenshot first.")
            return
        }
        
        if (point == null) {
            showError("No point selected. Please tap on the screenshot to select a point.")
            return
        }
        
        try {
            // Validate template region first
            val validationResult = templateManager.validateTemplateRegion(
                screenshot = screenshot,
                centerX = point.x,
                centerY = point.y,
                radius = currentRadius
            )
            
            if (!validationResult.isValid) {
                showError("Invalid template region: ${validationResult.message}")
                return
            }
            
            // Create template from the selected region
            val template = Template.createFromRegion(
                sourceBitmap = screenshot,
                centerX = point.x,
                centerY = point.y,
                radius = currentRadius,
                matchThreshold = currentThreshold
            )
            
            if (template == null) {
                showError("Failed to create template from selected region.")
                return
            }
            
            // Save template using TemplateManager
            val success = templateManager.saveCurrentTemplate(template)
            
            if (success) {
                Log.d(TAG, "Template saved successfully")
                Toast.makeText(this, getString(R.string.template_saved_successfully), Toast.LENGTH_SHORT).show()
                
                // Return success result
                setResult(RESULT_OK)
                finish()
            } else {
                showError("Failed to save template to file.")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving template", e)
            showError("Error saving template: ${e.message}")
        }
    }



    private fun updateUI() {
        val hasScreenshot = currentScreenshot != null
        val hasSelection = selectedPoint != null
        val canSave = hasScreenshot && hasSelection && !isCapturingScreenshot
        
        binding.apply {
            // Enable/disable controls based on state
            radiusSeekBar.isEnabled = hasSelection
            thresholdSeekBar.isEnabled = hasSelection
            saveTemplateButton.isEnabled = canSave
            
            // Update button text and state
            captureScreenshotButton.isEnabled = !isCapturingScreenshot
            captureScreenshotButton.text = if (isCapturingScreenshot) {
                "Capturing..."
            } else {
                "Capture Screenshot"
            }
            
            // Update instruction text
            instructionText.text = when {
                isCapturingScreenshot -> "Capturing screenshot..."
                !hasScreenshot -> "Tap 'Capture Screenshot' to begin"
                !hasSelection -> "Tap on the screenshot to select a point"
                else -> "Adjust radius and threshold, then save template"
            }
            
            // Show/hide controls based on state
            controlsLayout.visibility = if (hasSelection) View.VISIBLE else View.GONE
        }
    }

    private fun updateRadiusText() {
        binding.radiusText.text = "Radius: ${currentRadius}px"
    }

    private fun updateThresholdText() {
        binding.thresholdText.text = "Threshold: ${(currentThreshold * 100).toInt()}%"
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up bitmaps to prevent memory leaks
        currentScreenshot?.recycle()
        currentScreenshot = null
        
        Log.d(TAG, "TemplateCreationActivity destroyed")
    }
}