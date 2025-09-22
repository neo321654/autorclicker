package com.templatefinder

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun testActivityLaunch() {
        // Verify that the activity launches and displays the main elements
        onView(withText("Template Coordinate Finder")).check(matches(isDisplayed()))
        onView(withId(R.id.createTemplateButton)).check(matches(isDisplayed()))
        onView(withId(R.id.startSearchButton)).check(matches(isDisplayed()))
        onView(withId(R.id.stopSearchButton)).check(matches(isDisplayed()))
        onView(withId(R.id.settingsButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testInitialState() {
        // Verify initial state of UI elements
        onView(withId(R.id.permissionStatusText))
            .check(matches(isDisplayed()))

        onView(withId(R.id.templateStatusText))
            .check(matches(isDisplayed()))

        onView(withId(R.id.searchStatusText))
            .check(matches(isDisplayed()))

        onView(withId(R.id.startSearchButton))
            .check(matches(isDisplayed()))

        onView(withId(R.id.stopSearchButton))
            .check(matches(isDisplayed()))
            .check(matches(not(isEnabled()))) // Should be disabled initially

        onView(withId(R.id.noResultsText))
            .check(matches(isDisplayed()))
            .check(matches(withText("No coordinates found yet")))
    }

    @Test
    fun testPermissionSetupButton() {
        // Test that permission setup button is functional
        onView(withId(R.id.permissionSetupButton))
            .check(matches(isDisplayed()))
            .perform(click())

        // Should launch PermissionGuideActivity
        // We can't easily test the activity transition in Espresso,
        // but we verify the button responds to clicks
    }

    @Test
    fun testCreateTemplateButton() {
        // Test that create template button is functional
        onView(withId(R.id.createTemplateButton))
            .check(matches(isDisplayed()))
            .perform(click())

        // Should attempt to launch TemplateCreationActivity
        // Without proper permissions, this might show an error
    }

    @Test
    fun testSearchControlButtons() {
        // Test search control buttons
        onView(withId(R.id.startSearchButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("Start Search")))

        onView(withId(R.id.stopSearchButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("Stop Search")))
            .check(matches(not(isEnabled()))) // Initially disabled

        // Try clicking start search (will likely show error without template)
        onView(withId(R.id.startSearchButton))
            .perform(click())
    }

    @Test
    fun testSettingsButton() {
        // Test settings button
        onView(withId(R.id.settingsButton))
            .check(matches(isDisplayed()))
            .check(matches(withText("Settings")))
            .perform(click())

        // Should show "Settings coming soon" toast
    }

    @Test
    fun testStatusCards() {
        // Test that all status cards are displayed
        onView(withId(R.id.permissionStatusIcon))
            .check(matches(isDisplayed()))

        onView(withId(R.id.templateStatusIcon))
            .check(matches(isDisplayed()))

        // Verify card content
        onView(withText("Permissions"))
            .check(matches(isDisplayed()))

        onView(withText("Template"))
            .check(matches(isDisplayed()))

        onView(withText("Search Control"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testResultsCardInitiallyHidden() {
        // Results card should be hidden initially
        onView(withId(R.id.resultsCard))
            .check(matches(not(isDisplayed())))

        // No results message should be visible
        onView(withId(R.id.noResultsText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testUILayout() {
        // Test that all major UI components are present and properly laid out
        onView(withId(R.id.permissionStatusText)).check(matches(isDisplayed()))
        onView(withId(R.id.templateStatusText)).check(matches(isDisplayed()))
        onView(withId(R.id.searchStatusText)).check(matches(isDisplayed()))
        onView(withId(R.id.createTemplateButton)).check(matches(isDisplayed()))
        onView(withId(R.id.startSearchButton)).check(matches(isDisplayed()))
        onView(withId(R.id.stopSearchButton)).check(matches(isDisplayed()))
        onView(withId(R.id.settingsButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testButtonStates() {
        // Test initial button states
        onView(withId(R.id.createTemplateButton))
            .check(matches(isDisplayed()))

        onView(withId(R.id.startSearchButton))
            .check(matches(isDisplayed()))

        onView(withId(R.id.stopSearchButton))
            .check(matches(not(isEnabled())))

        onView(withId(R.id.settingsButton))
            .check(matches(isEnabled()))

        onView(withId(R.id.permissionSetupButton))
            .check(matches(isDisplayed()))
    }
}