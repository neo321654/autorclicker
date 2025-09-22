package com.templatefinder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.templatefinder.R
import com.templatefinder.databinding.ActivityGalleryTemplateBinding
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Activity for selecting an image from gallery and marking a template point
 */
class GalleryTemplateActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GalleryTemplateActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val DEFAULT_TEMPLATE_RADIUS = 50
        private const val MIN_TEMPLATE_RADIUS = 10
        private const val MAX_TEMPLATE_RADIUS = 200
    }

    private lateinit var binding: ActivityGalleryTemplateBinding
    private lateinit var templateManager: TemplateManager
    
    private var selectedImageBitmap: Bitmap? = null
    private var templateX: Int = -1
    private var templateY: Int = -1
    private var templateRadius: Int = DEFAULT_TEMPLATE_RADIUS
    private var imageScaleX: Float = 1f
    private var imageScaleY: Float = 1f
    private var imageOffsetX: Float = 0f
    private var imageOffsetY: Float = 0f

    // Activity result launcher for gallery selection
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            openGallery()
        } else {
            Toast.makeText(this, getString(R.string.permission_required_gallery), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityGalleryTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        templateManager = TemplateManager(this)
        
        setupUI()
        setupEventListeners()
        
        Log.d(TAG, "GalleryTemplateActivity created")
    }

    private fun setupUI() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Create Template from Gallery"
        }
        
        // Initialize radius
        binding.radiusSeekBar.apply {
            min = MIN_TEMPLATE_RADIUS
            max = MAX_TEMPLATE_RADIUS
            progress = DEFAULT_TEMPLATE_RADIUS
        }
        
        updateRadiusText()
        updateUI()
    }

    private fun setupEventListeners() {
        // Select image button
        binding.selectImageButton.setOnClickListener {
            checkPermissionsAndOpenGallery()
        }
        
        // Image view touch listener for point selection
        binding.imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && selectedImageBitmap != null) {
                handleImageTouch(event.x, event.y)
                true
            } else {
                false
            }
        }
        
        // Radius seek bar
        binding.radiusSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                templateRadius = progress
                updateRadiusText()
                updateImageOverlay()
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // Create template button
        binding.createTemplateButton.setOnClickListener {
            createTemplate()
        }
        
        // Cancel button
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun checkPermissionsAndOpenGallery() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            openGallery()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
            galleryLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
            Toast.makeText(this, "${getString(R.string.error_opening_gallery)}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(uri)
                }
                
                if (bitmap != null) {
                    selectedImageBitmap = bitmap
                    binding.imageView.setImageBitmap(bitmap)
                    
                    // Reset template point
                    templateX = -1
                    templateY = -1
                    
                    updateUI()
                    
                    Log.d(TAG, "Image loaded successfully: ${bitmap.width}x${bitmap.height}")
                    Toast.makeText(this@GalleryTemplateActivity, getString(R.string.image_loaded_tap_instruction), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@GalleryTemplateActivity, getString(R.string.failed_to_load_image), Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image from URI", e)
                Toast.makeText(this@GalleryTemplateActivity, "Error loading image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                // First decode to get dimensions
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            // Calculate sample size to avoid memory issues
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1024, 1024)
            
            // Decode with sample size
            val finalInputStream: InputStream? = contentResolver.openInputStream(uri)
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }
            val bitmap = BitmapFactory.decodeStream(finalInputStream, null, finalOptions)
            finalInputStream?.close()
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from URI", e)
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sampleSize = 1
        if (width > maxWidth || height > maxHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / sampleSize) >= maxWidth && (halfHeight / sampleSize) >= maxHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun handleImageTouch(touchX: Float, touchY: Float) {
        val bitmap = selectedImageBitmap ?: return
        
        // Calculate image bounds in ImageView
        val imageView = binding.imageView
        val drawable = imageView.drawable ?: return
        
        val imageViewWidth = imageView.width - imageView.paddingLeft - imageView.paddingRight
        val imageViewHeight = imageView.height - imageView.paddingTop - imageView.paddingBottom
        
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        
        // Calculate scale and offset
        val scaleX = imageViewWidth.toFloat() / drawableWidth
        val scaleY = imageViewHeight.toFloat() / drawableHeight
        val scale = minOf(scaleX, scaleY)
        
        val scaledWidth = drawableWidth * scale
        val scaledHeight = drawableHeight * scale
        
        val offsetX = (imageViewWidth - scaledWidth) / 2
        val offsetY = (imageViewHeight - scaledHeight) / 2
        
        // Convert touch coordinates to image coordinates
        val imageX = ((touchX - offsetX - imageView.paddingLeft) / scale).toInt()
        val imageY = ((touchY - offsetY - imageView.paddingTop) / scale).toInt()
        
        // Validate coordinates
        if (imageX >= 0 && imageX < bitmap.width && imageY >= 0 && imageY < bitmap.height) {
            templateX = imageX
            templateY = imageY
            
            // Store scale and offset for overlay drawing
            imageScaleX = scale
            imageScaleY = scale
            imageOffsetX = offsetX + imageView.paddingLeft
            imageOffsetY = offsetY + imageView.paddingTop
            
            updateImageOverlay()
            updateUI()
            
            Log.d(TAG, "Template point selected: ($templateX, $templateY)")
            Toast.makeText(this, getString(R.string.template_point_selected, templateX, templateY), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateImageOverlay() {
        val bitmap = selectedImageBitmap ?: return
        if (templateX < 0 || templateY < 0) return
        
        // Create overlay bitmap
        val overlayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(overlayBitmap)
        
        // Draw template circle
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        
        canvas.drawCircle(templateX.toFloat(), templateY.toFloat(), templateRadius.toFloat(), paint)
        
        // Draw center point
        val centerPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        canvas.drawCircle(templateX.toFloat(), templateY.toFloat(), 5f, centerPaint)
        
        binding.imageView.setImageBitmap(overlayBitmap)
    }

    private fun updateRadiusText() {
        binding.radiusText.text = "Radius: ${templateRadius}px"
    }

    private fun updateUI() {
        val hasImage = selectedImageBitmap != null
        val hasPoint = templateX >= 0 && templateY >= 0
        
        binding.apply {
            // Show/hide instruction text
            instructionText.visibility = if (hasImage) View.GONE else View.VISIBLE
            
            // Show/hide radius controls
            radiusLabel.visibility = if (hasImage) View.VISIBLE else View.GONE
            radiusSeekBar.visibility = if (hasImage) View.VISIBLE else View.GONE
            radiusText.visibility = if (hasImage) View.VISIBLE else View.GONE
            
            // Show/hide point info
            pointInfoText.visibility = if (hasPoint) View.VISIBLE else View.GONE
            if (hasPoint) {
                pointInfoText.text = getString(R.string.selected_point_info, templateX, templateY, templateRadius)
            }
            
            // Enable/disable create button
            createTemplateButton.isEnabled = hasImage && hasPoint
        }
    }

    private fun createTemplate() {
        val bitmap = selectedImageBitmap ?: return
        if (templateX < 0 || templateY < 0) return
        
        lifecycleScope.launch {
            try {
                // Create template name
                val templateName = "Gallery_${System.currentTimeMillis()}"
                
                // Create template object
                val template = Template(
                    centerX = templateX,
                    centerY = templateY,
                    radius = templateRadius,
                    templateBitmap = bitmap,
                    matchThreshold = 0.8f,
                    createdAt = System.currentTimeMillis()
                )
                
                // Save template
                val success = withContext(Dispatchers.IO) {
                    templateManager.saveNamedTemplate(template, templateName)
                }
                
                if (success) {
                    Log.d(TAG, "Template created successfully: $templateName")
                    Toast.makeText(this@GalleryTemplateActivity, getString(R.string.template_created_from_gallery), Toast.LENGTH_LONG).show()
                    
                    // Return success result
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@GalleryTemplateActivity, getString(R.string.failed_to_save_template), Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error creating template", e)
                Toast.makeText(this@GalleryTemplateActivity, "${getString(R.string.error_creating_template)}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up bitmap to avoid memory leaks
        selectedImageBitmap?.recycle()
        selectedImageBitmap = null
        
        Log.d(TAG, "GalleryTemplateActivity destroyed")
    }
}