package com.templatefinder.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.templatefinder.R
import com.templatefinder.util.AnalyticsManager
import java.io.Serializable
import com.templatefinder.util.Logger

/**
 * Onboarding activity to guide new users through app features
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OnboardingActivity"
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        
        fun shouldShowOnboarding(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }
        
        fun markOnboardingCompleted(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var finishButton: Button
    
    private lateinit var logger: Logger
    private lateinit var analyticsManager: AnalyticsManager
    
    private val onboardingPages = listOf(
        OnboardingPage(
            title = "Welcome to Template Finder",
            description = "Find coordinates on your screen by creating visual templates. Perfect for automation and accessibility.",
            imageRes = R.drawable.ic_welcome,
            showPermissionInfo = false
        ),
        OnboardingPage(
            title = "Create Templates",
            description = "Take a screenshot and mark a point with a radius to create a search template. The app will find similar areas on future screenshots.",
            imageRes = R.drawable.ic_template_creation,
            showPermissionInfo = false
        ),
        OnboardingPage(
            title = "Automatic Search",
            description = "The app runs in the background, taking periodic screenshots and searching for your template. When found, you'll get the exact coordinates.",
            imageRes = R.drawable.ic_search,
            showPermissionInfo = false
        ),
        OnboardingPage(
            title = "Permissions Required",
            description = "To work properly, the app needs accessibility permissions to take screenshots and overlay permissions for notifications.",
            imageRes = R.drawable.ic_permissions,
            showPermissionInfo = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        
        logger = Logger.getInstance(this)
        analyticsManager = AnalyticsManager.getInstance(this)
        
        initializeViews()
        setupViewPager()
        setupButtons()
        
        analyticsManager.trackEvent("onboarding_started")
        logger.info(TAG, "Onboarding activity started")
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)
        finishButton = findViewById(R.id.finishButton)
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter(this, onboardingPages)
        viewPager.adapter = adapter
        
        // Connect tab layout with view pager
        TabLayoutMediator(tabLayout, viewPager) { _, _ ->
            // Tab layout will show dots automatically
        }.attach()
        
        // Listen for page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtons(position)
                analyticsManager.trackEvent("onboarding_page_viewed", mapOf("page" to position))
            }
        })
    }

    private fun setupButtons() {
        nextButton.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem < onboardingPages.size - 1) {
                viewPager.currentItem = currentItem + 1
                analyticsManager.trackUserAction("next_button_clicked", "onboarding")
            }
        }
        
        skipButton.setOnClickListener {
            analyticsManager.trackUserAction("skip_button_clicked", "onboarding")
            finishOnboarding()
        }
        
        finishButton.setOnClickListener {
            analyticsManager.trackUserAction("finish_button_clicked", "onboarding")
            finishOnboarding()
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == onboardingPages.size - 1
        
        nextButton.visibility = if (isLastPage) View.GONE else View.VISIBLE
        finishButton.visibility = if (isLastPage) View.VISIBLE else View.GONE
        
        // Update button text based on page
        when (position) {
            onboardingPages.size - 1 -> {
                finishButton.text = "Get Started"
            }
        }
    }

    private fun finishOnboarding() {
        markOnboardingCompleted(this)
        analyticsManager.trackEvent("onboarding_completed")
        
        // Start main activity
        val intent = Intent(this, com.templatefinder.MainActivity::class.java)
        startActivity(intent)
        finish()
        
        logger.info(TAG, "Onboarding completed")
    }

    override fun onBackPressed() {
        val currentItem = viewPager.currentItem
        if (currentItem > 0) {
            viewPager.currentItem = currentItem - 1
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Onboarding page data class
     */
    data class OnboardingPage(
        val title: String,
        val description: String,
        val imageRes: Int,
        val showPermissionInfo: Boolean = false
    ) : Serializable

    /**
     * ViewPager adapter for onboarding pages
     */
    private class OnboardingPagerAdapter(
        fragmentActivity: FragmentActivity,
        private val pages: List<OnboardingPage>
    ) : FragmentStateAdapter(fragmentActivity) {

        override fun getItemCount(): Int = pages.size

        override fun createFragment(position: Int): Fragment {
            return OnboardingPageFragment.newInstance(pages[position])
        }
    }

    /**
     * Fragment for individual onboarding page
     */
    class OnboardingPageFragment : Fragment() {

        companion object {
            private const val ARG_PAGE = "page"
            
            fun newInstance(page: OnboardingPage): OnboardingPageFragment {
                val fragment = OnboardingPageFragment()
                val args = Bundle()
                args.putSerializable(ARG_PAGE, page)
                fragment.arguments = args
                return fragment
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            return inflater.inflate(R.layout.fragment_onboarding_page, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            
            val page = arguments?.getSerializable(ARG_PAGE) as? OnboardingPage ?: return
            
            val titleText = view.findViewById<TextView>(R.id.titleText)
            val descriptionText = view.findViewById<TextView>(R.id.descriptionText)
            val imageView = view.findViewById<ImageView>(R.id.imageView)
            val permissionInfoLayout = view.findViewById<ViewGroup>(R.id.permissionInfoLayout)
            
            titleText.text = page.title
            descriptionText.text = page.description
            imageView.setImageResource(page.imageRes)
            
            // Show permission info if needed
            permissionInfoLayout.visibility = if (page.showPermissionInfo) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            // Set up permission info if shown
            if (page.showPermissionInfo) {
                setupPermissionInfo(view)
            }
        }

        private fun setupPermissionInfo(view: View) {
            val accessibilityInfo = view.findViewById<TextView>(R.id.accessibilityPermissionInfo)
            val overlayInfo = view.findViewById<TextView>(R.id.overlayPermissionInfo)
            
            accessibilityInfo.text = "• Accessibility Service: Required to take screenshots automatically"
            overlayInfo.text = "• Overlay Permission: Required to show notifications and results"
        }
    }
}