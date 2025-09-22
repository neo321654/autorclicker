package com.templatefinder.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.templatefinder.model.Template
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager class for handling template operations including save, load, and file management
 */
class TemplateManager(private val context: Context) {

    companion object {
        private const val TAG = "TemplateManager"
        private const val TEMPLATES_DIR = "templates"
        private const val CURRENT_TEMPLATE_FILE = "current_template.dat"
        private const val TEMPLATE_BACKUP_PREFIX = "template_backup_"
        private const val MAX_BACKUP_FILES = 5
    }

    private val templatesDir: File by lazy {
        File(context.filesDir, TEMPLATES_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created templates directory: $absolutePath")
            }
        }
    }

    /**
     * Save template as the current active template
     */
    fun saveCurrentTemplate(template: Template): Boolean {
        return try {
            val currentFile = getCurrentTemplateFile()
            
            // Create backup of existing template if it exists
            if (currentFile.exists()) {
                createBackup(currentFile)
            }
            
            val success = template.saveToFile(currentFile)
            
            if (success) {
                Log.d(TAG, "Template saved successfully to ${currentFile.absolutePath}")
                Log.d(TAG, "Template details: ${template.templateBitmap.width}x${template.templateBitmap.height}, " +
                        "center: (${template.centerX}, ${template.centerY}), " +
                        "radius: ${template.radius}, threshold: ${template.matchThreshold}")
            } else {
                Log.e(TAG, "Failed to save template to ${currentFile.absolutePath}")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error saving current template", e)
            false
        }
    }

    /**
     * Load the current active template
     */
    fun loadCurrentTemplate(): Template? {
        return try {
            val currentFile = getCurrentTemplateFile()
            
            if (!currentFile.exists()) {
                Log.d(TAG, "No current template file exists")
                return null
            }
            
            val template = Template.loadFromFile(currentFile)
            
            if (template != null) {
                Log.d(TAG, "Template loaded successfully from ${currentFile.absolutePath}")
                Log.d(TAG, "Template details: ${template.templateBitmap.width}x${template.templateBitmap.height}, " +
                        "center: (${template.centerX}, ${template.centerY}), " +
                        "radius: ${template.radius}, threshold: ${template.matchThreshold}")
            } else {
                Log.e(TAG, "Failed to load template from ${currentFile.absolutePath}")
            }
            
            template
        } catch (e: Exception) {
            Log.e(TAG, "Error loading current template", e)
            null
        }
    }

    /**
     * Save template with a custom name
     */
    fun saveNamedTemplate(template: Template, name: String): Boolean {
        return try {
            val sanitizedName = sanitizeFileName(name)
            val file = File(templatesDir, "$sanitizedName.dat")
            
            val success = template.saveToFile(file)
            
            if (success) {
                Log.d(TAG, "Named template '$name' saved to ${file.absolutePath}")
            } else {
                Log.e(TAG, "Failed to save named template '$name'")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error saving named template '$name'", e)
            false
        }
    }

    /**
     * Load template by name
     */
    fun loadNamedTemplate(name: String): Template? {
        return try {
            val sanitizedName = sanitizeFileName(name)
            val file = File(templatesDir, "$sanitizedName.dat")
            
            if (!file.exists()) {
                Log.d(TAG, "Named template '$name' does not exist")
                return null
            }
            
            val template = Template.loadFromFile(file)
            
            if (template != null) {
                Log.d(TAG, "Named template '$name' loaded successfully")
            } else {
                Log.e(TAG, "Failed to load named template '$name'")
            }
            
            template
        } catch (e: Exception) {
            Log.e(TAG, "Error loading named template '$name'", e)
            null
        }
    }

    /**
     * Check if current template exists
     */
    fun hasCurrentTemplate(): Boolean {
        return getCurrentTemplateFile().exists()
    }

    /**
     * Delete current template
     */
    fun deleteCurrentTemplate(): Boolean {
        return try {
            val currentFile = getCurrentTemplateFile()
            val deleted = currentFile.delete()
            
            if (deleted) {
                Log.d(TAG, "Current template deleted successfully")
            } else {
                Log.w(TAG, "Failed to delete current template or file doesn't exist")
            }
            
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting current template", e)
            false
        }
    }

    /**
     * Delete named template
     */
    fun deleteNamedTemplate(name: String): Boolean {
        return try {
            val sanitizedName = sanitizeFileName(name)
            val file = File(templatesDir, "$sanitizedName.dat")
            val deleted = file.delete()
            
            if (deleted) {
                Log.d(TAG, "Named template '$name' deleted successfully")
            } else {
                Log.w(TAG, "Failed to delete named template '$name' or file doesn't exist")
            }
            
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting named template '$name'", e)
            false
        }
    }

    /**
     * Get list of all saved template names
     */
    fun getTemplateNames(): List<String> {
        return try {
            templatesDir.listFiles { file ->
                file.isFile && file.name.endsWith(".dat") && file.name != CURRENT_TEMPLATE_FILE
            }?.map { file ->
                file.nameWithoutExtension
            }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting template names", e)
            emptyList()
        }
    }

    /**
     * Get template file information
     */
    fun getTemplateInfo(name: String? = null): TemplateInfo? {
        return try {
            val file = if (name != null) {
                val sanitizedName = sanitizeFileName(name)
                File(templatesDir, "$sanitizedName.dat")
            } else {
                getCurrentTemplateFile()
            }
            
            if (!file.exists()) {
                return null
            }
            
            TemplateInfo(
                name = name ?: "current",
                filePath = file.absolutePath,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                exists = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting template info", e)
            null
        }
    }

    /**
     * Validate template region before saving
     */
    fun validateTemplateRegion(
        screenshot: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): ValidationResult {
        return try {
            // Check if coordinates are within bounds
            if (centerX < 0 || centerX >= screenshot.width || centerY < 0 || centerY >= screenshot.height) {
                return ValidationResult(false, "Selected point is outside screenshot bounds")
            }
            
            // Check if radius is reasonable
            if (radius < 10) {
                return ValidationResult(false, "Radius is too small (minimum 10 pixels)")
            }
            
            if (radius > 500) {
                return ValidationResult(false, "Radius is too large (maximum 500 pixels)")
            }
            
            // Check if template region fits within screenshot
            val left = centerX - radius
            val top = centerY - radius
            val right = centerX + radius
            val bottom = centerY + radius
            
            if (left < 0 || top < 0 || right >= screenshot.width || bottom >= screenshot.height) {
                return ValidationResult(false, "Template region extends outside screenshot bounds")
            }
            
            // Check if region has sufficient size
            val regionWidth = right - left
            val regionHeight = bottom - top
            
            if (regionWidth < 20 || regionHeight < 20) {
                return ValidationResult(false, "Template region is too small")
            }
            
            ValidationResult(true, "Template region is valid")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating template region", e)
            ValidationResult(false, "Error validating template region: ${e.message}")
        }
    }

    /**
     * Create backup of existing template file
     */
    private fun createBackup(currentFile: File) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(templatesDir, "$TEMPLATE_BACKUP_PREFIX$timestamp.dat")
            
            currentFile.copyTo(backupFile, overwrite = true)
            Log.d(TAG, "Created backup: ${backupFile.name}")
            
            // Clean up old backups
            cleanupOldBackups()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup", e)
        }
    }

    /**
     * Clean up old backup files, keeping only the most recent ones
     */
    private fun cleanupOldBackups() {
        try {
            val backupFiles = templatesDir.listFiles { file ->
                file.isFile && file.name.startsWith(TEMPLATE_BACKUP_PREFIX)
            }?.sortedByDescending { it.lastModified() } ?: return
            
            if (backupFiles.size > MAX_BACKUP_FILES) {
                backupFiles.drop(MAX_BACKUP_FILES).forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old backup: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old backups", e)
        }
    }

    /**
     * Get current template file
     */
    private fun getCurrentTemplateFile(): File {
        return File(templatesDir, CURRENT_TEMPLATE_FILE)
    }

    /**
     * Sanitize file name to prevent issues
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    /**
     * Data class for template information
     */
    data class TemplateInfo(
        val name: String,
        val filePath: String,
        val fileSize: Long,
        val lastModified: Long,
        val exists: Boolean
    )

    /**
     * Replace current template with validation
     */
    fun replaceCurrentTemplate(newTemplate: Template): Boolean {
        return try {
            // Validate new template first
            val validationResult = validateTemplate(newTemplate)
            if (!validationResult.isValid) {
                Log.e(TAG, "Template validation failed: ${validationResult.message}")
                return false
            }
            
            // Create backup of existing template if it exists
            if (hasCurrentTemplate()) {
                createTemplateBackup()
            }
            
            // Save new template
            val success = saveCurrentTemplate(newTemplate)
            
            if (success) {
                Log.d(TAG, "Template replaced successfully")
            } else {
                Log.e(TAG, "Failed to replace template")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing template", e)
            false
        }
    }

    /**
     * Update existing template properties
     */
    fun updateTemplate(
        matchThreshold: Float? = null,
        centerX: Int? = null,
        centerY: Int? = null,
        radius: Int? = null
    ): Boolean {
        return try {
            val currentTemplate = loadCurrentTemplate() ?: return false
            
            val updatedTemplate = Template(
                centerX = centerX ?: currentTemplate.centerX,
                centerY = centerY ?: currentTemplate.centerY,
                radius = radius ?: currentTemplate.radius,
                templateBitmap = currentTemplate.templateBitmap,
                matchThreshold = matchThreshold ?: currentTemplate.matchThreshold,
                createdAt = currentTemplate.createdAt
            )
            
            // Validate updated template
            val validationResult = validateTemplate(updatedTemplate)
            if (!validationResult.isValid) {
                Log.e(TAG, "Updated template validation failed: ${validationResult.message}")
                return false
            }
            
            saveCurrentTemplate(updatedTemplate)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating template", e)
            false
        }
    }

    /**
     * Validate template integrity and properties
     */
    fun validateTemplate(template: Template): ValidationResult {
        return try {
            // Check bitmap validity
            if (template.templateBitmap.isRecycled) {
                return ValidationResult(false, "Template bitmap is recycled")
            }
            
            if (template.templateBitmap.width <= 0 || template.templateBitmap.height <= 0) {
                return ValidationResult(false, "Template bitmap has invalid dimensions")
            }
            
            // Check coordinates
            if (template.centerX < 0 || template.centerY < 0) {
                return ValidationResult(false, "Template center coordinates cannot be negative")
            }
            
            // Check radius
            if (template.radius <= 0) {
                return ValidationResult(false, "Template radius must be positive")
            }
            
            if (template.radius > maxOf(template.templateBitmap.width, template.templateBitmap.height)) {
                return ValidationResult(false, "Template radius is larger than bitmap dimensions")
            }
            
            // Check match threshold
            if (template.matchThreshold < 0.1f || template.matchThreshold > 1.0f) {
                return ValidationResult(false, "Match threshold must be between 0.1 and 1.0")
            }
            
            // Check bitmap format
            if (template.templateBitmap.config == null) {
                return ValidationResult(false, "Template bitmap has invalid configuration")
            }
            
            ValidationResult(true, "Template is valid")
            
        } catch (e: Exception) {
            ValidationResult(false, "Error validating template: ${e.message}")
        }
    }

    /**
     * Create backup of current template
     */
    fun createTemplateBackup(): Boolean {
        return try {
            val currentTemplate = loadCurrentTemplate() ?: return false
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupName = "template_backup_$timestamp"
            
            val success = saveNamedTemplate(currentTemplate, backupName)
            
            if (success) {
                Log.d(TAG, "Template backup created: $backupName")
                
                // Clean up old backups
                cleanupOldBackups()
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error creating template backup", e)
            false
        }
    }

    /**
     * Restore template from backup
     */
    fun restoreTemplateFromBackup(backupName: String): Boolean {
        return try {
            val backupTemplate = loadNamedTemplate(backupName)
            if (backupTemplate == null) {
                Log.e(TAG, "Backup template not found: $backupName")
                return false
            }
            
            // Validate backup template
            val validationResult = validateTemplate(backupTemplate)
            if (!validationResult.isValid) {
                Log.e(TAG, "Backup template validation failed: ${validationResult.message}")
                return false
            }
            
            // Create backup of current template before restoring
            if (hasCurrentTemplate()) {
                createTemplateBackup()
            }
            
            // Restore the backup
            val success = saveCurrentTemplate(backupTemplate)
            
            if (success) {
                Log.d(TAG, "Template restored from backup: $backupName")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring template from backup", e)
            false
        }
    }

    /**
     * Get list of available backups
     */
    fun getTemplateBackups(): List<BackupInfo> {
        return try {
            val backupNames = getTemplateNames().filter { it.startsWith("template_backup_") }
            
            backupNames.mapNotNull { backupName ->
                val templateInfo = getTemplateInfo(backupName)
                if (templateInfo != null) {
                    BackupInfo(
                        name = backupName,
                        displayName = formatBackupDisplayName(backupName),
                        createdAt = templateInfo.lastModified,
                        fileSize = templateInfo.fileSize
                    )
                } else {
                    null
                }
            }.sortedByDescending { it.createdAt }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting template backups", e)
            emptyList()
        }
    }

    /**
     * Format backup name for display
     */
    private fun formatBackupDisplayName(backupName: String): String {
        return try {
            val timestamp = backupName.removePrefix("template_backup_")
            val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).parse(timestamp)
            
            if (date != null) {
                val displayFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                "Backup - ${displayFormat.format(date)}"
            } else {
                backupName
            }
        } catch (e: Exception) {
            backupName
        }
    }

    /**
     * Export template to external storage
     */
    fun exportTemplate(template: Template? = null): File? {
        return try {
            val templateToExport = template ?: loadCurrentTemplate() ?: return null
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(context.getExternalFilesDir(null), "template_export_$timestamp.dat")
            
            val success = templateToExport.saveToFile(exportFile)
            
            if (success) {
                Log.d(TAG, "Template exported to: ${exportFile.absolutePath}")
                exportFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting template", e)
            null
        }
    }

    /**
     * Import template from file
     */
    fun importTemplate(file: File): Boolean {
        return try {
            if (!file.exists()) {
                Log.e(TAG, "Import file does not exist: ${file.absolutePath}")
                return false
            }
            
            val importedTemplate = Template.loadFromFile(file)
            if (importedTemplate == null) {
                Log.e(TAG, "Failed to load template from file: ${file.absolutePath}")
                return false
            }
            
            // Validate imported template
            val validationResult = validateTemplate(importedTemplate)
            if (!validationResult.isValid) {
                Log.e(TAG, "Imported template validation failed: ${validationResult.message}")
                return false
            }
            
            // Create backup before importing
            if (hasCurrentTemplate()) {
                createTemplateBackup()
            }
            
            // Save imported template
            val success = saveCurrentTemplate(importedTemplate)
            
            if (success) {
                Log.d(TAG, "Template imported successfully from: ${file.absolutePath}")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error importing template", e)
            false
        }
    }

    /**
     * Get template management statistics
     */
    fun getTemplateStats(): TemplateStats {
        return try {
            val currentTemplate = loadCurrentTemplate()
            val backups = getTemplateBackups()
            val namedTemplates = getTemplateNames().filter { !it.startsWith("template_backup_") }
            
            TemplateStats(
                hasCurrentTemplate = currentTemplate != null,
                currentTemplateSize = currentTemplate?.templateBitmap?.byteCount ?: 0,
                backupCount = backups.size,
                namedTemplateCount = namedTemplates.size,
                totalTemplates = if (currentTemplate != null) 1 else 0 + backups.size + namedTemplates.size,
                lastBackupTime = backups.firstOrNull()?.createdAt ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting template stats", e)
            TemplateStats(false, 0, 0, 0, 0, 0L)
        }
    }

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )

    /**
     * Data class for backup information
     */
    data class BackupInfo(
        val name: String,
        val displayName: String,
        val createdAt: Long,
        val fileSize: Long
    )

    /**
     * Data class for template statistics
     */
    data class TemplateStats(
        val hasCurrentTemplate: Boolean,
        val currentTemplateSize: Int,
        val backupCount: Int,
        val namedTemplateCount: Int,
        val totalTemplates: Int,
        val lastBackupTime: Long
    )
}