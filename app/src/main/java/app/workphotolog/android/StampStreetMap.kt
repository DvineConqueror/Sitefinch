package app.workphotolog.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.io.DataInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val streetMapSize = 384
private const val streetMapSignature = 0x53465331
private const val storedDegrees = 100_000.0
private const val latitudeMetersPerDegree = 111_200.0
private const val waterway = 3
private val ink = 0xff283c38.toInt()
private val paper = 0xfff2f4e9.toInt()
private val streetDataLock = Any()
private val coverage = StreetBounds(12_030_000, 1_600_000, 12_045_000, 1_610_000)

@Volatile
private var streetData: StreetData? = null

internal suspend fun renderStreetMap(context: Context, fix: LocationFix, zoom: Float): Bitmap? {
    val longitude = fix.longitude * storedDegrees
    val latitude = fix.latitude * storedDegrees
    if (!coverage.contains(longitude, latitude)) return null
    val data = streetData ?: withContext(Dispatchers.IO) {
        synchronized(streetDataLock) {
            streetData ?: readStreetData(context).also { streetData = it }
        }
    }
    if (data.bounds.none { it.contains(longitude, latitude) }) return null

    val accuracy = fix.accuracyMeters.takeIf { it.isFinite() && it > 0 } ?: 0.0
    val frame = StreetFrame(longitude, latitude, maxOf(950.0 / zoom, accuracy * 4.5))
    return withContext(Dispatchers.Default) {
        val visible = data.ways.filter { frame.intersects(it.bounds) }
        if (visible.none { it.kind != waterway }) return@withContext null
        Bitmap.createBitmap(streetMapSize, streetMapSize, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawStreets(Canvas(bitmap), frame, visible, accuracy)
        }
    }
}

private fun readStreetData(context: Context): StreetData = try {
    context.assets.open("sitefinch_streets.bin").use { asset ->
        DataInputStream(GZIPInputStream(asset).buffered()).use { input ->
            if (input.readInt() != streetMapSignature) throw IOException("Unsupported street map data")
            val boundCount = input.readUnsignedShort()
            val wayCount = input.readUnsignedShort()
            val bounds = List(boundCount) {
                StreetBounds(input.readInt(), input.readInt(), input.readInt(), input.readInt())
            }
            val ways = List(wayCount) {
                val kind = input.readUnsignedByte()
                val nameLength = input.readUnsignedShort()
                val pointCount = input.readUnsignedShort()
                val name = ByteArray(nameLength).also { input.readFully(it) }.decodeToString()
                StreetWay(kind, name, IntArray(pointCount * 2) { input.readInt() })
            }
            StreetData(bounds, ways)
        }
    }
} catch (_: IOException) {
    StreetData(emptyList(), emptyList())
}

private fun drawStreets(canvas: Canvas, frame: StreetFrame, ways: List<StreetWay>, accuracy: Double) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    canvas.drawColor(paper)
    paint.style = Paint.Style.STROKE
    for (kind in waterway downTo 0) {
        path.rewind()
        ways.filter { it.kind == kind }.forEach { way ->
            val points = way.points
            path.moveTo(frame.x(points[0]), frame.y(points[1]))
            for (index in 2 until points.size step 2) {
                path.lineTo(frame.x(points[index]), frame.y(points[index + 1]))
            }
        }
        if (kind == waterway) {
            paint.color = 0xff92c8d2.toInt()
            paint.strokeWidth = 6f
            canvas.drawPath(path, paint)
            continue
        }
        paint.strokeWidth = 11f - kind * 2.7f
        paint.color = if (kind == 0) 0xffa99f89.toInt() else 0xffc8d0c0.toInt()
        canvas.drawPath(path, paint)
        paint.strokeWidth -= 2.5f
        paint.color = if (kind == 0) 0xffffe1a1.toInt() else 0xfffefefa.toInt()
        canvas.drawPath(path, paint)
    }

    paint.style = Paint.Style.FILL
    val center = streetMapSize / 2f
    if (accuracy > 0) {
        paint.color = 0x344873c4
        canvas.drawCircle(center, center, (accuracy * frame.pixelsPerMeter).toFloat(), paint)
    }
    drawRoadLabels(canvas, frame, ways, paint)
    paint.color = 0xfffbfffb.toInt()
    canvas.drawCircle(center, center, 15f, paint)
    paint.color = 0xff265a93.toInt()
    canvas.drawCircle(center, center, 11f, paint)
    paint.color = 0xfff8faf7.toInt()
    canvas.drawCircle(center, center, 4f, paint)
    drawStreetLegend(canvas, frame, paint)
}

