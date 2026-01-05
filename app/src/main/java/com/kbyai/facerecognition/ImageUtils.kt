package com.kbyai.facerecognition

import android.graphics.Bitmap
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

/**
 * Utility class for image format conversions
 */
object ImageUtils {

    /**
     * Convert Android Image (YUV format) to Bitmap
     */
    @JvmStatic
    fun yuv2Bitmap(image: Image): Bitmap {
        val planes = image.planes
        val ySize = planes[0].buffer.remaining()
        val uvSize = planes[1].buffer.remaining()

        val nv21 = ByteArray(ySize + uvSize)
        planes[0].buffer.get(nv21, 0, ySize)
        planes[1].buffer.get(nv21, ySize, uvSize)

        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)

        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Convert Fotoapparat ByteArray (NV21) to Bitmap
     * Fotoapparat provides raw NV21 bytes along with dimensions
     */
    @JvmStatic
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
