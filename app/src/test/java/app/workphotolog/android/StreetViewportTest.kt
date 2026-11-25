package app.workphotolog.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetViewportTest {
    @Test
    fun streetZoomWorksAwayFromBundledCoverage() {
        val normal = checkNotNull(streetViewport(10.3157, 123.8854, 20.0, 1f))
        val close = checkNotNull(streetViewport(10.3157, 123.8854, 20.0, 2f))
        assertEquals(4, normal.tiles.size)
        assertTrue(close.tiles.first().zoom > normal.tiles.first().zoom)
        assertTrue(close.accuracyPixels > normal.accuracyPixels)
    }

    @Test
    fun tileColumnsWrapAcrossTheDateline() {
        val view = checkNotNull(streetViewport(0.0, 180.0, 12.0, 1f))
        val count = 1 shl view.tiles.first().zoom
        assertEquals(setOf(0, count - 1), view.tiles.map { it.x }.toSet())
        assertTrue(view.tiles.all { it.y in 0 until count })
        assertNull(streetViewport(89.0, 0.0, 0.0, 1f))
    }
}