private fun drawRoadLabels(canvas: Canvas, frame: StreetFrame, ways: List<StreetWay>, paint: Paint) {
    paint.textSize = 19f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val labels = mutableSetOf<String>()
    val center = streetMapSize / 2f
    val occupied = mutableListOf(RectF(center - 21, center - 21, center + 21, center + 21))
    for (kind in 0..1) {
        for (way in ways) {
            if (way.kind != kind || way.name.isBlank() || way.name in labels) continue
            val width = paint.measureText(way.name)
            for (index in 2 until way.points.size step 2) {
                val x = (frame.x(way.points[index - 2]) + frame.x(way.points[index])) / 2f
                val y = (frame.y(way.points[index - 1]) + frame.y(way.points[index + 1])) / 2f
                val rect = RectF(x - width / 2f - 4f, y - 16f, x + width / 2f + 4f, y + 8f)
                if (rect.left < 10f || rect.right > streetMapSize - 10f || rect.top < 9f ||
                    rect.bottom > streetMapSize - 42f || occupied.any { RectF.intersects(it, rect) }
                ) continue
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = paper
                canvas.drawText(way.name, x - width / 2f, y + 3f, paint)
                paint.style = Paint.Style.FILL
                paint.color = ink
                canvas.drawText(way.name, x - width / 2f, y + 3f, paint)
                occupied.add(rect)
                labels.add(way.name)
                if (labels.size == 7) return
                break
            }
        }
    }
}

private fun drawStreetLegend(canvas: Canvas, frame: StreetFrame, paint: Paint) {
    val size = streetMapSize.toFloat()
    paint.color = 0xfafcfcf6.toInt()
    canvas.drawRect(0f, size - 42f, size, size, paint)
    paint.color = ink
    paint.textSize = 18f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val distance = listOf(500, 250, 200, 100, 50, 25).firstOrNull { it * frame.pixelsPerMeter <= 74 }
    if (distance != null) {
        canvas.drawText("$distance m", 10f, size - 14f, paint)
        canvas.drawRect(10f, size - 9f, 10f + (distance * frame.pixelsPerMeter).toFloat(), size - 7f, paint)
    }
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("© OpenStreetMap contributors", size - 10f, size - 23f, paint)
    paint.textSize = 15f
    paint.typeface = Typeface.DEFAULT
    canvas.drawText("openstreetmap.org/copyright", size - 10f, size - 7f, paint)
}

private data class StreetData(val bounds: List<StreetBounds>, val ways: List<StreetWay>)

private class StreetBounds(val west: Int, val south: Int, val east: Int, val north: Int) {
    fun contains(longitude: Double, latitude: Double): Boolean = longitude >= west && longitude <= east &&
        latitude >= south && latitude <= north
}

private class StreetWay(val kind: Int, val name: String, val points: IntArray) {
    val bounds: StreetBounds

    init {
        var west = points[0]
        var east = west
        var south = points[1]
        var north = south
        for (index in 2 until points.size step 2) {
            west = minOf(west, points[index])
            east = maxOf(east, points[index])
            south = minOf(south, points[index + 1])
            north = maxOf(north, points[index + 1])
        }
        bounds = StreetBounds(west, south, east, north)
    }
}

private class StreetFrame(private val longitude: Double, private val latitude: Double, widthMeters: Double) {
    val pixelsPerMeter = streetMapSize / widthMeters
    private val latitudeScale = pixelsPerMeter * latitudeMetersPerDegree / storedDegrees
    private val longitudeScale = latitudeScale * cos(Math.toRadians(latitude / storedDegrees))
    private val center = streetMapSize / 2.0
    private val west = longitude - center / longitudeScale
    private val east = longitude + center / longitudeScale
    private val south = latitude - center / latitudeScale
    private val north = latitude + center / latitudeScale

    fun intersects(bounds: StreetBounds): Boolean = west <= bounds.east && east >= bounds.west &&
        south <= bounds.north && north >= bounds.south

    fun x(point: Int): Float = (center + (point - longitude) * longitudeScale).toFloat()

    fun y(point: Int): Float = (center - (point - latitude) * latitudeScale).toFloat()
}
