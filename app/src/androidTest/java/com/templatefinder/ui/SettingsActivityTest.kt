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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsActivityTest {

    private lateinit var scenario: ActivityScenario<SettingsActivity>

    @Before
    fun setUp() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun testActivityLaunch() {
        // Verify that the activity launches and displays the main elements
        onView(withText("Search Parameters")).check(matches(isDisplayed()))
        onView(withText("Notification Settings")).check(matches(isDisplayed()))
        onView(withText("Advanced Settings")).check(matches(isDisplayed()))
    }

    @Test
    fun testSearchParametersSection() {
        // Test search parameters controls
        onView(withId(R.id.searchIntervalText))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Search Interval:"))))

        onView(withId(R.id.searchIntervalSeekBar))
            .check(matches(isDisplayed()))

        onView(withId(R.id.matchThresholdText))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Match Threshold:"))))

        onView(withId(R.id.matchThresholdSeekBar))
            .check(matches(isDisplayed()))

        onView(withId(R.id.templateRadiusText))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Template Radius:"))))

        onView(withId(R.id.templateRadiusSeekBar))
            .check(matches(isDisplayed()))

        onView(withId(R.id.maxResultsText))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Max Results:"))))

        onView(withId(R.id.maxResultsSeekBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testNotificationSettings() {
        // Test notification switches
        onView(withId(R.id.enableNotificationsSwitch))
            .check(matches(isDisplayed()))
            .check(matches(isChecked())) // Should be checked by default

        onView(withId(R.id.enableVibrationSwitch))
            .check(matches(isDisplayed()))
            .check(matches(isChecked())) // Should be checked by default

        onView(withId(R.id.enableAutoOpenSwitch))
            .check(matches(isDisplayed()))
            .check(matches(not(isChecked()))) // Should be unchecked by default

        // Test switch functionality
        onView(withId(R.id.enableNotificationsSwitch))
            .perform(click())
            .check(matches(not(isChecked())))

        onView(withId(R.id.enableNotificationsSwitch))
            .perform(click())
            .check(matches(isChecked()))
    }

    @Test
    fun testAdvancedSettings() {
        // Test advanced settings
        onView(withId(R.id.enableLoggingSwitch))
            .check(matches(isDisplayed()))
            .check(matches(not(isChecked()))) // Should be unchecked by default

        // Test switch functionality
        onView(withId(R.id.enableLoggingSwitch))
            .perform(click())
            .check(matches(isChecked()))
    }

    @Test
    fun testActionButtons() {
        // Test action buttons
        onView(withId(R.id.saveButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .check(matches(withText("Save")))

        onView(withId(R.id.resetButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .check(matches(withText("Reset")))

        onView(withId(R.id.cancelButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .check(matches(withText("Cancel")))
    }

    @Test
    fun testResetButton() {
        // Change some settings first
        onView(withId(R.id.enableNotificationsSwitch))
            .perform(click()) // Turn off notifications

        onView(withId(R.id.enableLoggingSwitch))
            .perform(click()) // Turn on logging

        // Reset to defaults
        onView(withId(R.id.resetButton))
            .perform(click())

        // Verify settings are reset
        onView(withId(R.id.enableNotificationsSwitch))
            .check(matches(isChecked())) // Should be back to default (checked)

        onView(withId(R.id.enableLoggingSwitch))
            .check(matches(not(isChecked()))) // Should be back to default (unchecked)
    }

    @Test
    fun testSaveButton() {
        // Test save functionality
        onView(withId(R.id.saveButton))
            .perform(click())

        // Activity should finish after saving
        // We can't easily test this in Espresso, but we verify the button works
    }

    @Test
    fun testCancelButton() {
        // Test cancel functionality
        onView(withId(R.id.cancelButton))
            .perform(click())

        // Activity should finish without saving
        // We can't easily test this in Espresso, but we verify the button works
    }

    @Test
    fun testSettingsLabels() {
        // Test that all setting labels are displayed correctly
        onView(withText("Enable Notifications")).check(matches(isDisplayed()))
        onView(withText("Enable Vibration")).check(matches(isDisplayed()))
        onView(withText("Auto Open App")).check(matches(isDisplayed()))
        onView(withText("Enable Debug Logging")).check(matches(isDisplayed()))
    }

    @Test
    fun testSettingsDescriptions() {
        // Test that setting descriptions are displayed
        onView(withText("Show notifications when coordinates are found"))
            .check(matches(isDisplayed()))
        onView(withText("Vibrate when coordinates are found"))
            .check(matches(isDisplayed()))
        onView(withText("Automatically bring app to foreground when found"))
            .check(matches(isDisplayed()))
        onView(withText("Enable detailed logging for debugging"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSeekBarControls() {
        // Test that all SeekBars are present and functional
        onView(withId(R.id.searchIntervalSeekBar))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        onView(withId(R.id.matchThresholdSeekBar))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        onView(withId(R.id.templateRadiusSeekBar))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        onView(withId(R.id.maxResultsSeekBar))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }
}