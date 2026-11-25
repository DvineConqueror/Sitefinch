package app.workphotolog.android

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun includesUltraWideAndTheLensMaximum() {
        assertEquals(
            listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.7f),
            cameraZoomPresets(0.5f, 3.7f),
        )
    }

    @Test
    fun respectsLensLimitsWithoutDuplicatingPresets() {
        assertEquals(listOf(0.65f, 1f, 1.5f, 2f), cameraZoomPresets(0.65f, 2f))
        assertEquals(listOf(1f, 1.5f, 1.8f), cameraZoomPresets(1f, 1.8f))
        assertEquals(listOf(1f), cameraZoomPresets(1f, 1f))
    }
}
