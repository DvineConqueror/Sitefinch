package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {
    @Test
    fun originalPersistsAndZipMakesLocationOptional() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PhotoRepository(context)
        assertTrue(withTimeout(5_000) { repository.projects.first() }.any { it.name == "Inbox" })
        val project = repository.createProject("Test ${UUID.randomUUID()}")
        val staged = repository.newCaptureFile()
        val fixture = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            fixture.eraseColor(Color.RED)
            staged.outputStream().use { assertTrue(fixture.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            fixture.recycle()
        }
        val bytes = staged.readBytes()
        val fix = LocationFix(14.0, 121.0, 500.0, 200L, "approximate")
        val photo = repository.saveCapture(
            CaptureDraft(staged, project.id, 1_700_000_000_000, 480, "Site checked", fix),
        )

        val reopened = PhotoRepository(context)
        assertEquals(photo, withTimeout(5_000) { reopened.photos(project.id).first() }.single())
        val original = reopened.originalFile(photo)
        assertFalse(staged.exists())
        assertArrayEquals(bytes, original.readBytes())
        assertTrue(reopened.checkOriginal(photo))

        for (includeLocation in listOf(false, true)) {
            val archive = reopened.exportProject(project, includeLocation)
            try {
                ZipFile(archive).use { zip ->
                    val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() })
                    val entry = manifest.getJSONArray("photos").getJSONObject(0)
                    assertEquals("device", manifest.getString("time_source"))
                    assertEquals(1, manifest.getInt("schema_version"))
                    assertEquals(photo.id, entry.getString("id"))
                    assertEquals(photo.sha256, entry.getString("sha256"))
                    assertEquals(includeLocation, entry.has("location"))
                    assertEquals("photos/${photo.id}.jpg", entry.getString("path"))
                    assertArrayEquals(bytes, zip.getInputStream(zip.getEntry(entry.getString("path"))).use { it.readBytes() })
                }
            } finally {
                archive.delete()
            }
        }

        original.appendBytes(byteArrayOf(0))
        try {
            assertFalse(reopened.checkOriginal(photo))
        } finally {
            original.writeBytes(bytes)
        }
    }
}
