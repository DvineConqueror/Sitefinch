package app.workphotolog.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPinchTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun spreadingAndPinchingChangeScaleInOppositeDirections() {
        var scale = 1f
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("gestureSurface").pointerInput(Unit) {
                detectCameraPinch { scale *= it }
            })
        }
        val surface = compose.onNodeWithTag("gestureSurface")
        surface.performTouchInput {
            pinch(
                Offset(width * 0.4f, centerY), Offset(width * 0.2f, centerY),
                Offset(width * 0.6f, centerY), Offset(width * 0.8f, centerY),
            )
        }
        val zoomed = compose.runOnIdle { scale }
        assertTrue("Spreading fingers should zoom in", zoomed > 1f)
        surface.performTouchInput {
            pinch(
                Offset(width * 0.2f, centerY), Offset(width * 0.4f, centerY),
                Offset(width * 0.8f, centerY), Offset(width * 0.6f, centerY),
            )
        }
        assertTrue("Pinching fingers should zoom out", compose.runOnIdle { scale } < zoomed)
    }
}
