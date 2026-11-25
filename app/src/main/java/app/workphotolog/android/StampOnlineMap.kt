package app.workphotolog.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.http.HttpResponseCache
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private const val tileSize = 256
private const val maxTileBytes = 512 * 1024
private const val rasterLimit = 85.05112878
private const val cacheSize = 24L * 1024 * 1024

internal data class StreetTile(val zoom: Int, val x: Int, val y: Int, val left: Float, val top: Float)

internal data class StreetViewport(val tiles: List<StreetTile>, val accuracyPixels: Float)

internal fun streetViewport(latitude: Double, longitude: Double, accuracy: Double, zoom: Float): StreetViewport? {
    if (latitude !in -rasterLimit..rasterLimit || longitude !in -180.0..180.0) return null
    val radians = Math.toRadians(latitude)
    val circumferenceAtLatitude = 40_075_016.686 * cos(radians)
    val accuracyMeters = accuracy.takeIf { it.isFinite() && it > 0 } ?: 0.0
    val targetWidth = maxOf(950.0 / zoom, accuracyMeters * 4.5)
    val level = log2(circumferenceAtLatitude / targetWidth).roundToInt().coerceIn(0, 19)
    val count = 1 shl level
    val world = count * tileSize.toDouble()
    val centerX = (longitude + 180) / 360 * world
    val centerY = (1 - asinh(tan(radians)) / PI) / 2 * world
    val left = centerX - tileSize / 2.0
    val top = (centerY - tileSize / 2.0).coerceIn(0.0, world - tileSize)
    // Close to the projection edge, the centered marker would misrepresent the fix.
    if (top != centerY - tileSize / 2.0) return null
    val firstX = floor(left / tileSize).toInt()
    val firstY = floor(top / tileSize).toInt()
    val lastX = floor((left + tileSize - 0.001) / tileSize).toInt()
    val lastY = floor((top + tileSize - 0.001) / tileSize).toInt()
    val tiles = buildList {
        for (y in firstY..lastY) {
            for (x in firstX..lastX) {
                add(StreetTile(level, Math.floorMod(x, count), y, (x * tileSize - left).toFloat(),
                    (y * tileSize - top).toFloat()))
            }
        }
    }
    return StreetViewport(tiles, (accuracyMeters * world / circumferenceAtLatitude).toFloat())
}

internal suspend fun renderOnlineStreetMap(context: Context, fix: LocationFix, zoom: Float): Bitmap? {
    val viewport = streetViewport(fix.latitude, fix.longitude, fix.accuracyMeters, zoom) ?: return null
    if (!withContext(Dispatchers.IO) { ensureStreetCache(context) }) return null
    // Fetch only tiles intersecting the map currently being rendered.
    val tiles = coroutineScope {
        viewport.tiles.map { tile -> async(Dispatchers.IO) { loadStreetTile(tile) } }.awaitAll()
    }
    try {
        if (tiles.any { it == null }) return null
        return Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            viewport.tiles.zip(tiles).forEach { (tile, bitmap) ->
                canvas.drawBitmap(checkNotNull(bitmap), null,
                    RectF(tile.left, tile.top, tile.left + tileSize, tile.top + tileSize), paint)
            }
            val center = tileSize / 2f
            if (viewport.accuracyPixels > 0f) {
                paint.color = 0x344873c4
                canvas.drawCircle(center, center, viewport.accuracyPixels.coerceAtMost(center), paint)
            }
            paint.color = 0xfffbfffb.toInt()
            canvas.drawCircle(center, center, 9f, paint)
            paint.color = 0xff265a93.toInt()
            canvas.drawCircle(center, center, 6.5f, paint)
            paint.color = 0xfafcfcf6.toInt()
            canvas.drawRect(0f, tileSize - 34f, tileSize.toFloat(), tileSize.toFloat(), paint)
            paint.color = 0xff283c38.toInt()
            paint.textSize = 13f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("© OpenStreetMap contributors", center, tileSize - 19f, paint)
            canvas.drawText("openstreetmap.org/copyright", center, tileSize - 5f, paint)
        }
    } finally {
        tiles.forEach { it?.recycle() }
    }
}

@Synchronized
private fun ensureStreetCache(context: Context): Boolean {
    if (HttpResponseCache.getInstalled() != null) return true
    return try {
        HttpResponseCache.install(File(context.cacheDir, "street-tiles"), cacheSize)
        true
    } catch (_: IOException) {
        false
    }
}

private fun loadStreetTile(tile: StreetTile): Bitmap? {
    val url = URL("https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png")
    return try {
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_200
            connection.setRequestProperty("User-Agent", "Sitefinch-Android (+https://github.com/DvineConqueror/work-photo-log)")
            if (connection.responseCode != 200 || connection.contentLength > maxTileBytes) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (output.size() + read > maxTileBytes) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val format = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, format)
            if (format.outWidth != tileSize || format.outHeight != tileSize) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        null
    }
}
