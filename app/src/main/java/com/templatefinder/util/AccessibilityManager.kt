package com.templatefinder.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent

import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Manager for accessibility improvements and compliance
 */
class AccessibilityManager(private val context: Context) {

    companion object {
        private const val TAG = "AccessibilityManager"
        private const val MIN_TOUCH_TARGET_SIZE_DP = 48
        private const val MIN_TEXT_SIZE_SP = 12
        private const val RECOMMENDED_TEXT_SIZE_SP = 16
        
        @Volatile
        private var instance: AccessibilityManager? = null
        
        fun getInstance(context: Context): AccessibilityManager {
            return instance ?: synchronized(this) {
                val newInstance = instance ?: AccessibilityManager(context.applicationContext)
                instance = newInstance
                newInstance
            }
        }
    }

    private val logger = Logger.getInstance(context)
    private val systemAccessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    
    // Accessibility settings
    private var isAccessibilityEnabled = false
    private var isTalkBackEnabled = false
    private var isHighContrastEnabled = false
    private var isLargeTextEnabled = false
    private var fontScale = 1.0f

    init {
        updateAccessibilitySettings()
    }

    /**
     * Update accessibility settings from system
     */
    private fun updateAccessibilitySettings() {
        try {
            isAccessibilityEnabled = systemAccessibilityManager.isEnabled
            isTalkBackEnabled = systemAccessibilityManager.isTouchExplorationEnabled
            
            // Check for high contrast
            isHighContrastEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
            } else {
                false
            }
            
            // Get font scale
            val configuration = context.resources.configuration
            fontScale = configuration.fontScale
            isLargeTextEnabled = fontScale > 1.0f
            
            logger.info(TAG, "Accessibility settings updated - enabled: $isAccessibilityEnabled, " +
                    "talkback: $isTalkBackEnabled, high contrast: $isHighContrastEnabled, " +
                    "large text: $isLargeTextEnabled (scale: $fontScale)")
            
        } catch (e: Exception) {
            logger.error(TAG, "Error updating accessibility settings", e)
        }
    }

    /**
     * Apply accessibility improvements to a view
     */
    fun applyAccessibilityImprovements(view: View) {
        try {
            when (view) {
                is Button -> applyButtonAccessibility(view)
                is TextView -> applyTextViewAccessibility(view)
                is ViewGroup -> applyViewGroupAccessibility(view)
            }
            
            // Apply general improvements
            applyGeneralAccessibility(view)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error applying accessibility improvements", e)
        }
    }

    /**
     * Apply accessibility improvements to buttons
     */
    private fun applyButtonAccessibility(button: Button) {
        // Ensure minimum touch target size
        ensureMinimumTouchTarget(button)
        
        // Improve text contrast if needed
        if (isHighContrastEnabled) {
            button.setTextColor(getHighContrastTextColor())
        }
        
        // Add content description if missing
        if (button.contentDescription.isNullOrEmpty()) {
            button.contentDescription = button.text
        }
        
        // Ensure text is readable
        ensureReadableTextSize(button)
    }

    /**
     * Apply accessibility improvements to text views
     */
    private fun applyTextViewAccessibility(textView: TextView) {
        // Ensure readable text size
        ensureReadableTextSize(textView)
        
        // Improve contrast if needed
        if (isHighContrastEnabled) {
            textView.setTextColor(getHighContrastTextColor())
        }
        
        // Improve line spacing for readability
        if (isLargeTextEnabled) {
            textView.setLineSpacing(0f, 1.2f)
        }
    }

    /**
     * Apply accessibility improvements to view groups
     */
    private fun applyViewGroupAccessibility(viewGroup: ViewGroup) {
        // Apply improvements to all children
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            applyAccessibilityImprovements(child)
        }
        
        // Set proper focus order if needed
        if (isTalkBackEnabled) {
            setupFocusOrder(viewGroup)
        }
    }

    /**
     * Apply general accessibility improvements
     */
    private fun applyGeneralAccessibility(view: View) {
        // Ensure view is focusable if it should be
        if (view is Button || view.isClickable) {
            view.isFocusable = true
            view.isFocusableInTouchMode = false // Use accessibility focus instead
        }
        
        // Add accessibility delegate for custom behavior
        ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                // Add custom accessibility actions if needed
                when (host) {
                    is Button -> {
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                        info.className = Button::class.java.name
                    }
                }
            }
        })
    }

    /**
     * Ensure minimum touch target size
     */
    private fun ensureMinimumTouchTarget(view: View) {
        val minSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            MIN_TOUCH_TARGET_SIZE_DP.toFloat(),
            context.resources.displayMetrics
        ).toInt()
        
        val layoutParams = view.layoutParams
        if (layoutParams != null) {
            if (layoutParams.width < minSizePx && layoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                layoutParams.width = minSizePx
            }
            if (layoutParams.height < minSizePx && layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                layoutParams.height = minSizePx
            }
            view.layoutParams = layoutParams
        }
        
        // Ensure minimum padding for touch target
        val currentPadding = maxOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        if (currentPadding < minSizePx / 4) {
            val padding = minSizePx / 4
            view.setPadding(padding, padding, padding, padding)
        }
    }

    /**
     * Ensure readable text size
     */
    private fun ensureReadableTextSize(textView: TextView) {
        val currentTextSize = textView.textSize / context.resources.displayMetrics.scaledDensity
        val minTextSize = if (isLargeTextEnabled) RECOMMENDED_TEXT_SIZE_SP else MIN_TEXT_SIZE_SP
        
        if (currentTextSize < minTextSize) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, minTextSize.toFloat())
        }
        
        // Apply font scale if large text is enabled
        if (isLargeTextEnabled && fontScale > 1.0f) {
            val scaledSize = currentTextSize * fontScale
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize)
        }
    }

    /**
     * Get high contrast text color
     */
    private fun getHighContrastTextColor(): Int {
        return if (isDarkMode()) {
            android.graphics.Color.WHITE
        } else {
            android.graphics.Color.BLACK
        }
    }

    /**
     * Check if dark mode is enabled
     */
    private fun isDarkMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Setup proper focus order for TalkBack
     */
    private fun setupFocusOrder(viewGroup: ViewGroup) {
        val focusableViews = mutableListOf<View>()
        
        // Collect all focusable views
        collectFocusableViews(viewGroup, focusableViews)
        
        // Set up focus order
        for (i in focusableViews.indices) {
            val currentView = focusableViews[i]
            val nextView = if (i < focusableViews.size - 1) focusableViews[i + 1] else null
            
            if (nextView != null) {
                currentView.nextFocusDownId = nextView.id
                currentView.nextFocusForwardId = nextView.id
            }
        }
    }

    /**
     * Collect all focusable views from a view group
     */
    private fun collectFocusableViews(viewGroup: ViewGroup, focusableViews: MutableList<View>) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            
            if (child.isFocusable || child.isClickable) {
                focusableViews.add(child)
            }
            
            if (child is ViewGroup) {
                collectFocusableViews(child, focusableViews)
            }
        }
    }

    /**
     * Announce text for screen readers
     */
    fun announceForAccessibility(view: View, text: String) {
        if (isAccessibilityEnabled) {
            view.announceForAccessibility(text)
        }
    }

    /**
     * Send accessibility event
     */
    fun sendAccessibilityEvent(view: View, eventType: Int) {
        if (isAccessibilityEnabled) {
            view.sendAccessibilityEvent(eventType)
        }
    }

    /**
     * Check if accessibility services are enabled
     */
    fun isAccessibilityEnabled(): Boolean {
        updateAccessibilitySettings()
        return isAccessibilityEnabled
    }

    /**
     * Check if TalkBack is enabled
     */
    fun isTalkBackEnabled(): Boolean {
        updateAccessibilitySettings()
        return isTalkBackEnabled
    }

    /**
     * Get accessibility statistics
     */
    fun getAccessibilityStats(): AccessibilityStats {
        updateAccessibilitySettings()
        
        return AccessibilityStats(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isTalkBackEnabled = isTalkBackEnabled,
            isHighContrastEnabled = isHighContrastEnabled,
            isLargeTextEnabled = isLargeTextEnabled,
            fontScale = fontScale,
            isDarkMode = isDarkMode()
        )
    }

    /**
     * Apply accessibility improvements to an entire activity
     */
    fun applyActivityAccessibilityImprovements(rootView: View) {
        try {
            logger.info(TAG, "Applying accessibility improvements to activity")
            
            if (rootView is ViewGroup) {
                applyViewGroupAccessibility(rootView)
            } else {
                applyAccessibilityImprovements(rootView)
            }
            
            // Send accessibility event to announce activity
            if (isAccessibilityEnabled) {
                rootView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Error applying activity accessibility improvements", e)
        }
    }

    /**
     * Accessibility statistics data class
     */
    data class AccessibilityStats(
        val isAccessibilityEnabled: Boolean,
        val isTalkBackEnabled: Boolean,
        val isHighContrastEnabled: Boolean,
        val isLargeTextEnabled: Boolean,
        val fontScale: Float,
        val isDarkMode: Boolean
    )
}