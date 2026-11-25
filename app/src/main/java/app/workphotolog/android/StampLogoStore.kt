package app.workphotolog.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StampLogoStore(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val directory = File(app.filesDir, "stamp-logos")

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
        if (id == null) return null
        BuiltInStampMark.entries.firstOrNull { it.id == id }?.let { mark ->
            val drawable = ContextCompat.getDrawable(app, mark.drawable) ?: return null
            val bitmap = createBitmap(160, 160)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            return bitmap
        }
        if (!validStampLogoId(id)) return null
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
    }
}

internal enum class BuiltInStampMark(val id: String, val drawable: Int) {
    PARCEL("sitefinch-mark-parcel", R.drawable.stamp_mark_parcel),
    PATROL("sitefinch-mark-patrol", R.drawable.stamp_mark_patrol),
    CIVIC("sitefinch-mark-civic", R.drawable.stamp_mark_civic),
    CREW("sitefinch-mark-crew", R.drawable.stamp_mark_crew),
    RETAIL("sitefinch-mark-retail", R.drawable.stamp_mark_retail),
    BUILD("sitefinch-mark-build", R.drawable.stamp_mark_build),
    SCHOOL("sitefinch-mark-school", R.drawable.stamp_mark_school),
    UTILITY("sitefinch-mark-utility", R.drawable.stamp_mark_utility),
}

internal fun knownStampLogoId(id: String): Boolean =
    validStampLogoId(id) || BuiltInStampMark.entries.any { it.id == id }

fun stampLogoSource(bitmap: Bitmap, zoom: Float): Rect {
    val edge = (minOf(bitmap.width, bitmap.height) / boundedLogoZoom(zoom)).roundToInt().coerceAtLeast(1)
    val left = (bitmap.width - edge) / 2
    val top = (bitmap.height - edge) / 2
    return Rect(left, top, left + edge, top + edge)
}

internal fun validStampLogoId(id: String): Boolean = STAMP_LOGO_ID.matches(id)

private val STAMP_LOGO_ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

fun drawStampLogo(
    canvas: Canvas,
    logo: Bitmap,
    zoom: Float,
    shape: StampLogoShape,
    destination: RectF,
    paint: Paint,
) {
    if (logo.isRecycled || destination.isEmpty) return
    val source = stampLogoSource(logo, zoom)
    if (shape == StampLogoShape.SQUARE) {
        canvas.drawBitmap(logo, source, destination, paint)
        return
    }
    val clip = Path()
    if (shape == StampLogoShape.CIRCLE) {
        clip.addOval(destination, Path.Direction.CW)
    } else {
        val radius = minOf(destination.width(), destination.height()) * 0.22f
        clip.addRoundRect(destination, radius, radius, Path.Direction.CW)
    }
    canvas.withSave {
        clipPath(clip)
        drawBitmap(logo, source, destination, paint)
    }
}
