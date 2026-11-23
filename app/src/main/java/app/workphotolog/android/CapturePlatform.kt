package app.workphotolog.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.withTranslation
import androidx.core.location.LocationManagerCompat
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

suspend fun ImageCapture.captureFile(file: File, executor: Executor) =
    suspendCancellableCoroutine { continuation ->
        takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) continuation.resume(Unit) else file.delete()
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                    else file.delete()
                }
            },
        )
    }

private const val MAX_LOCATION_AGE_MILLIS = 60_000L

@SuppressLint("MissingPermission")
suspend fun currentFix(context: Context): LocationFix? {
    val precise = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    if (!precise && !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val candidates = if (precise) {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    } else {
        listOf(LocationManager.NETWORK_PROVIDER)
    }

    return try {
        val available = candidates.filter(manager::isProviderEnabled)
        val cached = available.mapNotNull { manager.getLastKnownLocation(it)?.toFix(precise) }
            .minByOrNull { it.ageMillis }
        cached ?: available.firstOrNull()?.let { provider ->
            withTimeoutOrNull(2200L) {
                suspendCancellableCoroutine { continuation ->
                    val cancel = CancellationSignal()
                    continuation.invokeOnCancellation { cancel.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, cancel, ContextCompat.getMainExecutor(context),
                    ) { location ->
                        if (continuation.isActive) continuation.resume(location?.toFix(precise))
                    }
                }
            }
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun Location.toFix(finePermission: Boolean): LocationFix? {
    val age = ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L)
    if (age !in 0L..MAX_LOCATION_AGE_MILLIS || !hasAccuracy()) return null
    val isPrecise = finePermission && provider == LocationManager.GPS_PROVIDER
    return LocationFix(latitude, longitude, accuracy.toDouble(), age, if (isPrecise) "precise" else "approximate")
}

fun displayTime(epochMillis: Long, offsetMinutes: Int): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)))

fun displayPosition(location: LocationFix): String =
    String.format(Locale.ROOT, "%.5f, %.5f · ±%d m", location.latitude, location.longitude, location.accuracyMeters.roundToInt())

data class StampDetails(val project: String, val time: String, val note: String, val location: String?)

fun stampDetails(project: String, note: String, time: Long, offset: Int, location: LocationFix?): StampDetails =
    StampDetails(project, displayTime(time, offset), note.trim(), location?.let(::displayPosition))

fun decodeJpeg(file: File, longestEdge: Int, mutable: Boolean = false): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        val scale = longestEdge.toFloat() / maxOf(width, height)
        if (scale < 1f) {
            decoder.setTargetSize(maxOf(1, (width * scale).roundToInt()), maxOf(1, (height * scale).roundToInt()))
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = mutable
    }

fun createStampedCopy(context: Context, original: File, details: StampDetails): File {
    val bitmap = decodeJpeg(original, 4096, mutable = true)
    try {
        drawStamp(bitmap, details)
        val directory = File(context.cacheDir, "share")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Couldn't create share directory")
        val copy = File.createTempFile("stamped-", ".jpg", directory)
        try {
            copy.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                    throw IOException("Couldn't write stamped JPEG")
                }
            }
            return copy
        } catch (failure: Exception) {
            copy.delete()
            throw failure
        }
    } finally {
        bitmap.recycle()
    }
}

fun drawStamp(bitmap: Bitmap, details: StampDetails) {
    val shortestEdge = minOf(bitmap.width, bitmap.height)
    val padding = shortestEdge * 0.03f
    val font = shortestEdge * 0.045f
    val inset = shortestEdge * 0.04f
    val lineWidth = (bitmap.width - 2 * (inset + padding)).roundToInt().coerceAtLeast(1)
    val fields = buildList {
        add(Triple(details.project, true, 1))
        add(Triple(details.time, false, 1))
        details.location?.let { add(Triple(it, false, 1)) }
        if (details.note.isNotBlank()) add(Triple(details.note, false, 2))
    }
    val layouts = fields.map { (text, prominent, lines) ->
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = if (prominent) font * 1.15f else font
            isFakeBoldText = prominent
        }
        StaticLayout.Builder.obtain(text, 0, text.length, paint, lineWidth)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(lines)
            .setIncludePad(false)
            .build()
    }
    val spaceBetween = font * 0.22f
    val height = layouts.sumOf { it.height } + (layouts.size - 1) * spaceBetween + 2 * padding
    val top = bitmap.height - inset - height
    val canvas = Canvas(bitmap)
    canvas.drawRoundRect(
        RectF(inset, top, bitmap.width - inset, bitmap.height - inset),
        padding * 0.55f, padding * 0.55f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xe6262c21.toInt() },
    )
    canvas.withTranslation(inset + padding, top + padding) {
        for (layout in layouts) {
            layout.draw(this)
            translate(0f, layout.height + spaceBetween)
        }
    }
}

fun Context.shareFile(request: ShareRequest) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", request.file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = request.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Work Photo Log", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}
