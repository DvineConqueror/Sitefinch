package app.workphotolog.android

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class GalleryPhotos(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    suspend fun find(photo: Photo, stamped: Boolean = false): Uri? =
        withContext(Dispatchers.IO) { findByName(fileName(photo, stamped)) }

    suspend fun publish(photo: Photo, file: File, stamped: Boolean = false): Uri = withContext(Dispatchers.IO) {
        require(!photo.isVideo)
        check(file.isFile) { "Photo copy is missing." }
        publishing.withLock {
            val name = fileName(photo, stamped)
            findByName(name) ?: withContext(NonCancellable) {
                val entry = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, photo.takenAtMillis)
                    put(MediaStore.Images.Media.RELATIVE_PATH, directory)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, entry) ?: throw IOException("Couldn't create Gallery photo.")
                try {
                    val output = resolver.openOutputStream(uri, "w") ?: throw IOException("Couldn't open Gallery photo.")
                    output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
                    entry.clear()
                    entry.put(MediaStore.Images.Media.IS_PENDING, 0)
                    if (resolver.update(uri, entry, null, null) != 1) throw IOException("Couldn't publish Gallery photo.")
                    uri
                } catch (failure: Exception) {
                    try {
                        resolver.delete(uri, null, null)
                    } catch (cleanupFailure: Exception) {
                        failure.addSuppressed(cleanupFailure)
                    }
                    throw failure
                }
            }
        }
    }

    private fun findByName(name: String): Uri? {
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
        return resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, arrayOf(name, directory), null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            }
    }

    private fun fileName(photo: Photo, stamped: Boolean): String {
        val suffix = if (stamped) "_stamped" else ""
        return "Sitefinch_${photo.takenAtMillis}_${photo.id}$suffix.jpg"
    }

    private companion object {
        val publishing = Mutex()
        val directory = "${Environment.DIRECTORY_PICTURES}/Sitefinch/"
    }
}
