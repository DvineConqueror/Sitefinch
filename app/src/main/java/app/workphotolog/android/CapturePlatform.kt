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
import android.graphics.Typeface
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
import androidx.core.graphics.withScale
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
import kotlinx.coroutines.suspendCancellableCoroutine
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

data class StampDetails(
    val project: String,
    val time: String,
    val note: String,
    val location: String?,
    val template: StampTemplate = StampTemplate.DEFAULT_COPY,
    val photoCode: String? = null,
    val logo: Bitmap? = null,
    val clockTime: String? = null,
    val clockDate: String? = null,
)

fun stampDetails(
    project: String,
    note: String,
    time: Long,
    offset: Int,
    location: LocationFix?,
    template: StampTemplate = StampTemplate.DEFAULT_COPY,
    photoCode: String? = null,
    logo: Bitmap? = null,
): StampDetails {
    val clock = if (template.showTime && template.layout == StampLayout.CLOCK) {
        Instant.ofEpochMilli(time).atOffset(ZoneOffset.ofTotalSeconds(offset * 60))
    } else {
        null
    }
    return StampDetails(
        project = project.trim(),
        time = if (template.showTime) displayTime(time, offset) else "",
        note = if (template.showNote) note.trim() else "",
        location = location?.takeIf { template.showCoordinates }?.let(::displayPosition),
        template = template,
        photoCode = photoCode,
        logo = logo,
        clockTime = clock?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        clockDate = clock?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
    )
}

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

fun createStampedCopy(
    context: Context,
    original: File,
    details: StampDetails?,
    markup: (Bitmap) -> Unit = {},
): File {
    val bitmap = decodeJpeg(original, 4096, mutable = true)
    var loadedLogo: Bitmap? = null
    try {
        if (details != null && details.template.preset != null) {
            if (details.logo == null) loadedLogo = StampLogoStore(context).loadForRender(details.template.logoId)
            drawStamp(bitmap, details.copy(logo = details.logo ?: loadedLogo))
        }
        markup(bitmap)
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
        loadedLogo?.recycle()
        bitmap.recycle()
    }
}

fun drawStamp(bitmap: Bitmap, details: StampDetails) =
    drawStamp(Canvas(bitmap), bitmap.width, bitmap.height, details)

