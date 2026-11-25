package app.workphotolog.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

internal fun Context.isDeviceLocationEnabled(): Boolean =
    getSystemService(LocationManager::class.java)?.isLocationEnabled == true

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
suspend fun currentFix(context: Context, timeoutMillis: Long = 4_000): LocationFix? {
    val precise = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    if (!precise && !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    if (!manager.isLocationEnabled) return null
    val candidates = listOf("fused", LocationManager.NETWORK_PROVIDER) +
        listOfNotNull(LocationManager.GPS_PROVIDER.takeIf { precise })

    return try {
        val available = candidates.filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        val cached = available.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider)?.toFix(precise) }.getOrNull()
        }.minWithOrNull(compareBy({ it.precision != "precise" }, { it.ageMillis }))
        if (cached != null && (!precise || cached.precision == "precise")) return cached
        var approximate = cached
        withTimeoutOrNull(timeoutMillis) {
            coroutineScope {
                val fixes = Channel<LocationFix?>(available.size)
                val requests = available.map { provider ->
                    launch {
                        val fix = try {
                            suspendCancellableCoroutine<LocationFix?> { continuation ->
                                val cancel = CancellationSignal()
                                continuation.invokeOnCancellation { cancel.cancel() }
                                LocationManagerCompat.getCurrentLocation(
                                    manager, provider, cancel, ContextCompat.getMainExecutor(context),
                                ) { location ->
                                    if (continuation.isActive) continuation.resume(location?.toFix(precise))
                                }
                            }
                        } catch (_: SecurityException) {
                            null
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                        fixes.send(fix)
                    }
                }
                try {
                    repeat(available.size) {
                        val fix = fixes.receive()
                        if (fix != null) {
                            if (!precise || fix.precision == "precise") return@coroutineScope fix
                            if (approximate == null || fix.ageMillis < checkNotNull(approximate).ageMillis) {
                                approximate = fix
                            }
                        }
                    }
                    approximate
                } finally {
                    requests.forEach { it.cancel() }
                    fixes.cancel()
                }
            }
        } ?: approximate
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun Location.toFix(finePermission: Boolean): LocationFix? {
    val age = ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L)
    if (age !in 0L..MAX_LOCATION_AGE_MILLIS || !hasAccuracy()) return null
    val isPrecise = finePermission && (provider == LocationManager.GPS_PROVIDER || accuracy <= 100f)
    return LocationFix(
        latitude, longitude, accuracy.toDouble(), age,
        if (isPrecise) "precise" else "approximate",
        altitudeMeters = altitude.takeIf { isPrecise && hasAltitude() && it.isFinite() },
    )
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
    val altitudeMeters: Double? = null,
    val headingDegrees: Float? = null,
    val map: Bitmap? = null,
    val address: String = template.address,
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
    headingDegrees: Float? = null,
    map: Bitmap? = null,
): StampDetails {
    val clock = if (template.showTime && template.usesLargeClock) {
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
        address = template.displayAddress(includeRecorded = location != null),
        photoCode = photoCode,
        logo = logo.takeIf { template.showLogo },
        altitudeMeters = location?.altitudeMeters?.takeIf { template.showAltitude && it.isFinite() },
        headingDegrees = headingDegrees?.takeIf { template.showCompass && it.isFinite() },
        map = map.takeIf { template.showMap },
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
            if (details.logo == null && details.template.showLogo) {
                loadedLogo = StampLogoStore(context).loadForRender(details.template.logoId)
            }
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

fun drawStamp(
    canvas: Canvas,
    width: Int,
    height: Int,
    details: StampDetails,
    onBounds: ((StampGroup, RectF) -> Unit)? = null,
    onOffset: ((StampGroup, StampOffset) -> Unit)? = null,
) {
    val template = details.template
    val preset = template.preset ?: return
    if (width <= 0 || height <= 0) return

    val unit = minOf(width, height)
    val inset = unit * 0.039f
    val padding = unit * 0.036f * template.size.scale
    val clockLayout = template.layout == StampLayout.CLOCK
    val ticket = template.layout == StampLayout.TICKET
    val baseWidth = if (clockLayout) minOf(width * 0.72f, unit * 1.08f)
        else width * (if (width > height) 0.72f else 0.90f)
    val cardWidth = minOf(baseWidth * template.size.scale, width - 2 * inset)
    val contentWidth = (cardWidth - 2 * padding).roundToInt().coerceAtLeast(1)
    val baseFont = unit * 0.044f * template.size.scale
    val font = baseFont * template.textScale.boundedStampValue(0.75f, 1.5f)
    val clockFont = baseFont * template.clockScale.boundedStampValue(0.75f, 1.6f)
    val gap = font * 0.22f
    val textColor = template.textColorArgb?.opaque() ?: template.textColor.argb
    val secondaryTextColor = template.textColorArgb?.opaque() ?: template.textColor.secondary
    val accent = template.accentColorArgb?.opaque() ?: template.color.accent
    val background = when (template.background) {
        StampBackground.PRESET -> template.color.background.takeUnless { template.layout == StampLayout.PLAIN }
        StampBackground.SOLID -> (template.primaryColorArgb ?: template.color.background).opaque()
        StampBackground.NONE -> null
    }?.let { color ->
        color.withStampAlpha((Color.alpha(color) * template.backgroundOpacity.boundedStampValue(0f, 1f)).roundToInt())
    }?.takeUnless { Color.alpha(it) == 0 }
    val transparentBackground = background == null || Color.alpha(background) < 128
    val fontFamily = template.typography.family?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
    val logo = details.logo?.takeIf { template.showLogo && !it.isRecycled }
    val map = details.map?.takeIf { template.showMap && !it.isRecycled }
    val logoStyle = template.styleFor(StampGroup.LOGO).bounded()
    val mapStyle = template.styleFor(StampGroup.MAP).bounded()
    val mapAspect = if (map != null) map.width.toFloat() / map.height else 1f
    val mapHeight = if (map != null) minOf(font * 6.8f * mapStyle.scale, contentWidth / mapAspect) else 0f
    val mapWidth = mapHeight * mapAspect
    val logoEdge = if (logo != null) minOf(font * 3.15f * logoStyle.scale, contentWidth * 0.26f * logoStyle.scale) else 0f
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
        group: StampGroup = StampGroup.DETAILS,
    ): StampLine {
        val style = template.styleFor(group).bounded()
        val layerColor = style.colorArgb?.opaque() ?: color
        val layerFontFamily = style.typography?.let { typography ->
            typography.family?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
        } ?: fontFamily
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = layerColor.withStampOpacity(style.opacity)
            textSize = size * style.scale
            typeface = Typeface.create(layerFontFamily, weight, false)
            if (clockDigits) {
                fontFeatureSettings = "'tnum'"
                val measured = measureText(text)
                if (measured > lineWidth) textSize *= lineWidth / measured * 0.98f
            }
            if (transparentBackground) {
                setShadowLayer(
                    (font * 0.18f).coerceAtLeast(1f), 0f, font * 0.045f,
                    0xff101411.toInt().withStampOpacity(style.opacity),
                )
            }
        }
        return StampLine(
            StaticLayout.Builder.obtain(text, 0, text.length, paint, lineWidth)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(lines)
                .setIncludePad(false)
                .build(),
            group,
        )
    }

    fun field(value: String, visible: Boolean): String = if (visible) value.trim().boundedStampText() else ""

    val title = field(template.heading, template.showTitle)
    val heading = if (template.showTitle) title.ifEmpty { preset.label } else ""
    val organization = field(template.organization, template.showCompany)
    val employeeName = field(template.worker, template.showEmployeeName)
    val employeeNumber = field(template.employeeNumber, template.showEmployeeNumber)
        .takeIf { it.isNotEmpty() }?.let { "ID $it" }.orEmpty()
    val worker = joinStampText(employeeName, employeeNumber)
    val site = field(template.site, template.showSite)
    val address = field(details.address, template.showAddress)
    val project = field(details.project, template.projectVisible)
    val highlightsSite = preset == StampPreset.SITE_VISIT && site.isNotBlank()
    val highlight = if (highlightsSite) site else project
    val clockTime = details.clockTime?.takeIf { template.usesLargeClock && template.showTime && it.isNotBlank() }
    val timeFirst = clockLayout || clockTime != null
    fun layer(group: StampGroup) = if (template.layerEditing) group else StampGroup.DETAILS
    val context = if (timeFirst) joinStampText(organization, worker) else when (preset) {
        StampPreset.FIELD_NOTE -> joinStampText(organization, site, worker)
        StampPreset.SITE_VISIT -> joinStampText(project.takeUnless { it == highlight }.orEmpty(), organization, worker)
        StampPreset.COMPLETION -> joinStampText(worker, organization, site)
    }

    val header = buildList {
        if (clockTime != null) {
            add(line(clockTime, clockFont * 2.45f, textColor, weight = 800, lineWidth = headerWidth,
                clockDigits = true, group = StampGroup.CLOCK))
            val caption = joinStampText("Device time", details.clockDate.orEmpty())
            add(line(caption, font * 0.85f, accent, weight = 600, lines = 2,
                lineWidth = headerWidth, group = StampGroup.CLOCK))
        } else {
            if (heading.isNotBlank()) {
                add(line(heading, font * 0.88f, accent, weight = 700, lineWidth = headerWidth,
                    group = layer(StampGroup.TITLE)))
            }
            if (highlight.isNotBlank()) {
                val group = if (highlightsSite) StampGroup.SITE else StampGroup.PROJECT
                add(line(highlight, font * 1.26f, textColor, weight = 700, lineWidth = headerWidth,
                    group = layer(group)))
            }
        }
    }
    val metadata = buildList {
        fun addTime() {
            if (template.showTime && details.time.isNotBlank()) {
                add(line(details.time, clockFont, secondaryTextColor, group = StampGroup.CLOCK))
            }
        }
        if (template.layerEditing) {
            fun addField(group: StampGroup, text: String, color: Int = secondaryTextColor, weight: Int = 400) {
                if (text.isNotBlank()) add(line(text, font, color, weight = weight, lines = 2, group = group))
            }
            if (timeFirst || template.layout == StampLayout.LEDGER) {
                if (clockTime == null) addTime()
            }
            if (clockTime != null) addField(StampGroup.TITLE, heading, accent, weight = 700)
            if (clockTime != null || highlightsSite) addField(StampGroup.PROJECT, project)
            addField(StampGroup.COMPANY, organization)
            addField(StampGroup.EMPLOYEE, employeeName)
            addField(StampGroup.EMPLOYEE_NUMBER, employeeNumber)
            if (clockTime != null || !highlightsSite) addField(StampGroup.SITE, site)
            addField(StampGroup.ADDRESS, address)
            if (!timeFirst && template.layout != StampLayout.LEDGER) addTime()
        } else {
            if (timeFirst) {
                if (clockTime == null) addTime()
                val place = joinStampText(
                    site.takeUnless { clockTime == null && it == highlight }.orEmpty(),
                    address,
                )
                val subject = if (clockTime != null) joinStampText(title, project)
                    else project.takeUnless { it == highlight }.orEmpty()
                if (place.isNotBlank()) add(line(place, font * 0.94f, textColor, weight = 600, lines = 2))
                if (subject.isNotBlank()) add(line(subject, font, secondaryTextColor, lines = 2))
            } else if (template.layout == StampLayout.LEDGER) {
                addTime()
            }
            if (context.isNotBlank()) add(line(context, font, secondaryTextColor, lines = 2))
            if (template.layout != StampLayout.LEDGER && !timeFirst) addTime()
            if (!timeFirst && address.isNotBlank()) {
                add(line("Address · $address", font, secondaryTextColor, lines = 2))
            }
        }
        if (template.showCoordinates) details.location?.let {
            add(line(it, font, secondaryTextColor, group = layer(StampGroup.COORDINATES)))
        }
        val altitude = details.altitudeMeters?.takeIf { template.showAltitude && it.isFinite() }
            ?.let { String.format(Locale.getDefault(), "Altitude · %,.0f m", it) }.orEmpty()
        val compass = details.headingDegrees?.takeIf { template.showCompass && it.isFinite() }
            ?.let {
                val angle = ((it % 360 + 360) % 360).roundToInt() % 360
                "Compass · ${angle}° magnetic"
            }.orEmpty()
        if (template.layerEditing) {
            if (altitude.isNotBlank()) add(line(altitude, font, secondaryTextColor, group = StampGroup.ALTITUDE))
            if (compass.isNotBlank()) add(line(compass, font, secondaryTextColor, group = StampGroup.COMPASS))
        } else {
            val instruments = joinStampText(altitude, compass)
            if (instruments.isNotBlank()) add(line(instruments, font, secondaryTextColor, lines = 2))
        }
        val weather = field(template.weather, template.showWeather)
        if (weather.isNotBlank()) {
            add(line("Weather (manual) · $weather", font, secondaryTextColor, lines = 2, group = layer(StampGroup.WEATHER)))
        }
        val tags = field(template.tags, template.showTags)
        if (tags.isNotBlank()) {
            add(line("Tags · $tags", font, secondaryTextColor, lines = 2, group = layer(StampGroup.TAGS)))
        }
        val note = field(details.note, template.showNote)
        if (note.isNotBlank()) add(line(note, font, textColor, lines = 2, group = layer(StampGroup.NOTE)))
        val styledNote = field(template.styledNote, template.showStyledNote)
        if (styledNote.isNotBlank()) {
            val group = layer(StampGroup.STYLED_NOTE)
            add(when (template.styledNoteStyle) {
                StampNoteStyle.PLAIN -> line(styledNote, font, textColor, lines = 2, group = group)
                StampNoteStyle.HIGHLIGHT -> line(styledNote, font, accent, weight = 700, lines = 2, group = group)
                StampNoteStyle.ALERT -> line("Alert · $styledNote", font, 0xffffbdad.toInt(), weight = 700, lines = 2, group = group)
            })
        }
        if (template.showBusinessCard) {
            val name = field(template.businessCardName, true)
            val contact = joinStampText(field(template.businessCardRole, true), field(template.businessCardContact, true))
            val group = layer(StampGroup.CONTACT)
            if (name.isNotBlank()) add(line(name, font, textColor, weight = 700, group = group))
            if (contact.isNotBlank()) add(line(contact, font, secondaryTextColor, lines = 2, group = group))
        }
        if (template.showPhotoCode) {
            details.photoCode?.takeIf { it.isNotBlank() }?.boundedStampText()?.let {
                add(line("Local code · $it", font * 0.84f, secondaryTextColor, lines = 2, group = layer(StampGroup.PHOTO_CODE)))
            }
        }
    }

    if (header.isEmpty() && metadata.isEmpty() && logo == null && map == null) return

    val headerHeight = maxOf(logoEdge, header.stampHeight(gap))
    val metadataHeight = metadata.stampHeight(gap)
    val hasBody = metadata.isNotEmpty() || map != null
    val divider = (template.layout == StampLayout.LEDGER || ticket) && headerHeight > 0f && hasBody
    val sectionGap = if (headerHeight == 0f || !hasBody) 0f else if (divider) font * 0.73f else gap
    val mapGap = if (map != null && metadata.isNotEmpty()) gap * 2 else 0f
    val cardHeight = 2 * padding + headerHeight + sectionGap + metadataHeight + mapGap + mapHeight
    val left = if (template.position.right) width - inset - cardWidth else inset
    val top = if (template.position.bottom) height - inset - cardHeight else inset
    val card = RectF(left, top, left + cardWidth, top + cardHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val fit = ((height - 2 * inset) / cardHeight).coerceAtMost(1f)
    val anchorX = if (template.position.right) card.right else card.left
    val anchorY = if (template.position.bottom) card.bottom else card.top
    val contentLeft = left + padding
    val contentTop = top + padding
    val headerLeft = if (template.logoSide == StampLogoSide.LEFT) contentLeft + logoSpace else contentLeft
    val bodyTop = contentTop + headerHeight + sectionGap
    val dividerY = contentTop + headerHeight + sectionGap / 2
    val logoRect = logo?.let {
        val logoLeft = if (template.logoSide == StampLogoSide.LEFT) contentLeft
            else contentLeft + contentWidth - logoEdge
        RectF(logoLeft, contentTop, logoLeft + logoEdge, contentTop + logoEdge)
    }
    val mapRect = map?.let {
        val mapTop = bodyTop + metadataHeight + mapGap
        RectF(contentLeft, mapTop, contentLeft + mapWidth, mapTop + mapHeight)
    }
    val stampRows = buildList {
        addStampRows(header, headerLeft, contentTop, gap)
        addStampRows(metadata, contentLeft, bodyTop, gap)
    }

    val groups = mutableMapOf<StampGroup, RectF>()
    fun include(group: StampGroup, rect: RectF) {
        val fitted = rect.stampFit(fit, anchorX, anchorY)
        groups.getOrPut(group) { RectF(fitted) }.union(fitted)
    }
    logoRect?.let { include(StampGroup.LOGO, it) }
    mapRect?.let { include(StampGroup.MAP, it) }
    for (row in stampRows) include(row.line.group, row.bounds)

    val legacyDetailsBounds = if (!template.layerEditing && !template.layerOffsets.containsKey(StampGroup.MAP)) {
        groups[StampGroup.DETAILS]?.let { detailsBounds ->
            groups[StampGroup.MAP]?.let { mapBounds -> RectF(detailsBounds).apply { union(mapBounds) } }
        }
    } else null
    val groupMoves = groups.mapValues { (group, rect) ->
        val clampBounds = when (group) {
            StampGroup.DETAILS, StampGroup.MAP -> legacyDetailsBounds ?: rect
            else -> rect
        }
        template.offsetFor(group).toPixels(width, height).clampToFrame(clampBounds, width, height)
    }
    val panel = if (background != null) card.stampFit(fit, anchorX, anchorY) else null
    val whole = RectF()
    panel?.let { whole.set(it) }
    if (divider) {
        whole.union(RectF(contentLeft, dividerY, contentLeft + contentWidth, dividerY + 1f).stampFit(fit, anchorX, anchorY))
    }
    for ((group, rect) in groups) {
        val moved = rect.shifted(groupMoves.getValue(group))
        whole.union(moved)
        if (panel != null && !panel.contains(moved)) {
            val margin = padding * fit
            panel.union(
                (moved.left - margin).coerceAtLeast(0f),
                (moved.top - margin).coerceAtLeast(0f),
                (moved.right + margin).coerceAtMost(width.toFloat()),
                (moved.bottom + margin).coerceAtMost(height.toFloat()),
            )
        }
    }
    panel?.let { whole.union(it) }
    val wholeMove = template.stampOffset.toPixels(width, height).clampToFrame(whole, width, height)
    onBounds?.invoke(StampGroup.WHOLE, whole.shifted(wholeMove))
    onOffset?.invoke(StampGroup.WHOLE, wholeMove.normalized(width, height))
    for ((group, rect) in groups) {
        val adjustment = groupMoves.getValue(group)
        onBounds?.invoke(group, rect.shifted(adjustment).shifted(wholeMove))
        onOffset?.invoke(group, adjustment.normalized(width, height))
    }

    canvas.withTranslation(wholeMove.x, wholeMove.y) {
        if (panel != null && background != null) {
            val radius = padding * fit * when (template.layout) {
                StampLayout.CLOCK, StampLayout.TICKET -> 1.35f
                StampLayout.PANEL -> 0.75f
                else -> 0.22f
            }
            paint.color = background
            drawRoundRect(panel, radius, radius, paint)
            if (template.layout == StampLayout.PANEL) {
                val alpha = (77 * template.backgroundOpacity.boundedStampValue(0f, 1f)).roundToInt()
                paint.color = accent.withStampAlpha(alpha)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (unit * 0.0035f).coerceAtLeast(1f) * fit
                drawRoundRect(panel, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
        }
        withScale(fit, fit, anchorX, anchorY) {
            if (ticket && background != null && headerHeight > 0f) {
                val margin = padding * 0.45f
                val headerPanel = RectF(
                    contentLeft - margin, contentTop - margin,
                    contentLeft + contentWidth + margin, contentTop + headerHeight + margin,
                )
                paint.color = accent.withStampAlpha((42 * template.backgroundOpacity.boundedStampValue(0f, 1f)).roundToInt())
                drawRoundRect(headerPanel, padding, padding, paint)
            }
            if (logo != null && logoRect != null) {
                val move = groupMoves.getValue(StampGroup.LOGO)
                withTranslation(move.x / fit, move.y / fit) {
                    paint.alpha = (255 * logoStyle.opacity).roundToInt()
                    drawStampLogo(this, logo, template.logoZoom, template.logoShape, logoRect, paint)
                }
            }
            if (divider) {
                paint.color = accent.withStampAlpha(140)
                paint.strokeWidth = (unit * 0.003f).coerceAtLeast(1f)
                drawLine(contentLeft, dividerY, contentLeft + contentWidth, dividerY, paint)
            }
            for (row in stampRows) {
                val move = groupMoves.getValue(row.line.group)
                withTranslation(row.x + move.x / fit, row.y + move.y / fit) {
                    row.line.layout.draw(this)
                }
            }
            if (map != null && mapRect != null) {
                val move = groupMoves.getValue(StampGroup.MAP)
                withTranslation(move.x / fit, move.y / fit) {
                    paint.alpha = (255 * mapStyle.opacity).roundToInt()
                    drawBitmap(map, null, mapRect, paint)
                }
            }
        }
    }
}

private fun joinStampText(vararg fields: String): String =
    fields.filter { it.isNotBlank() }.joinToString(" · ")

private data class StampLine(val layout: StaticLayout, val group: StampGroup)

private data class StampRow(val line: StampLine, val x: Float, val y: Float) {
    val bounds: RectF
        get() {
            val lines = 0 until line.layout.lineCount
            return RectF(
                x + lines.minOfOrNull(line.layout::getLineLeft).orZero(),
                y,
                x + lines.maxOfOrNull(line.layout::getLineRight).orZero().coerceAtLeast(1f),
                y + line.layout.height,
            )
        }
}

private fun List<StampLine>.stampHeight(gap: Float): Float =
    sumOf { it.layout.height } + (size - 1).coerceAtLeast(0) * gap

private fun MutableList<StampRow>.addStampRows(lines: List<StampLine>, left: Float, top: Float, gap: Float) {
    var y = top
    for (line in lines) {
        add(StampRow(line, left, y))
        y += line.layout.height + gap
    }
}

private fun Float?.orZero(): Float = this ?: 0f

private fun RectF.stampFit(scale: Float, anchorX: Float, anchorY: Float) = RectF(
    anchorX + (left - anchorX) * scale,
    anchorY + (top - anchorY) * scale,
    anchorX + (right - anchorX) * scale,
    anchorY + (bottom - anchorY) * scale,
)

private fun RectF.shifted(move: StampOffset): RectF = RectF(this).apply { offset(move.x, move.y) }

private fun StampOffset.toPixels(width: Int, height: Int): StampOffset =
    bounded().let { StampOffset(it.x * width, it.y * height) }

private fun StampOffset.normalized(width: Int, height: Int): StampOffset = StampOffset(x / width, y / height)

private fun StampOffset.clampToFrame(rect: RectF, width: Int, height: Int): StampOffset = StampOffset(
    x.clampStampDelta(-rect.left, width - rect.right),
    y.clampStampDelta(-rect.top, height - rect.bottom),
)

private fun Float.clampStampDelta(minimum: Float, maximum: Float): Float =
    if (minimum <= maximum) coerceIn(minimum, maximum) else (minimum + maximum) / 2

private fun Int.opaque(): Int = this or 0xff000000.toInt()
private fun Int.withStampAlpha(alpha: Int): Int = (this and 0x00ffffff) or (alpha shl 24)
private fun Int.withStampOpacity(opacity: Float): Int = withStampAlpha((Color.alpha(this) * opacity).roundToInt())

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
