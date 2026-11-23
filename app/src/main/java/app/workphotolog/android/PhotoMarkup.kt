package app.workphotolog.android

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.BorderOuter
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class MarkTool(val label: String) {
    PEN("Pen"), BOX("Box"), ARROW("Arrow"), TEXT("Text"),
}

internal data class PhotoMark(val tool: MarkTool, val points: List<Offset>, val text: String = "")

internal fun drawPhotoMarks(canvas: AndroidCanvas, width: Int, height: Int, marks: List<PhotoMark>) {
    if (marks.isEmpty() || width <= 0 || height <= 0) return
    val unit = minOf(width, height).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        typeface = Typeface.DEFAULT_BOLD
    }
    fun drawPath(path: Path) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = unit * 0.012f
        paint.color = 0xff2d2315.toInt()
        canvas.drawPath(path, paint)
        paint.strokeWidth = unit * 0.007f
        paint.color = 0xffffd072.toInt()
        canvas.drawPath(path, paint)
    }
    marks.forEach { mark ->
        val start = mark.points.first()
        val end = mark.points.last()
        val x = start.x * width
        val y = start.y * height
        val endX = end.x * width
        val endY = end.y * height
        if (mark.tool == MarkTool.TEXT) {
            paint.textSize = unit * 0.055f
            drawMarkText(canvas, paint, mark.text, x, y, width, height)
        } else {
            val path = Path().apply {
                when (mark.tool) {
                    MarkTool.PEN -> {
                        moveTo(x, y)
                        mark.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
                    }
                    MarkTool.BOX -> addRect(minOf(x, endX), minOf(y, endY), maxOf(x, endX), maxOf(y, endY), Path.Direction.CW)
                    MarkTool.ARROW -> {
                        val angle = atan2(endY - y, endX - x)
                        val tip = minOf(unit * 0.055f, Offset(endX - x, endY - y).getDistance() / 3)
                        moveTo(x, y)
                        lineTo(endX, endY)
                        for (side in listOf(-0.55f, 0.55f)) {
                            moveTo(endX, endY)
                            lineTo(endX - tip * cos(angle + side), endY - tip * sin(angle + side))
                        }
                    }
                    MarkTool.TEXT -> Unit
                }
            }
            drawPath(path)
        }
    }
}

private fun drawMarkText(canvas: AndroidCanvas, paint: Paint, text: String, x: Float, y: Float, width: Int, height: Int) {
    paint.style = Paint.Style.FILL
    val pad = paint.textSize * 0.25f
    val size = paint.measureText(text) + 2 * pad
    if (size > width) paint.textSize *= width / size
    val measured = minOf(width.toFloat(), paint.measureText(text) + 2 * pad)
    val left = x.coerceIn(0f, width - measured)
    val top = y.coerceIn(0f, (height - paint.fontSpacing - pad).coerceAtLeast(0f))
    paint.color = 0xe6262119.toInt()
    canvas.drawRoundRect(left, top, left + measured, top + paint.fontSpacing + pad, pad, pad, paint)
    paint.color = 0xffffe5a9.toInt()
    canvas.drawText(text, left + pad, top - paint.ascent() + pad / 2, paint)
}

@Composable
internal fun PhotoMarkupEditor(
    file: File,
    stamp: StampDetails?,
    initial: List<PhotoMark>,
    onApply: (List<PhotoMark>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tool by remember { mutableStateOf(MarkTool.PEN) }
    var marks by remember { mutableStateOf(initial) }
    var stroke by remember { mutableStateOf(emptyList<Offset>()) }
    var point by remember { mutableStateOf<Offset?>(null) }
    var text by remember { mutableStateOf("") }
    val preview by produceState<MarkupPreview>(MarkupPreview.Loading, file, stamp) {
        value = withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeJpeg(file, 1000, mutable = stamp != null)
                if (stamp != null) drawStamp(bitmap, stamp)
                MarkupPreview.Ready(bitmap.asImageBitmap())
            } catch (_: IOException) {
                MarkupPreview.Failed
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Mark up photo", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val result = preview) {
                    MarkupPreview.Loading -> CircularProgressIndicator()
                    MarkupPreview.Failed -> Text("Couldn't load this photo.")
                    is MarkupPreview.Ready -> {
                        val image = result.image
                        val ratio = image.width.toFloat() / image.height
                        val width = minOf(maxWidth, maxHeight * ratio)
                        val height = width / ratio
                        val input = Modifier.size(width, height).clip(RoundedCornerShape(12.dp))
                        Image(image, "Photo to annotate", input, contentScale = ContentScale.FillBounds)
                        Canvas(input.pointerInput(tool, marks.size) {
                            if (tool == MarkTool.TEXT && marks.size < MAX_MARKS) {
                                detectTapGestures { point = normalized(it, size); text = "" }
                            } else if (tool != MarkTool.TEXT && marks.size < MAX_MARKS) {
                                detectDragGestures(
                                    onDragStart = { stroke = listOf(normalized(it, size)) },
                                    onDragCancel = { stroke = emptyList() },
                                    onDragEnd = {
                                        if (stroke.size > 1) marks = marks + PhotoMark(tool, stroke)
                                        stroke = emptyList()
                                    },
                                ) { change, _ ->
                                    change.consume()
                                    val position = normalized(change.position, size)
                                    stroke = if (tool == MarkTool.PEN) stroke.take(159) + position
                                    else listOf(stroke.firstOrNull() ?: position, position)
                                }
                            }
                        }) {
                            val current = if (stroke.size > 1) marks + PhotoMark(tool, stroke) else marks
                            drawIntoCanvas { drawPhotoMarks(it.nativeCanvas, size.width.toInt(), size.height.toInt(), current) }
                        }
                    }
                }
            }
            Text(
                when {
                    marks.size >= MAX_MARKS -> "Undo a mark to add another."
                    tool == MarkTool.TEXT -> "Tap the photo to place a label."
                    else -> "Drag on the photo to mark it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MarkTool.entries.forEach { option ->
                    val selected = option == tool
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .selectable(selected, role = Role.RadioButton) { tool = option }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            when (option) {
                                MarkTool.PEN -> Icons.Outlined.Draw
                                MarkTool.BOX -> Icons.Outlined.BorderOuter
                                MarkTool.ARROW -> Icons.Outlined.NorthEast
                                MarkTool.TEXT -> Icons.Outlined.Title
                            }, null,
                        )
                        Text(option.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { marks = marks.dropLast(1) }, enabled = marks.isNotEmpty()) { Text("Undo") }
                Button(onClick = { onApply(marks) }, enabled = preview is MarkupPreview.Ready) { Text("Apply markup") }
            }
        }
    }
    point?.let { position ->
        AlertDialog(
            onDismissRequest = { point = null },
            title = { Text("Photo label") },
            text = { OutlinedTextField(text, { text = it.take(42) }, label = { Text("Text") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    marks = marks + PhotoMark(MarkTool.TEXT, listOf(position), text.trim())
                    point = null
                }, enabled = text.isNotBlank()) { Text("Add label") }
            },
            dismissButton = { TextButton(onClick = { point = null }) { Text("Cancel") } },
        )
    }
}

private const val MAX_MARKS = 30

private sealed interface MarkupPreview {
    data object Loading : MarkupPreview
    data object Failed : MarkupPreview
    data class Ready(val image: ImageBitmap) : MarkupPreview
}

private fun normalized(point: Offset, size: IntSize) = Offset(
    (point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
    (point.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
)
