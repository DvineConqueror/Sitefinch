package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.location.Address
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StampCopyTest {
    @Test
    fun capturedAddressUsesSelectedPartsAndRespectsLocationDisclosure() {
        val result = Address(Locale.ENGLISH).apply {
            countryCode = "PH"
            adminArea = "Sample Region"
            subAdminArea = "Sample Province"
            locality = "Sample Town"
            subLocality = "Sample Barangay"
            thoroughfare = "Sample Road"
            subThoroughfare = "8"
        }
        val template = StampTemplate(
            StampPreset.FIELD_NOTE,
            address = "Remote job",
            recordedAddress = result.stampParts(precise = true),
        )
        val fix = LocationFix(16.0, 120.0, 10.0, 0L, "precise")
        val saved = StampTemplate.fromJson(template.toJson())
        assertEquals(template, saved)
        assertEquals("Sample Town, Sample Province", stampDetails("Job", "", 0L, 0, fix, saved).address)
        assertEquals("Remote job", stampDetails("Job", "", 0L, 0, null, saved).address)
        assertEquals("", saved.copy(showAddress = false).displayAddress(includeRecorded = true))
        val streetParts = setOf(AddressPart.BUILDING_NUMBER, AddressPart.STREET, AddressPart.NEIGHBORHOOD)
        assertEquals("8 Sample Road, Sample Barangay", saved.copy(addressParts = streetParts).displayAddress(true))
        assertTrue(result.stampParts(precise = false).keys.none { it in streetParts })
        assertEquals("Remote job", saved.copy(
            addressParts = streetParts,
            recordedAddress = result.stampParts(precise = false),
        ).displayAddress(true))
        val hidden = StampTemplate.fromJson(saved.copy(addressParts = emptySet()).toJson())
        assertEquals("", hidden.displayAddress(includeRecorded = true))
    }

    @Test
    fun plainStampCombinesLargeClockAndIndependentLogoPlacement() {
        val template = StampTemplate(
            StampPreset.COMPLETION,
            layout = StampLayout.PLAIN,
            background = StampBackground.NONE,
            backgroundOpacity = 0.4f,
            largeClock = true,
            typography = StampTypography.SERIF,
            textScale = 1.12f,
            clockScale = 1.24f,
            primaryColorArgb = Color.BLUE,
            accentColorArgb = Color.GREEN,
            textColorArgb = Color.YELLOW,
            logoOffset = StampOffset(0.05f, -0.24f),
            showTitle = false,
        )
        val logo = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val output = Bitmap.createBitmap(640, 880, Bitmap.Config.ARGB_8888)
        val details = stampDetails("Project", "", 1_700_000_000_000L, 0, null, template, logo = logo)
        val canvas = Canvas(output)
        try {
            assertEquals(template, StampTemplate.fromJson(template.toJson()))
            assertTrue(StampTemplate.fromJson("""{"preset":"SITE_VISIT","layout":"CLOCK"}""").usesLargeClock)
            val groups = mutableMapOf<StampGroup, RectF>()
            val collectBounds: (StampGroup, RectF) -> Unit = { group, bounds -> groups[group] = RectF(bounds) }
            drawStamp(
                canvas, output.width, output.height,
                details.copy(template = template.copy(logoOffset = StampOffset())),
                onBounds = collectBounds,
            )
            val initialLogo = groups.getValue(StampGroup.LOGO)
            val initialClock = groups.getValue(StampGroup.CLOCK)
            assertTrue(initialLogo.right < initialClock.left)

            output.eraseColor(Color.TRANSPARENT)
            groups.clear()
            drawStamp(canvas, output.width, output.height, details, onBounds = collectBounds)
            val movedLogo = groups.getValue(StampGroup.LOGO)
            assertTrue(movedLogo.top < initialLogo.top - output.height * 0.15f)
            assertEquals(initialClock, groups.getValue(StampGroup.CLOCK))
            assertEquals(Color.RED, output.getPixel(movedLogo.centerX().toInt(), movedLogo.centerY().toInt()))
            val cornerX = (initialLogo.left - 4).toInt()
            val cornerY = (initialClock.centerY()).toInt()
            assertEquals(Color.TRANSPARENT, output.getPixel(cornerX, cornerY))

            output.eraseColor(Color.TRANSPARENT)
            val paneled = template.copy(background = StampBackground.SOLID, logoOffset = StampOffset())
            drawStamp(canvas, output.width, output.height, details.copy(template = paneled))
            val panelPixel = output.getPixel(cornerX, cornerY)
            assertTrue(Color.alpha(panelPixel) in 95..110)
            assertEquals(255, Color.blue(panelPixel))
        } finally {
            logo.recycle()
            output.recycle()
        }
    }

    @Test
    fun editableFieldsAndMapRenderIndependentTransforms() {
        val template = StampTemplate(
            StampPreset.COMPLETION,
            heading = "Completed",
            organization = "Field crew",
            worker = "Ada",
            employeeNumber = "314",
            layout = StampLayout.PLAIN,
            position = StampPosition.TOP_LEFT,
            showTime = false,
            showMap = true,
            layerEditing = true,
        )
        val map = Bitmap.createBitmap(320, 176, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val output = Bitmap.createBitmap(640, 880, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bounds = mutableMapOf<StampGroup, RectF>()
        val collectBounds: (StampGroup, RectF) -> Unit = { group, rect -> bounds[group] = RectF(rect) }
        val details = stampDetails("Project", "", 0L, 0, null, template, map = map)
        try {
            drawStamp(canvas, output.width, output.height, details, onBounds = collectBounds)
            val initial = bounds.toMap()
            assertTrue(initial.getValue(StampGroup.EMPLOYEE).bottom < initial.getValue(StampGroup.EMPLOYEE_NUMBER).top)
            val edited = template
                .withOffset(StampGroup.TITLE, StampOffset(0.3f, 0f))
                .withLayerStyle(StampGroup.TITLE, StampLayerStyle(colorArgb = Color.RED))
                .withOffset(StampGroup.MAP, StampOffset(0.08f, 0.2f))
                .withLayerStyle(StampGroup.MAP, StampLayerStyle(scale = 1.25f, opacity = 0.5f))
            assertEquals(edited, StampTemplate.fromJson(edited.toJson()))

            output.eraseColor(Color.TRANSPARENT)
            bounds.clear()
            drawStamp(canvas, output.width, output.height, details.copy(template = edited), onBounds = collectBounds)
            assertEquals(initial.getValue(StampGroup.COMPANY), bounds.getValue(StampGroup.COMPANY))
            val titleRect = bounds.getValue(StampGroup.TITLE)
            assertTrue(titleRect.left > initial.getValue(StampGroup.TITLE).left + 120f)
            assertTrue((titleRect.top.toInt() until titleRect.bottom.toInt()).any { y ->
                (titleRect.left.toInt() until titleRect.right.toInt()).any { x ->
                    val pixel = output.getPixel(x, y)
                    Color.red(pixel) > 220 && Color.green(pixel) < 20 && Color.blue(pixel) < 20
                }
            })
            val mapRect = bounds.getValue(StampGroup.MAP)
            assertTrue(mapRect.top > initial.getValue(StampGroup.MAP).top + 150f)
            assertTrue(mapRect.width() > initial.getValue(StampGroup.MAP).width())
            val mapPixel = output.getPixel(mapRect.centerX().toInt(), mapRect.centerY().toInt())
            assertTrue(Color.alpha(mapPixel) in 127..128)
            assertEquals(255, Color.blue(mapPixel))
        } finally {
            output.recycle()
            map.recycle()
        }
    }

    @Test
    fun offlineMapLoadsFromThePackagedAsset() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fix = LocationFix(14.60, 120.98, 18.0, 0, "precise")
        val map = checkNotNull(renderStampMap(context, fix))
        try {
            assertEquals(320, map.width)
            assertEquals(176, map.height)
            assertTrue(map.getPixel(20, 20) != map.getPixel(map.width / 2, map.height / 2))
        } finally {
            map.recycle()
        }
    }

    @Test
    fun pangasinanStreetMapUsesTheRequestedZoom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fix = LocationFix(16.04, 120.34, 20.0, 0, "precise")
        val neighborhood = checkNotNull(renderStampMap(context, fix))
        val closeUp = checkNotNull(renderStampMap(context, fix, zoom = 2f))
        try {
            assertEquals(neighborhood.width, neighborhood.height)
            assertEquals(neighborhood.width, closeUp.width)
            assertTrue(!neighborhood.sameAs(closeUp))
        } finally {
            neighborhood.recycle()
            closeUp.recycle()
        }
    }

    @Test
    fun hiddenStampFieldsPaintNothingAndAVisibleLogoUsesItsShape() {
        val template = StampTemplate(
            StampPreset.COMPLETION,
            heading = "Kept title",
            organization = "Kept company",
            worker = "Kept employee",
            site = "Kept site",
            employeeNumber = "Kept number",
            address = "Kept address",
            tags = "Kept tags",
            businessCardName = "Kept contact",
            styledNote = "Kept styled note",
            weather = "Kept weather",
            showLogo = false,
            showTitle = false,
            showCompany = false,
            showEmployeeName = false,
            showSite = false,
            showEmployeeNumber = false,
            showAddress = false,
            showTags = false,
            showTime = false,
            showNote = false,
            logoShape = StampLogoShape.CIRCLE,
        )
        val logo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val output = Bitmap.createBitmap(320, 420, Bitmap.Config.ARGB_8888)
        val reference = Bitmap.createBitmap(320, 420, Bitmap.Config.ARGB_8888)
        try {
            assertEquals(template, StampTemplate.fromJson(template.toJson()))
            val blank = stampDetails("Project", "Note", 1_700_000_000_000, 0, null, template, logo = logo)
            drawStamp(output, blank)
            assertTrue(output.sameAs(reference))
            drawStamp(output, blank.copy(template = template.copy(showCompany = true)))
            assertFalse(output.sameAs(reference))

            output.eraseColor(Color.TRANSPARENT)
            drawStampLogo(Canvas(output), logo, 1f, StampLogoShape.CIRCLE, RectF(0f, 0f, 100f, 100f), Paint(Paint.ANTI_ALIAS_FLAG))
            assertEquals(Color.TRANSPARENT, output.getPixel(4, 4))
            assertEquals(Color.RED, output.getPixel(50, 50))
        } finally {
            reference.recycle()
            output.recycle()
            logo.recycle()
        }
    }

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
