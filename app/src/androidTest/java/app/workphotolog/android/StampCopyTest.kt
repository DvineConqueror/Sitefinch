package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StampCopyTest {
    @Test
    fun importedLogoRoundTripsAndOnlyTheCopyHasStampAndMarkup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val original = File.createTempFile("fixture-", ".jpg", directory)
        val source = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888)
        var derivative: File? = null
        try {
            source.eraseColor(Color.rgb(74, 124, 92))
            original.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 96, it)) }
            val bytes = original.readBytes()
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", original)
            val store = StampLogoStore(context)
            val id = store.importLogo(uri)
            val template = StampTemplate(
                StampPreset.SITE_VISIT,
                color = StampColor.JADE,
                position = StampPosition.TOP_RIGHT,
                layout = StampLayout.CLOCK,
                address = "41 Sample Avenue",
                logoSide = StampLogoSide.RIGHT,
                logoId = id,
                logoZoom = 1.5f,
            )
            assertEquals(template, StampTemplate.fromJson(template.toJson()))
            val logo = store.loadLogo(id)
            assertNotNull(logo)
            logo?.recycle()
            val stamp = stampDetails("Synthetic site", "", 1_700_000_000_000L, 0, null, template)
            assertNotNull(stamp.clockTime)
            derivative = createStampedCopy(context, original, stamp) { bitmap ->
                drawPhotoMarks(
                    Canvas(bitmap), bitmap.width, bitmap.height,
                    listOf(PhotoMark(MarkTool.BOX, listOf(Offset(.2f, .6f), Offset(.7f, .9f)))),
                )
            }
            val copy = decodeJpeg(derivative, 560)
            try {
                assertEquals(400, copy.width)
                assertEquals(560, copy.height)
                assertTrue(Color.red(copy.getPixel(140, 336)) > 150)
                assertTrue(Color.red(copy.getPixel(359, 20)) < 70)
            } finally {
                copy.recycle()
            }
            assertArrayEquals(bytes, original.readBytes())
        } finally {
            source.recycle()
            derivative?.delete()
            original.delete()
        }
    }
}
