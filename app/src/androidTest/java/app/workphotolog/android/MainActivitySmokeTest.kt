package app.workphotolog.android

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        compose.onNodeWithText("Work Photo Log").assertIsDisplayed()
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
        waitForShutter()
        compose.onNodeWithTag("shutter").performClick()
        waitForText("Review photo", 25_000)
        val preview = hasContentDescription("Photo awaiting review")
        compose.waitUntil(10_000) { compose.onAllNodes(preview).fetchSemanticsNodes().isNotEmpty() }
        captureScreenshot("smoke-review.png")
        compose.onNodeWithTag("saveOriginal").assertIsDisplayed().performClick()
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

    private fun createProject(name: String) {
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithTag("newProjectName").performTextInput(name)
        compose.onNodeWithText("Create project").performClick()
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
