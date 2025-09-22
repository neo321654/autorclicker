package com.templatefinder.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.templatefinder.R
import com.templatefinder.TemplateFinderApplication
import com.templatefinder.util.Logger

/**
 * Help and documentation activity
 */
class HelpActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HelpActivity"
    }

    private lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        
        logger = Logger.getInstance(this)
        
        // Set up action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Help & Documentation"
        }
        
        setupHelpContent()
        
        // Track analytics
        val app = application as TemplateFinderApplication
        app.getAnalyticsManager().trackUserAction("help_opened")
        
        logger.info(TAG, "Help activity opened")
    }

    private fun setupHelpContent() {
        val helpContent = findViewById<TextView>(R.id.helpContent)
        
        val htmlContent = """
            <h2>Getting Started</h2>
            <p>Template Coordinate Finder helps you automatically find specific locations on your screen by creating visual templates.</p>
            
            <h3>1. Set Up Permissions</h3>
            <p>The app requires two main permissions:</p>
            <ul>
                <li><b>Accessibility Service:</b> Allows the app to take screenshots automatically</li>
                <li><b>Overlay Permission:</b> Allows the app to show notifications and results over other apps</li>
            </ul>
            <p>Go to Settings → Permissions to set these up.</p>
            
            <h3>2. Create a Template</h3>
            <p>To create a search template:</p>
            <ol>
                <li>Tap "Create Template" on the main screen</li>
                <li>The app will take a screenshot of your current screen</li>
                <li>Tap on the point you want to find in future screenshots</li>
                <li>Adjust the radius to define the search area around that point</li>
                <li>Save the template</li>
            </ol>
            
            <h3>3. Start Searching</h3>
            <p>Once you have a template:</p>
            <ol>
                <li>Tap "Start Search" to begin automatic searching</li>
                <li>The app will run in the background, taking periodic screenshots</li>
                <li>When your template is found, you'll get a notification with the coordinates</li>
                <li>The app can also automatically open to show you the results</li>
            </ol>
            
            <h3>4. Manage Settings</h3>
            <p>In the Settings screen, you can adjust:</p>
            <ul>
                <li><b>Search Interval:</b> How often to take screenshots (affects battery usage)</li>
                <li><b>Match Threshold:</b> How closely the template must match (higher = more strict)</li>
                <li><b>Template Radius:</b> Size of the search area around your selected point</li>
            </ul>
            
            <h2>Tips for Best Results</h2>
            <ul>
                <li>Choose distinctive visual elements for your templates (buttons, icons, text)</li>
                <li>Avoid areas that change frequently (like clocks or dynamic content)</li>
                <li>Use a smaller radius for precise matching, larger for more flexible matching</li>
                <li>Test your template by starting a search and checking if it finds the right location</li>
            </ul>
            
            <h2>Battery Optimization</h2>
            <p>The app automatically optimizes battery usage:</p>
            <ul>
                <li>Search frequency is reduced when battery is low</li>
                <li>Power save mode triggers additional optimizations</li>
                <li>You can adjust optimization levels in Settings</li>
            </ul>
            
            <h2>Accessibility</h2>
            <p>This app is designed to be accessible:</p>
            <ul>
                <li>All buttons and controls work with screen readers</li>
                <li>Text size adapts to your system font size settings</li>
                <li>High contrast mode is supported</li>
                <li>Touch targets meet accessibility guidelines</li>
            </ul>
            
            <h2>Troubleshooting</h2>
            <h3>Template Not Found</h3>
            <ul>
                <li>Check that the visual element still looks the same</li>
                <li>Try lowering the match threshold in Settings</li>
                <li>Create a new template if the screen layout has changed</li>
            </ul>
            
            <h3>App Not Taking Screenshots</h3>
            <ul>
                <li>Ensure Accessibility Service is enabled in Android Settings</li>
                <li>Restart the app after enabling permissions</li>
                <li>Check that the service is running in the notification area</li>
            </ul>
            
            <h3>High Battery Usage</h3>
            <ul>
                <li>Increase the search interval in Settings</li>
                <li>Enable aggressive battery optimization</li>
                <li>Pause search when not needed</li>
            </ul>
            
            <h2>Privacy & Security</h2>
            <p>Your privacy is important:</p>
            <ul>
                <li>Screenshots are processed locally on your device</li>
                <li>No image data is sent to external servers</li>
                <li>Templates are stored securely in the app's private storage</li>
                <li>Analytics data is anonymous and optional</li>
            </ul>
            
            <h2>Support</h2>
            <p>If you need additional help:</p>
            <ul>
                <li>Check the app settings for diagnostic information</li>
                <li>Review the error logs in Settings → Advanced</li>
                <li>Restart the app if you encounter issues</li>
            </ul>
        """.trimIndent()
        
        helpContent.text = HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}