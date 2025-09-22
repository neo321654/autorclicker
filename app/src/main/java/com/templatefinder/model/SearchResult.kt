package com.templatefinder.model

import android.graphics.Point

data class SearchResult(
    val found: Boolean,
    val coordinates: Point? = null,
    val confidence: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    
    /**
     * Returns formatted coordinates string
     */
    fun getFormattedCoordinates(): String {
        return if (found && coordinates != null) {
            "X: ${coordinates.x}, Y: ${coordinates.y}"
        } else {
            "No coordinates found"
        }
    }
    
    /**
     * Returns confidence as percentage
     */
    fun getConfidencePercentage(): Int {
        return (confidence * 100).toInt()
    }
    
    /**
     * Checks if result is valid (found with valid coordinates)
     */
    fun isValid(): Boolean {
        return found && coordinates != null && confidence > 0f
    }
    
    companion object {
        /**
         * Creates a successful search result
         */
        fun success(x: Int, y: Int, confidence: Float): SearchResult {
            return SearchResult(
                found = true,
                coordinates = Point(x, y),
                confidence = confidence
            )
        }
        
        /**
         * Creates a failed search result
         */
        fun failure(): SearchResult {
            return SearchResult(
                found = false,
                coordinates = null,
                confidence = 0f
            )
        }
    }
}