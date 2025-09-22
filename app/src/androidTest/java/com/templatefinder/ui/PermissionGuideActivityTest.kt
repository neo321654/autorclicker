package com.templatefinder.ui

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.templatefinder.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionGuideActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(PermissionGuideActivity::class.java)

    @Test
    fun testPermissionGuideUIElements() {
        // Check that all main UI elements are displayed
        onView(withText("Setup Permissions")).check(matches(isDisplayed()))
        onView(withText("Accessibility Service")).check(matches(isDisplayed()))
        onView(withText("Display over other apps")).check(matches(isDisplayed()))
        onView(withText("Notifications")).check(matches(isDisplayed()))
        
        // Check buttons are present
        onView(withId(R.id.btnEnableAccessibility)).check(matches(isDisplayed()))
        onView(withId(R.id.btnEnableOverlay)).check(matches(isDisplayed()))
        onView(withId(R.id.btnEnableNotifications)).check(matches(isDisplayed()))
        onView(withId(R.id.btnContinue)).check(matches(isDisplayed()))
        onView(withId(R.id.btnSkip)).check(matches(isDisplayed()))
    }

    @Test
    fun testContinueButtonInitiallyDisabled() {
        // Continue button should be disabled initially (assuming permissions not granted)
        onView(withId(R.id.btnContinue)).check(matches(not(isEnabled())))
    }

    @Test
    fun testSkipButtonClickable() {
        // Skip button should be clickable
        onView(withId(R.id.btnSkip)).check(matches(isEnabled()))
        onView(withId(R.id.btnSkip)).perform(click())
    }

    @Test
    fun testPermissionButtonsClickable() {
        // All permission buttons should be clickable
        onView(withId(R.id.btnEnableAccessibility)).check(matches(isEnabled()))
        onView(withId(R.id.btnEnableOverlay)).check(matches(isEnabled()))
        onView(withId(R.id.btnEnableNotifications)).check(matches(isEnabled()))
    }
}