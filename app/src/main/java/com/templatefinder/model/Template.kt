package com.templatefinder.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class Template(
    val centerX: Int,
    val centerY: Int,
    val radius: Int,
    val templateBitmap: Bitmap,
    val matchThreshold: Float = 0.8f,
    val createdAt: Long = System.currentTimeMillis()
) {
    
    /**
     * Saves template to file system
     */
    fun saveToFile(file: File): Boolean {
        return try {
            FileOutputStream(file).use { fos ->
                // Write metadata
                fos.write(centerX.toString().toByteArray())
                fos.write("\n".toByteArray())
                fos.write(centerY.toString().toByteArray())
                fos.write("\n".toByteArray())
                fos.write(radius.toString().toByteArray())
                fos.write("\n".toByteArray())
                fos.write(matchThreshold.toString().toByteArray())
                fos.write("\n".toByteArray())
                fos.write(createdAt.toString().toByteArray())
                fos.write("\n".toByteArray())
                
                // Write bitmap
                val stream = ByteArrayOutputStream()
                templateBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bitmapData = stream.toByteArray()
                fos.write(bitmapData.size.toString().toByteArray())
                fos.write("\n".toByteArray())
                fos.write(bitmapData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    companion object {
        /**
         * Loads template from file system
         */
        fun loadFromFile(file: File): Template? {
            return try {
                FileInputStream(file).use { fis ->
                    val lines = fis.bufferedReader().readLines()
                    
                    val centerX = lines[0].toInt()
                    val centerY = lines[1].toInt()
                    val radius = lines[2].toInt()
                    val matchThreshold = lines[3].toFloat()
                    val createdAt = lines[4].toLong()
                    val bitmapSize = lines[5].toInt()
                    
                    // Read bitmap data
                    fis.close()
                    val allBytes = file.readBytes()
                    val metadataSize = lines.take(6).joinToString("\n").toByteArray().size + 1
                    val bitmapData = allBytes.sliceArray(metadataSize until metadataSize + bitmapSize)
                    val bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size)
                    
                    Template(centerX, centerY, radius, bitmap, matchThreshold, createdAt)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        /**
         * Creates template from bitmap region
         */
        fun createFromRegion(
            sourceBitmap: Bitmap,
            centerX: Int,
            centerY: Int,
            radius: Int,
            matchThreshold: Float = 0.8f
        ): Template? {
            return try {
                val left = maxOf(0, centerX - radius)
                val top = maxOf(0, centerY - radius)
                val right = minOf(sourceBitmap.width, centerX + radius)
                val bottom = minOf(sourceBitmap.height, centerY + radius)
                
                val templateBitmap = Bitmap.createBitmap(
                    sourceBitmap,
                    left,
                    top,
                    right - left,
                    bottom - top
                )
                
                Template(centerX, centerY, radius, templateBitmap, matchThreshold)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}