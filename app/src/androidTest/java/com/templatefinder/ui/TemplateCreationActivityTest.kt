package com.templatefinder.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.templatefinder.R
import com.templatefinder.manager.TemplateManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TemplateCreationActivityTest {

    private lateinit var scenario: ActivityScenario<TemplateCreationActivity>

    @Before
    fun setUp() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), TemplateCreationActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun testActivityLaunch() {
        // Verify that the activity launches and displays the main elements
        onView(withText("Create Template")).check(matches(isDisplayed()))
        onView(withId(R.id.captureScreenshotButton)).check(matches(isDisplayed()))
        onView(withId(R.id.cancelButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testInitialState() {
        // Verify initial state of UI elements
        onView(withId(R.id.captureScreenshotButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        onView(withId(R.id.saveTemplateButton))
            .check(matches(isDisplayed()))
            .check(matches(not(isEnabled()))) // Should be disabled initially

        onView(withId(R.id.controlsLayout))
            .check(matches(not(isDisplayed()))) // Should be hidden initially

        onView(withId(R.id.instructionText))
            .check(matches(withText("Tap 'Capture Screenshot' to begin")))
    }

    @Test
    fun testCaptureScreenshotButton() {
        // Test that clicking capture screenshot button changes the UI state
        onView(withId(R.id.captureScreenshotButton))
            .perform(click())

        // Note: In a real test environment, this would require accessibility service
        // to be enabled, so we just test that the button responds to clicks
        onView(withId(R.id.captureScreenshotButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCancelButton() {
        // Test that cancel button is functional
        onView(withId(R.id.cancelButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .perform(click())

        // Activity should finish when cancel is clicked
        // We can't easily test this in Espresso, but we verify the button works
    }

    @Test
    fun testSeekBarControls() {
        // The SeekBars should be present but initially disabled
        onView(withId(R.id.radiusSeekBar))
            .check(matches(isDisplayed()))

        onView(withId(R.id.thresholdSeekBar))
            .check(matches(isDisplayed()))

        // Text views should show initial values
        onView(withId(R.id.radiusText))
            .check(matches(withText("Radius: 50px")))

        onView(withId(R.id.thresholdText))
            .check(matches(withText("Threshold: 80%")))
    }

    @Test
    fun testImageView() {
        // Verify ImageView is present and properly configured
        onView(withId(R.id.screenshotImageView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testInstructionTextUpdates() {
        // Verify instruction text is displayed
        onView(withId(R.id.instructionText))
            .check(matches(isDisplayed()))
            .check(matches(withText("Tap 'Capture Screenshot' to begin")))
    }

    @Test
    fun testLayoutStructure() {
        // Test that all major layout components are present
        onView(withId(R.id.screenshotImageView)).check(matches(isDisplayed()))
        onView(withId(R.id.controlsLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.captureScreenshotButton)).check(matches(isDisplayed()))
        onView(withId(R.id.saveTemplateButton)).check(matches(isDisplayed()))
        onView(withId(R.id.cancelButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testButtonStates() {
        // Test initial button states
        onView(withId(R.id.captureScreenshotButton))
            .check(matches(isEnabled()))
            .check(matches(withText("Capture Screenshot")))

        onView(withId(R.id.saveTemplateButton))
            .check(matches(not(isEnabled())))
            .check(matches(withText("Save Template")))

        onView(withId(R.id.cancelButton))
            .check(matches(isEnabled()))
            .check(matches(withText("Cancel")))
    }

    @Test
    fun testTemplateManagerIntegration() {
        // Test that TemplateManager is properly integrated
        scenario.onActivity { activity ->
            // Verify that the activity can access TemplateManager functionality
            // This is an indirect test since TemplateManager is private
            assertNotNull("Activity should be created successfully", activity)
        }
    }

    @Test
    fun testScreenshotCaptureFlow() {
        // Test the complete screenshot capture flow
        onView(withId(R.id.captureScreenshotButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // Click capture button
        onView(withId(R.id.captureScreenshotButton))
            .perform(click())

        // Note: Without accessibility service enabled, this will show an error
        // but we can test that the UI responds appropriately
        onView(withId(R.id.captureScreenshotButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testTemplateCreationWorkflow() {
        // Test the complete workflow from start to finish
        
        // 1. Initial state
        onView(withId(R.id.instructionText))
            .check(matches(withText("Tap 'Capture Screenshot' to begin")))
        
        onView(withId(R.id.saveTemplateButton))
            .check(matches(not(isEnabled())))
        
        // 2. Try to capture screenshot
        onView(withId(R.id.captureScreenshotButton))
            .perform(click())
        
        // 3. Verify UI elements are still functional
        onView(withId(R.id.cancelButton))
            .check(matches(isEnabled()))
    }
}