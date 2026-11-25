package app.workphotolog.android

import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import kotlin.math.abs

internal fun cameraZoomPresets(minimum: Float, maximum: Float): List<Float> =
    listOf(minimum, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 10f, maximum)
        .filter { it in minimum..maximum }
        .distinctBy(::zoomLabel)
        .sorted()

private fun zoomLabel(ratio: Float): String =
    String.format(Locale.ROOT, "%.2f", ratio).trimEnd('0').trimEnd('.') + "×"

internal fun Modifier.cameraPinchZoom(camera: Camera?, enabled: Boolean): Modifier {
    if (camera == null || !enabled) return this
    return pointerInput(camera) {
        detectTransformGestures { _, _, scale, _ ->
            val state = camera.cameraInfo.zoomState.value ?: return@detectTransformGestures
            val ratio = (state.zoomRatio * scale).coerceIn(state.minZoomRatio, state.maxZoomRatio)
            if (ratio != state.zoomRatio) camera.cameraControl.setZoomRatio(ratio)
        }
    }
}

@Composable
internal fun CameraZoom(camera: Camera, enabled: Boolean, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    val zoomState = camera.cameraInfo.zoomState
    var zoom by remember(camera) { mutableStateOf(zoomState.value) }
    var showPresets by remember(camera) { mutableStateOf(false) }

    DisposableEffect(owner, camera) {
        val observer = Observer<ZoomState> { zoom = it }
        zoomState.observe(owner, observer)
        onDispose { zoomState.removeObserver(observer) }
    }

    val current = zoom ?: return
    if (current.minZoomRatio >= current.maxZoomRatio) return
    val label = zoomLabel(current.zoomRatio)
    TextButton(
        onClick = { showPresets = true },
        enabled = enabled,
        modifier = modifier.background(Color(0xcc20271d), CircleShape).testTag("cameraZoom")
            .semantics { contentDescription = "Camera zoom, $label" },
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) { Text(label, color = Color.White) }

    if (showPresets && enabled) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Camera zoom") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (ratio in cameraZoomPresets(current.minZoomRatio, current.maxZoomRatio)) {
                        FilterChip(
                            selected = abs(current.zoomRatio - ratio) < 0.005f,
                            onClick = {
                                camera.cameraControl.setZoomRatio(ratio)
                                showPresets = false
                            },
                            label = { Text(zoomLabel(ratio)) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresets = false }) { Text("Done") } },
        )
    }
}