fun drawStamp(canvas: Canvas, width: Int, height: Int, details: StampDetails) {
    val template = details.template
    val preset = template.preset ?: return
    if (width <= 0 || height <= 0) return

    val unit = minOf(width, height)
    val inset = unit * 0.039f
    val padding = unit * 0.036f * template.size.scale
    val clockLayout = template.layout == StampLayout.CLOCK
    val baseWidth = if (clockLayout) minOf(width * 0.72f, unit * 1.08f)
        else width * (if (width > height) 0.72f else 0.90f)
    val cardWidth = minOf(baseWidth * template.size.scale, width - 2 * inset)
    val contentWidth = (cardWidth - 2 * padding).roundToInt().coerceAtLeast(1)
    val font = unit * 0.044f * template.size.scale
    val gap = font * 0.22f
    val textColor = template.textColor
    val logo = details.logo?.takeUnless { it.isRecycled }
    val logoEdge = if (logo != null) minOf(font * 3.15f, contentWidth * 0.26f) else 0f
    val logoSpace = if (logo != null) logoEdge + padding else 0f
    val headerWidth = (contentWidth - logoSpace).roundToInt().coerceAtLeast(1)

    fun line(
        text: String,
        size: Float,
        color: Int,
        weight: Int = 400,
        lines: Int = 1,
        lineWidth: Int = contentWidth,
        clockDigits: Boolean = false,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            if (clockDigits) {
                fontFeatureSettings = "'tnum'"
                val measured = measureText(text)
                if (measured > lineWidth) textSize *= lineWidth / measured * 0.98f
            }
            if (template.layout == StampLayout.PLAIN) {
                setShadowLayer((font * 0.18f).coerceAtLeast(1f), 0f, font * 0.045f, 0xff101411.toInt())
            }
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, lineWidth)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(lines)
            .setIncludePad(false)
            .build()
    }

    val heading = template.heading.trim().ifEmpty { preset.label }
    val organization = template.organization.trim()
    val employee = template.employeeNumber.trim().takeIf { it.isNotEmpty() }?.let { "ID $it" }.orEmpty()
    val worker = joinStampText(template.worker.trim(), employee)
    val site = template.site.trim()
    val project = details.project
    val highlight = if (preset == StampPreset.SITE_VISIT && site.isNotBlank()) site else project
    val context = if (clockLayout) joinStampText(organization, worker) else when (preset) {
        StampPreset.FIELD_NOTE -> joinStampText(organization, site, worker)
        StampPreset.SITE_VISIT -> joinStampText(project.takeUnless { it == highlight }.orEmpty(), organization, worker)
        StampPreset.COMPLETION -> joinStampText(worker, organization, site)
    }
    val clockTime = details.clockTime?.takeIf { clockLayout && template.showTime && it.isNotBlank() }

    val header = buildList {
        if (clockTime != null) {
            add(line(clockTime, font * 2.45f, textColor.argb, weight = 800, lineWidth = headerWidth, clockDigits = true))
            val caption = joinStampText("Device time", details.clockDate.orEmpty())
            add(line(caption, font * 0.85f, template.color.accent, weight = 600, lines = 2, lineWidth = headerWidth))
        } else {
            add(line(heading, font * 0.88f, template.color.accent, weight = 700, lineWidth = headerWidth))
            if (highlight.isNotBlank()) {
                add(line(highlight, font * 1.26f, textColor.argb, weight = 700, lineWidth = headerWidth))
            }
        }
    }
    val metadata = buildList {
        fun addTime() {
            if (template.showTime && details.time.isNotBlank()) add(line(details.time, font, textColor.secondary))
        }
        if (clockLayout) {
            if (clockTime == null) addTime()
            val place = joinStampText(
                site.takeUnless { clockTime == null && it == highlight }.orEmpty(),
                template.address.trim(),
            )
            val subject = if (clockTime != null) joinStampText(template.heading.trim(), project)
                else project.takeUnless { it == highlight }.orEmpty()
            if (place.isNotBlank()) add(line(place, font * 0.94f, textColor.argb, weight = 600, lines = 2))
            if (subject.isNotBlank()) add(line(subject, font, textColor.secondary, lines = 2))
        } else if (template.layout == StampLayout.LEDGER) {
            addTime()
        }
        if (context.isNotBlank()) add(line(context, font, textColor.secondary, lines = 2))
        if (template.layout != StampLayout.LEDGER && !clockLayout) addTime()
        if (!clockLayout && template.address.isNotBlank()) {
            add(line("Address · ${template.address.trim()}", font, textColor.secondary, lines = 2))
        }
        if (template.showCoordinates) details.location?.let { add(line(it, font, textColor.secondary)) }
        if (template.tags.isNotBlank()) add(line("Tags · ${template.tags.trim()}", font, textColor.secondary, lines = 2))
        if (template.showNote && details.note.isNotBlank()) add(line(details.note, font, textColor.argb, lines = 2))
        if (template.showPhotoCode) {
            details.photoCode?.takeIf { it.isNotBlank() }?.let {
                add(line("Local code · $it", font * 0.84f, textColor.secondary, lines = 2))
            }
        }
    }

    val headerHeight = maxOf(logoEdge, header.sumOf { it.height } + (header.size - 1) * gap)
    val divider = template.layout == StampLayout.LEDGER && metadata.isNotEmpty()
    val sectionGap = if (metadata.isEmpty()) 0f else if (divider) font * 0.73f else gap
    val cardHeight = 2 * padding + headerHeight + sectionGap +
        metadata.sumOf { it.height } + (metadata.size - 1).coerceAtLeast(0) * gap
    val left = if (template.position.right) width - inset - cardWidth else inset
    val top = if (template.position.bottom) height - inset - cardHeight else inset
    val card = RectF(left, top, left + cardWidth, top + cardHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val fit = ((height - 2 * inset) / cardHeight).coerceAtMost(1f)
    val anchorX = if (template.position.right) card.right else card.left
    val anchorY = if (template.position.bottom) card.bottom else card.top
    canvas.withScale(fit, fit, anchorX, anchorY) {
        if (template.layout != StampLayout.PLAIN) {
            val radius = padding * when (template.layout) {
                StampLayout.CLOCK -> 1.35f
                StampLayout.PANEL -> 0.75f
                else -> 0.22f
            }
            paint.color = template.color.background
            drawRoundRect(card, radius, radius, paint)
            if (template.layout == StampLayout.PANEL) {
                paint.color = template.color.accent and 0x4dffffff
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (unit * 0.0035f).coerceAtLeast(1f)
                drawRoundRect(card, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val contentLeft = left + padding
        val contentTop = top + padding
        if (logo != null) {
            val logoLeft = if (template.logoSide == StampLogoSide.LEFT) contentLeft
                else contentLeft + contentWidth - logoEdge
            val target = RectF(logoLeft, contentTop, logoLeft + logoEdge, contentTop + logoEdge)
            paint.alpha = 255
            drawBitmap(logo, stampLogoSource(logo, template.logoZoom), target, paint)
        }
        val headerLeft = if (template.logoSide == StampLogoSide.LEFT) contentLeft + logoSpace else contentLeft
        drawStampLines(header, headerLeft, contentTop, gap)

        if (divider) {
            paint.color = template.color.accent and 0x8cffffff.toInt()
            paint.strokeWidth = (unit * 0.003f).coerceAtLeast(1f)
            val dividerY = contentTop + headerHeight + sectionGap / 2
            drawLine(contentLeft, dividerY, contentLeft + contentWidth, dividerY, paint)
        }
        drawStampLines(metadata, contentLeft, contentTop + headerHeight + sectionGap, gap)
    }
}

private fun joinStampText(vararg fields: String): String =
    fields.filter { it.isNotBlank() }.joinToString(" · ")

private fun Canvas.drawStampLines(layouts: List<StaticLayout>, left: Float, top: Float, gap: Float) {
    withTranslation(left, top) {
        for (layout in layouts) {
            layout.draw(this)
            translate(0f, layout.height + gap)
        }
    }
}

fun Context.shareFile(request: ShareRequest) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", request.file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = request.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(getString(R.string.app_name), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}
