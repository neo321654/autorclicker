package com.templatefinder.model

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class AppSettingsTest {

    @Test
    fun testDefaultSettings() {
        val settings = AppSettings()
        
        assertEquals(5000L, settings.searchInterval)
        assertEquals(0.8f, settings.matchThreshold, 0.001f)
        assertEquals(50, settings.templateRadius)
        assertFalse(settings.isSearchActive)
        assertTrue(settings.isValid())
    }
    
    @Test
    fun testSettingsValidation() {
        val validSettings = AppSettings(
            searchInterval = 3000L,
            matchThreshold = 0.7f,
            templateRadius = 30,
            isSearchActive = true
        )
        assertTrue(validSettings.isValid())
        
        val invalidSettings1 = AppSettings(searchInterval = 0L)
        assertFalse(invalidSettings1.isValid())
        
        val invalidSettings2 = AppSettings(matchThreshold = 0f)
        assertFalse(invalidSettings2.isValid())
        
        val invalidSettings3 = AppSettings(matchThreshold = 1.5f)
        assertFalse(invalidSettings3.isValid())
        
        val invalidSettings4 = AppSettings(templateRadius = 0)
        assertFalse(invalidSettings4.isValid())
    }
    
    @Test
    fun testUpdateMethods() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putFloat(anyString(), anyFloat())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        
        val settings = AppSettings()
        
        val updatedSettings1 = settings.updateSearchActive(mockContext, true)
        assertTrue(updatedSettings1.isSearchActive)
        
        val updatedSettings2 = settings.updateSearchInterval(mockContext, 10000L)
        assertEquals(10000L, updatedSettings2.searchInterval)
        
        val updatedSettings3 = settings.updateMatchThreshold(mockContext, 0.9f)
        assertEquals(0.9f, updatedSettings3.matchThreshold, 0.001f)
        
        val updatedSettings4 = settings.updateTemplateRadius(mockContext, 75)
        assertEquals(75, updatedSettings4.templateRadius)
    }
}