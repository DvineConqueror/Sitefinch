package app.workphotolog.android

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBackupTest {
    @Test
    fun restoresBothMediaTypesWithoutOverwritingExistingRecords() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val shared = File(context.cacheDir, "share").apply { mkdirs() }
            val zip = File.createTempFile("backup-test-", ".zip", shared)
            val corruptZip = File.createTempFile("backup-corrupt-test-", ".zip", shared)
            val root = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
            val database = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).build()
            try {
                val source = File(root, "source").apply { mkdirs() }
                val restored = File(root, "restored").apply { mkdirs() }
                val project = ProjectRow(UUID.randomUUID().toString(), "Original project")
                val jpeg = byteArrayOf(1, 2, 3)
                val clip = byteArrayOf(4, 5, 6)
                val photo = fixture(project, jpeg, false)
                val video = fixture(project, clip, true)
                fun media(dir: File, item: Photo) = File(dir, item.id + if (item.isVideo) ".mp4" else ".jpg")
                media(source, photo).writeBytes(jpeg)
                media(source, video).writeBytes(clip)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
                val backup = LibraryBackup(context, database)

                backup.write(uri, listOf(project), listOf(photo, video)) { media(source, it) }
                database.photoDao().addProject(project.copy(name = "Existing project"))

                ZipFile(zip).use { originalZip ->
                    ZipOutputStream(corruptZip.outputStream()).use { output ->
                        originalZip.entries().asSequence().forEach { entry ->
                            output.putNextEntry(ZipEntry(entry.name))
                            if (entry.name.endsWith(".mp4")) output.write(0)
                            else originalZip.getInputStream(entry).use { it.copyTo(output) }
                            output.closeEntry()
                        }
                    }
                }
                val corruptUri = FileProvider.getUriForFile(context, "${context.packageName}.files", corruptZip)
                assertTrue(runCatching { backup.restore(corruptUri) { media(restored, it) } }.isFailure)
                assertTrue(database.photoDao().backupPhotos().isEmpty())
                assertTrue(restored.listFiles().orEmpty().isEmpty())

                assertEquals(2, backup.restore(uri) { media(restored, it) })
                assertEquals(0, backup.restore(uri) { media(restored, it) })
                assertTrue(media(restored, photo).delete())
                assertEquals(1, backup.restore(uri) { media(restored, it) })
                assertEquals(listOf(project.copy(name = "Existing project")), database.photoDao().backupProjects())
                assertEquals(setOf(photo, video), database.photoDao().backupPhotos().toSet())
                assertArrayEquals(jpeg, media(restored, photo).readBytes())
                assertArrayEquals(clip, media(restored, video).readBytes())
            } finally {
                database.close()
                root.deleteRecursively()
                zip.delete()
                corruptZip.delete()
            }
        }
    }

    private fun fixture(project: ProjectRow, bytes: ByteArray, video: Boolean): Photo =
        Photo(
            UUID.randomUUID().toString(), project.id, 1_700_000_000_000L, 480, "Synthetic capture", null,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            isVideo = video,
        )
}
