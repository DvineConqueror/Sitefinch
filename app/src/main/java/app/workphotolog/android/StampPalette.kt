package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.Color

private const val paletteSampleEdge = 64
private const val maxPaletteColors = 6
private const val minimumSampleAlpha = 96
private const val minimumColorDistanceSquared = 42 * 42

/** Returns distinct source colors from the software bitmap decoded by StampLogoStore. */
internal fun sampleLogoColors(bitmap: Bitmap): List<Int> {
    if (bitmap.isRecycled || bitmap.config == Bitmap.Config.HARDWARE) return emptyList()
    val columns = minOf(bitmap.width, paletteSampleEdge)
    val rows = minOf(bitmap.height, paletteSampleEdge)
    val histogram = mutableMapOf<Int, ColorBin>()
    var totalWeight = 0

    for (row in 0 until rows) {
        val y = sampleCoordinate(row, rows, bitmap.height)
        for (column in 0 until columns) {
            val x = sampleCoordinate(column, columns, bitmap.width)
            val pixel = bitmap.getPixel(x, y)
            val alpha = Color.alpha(pixel)
            if (alpha < minimumSampleAlpha) continue
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            val bin = histogram.getOrPut(key) { ColorBin() }
            bin.weight += alpha
            totalWeight += alpha
            if (alpha > bin.sampleAlpha) {
                bin.sampleAlpha = alpha
                bin.sample = Color.rgb(red, green, blue)
            }
        }
    }

    val bins = histogram.values.sortedByDescending(ColorBin::weight)
    val strongest = bins.firstOrNull() ?: return emptyList()
    val minimumWeight = minOf(strongest.weight, totalWeight / 200)
    return buildList {
        for (bin in bins) {
            if (bin.weight < minimumWeight || size == maxPaletteColors) break
            if (none { colorDistanceSquared(it, bin.sample) < minimumColorDistanceSquared }) add(bin.sample)
        }
    }
}

private fun sampleCoordinate(index: Int, samples: Int, extent: Int): Int =
    ((2L * index + 1) * extent / (2L * samples)).toInt()

private fun colorDistanceSquared(left: Int, right: Int): Int {
    val red = Color.red(left) - Color.red(right)
    val green = Color.green(left) - Color.green(right)
    val blue = Color.blue(left) - Color.blue(right)
    return red * red + green * green + blue * blue
}

private class ColorBin {
    var weight = 0
    var sampleAlpha = 0
    var sample = Color.TRANSPARENT
}
