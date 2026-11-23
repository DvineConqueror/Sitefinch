package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StampPaletteTest {
    @Test
    fun suggestsActualColorsAndSkipsTransparentPixels() {
        val forest = Color.rgb(31, 70, 55)
        val neighboringForest = Color.rgb(34, 72, 56)
        val gold = Color.rgb(236, 163, 56)
        val transparentBlue = Color.argb(32, 0, 0, 255)
        val logo = Bitmap.createBitmap(384, 256, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(logo)
            val paint = Paint()
            fun band(left: Float, right: Float, color: Int) {
                paint.color = color
                canvas.drawRect(left, 0f, right, logo.height.toFloat(), paint)
            }
            band(0f, 144f, forest)
            band(144f, 218f, neighboringForest)
            band(218f, 300f, gold)
            band(300f, 350f, transparentBlue)
            assertEquals(listOf(forest, gold), sampleLogoColors(logo))
            assertEquals(forest, logo.getPixel(0, 0))
        } finally {
            logo.recycle()
        }
    }

    @Test
    fun returnsOneSwatchForAPartiallyTransparentMonochromeLogo() {
        val logo = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            logo.setPixel(4, 4, Color.argb(160, 0, 0, 0))
            assertEquals(listOf(Color.BLACK), sampleLogoColors(logo))
            logo.eraseColor(Color.TRANSPARENT)
            assertEquals(emptyList<Int>(), sampleLogoColors(logo))
        } finally {
            logo.recycle()
        }
    }
}
