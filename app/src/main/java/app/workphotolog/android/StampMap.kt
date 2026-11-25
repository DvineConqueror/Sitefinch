package app.workphotolog.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.io.DataInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos

private const val mapWidth = 320
private const val mapHeight = 176
private const val mapSignature = 0x53464D31
private const val kilometersPerLatitudeDegree = 111.2
private val mapWidths = intArrayOf(500, 1_500, 3_500, 7_500)
private val outlinesLock = Any()

@Volatile
private var cachedOutlines: List<MapOutline>? = null

internal suspend fun renderStampMap(
    context: Context,
    fix: LocationFix?,
    zoom: Float = 1f,
    online: Boolean = false,
): Bitmap? {
    if (fix == null || fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) return null
    val mapZoom = zoom.takeIf { it.isFinite() }?.coerceIn(0.6f, 2f) ?: 1f
    renderStreetMap(context.applicationContext, fix, mapZoom)?.let { return it }
    if (online) renderOnlineStreetMap(context.applicationContext, fix, mapZoom)?.let { return it }
    val outlines = cachedOutlines ?: withContext(Dispatchers.IO) { loadOutlines(context.applicationContext) }
    if (outlines.isEmpty()) return null

    return withContext(Dispatchers.Default) {
        val width = mapWidths.firstOrNull { MapFrame(fix.latitude, fix.longitude, it).hasVisibleOutline(outlines) }
            ?: mapWidths.last()
        val frame = MapFrame(fix.latitude, fix.longitude, (width / mapZoom).toInt())
        Bitmap.createBitmap(mapWidth, mapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawMap(Canvas(bitmap), frame, outlines)
        }
    }
}

private fun loadOutlines(context: Context): List<MapOutline> = synchronized(outlinesLock) {
    cachedOutlines ?: readOutlines(context).also { cachedOutlines = it }
}

private fun readOutlines(context: Context): List<MapOutline> = try {
    context.assets.open("sitefinch_map.bin").use { packed ->
        DataInputStream(packed.buffered()).use { input ->
            if (input.readInt() != mapSignature) throw IOException("Unsupported regional map data")
            List(input.readUnsignedShort()) {
                val closed = input.readBoolean()
                val paths = List(input.readUnsignedShort()) {
                    ShortArray(input.readUnsignedShort() * 2) { input.readShort() }
                }
                MapOutline(closed, paths)
            }
        }
    }
} catch (_: IOException) {
    emptyList()
}

private fun drawMap(canvas: Canvas, frame: MapFrame, outlines: List<MapOutline>) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val shoreline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.1f
        color = 0xff849480.toInt()
    }
    val border = Paint(shoreline).apply {
        strokeWidth = 1.2f
        color = 0xff697966.toInt()
    }
    val path = Path()
    canvas.drawColor(0xffd9e9e8.toInt())
    fill.color = 0xffd2ddc1.toInt()

    outlines.forEach { outline ->
        frame.longitudeOffsets.forEach { offset ->
            if (frame.intersects(outline, offset)) {
                path.rewind()
                path.fillType = Path.FillType.EVEN_ODD
                outline.paths.forEach { points ->
                    for (index in points.indices step 2) {
                        val x = frame.x(points[index] / 100.0 + offset)
                        val y = frame.y(points[index + 1] / 100.0)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    if (outline.closed) path.close()
                }
                if (outline.closed) {
                    canvas.drawPath(path, fill)
                    canvas.drawPath(path, shoreline)
                } else {
                    canvas.drawPath(path, border)
                }
            }
        }
    }

    val centerX = mapWidth / 2f
    val centerY = mapHeight / 2f
    fill.color = 0xfff5f5e9.toInt()
    canvas.drawCircle(centerX, centerY, 13f, fill)
    fill.color = 0xff1c4535.toInt()
    canvas.drawCircle(centerX, centerY, 9.5f, fill)
    fill.color = 0xffe5a745.toInt()
    canvas.drawCircle(centerX, centerY, 4f, fill)

    drawMapLegend(canvas, frame, fill)
}

private fun drawMapLegend(canvas: Canvas, frame: MapFrame, paint: Paint) {
    paint.color = 0xfaf5f5e9.toInt()
    val scale = frame.scaleKilometers
    if (scale != null) canvas.drawRoundRect(9f, mapHeight - 36f, 93f, mapHeight - 9f, 4f, 4f, paint)
    canvas.drawRoundRect(mapWidth - 34f, 9f, mapWidth - 9f, 40f, 4f, 4f, paint)

    paint.color = 0xff274338.toInt()
    paint.textSize = 13f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    if (scale != null) {
        canvas.drawText("$scale km", 16f, mapHeight - 22f, paint)
        canvas.drawRect(16f, mapHeight - 18f, 16f + mapWidth / 5f, mapHeight - 16f, paint)
    }
    canvas.drawText("N", mapWidth - 26f, 34f, paint)
    val northArrow = Path().apply {
        moveTo(mapWidth - 21f, 13f)
        lineTo(mapWidth - 26f, 22f)
        lineTo(mapWidth - 16f, 22f)
        close()
    }
    canvas.drawPath(northArrow, paint)
}

private class MapOutline(val closed: Boolean, val paths: List<ShortArray>) {
    var west = Double.POSITIVE_INFINITY
        private set
    var east = Double.NEGATIVE_INFINITY
        private set
    var south = Double.POSITIVE_INFINITY
        private set
    var north = Double.NEGATIVE_INFINITY
        private set

    init {
        paths.forEach { points ->
            for (index in points.indices step 2) {
                val longitude = points[index] / 100.0
                val latitude = points[index + 1] / 100.0
                west = minOf(west, longitude)
                east = maxOf(east, longitude)
                south = minOf(south, latitude)
                north = maxOf(north, latitude)
            }
        }
    }
}

private class MapFrame(
    private val latitude: Double,
    private val longitude: Double,
    widthKilometers: Int,
) {
    private val latitudeScale = mapWidth * kilometersPerLatitudeDegree / widthKilometers
    private val longitudeFactor = cos(Math.toRadians(latitude))
    private val longitudeScale = latitudeScale * longitudeFactor.coerceAtLeast(0.15)
    val scaleKilometers = (widthKilometers / 5).takeIf { longitudeFactor >= 0.15 }
    private val west = longitude - mapWidth / 2.0 / longitudeScale
    private val east = longitude + mapWidth / 2.0 / longitudeScale
    private val south = latitude - mapHeight / 2.0 / latitudeScale
    private val north = latitude + mapHeight / 2.0 / latitudeScale
    val longitudeOffsets = buildList {
        add(0)
        if (west < -180) add(-360)
        if (east > 180) add(360)
    }

    fun x(pointLongitude: Double): Float = (mapWidth / 2.0 + (pointLongitude - longitude) * longitudeScale).toFloat()

    fun y(pointLatitude: Double): Float = (mapHeight / 2.0 - (pointLatitude - latitude) * latitudeScale).toFloat()

    fun intersects(outline: MapOutline, offset: Int): Boolean =
        west <= outline.east + offset && east >= outline.west + offset &&
            south <= outline.north && north >= outline.south

    // A wider extent gives inland or open-water locations a recognizable outline.
    fun hasVisibleOutline(outlines: List<MapOutline>): Boolean = outlines.any { outline ->
        longitudeOffsets.any { offset ->
            intersects(outline, offset) && outline.paths.any { points ->
                (points.indices step 2).any { index ->
                    points[index] / 100.0 + offset in west..east &&
                        points[index + 1] / 100.0 in south..north
                }
            }
        }
    }
}
