package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoOverlayTest {
    @Test
    fun stampCoordinatesFollowCropRotationAndMirroring() {
        val crop = Rect(8, 10, 48, 36)
        val topLeft = 9 to 11
        val topRight = 46 to 11
        val bottomLeft = 9 to 34
        val bottomRight = 46 to 34
        val expectedCorners = listOf(
            Triple(0, false, topLeft),
            Triple(90, false, bottomLeft),
            Triple(180, false, bottomRight),
            Triple(270, false, topRight),
            Triple(0, true, topRight),
            Triple(90, true, topLeft),
            Triple(180, true, bottomLeft),
            Triple(270, true, bottomRight),
        )

        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            for ((rotation, mirrored, corner) in expectedCorners) {
                bitmap.eraseColor(Color.TRANSPARENT)
                val canvas = Canvas(bitmap)
                canvas.withVideoFrameCoordinates(crop, rotation, mirrored) { width, height ->
                    val expectedDimensions = if (rotation % 180 == 0) 40 to 26 else 26 to 40
                    assertEquals(expectedDimensions, width to height)
                    drawColor(Color.BLUE)
                    drawRect(0f, 0f, 4f, 4f, Paint().apply { color = Color.RED })
                }
                assertEquals(Color.RED, bitmap.getPixel(corner.first, corner.second))
                assertEquals(Color.BLUE, bitmap.getPixel(crop.centerX(), crop.centerY()))
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(crop.left - 1, crop.top))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
