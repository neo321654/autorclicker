package com.templatefinder.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.templatefinder.databinding.ActivityTemplateTestBinding
import com.templatefinder.manager.TemplateManager
import com.templatefinder.model.Template
import com.templatefinder.service.TemplateMatchingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class TemplateTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TemplateTestActivity"
    }

    private lateinit var binding: ActivityTemplateTestBinding
    private lateinit var templateManager: TemplateManager
    private lateinit var templateMatchingService: TemplateMatchingService

    private var selectedImageBitmap: Bitmap? = null
    private var selectedTemplate: Template? = null
    private var selectedTemplateName: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        templateManager = TemplateManager(this)
        templateMatchingService = TemplateMatchingService(this)

        setupUI()
        setupEventListeners()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Test Template Recognition"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.startTestButton.isEnabled = false
    }

    private fun setupEventListeners() {
        binding.selectImageButton.setOnClickListener {
            openGallery()
        }

        binding.selectTemplateButton.setOnClickListener {
            showTemplateSelectionDialog()
        }

        binding.startTestButton.setOnClickListener {
            startTest()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun loadImageFromUri(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            selectedImageBitmap = withContext(Dispatchers.IO) {
                decodeBitmapFromUri(uri)
            }
            binding.imageView.setImageBitmap(selectedImageBitmap)
            binding.imageInfoText.text = "Image selected"
            binding.progressBar.visibility = View.GONE
            updateTestButtonState()
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun showTemplateSelectionDialog() {
        val templateNames = templateManager.getTemplateNames().toTypedArray()
        if (templateNames.isEmpty()) {
            Toast.makeText(this, "No saved templates found.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select a Template")
            .setItems(templateNames) { _, which ->
                val name = templateNames[which]
                selectedTemplateName = name
                selectedTemplate = templateManager.loadNamedTemplate(name)
                binding.templateInfoText.text = "Template: $name"
                updateTestButtonState()
            }
            .show()
    }

    private fun updateTestButtonState() {
        binding.startTestButton.isEnabled = selectedImageBitmap != null && selectedTemplate != null
    }

    private fun startTest() {
        val image = selectedImageBitmap ?: return
        val template = selectedTemplate ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.startTestButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.Default) {
            val results = templateMatchingService.findAllMatches(image, template)

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.startTestButton.isEnabled = true
                if (results.isNotEmpty()) {
                    val resultBitmap = drawResultsOnBitmap(image, results)
                    binding.imageView.setImageBitmap(resultBitmap)
                    Toast.makeText(this@TemplateTestActivity, "Found ${results.size} matches.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@TemplateTestActivity, "No matches found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun drawResultsOnBitmap(originalBitmap: Bitmap, results: List<com.templatefinder.model.SearchResult>): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
            textSize = 24f
        }

        val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 24f
            isAntiAlias = true
        }

        results.forEach { result ->
            result.coordinates?.let {
                val templateWidth = selectedTemplate?.templateBitmap?.width ?: 0
                val templateHeight = selectedTemplate?.templateBitmap?.height ?: 0
                val left = it.x - templateWidth / 2
                val top = it.y - templateHeight / 2
                val right = it.x + templateWidth / 2
                val bottom = it.y + templateHeight / 2
                val rect = Rect(left, top, right, bottom)
                canvas.drawRect(rect, paint)

                val confidenceText = "${result.getConfidencePercentage()}%"
                canvas.drawText(confidenceText, (right + 5).toFloat(), (top + 20).toFloat(), textPaint)
            }
        }
        return resultBitmap
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
