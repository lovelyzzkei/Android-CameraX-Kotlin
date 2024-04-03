package com.example.monocon

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object BitmapUtils {
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }
}