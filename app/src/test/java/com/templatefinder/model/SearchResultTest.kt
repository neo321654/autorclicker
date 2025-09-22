package com.templatefinder.model

import android.graphics.Point
import org.junit.Test
import org.junit.Assert.*

class SearchResultTest {

    @Test
    fun testSuccessfulResult() {
        val result = SearchResult.success(100, 200, 0.9f)
        
        assertTrue(result.found)
        assertNotNull(result.coordinates)
        assertEquals(100, result.coordinates?.x)
        assertEquals(200, result.coordinates?.y)
        assertEquals(0.9f, result.confidence, 0.001f)
        assertTrue(result.isValid())
    }
    
    @Test
    fun testFailedResult() {
        val result = SearchResult.failure()
        
        assertFalse(result.found)
        assertNull(result.coordinates)
        assertEquals(0f, result.confidence, 0.001f)
        assertFalse(result.isValid())
    }
    
    @Test
    fun testFormattedCoordinates() {
        val successResult = SearchResult.success(150, 250, 0.85f)
        assertEquals("X: 150, Y: 250", successResult.getFormattedCoordinates())
        
        val failResult = SearchResult.failure()
        assertEquals("No coordinates found", failResult.getFormattedCoordinates())
    }
    
    @Test
    fun testConfidencePercentage() {
        val result = SearchResult.success(100, 100, 0.75f)
        assertEquals(75, result.getConfidencePercentage())
    }
    
    @Test
    fun testIsValid() {
        val validResult = SearchResult(true, Point(100, 100), 0.8f)
        assertTrue(validResult.isValid())
        
        val invalidResult1 = SearchResult(false, Point(100, 100), 0.8f)
        assertFalse(invalidResult1.isValid())
        
        val invalidResult2 = SearchResult(true, null, 0.8f)
        assertFalse(invalidResult2.isValid())
        
        val invalidResult3 = SearchResult(true, Point(100, 100), 0f)
        assertFalse(invalidResult3.isValid())
    }
}