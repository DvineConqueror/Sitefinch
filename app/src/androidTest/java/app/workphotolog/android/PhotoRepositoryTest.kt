package app.workphotolog.android

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {
    @Test
    fun upgradeKeepsExistingPhotoAndProjectData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDb = "upgrade-${UUID.randomUUID()}.db"
        val digest = "a".repeat(64)
        try {
            val file = context.getDatabasePath(testDb)
            check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
                createLegacySchema(legacy)
                legacy.execSQL("INSERT INTO projects (id, name) VALUES ('old-project', 'Roof survey')")
                legacy.execSQL(
                    "INSERT INTO photos (id, projectId, takenAtMillis, utcOffsetMinutes, note, sha256) " +
                        "VALUES ('old-photo', 'old-project', 1700000000000, 480, 'Before upgrade', ?)",
                    arrayOf(digest),
                )
            }
            val database = Room.databaseBuilder(context, PhotoDatabase::class.java, testDb).build()
            try {
                val dao = database.photoDao()
                assertEquals("Roof survey", dao.observeProjects().first().single().name)
                val photo = dao.observePhotos("old-project").first().single()
                assertEquals("old-photo", photo.id)
                assertEquals("Before upgrade", photo.note)
                assertEquals(digest, photo.sha256)
                assertNull(photo.stampJson)
                assertNull(photo.headingDegrees)
                assertFalse(photo.isVideo)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(testDb)
        }
    }

    private fun createLegacySchema(database: SQLiteDatabase) {
        val schemaPath = PhotoDatabase::class.java.name + "/1.json"
        val raw = InstrumentationRegistry.getInstrumentation().context.assets
            .open(schemaPath).bufferedReader().use { it.readText() }
        val schema = JSONObject(raw).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            val placeholder = "${'$'}{TABLE_NAME}"
            database.execSQL(entity.getString("createSql").replace(placeholder, table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                database.execSQL(indices.getJSONObject(j).getString("createSql").replace(placeholder, table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
        database.version = schema.getInt("version")
    }

    @Test
    fun originalPersistsAndZipMakesLocationOptional() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PhotoRepository(context)
        assertTrue(withTimeout(5_000) { repository.projects.first() }.any { it.name == "Inbox" })
        val project = repository.createProject("Test ${UUID.randomUUID()}")
        val staged = repository.newCaptureFile()
        val fixture = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            fixture.eraseColor(Color.RED)
            staged.outputStream().use { assertTrue(fixture.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            fixture.recycle()
        }
        ExifInterface(staged).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val bytes = staged.readBytes()
        val fix = LocationFix(14.0, 121.0, 5.0, 200L, "precise", altitudeMeters = 82.0)
        val photo = repository.saveCapture(
            CaptureDraft(staged, project.id, 1_700_000_000_000, 480, "Site checked", fix, headingDegrees = 185f),
        )

        val reopened = PhotoRepository(context)
        assertEquals(photo, withTimeout(5_000) { reopened.photos(project.id).first() }.single())
        assertEquals(fix, photo.location)
        assertEquals(185f, photo.headingDegrees)
        val original = reopened.originalFile(photo)
        assertFalse(staged.exists())
        assertArrayEquals(bytes, original.readBytes())
        assertTrue(reopened.checkOriginal(photo))

        assertSharePayload(context, original, "image/jpeg")
        val stamped = createStampedCopy(
            context,
            original,
            stampDetails(project.name, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes, null),
        )
        try {
            val image = decodeJpeg(stamped, 4096)
            try {
                assertEquals(240, image.width)
                assertEquals(320, image.height)
                assertTrue(Color.red(image.getPixel(120, 40)) > 225)
                assertTrue(Color.red(image.getPixel(120, 300)) < 100)
            } finally {
                image.recycle()
            }
            assertSharePayload(context, stamped, "image/jpeg")
            assertArrayEquals(bytes, original.readBytes())
        } finally {
            stamped.delete()
        }

        for (includeLocation in listOf(false, true)) {
            val archive = reopened.exportProject(project, includeLocation)
            try {
                assertSharePayload(context, archive, "application/zip")
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

    private fun assertSharePayload(context: Context, file: File, mimeType: String) {
        var launched: Intent? = null
        val shareContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                launched = intent
            }
        }
        shareContext.shareFile(ShareRequest(file, mimeType))

        val chooser = checkNotNull(launched)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(mimeType, send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        val uri = checkNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".files", uri.authority)
        assertEquals(uri, checkNotNull(send.clipData).getItemAt(0).uri)
        val shared = checkNotNull(context.contentResolver.openInputStream(uri))
        assertArrayEquals(file.readBytes(), shared.use { it.readBytes() })
    }
}
