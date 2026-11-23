package app.workphotolog.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StampLogoStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.filesDir, "stamp-logos")

    suspend fun importLogo(uri: Uri): String = withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException("Select an image from the photo picker")
        if (!directory.mkdirs() && !directory.isDirectory) throw IOException("Couldn't create logo storage")
        val id = UUID.randomUUID().toString()
        val inputCopy = File.createTempFile("input-", ".tmp", directory)
        try {
            val picked = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the selected image")
            picked.buffered().use { input ->
                inputCopy.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > MAX_SOURCE_BYTES) throw IOException("Choose an image under 24 MiB")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(inputCopy)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                if (width.toLong() * height > MAX_SOURCE_PIXELS) throw IOException("Choose a smaller image")
                val scale = minOf(1f, MAX_LOGO_EDGE.toFloat() / maxOf(width, height))
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                val staging = File.createTempFile("logo-", ".tmp", directory)
                try {
                    staging.outputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Couldn't save logo")
                        output.fd.sync()
                    }
                    ensureActive()
                    if (!staging.renameTo(File(directory, "$id.png"))) throw IOException("Couldn't save logo")
                } finally {
                    staging.delete()
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            inputCopy.delete()
        }
        id
    }

    suspend fun loadLogo(id: String?): Bitmap? = withContext(Dispatchers.IO) { loadForRender(id) }

    internal fun loadForRender(id: String?): Bitmap? {
        if (id == null || !LOGO_ID.matches(id)) return null
        val file = File(directory, "$id.png")
        if (!file.isFile) return null
        return try {
            decodeJpeg(file, MAX_LOGO_EDGE)
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 24L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 100_000_000
        const val MAX_LOGO_EDGE = 640
        val LOGO_ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }
}

fun stampLogoSource(bitmap: Bitmap, zoom: Float): Rect {
    val edge = (minOf(bitmap.width, bitmap.height) / boundedLogoZoom(zoom)).roundToInt().coerceAtLeast(1)
    val left = (bitmap.width - edge) / 2
    val top = (bitmap.height - edge) / 2
    return Rect(left, top, left + edge, top + edge)
}
