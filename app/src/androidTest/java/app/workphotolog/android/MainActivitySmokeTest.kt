package app.workphotolog.android

import android.Manifest
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.CAMERA)).around(compose)

    @Test
    fun cameraProjectsAndSavedOriginalOpen() {
        val name = "North roof " + SystemClock.elapsedRealtime()
        compose.onNodeWithText("Sitefinch").assertIsDisplayed()
        compose.onNodeWithTag("shutter").assertIsDisplayed()
        compose.onNodeWithTag("projectPicker").assertIsDisplayed()
        waitForShutter()
        captureScreenshot("smoke-camera.png")

        compose.onNodeWithTag("projectPicker").performClick()
        compose.onNodeWithText("Projects").assertIsDisplayed()
        captureScreenshot("smoke-projects.png")
        createProject(name)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PhotoRepository(context)
        runBlocking {
            val project = withTimeout(10_000) { repository.projects.first { rows -> rows.any { it.name == name } } }
                .first { it.name == name }
            val fixture = repository.newCaptureFile()
            val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.rgb(96, 114, 79))
                fixture.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            } finally {
                bitmap.recycle()
            }
            repository.saveCapture(
                CaptureDraft(fixture, project.id, System.currentTimeMillis(), 0, "Inspected roof flashing", null),
            )
        }

        compose.onNodeWithText("Album").performClick()
        waitForText("Inspected roof flashing")
        compose.onNodeWithText("Inspected roof flashing").performClick()
        compose.onNodeWithText("Photo details").assertIsDisplayed()
        captureScreenshot("smoke-photo.png")
        compose.onNodeWithText("Check original file").performScrollTo().performClick()
        waitForText("Original matches its saved SHA-256 hash.")
        compose.onNodeWithText("Share original").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cameraCapturesAndSavesAnOriginal() {
        val name = "Camera capture " + SystemClock.elapsedRealtime()
        compose.onNodeWithTag("projectPicker").performClick()
        createProject(name)
        compose.onNodeWithTag("templates").performClick()
        compose.onNodeWithTag("templateClean").performScrollTo().performClick()
        compose.onNodeWithTag("applyTemplate").performClick()
        waitForShutter()
        compose.onNodeWithTag("shutter").performClick()
        waitForText("Clean photo saved to Gallery", 25_000)
        val preview = hasContentDescription("Clean photo preview")
        compose.waitUntil(10_000) { compose.onAllNodes(preview).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shareQuickPhoto").assertIsDisplayed()
        captureScreenshot("smoke-quick-photo.png")
        compose.onNodeWithText("Photo details").performClick()
        waitForText("Photo details")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PhotoRepository(context)
        runBlocking {
            val project = withTimeout(10_000) { repository.projects.first { rows -> rows.any { it.name == name } } }
                .first { it.name == name }
            val photo = withTimeout(10_000) { repository.photos(project.id).first { it.isNotEmpty() } }.single()
            val jpeg = repository.originalFile(photo)
            assertTrue(jpeg.length() > 2)
            assertTrue(repository.checkOriginal(photo))
            val gallery = GalleryPhotos(context)
            val galleryUri = withTimeout(10_000) {
                var item = gallery.find(photo)
                while (item == null) {
                    delay(50)
                    item = gallery.find(photo)
                }
                item
            }
            try {
                val galleryBytes = checkNotNull(context.contentResolver.openInputStream(galleryUri)).use { it.readBytes() }
                assertArrayEquals(jpeg.readBytes(), galleryBytes)
                assertEquals(galleryUri, gallery.publish(photo, jpeg))
            } finally {
                context.contentResolver.delete(galleryUri, null, null)
            }
            assertNull(photo.location)
            assertNull(ExifInterface(jpeg).latLong)
            val bitmap = decodeJpeg(jpeg, 320)
            try {
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun shutterSavesSelectedStampToGalleryAndOffersImmediateSharing() {
        val name = "Stamped Gallery " + SystemClock.elapsedRealtime()
        compose.onNodeWithTag("projectPicker").performClick()
        createProject(name)
        compose.onNodeWithTag("templates").performClick()
        compose.onNodeWithTag("templateClock").performScrollTo().performClick()
        compose.onNodeWithTag("applyTemplate").performClick()
        try {
            waitForShutter()
            compose.onNodeWithTag("shutter").performClick()
            waitForText("Stamped copy saved to Gallery", 25_000)
            val preview = hasContentDescription("Stamped photo preview")
            compose.waitUntil(10_000) { compose.onAllNodes(preview).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("shareQuickPhoto").assertIsDisplayed()
            captureScreenshot("smoke-quick-stamped.png")

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repository = PhotoRepository(context)
            runBlocking {
                val project = withTimeout(10_000) { repository.projects.first { it.any { row -> row.name == name } } }
                    .single { it.name == name }
                val photo = withTimeout(10_000) { repository.photos(project.id).first { it.isNotEmpty() } }.single()
                val original = repository.originalFile(photo)
                val template = StampTemplate.fromJson(photo.stampJson)
                assertEquals(StampLayout.CLOCK, template.layout)
                val gallery = GalleryPhotos(context)
                val uri = checkNotNull(gallery.find(photo, stamped = true))
                val expected = createStampedCopy(context, original, stampDetails(
                    name, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes, photo.location, template, photoCode = photo.id,
                ))
                try {
                    val galleryBytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                    assertArrayEquals(expected.readBytes(), galleryBytes)
                    assertTrue(!original.readBytes().contentEquals(galleryBytes))
                    assertNull(gallery.find(photo))
                    assertTrue(repository.checkOriginal(photo))
                } finally {
                    expected.delete()
                    context.contentResolver.delete(uri, null, null)
                }
            }
        } finally {
            val dismissPreview = hasContentDescription("Dismiss photo preview")
            if (compose.onAllNodes(dismissPreview).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNode(dismissPreview).performClick()
                compose.waitForIdle()
            }
            val templates = hasTestTag("templates") and isEnabled()
            if (compose.onAllNodes(templates).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("templates").performClick()
                compose.onNodeWithTag("templateClean").performScrollTo().performClick()
                compose.onNodeWithTag("applyTemplate").performClick()
            }
        }
    }

    @Test
    fun cameraControlsStayAccessibleWithLargeTextDarkThemeAndReducedMotion() {
        val fontScale = shell("settings get system font_scale")
        val animationScale = shell("settings get global animator_duration_scale")
        val nightMode = shell("cmd uimode night").substringAfterLast(' ').trim()
        check(nightMode in listOf("yes", "no", "auto", "custom"))

        try {
            shell("settings put system font_scale 1.5")
            shell("settings put global animator_duration_scale 0")
            shell("cmd uimode night yes")
            compose.waitUntil(15_000) {
                val configuration = compose.activity.resources.configuration
                configuration.fontScale >= 1.5f &&
                    (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            assertTrue(shell("settings get global animator_duration_scale").toFloat() == 0f)
            waitForShutter()
            compose.onNodeWithTag("projectPicker").assertIsDisplayed()
            compose.onNodeWithText("Album").assertIsDisplayed()
            compose.onNodeWithText("Location off").assertIsDisplayed()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            compose.waitUntil(5_000) { automation.rootInActiveWindow.hasVisibleLabel("Take photo") }
            captureScreenshot("smoke-camera-accessibility.png")
        } finally {
            restoreSetting("system font_scale", fontScale)
            restoreSetting("global animator_duration_scale", animationScale)
            shell("cmd uimode night $nightMode")
        }
    }

    private fun AccessibilityNodeInfo?.hasVisibleLabel(label: String): Boolean {
        if (this == null) return false
        if (isVisibleToUser && contentDescription?.toString() == label) return true
        return (0 until childCount).any { getChild(it).hasVisibleLabel(label) }
    }

    private fun restoreSetting(key: String, value: String) {
        if (value == "null") shell("settings delete $key") else shell("settings put $key $value")
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
    }

    @Test
    fun selectsATemplateAndRecordsAPlayableClip() {
        val name = "Video sample " + SystemClock.elapsedRealtime()
        compose.onNodeWithTag("projectPicker").performClick()
        createProject(name)
        compose.onNodeWithTag("templates").performClick()
        compose.onNodeWithTag("templateSiteVisit").performScrollTo().performClick()
        compose.onNodeWithTag("applyTemplate").performClick()
        try {
            compose.onNodeWithTag("videoMode").performClick()
            waitForShutter()
            compose.onNodeWithTag("shutter").performClick()
            waitForText("00:01", 25_000)
            compose.onNodeWithTag("shutter").performClick()
            waitForText("Review clip", 25_000)
            compose.onNodeWithTag("saveOriginal").assertIsDisplayed().performClick()
            waitForText("Clip details")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repository = PhotoRepository(context)
            runBlocking {
                val project = withTimeout(10_000) { repository.projects.first { rows -> rows.any { it.name == name } } }
                    .first { it.name == name }
                val clip = withTimeout(10_000) { repository.photos(project.id).first { it.isNotEmpty() } }.single()
                assertTrue(clip.isVideo)
                assertTrue(StampTemplate.fromJson(clip.stampJson).preset == StampPreset.SITE_VISIT)
                assertTrue(repository.checkOriginal(clip))
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(repository.originalFile(clip).absolutePath)
                    val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    assertTrue((duration ?: 0) > 0)
                    val frame = checkNotNull(reader.getFrameAtTime(-1))
                    frame.recycle()
                } finally {
                    reader.release()
                }
            }
        } finally {
            val back = compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes()
            if (back.isNotEmpty()) compose.onNode(hasContentDescription("Back")).performClick()
            if (compose.onAllNodes(hasTestTag("templates") and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("templates").performClick()
                compose.onNodeWithTag("templateClean").performClick()
                compose.onNodeWithTag("applyTemplate").performClick()
            }
        }
    }

    @Test
    fun customStampCanBeSavedAndReused() {
        val name = "Survey " + SystemClock.elapsedRealtime()
        compose.onNodeWithTag("templates").performClick()
        compose.onNodeWithTag("templateClock").performScrollTo().performClick()
        compose.onNodeWithTag("editTemplate").performClick()
        compose.onNodeWithTag("stampShowTitle").performClick()
        compose.onNodeWithTag("stampEditorSTYLE").performClick()
        compose.onNodeWithTag("stampLayoutPLAIN").performScrollTo().performClick()
        compose.onNodeWithTag("stampBackgroundNONE").performScrollTo().performClick()
        compose.onNodeWithTag("stampLargeClock").performScrollTo().performClick()
        compose.onNodeWithTag("stampTypographySERIF").performScrollTo().performClick()
        compose.onNodeWithTag("stampSizeLARGE").performScrollTo().performClick()
        val dragCanvas = hasTestTag("stampDragCanvas")
        if (compose.onAllNodes(dragCanvas and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(dragCanvas).performScrollTo()
        }
        for (startX in listOf(0.17f, 0.37f, 0.46f)) {
            compose.onNodeWithTag("stampDragCanvas").performTouchInput {
                down(Offset(width * startX, height * 0.85f))
                moveBy(Offset(0f, -height * 0.13f))
                moveBy(Offset(0f, -height * 0.20f))
                up()
            }
        }
        captureScreenshot("smoke-stamp-customization.png")
        compose.onNodeWithTag("saveTemplateStyle").performClick()
        compose.onNodeWithTag("styleName").performTextInput(name)
        compose.onNodeWithText("Save", ignoreCase = false).performClick()
        compose.onNodeWithTag("applyTemplate").performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("camera", android.content.Context.MODE_PRIVATE)
        val library = org.json.JSONObject(checkNotNull(preferences.getString("stamp_library", null)))
        val saved = StampTemplate.fromJson(library.getString(name))
        assertEquals("Time & place", saved.heading)
        assertEquals(StampLayout.PLAIN, saved.layout)
        assertEquals(StampBackground.NONE, saved.background)
        assertEquals(StampSize.LARGE, saved.size)
        assertEquals(StampTypography.SERIF, saved.typography)
        assertTrue(saved.usesLargeClock)
        assertTrue(saved.stampOffset.y < -0.10f)
        assertTrue(!saved.showTitle)

        compose.onNodeWithTag("templates").performClick()
        compose.onNodeWithTag("savedStyles").performClick()
        compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete " + name).performScrollTo().performClick()
        compose.onNodeWithText("Delete style").performClick()
        compose.onNodeWithTag("exploreStyles").performClick()
        compose.onNodeWithTag("exploreCategoryINSPECTION").performScrollTo().performClick()
        compose.onNodeWithTag("exploreHazard").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("presetStyles").performScrollTo().performClick()
        compose.onNodeWithTag("templateClean").performScrollTo().performClick()
        compose.onNodeWithTag("applyTemplate").performClick()
    }

    @Test
    fun exploreFiltersAndAppliesAReadyMadeTemplate() {
        compose.onNodeWithTag("templates").performClick()
        compose.onNodeWithTag("exploreStyles").performClick()
        compose.onNodeWithTag("exploreCategoryRETAIL").performScrollTo().performClick()
        compose.onNodeWithTag("exploreShelfAudit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exploreParcel").assertDoesNotExist()
        compose.onNodeWithTag("exploreCategorySHIPPING").performScrollTo().performClick()
        compose.onNodeWithTag("exploreParcel").performScrollTo().performClick()
        compose.onNodeWithTag("applyTemplate").performClick()
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val preferences = context.getSharedPreferences("camera", android.content.Context.MODE_PRIVATE)
            val applied = StampTemplate.fromJson(preferences.getString("stamp_template", null))
            assertEquals("Parcel delivery", applied.heading)
            assertTrue(applied.showPhotoCode)
        } finally {
            compose.onNodeWithTag("templates").performClick()
            compose.onNodeWithTag("templateClean").performScrollTo().performClick()
            compose.onNodeWithTag("applyTemplate").performClick()
        }
    }

    private fun createProject(name: String) {
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithTag("newProjectName").performTextInput(name)
        compose.onNodeWithText("Create project").performClick()
        val camera = hasTestTag("projectPicker") and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(camera).fetchSemanticsNodes().isNotEmpty() }
        waitForText(name)
    }

    private fun waitForShutter() {
        val enabledShutter = hasTestTag("shutter") and isEnabled()
        compose.waitUntil(25_000) { compose.onAllNodes(enabledShutter).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(label: String, timeoutMillis: Long = 10_000) {
        val matcher = hasText(label)
        compose.waitUntil(timeoutMillis) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun captureScreenshot(name: String) {
        val outputPath = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        try {
            val directory = File(outputPath)
            if (directory.isDirectory || directory.mkdirs()) {
                File(directory, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            screenshot.recycle()
        }
    }
}
