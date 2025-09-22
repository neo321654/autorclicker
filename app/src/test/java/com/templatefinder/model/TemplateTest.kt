package com.templatefinder.model

import android.graphics.Bitmap
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import java.io.File

class TemplateTest {

    @Test
    fun testTemplateCreation() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(100)
        `when`(mockBitmap.height).thenReturn(100)
        
        val template = Template(
            centerX = 50,
            centerY = 50,
            radius = 25,
            templateBitmap = mockBitmap,
            matchThreshold = 0.8f
        )
        
        assertEquals(50, template.centerX)
        assertEquals(50, template.centerY)
        assertEquals(25, template.radius)
        assertEquals(0.8f, template.matchThreshold, 0.001f)
        assertNotNull(template.templateBitmap)
        assertTrue(template.createdAt > 0)
    }
    
    @Test
    fun testCreateFromRegion() {
        val mockSourceBitmap = mock(Bitmap::class.java)
        `when`(mockSourceBitmap.width).thenReturn(200)
        `when`(mockSourceBitmap.height).thenReturn(200)
        
        val mockTemplateBitmap = mock(Bitmap::class.java)
        mockStatic(Bitmap::class.java).use { mockedBitmap ->
            mockedBitmap.`when`<Bitmap> {
                Bitmap.createBitmap(mockSourceBitmap, 25, 25, 50, 50)
            }.thenReturn(mockTemplateBitmap)
            
            val template = Template.createFromRegion(
                sourceBitmap = mockSourceBitmap,
                centerX = 50,
                centerY = 50,
                radius = 25
            )
            
            assertNotNull(template)
            assertEquals(50, template?.centerX)
            assertEquals(50, template?.centerY)
            assertEquals(25, template?.radius)
        }
    }
    
    @Test
    fun testCreateFromRegionBoundaryCheck() {
        val mockSourceBitmap = mock(Bitmap::class.java)
        `when`(mockSourceBitmap.width).thenReturn(100)
        `when`(mockSourceBitmap.height).thenReturn(100)
        
        val mockTemplateBitmap = mock(Bitmap::class.java)
        mockStatic(Bitmap::class.java).use { mockedBitmap ->
            mockedBitmap.`when`<Bitmap> {
                Bitmap.createBitmap(mockSourceBitmap, 0, 0, 50, 50)
            }.thenReturn(mockTemplateBitmap)
            
            // Test boundary case - near edge
            val template = Template.createFromRegion(
                sourceBitmap = mockSourceBitmap,
                centerX = 10,
                centerY = 10,
                radius = 25
            )
            
            assertNotNull(template)
        }
    }
}