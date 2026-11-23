package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class StampPreview(
    val image: Bitmap? = null,
    val project: String,
    val note: String,
    val time: Long,
    val offset: Int,
    val location: LocationFix? = null,
    val photoCode: String? = null,
)

private enum class StudioSection(val label: String) {
    DETAILS("Details"), STYLE("Style"), LOGO("Logo"),
}

@Composable
internal fun StampStudioEditor(
    template: StampTemplate,
    onChange: (StampTemplate) -> Unit,
    modifier: Modifier = Modifier,
    preview: StampPreview? = null,
) {
    var section by rememberSaveable { mutableStateOf(StudioSection.DETAILS) }
    val context = LocalContext.current
    val store = remember(context) { StampLogoStore(context) }
    var importing by remember { mutableStateOf(false) }
    var logoError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentTemplate by rememberUpdatedState(template)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            logoError = false
            importing = true
            scope.launch {
                try {
                    val id = store.importLogo(uri)
                    onChange(currentTemplate.copy(logoId = id, logoZoom = 1f))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    logoError = true
                } finally {
                    importing = false
                }
            }
        }
    }
    val logo = rememberStampLogo(template.logoId)

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (preview == null) "Sample preview" else "Capture preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StudioPreviewFrame(template, Modifier.fillMaxWidth().height(170.dp), logo = logo, preview = preview)
        ChoiceChips(
            values = StudioSection.entries,
            selected = section,
            label = StudioSection::label,
            tag = { "stampEditor" + it.name },
            onSelect = { section = it },
        )
        HorizontalDivider()
        when (section) {
            StudioSection.DETAILS -> StampDetailsEditor(template, onChange)
            StudioSection.STYLE -> StampAppearanceEditor(template, onChange)
            StudioSection.LOGO -> StampLogoEditor(
                template, logo, importing, logoError,
                onPick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onChange = onChange,
            )
        }
    }
}

@Composable
internal fun StudioPreviewFrame(
    template: StampTemplate,
    modifier: Modifier,
    compact: Boolean = false,
    logo: Bitmap? = null,
    preview: StampPreview? = null,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val image = preview?.image
    val details = remember(template, logo, compact, preview) {
        val source = preview ?: StampPreview(
            project = if (compact) "North roof" else "North roof inspection",
            note = if (compact) "Flashing checked" else "Flashing inspected",
            time = 1_794_302_520_000L,
            offset = 0,
            location = LocationFix(14.6125, 121.031, 12.0, 0, "precise"),
            photoCode = "A1B2C3D4",
        )
        stampDetails(source.project, source.note, source.time, source.offset, source.location, template, source.photoCode, logo)
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    Box(
        modifier.clip(shape).background(Color(0xff263024)),
        contentAlignment = Alignment.Center,
    ) {
        if (template.preset == null && image == null) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = Color(0xffd5dec7), modifier = Modifier.size(26.dp))
        } else {
            Canvas(
                Modifier.matchParentSize().then(
                    if (compact) Modifier else Modifier.semantics {
                        contentDescription = when {
                            preview == null -> "Sample " + template.preset?.label.orEmpty() + " stamp"
                            template.preset == null -> "Capture without a stamp"
                            else -> "Capture with " + template.preset.label + " stamp"
                        }
                    },
                ),
            ) {
                if (image == null) {
                    val ink = Color(0xffaeb4a2).copy(alpha = 0.23f)
                    val inset = 9.dp.toPx()
                    val arm = 14.dp.toPx()
                    val stroke = 1.dp.toPx()
                    for (x in listOf(inset, size.width - inset)) {
                        for (y in listOf(inset, size.height - inset)) {
                            val dx = if (x == inset) arm else -arm
                            val dy = if (y == inset) arm else -arm
                            drawLine(ink, Offset(x, y), Offset(x + dx, y), stroke)
                            drawLine(ink, Offset(x, y), Offset(x, y + dy), stroke)
                        }
                    }
                    drawIntoCanvas {
                        val width = size.width.toInt()
                        val frameHeight = size.height.toInt()
                        val previewHeight = if (compact) {
                            minOf(width, (frameHeight * 1.6f).toInt()).coerceAtLeast(frameHeight)
                        } else frameHeight
                        val offset = if (template.position.bottom) (frameHeight - previewHeight).toFloat() else 0f
                        it.nativeCanvas.withTranslation(y = offset) {
                            drawStamp(this, width, previewHeight, details)
                        }
                    }
                } else {
                    val scale = minOf(size.width / image.width, size.height / image.height)
                    val width = image.width * scale
                    val height = image.height * scale
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.withTranslation((size.width - width) / 2, (size.height - height) / 2) {
                            drawBitmap(image, null, RectF(0f, 0f, width, height), paint)
                            drawStamp(this, width.toInt(), height.toInt(), details)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StampDetailsEditor(template: StampTemplate, onChange: (StampTemplate) -> Unit) {
    StudioHeading("Stamp text")
    StampField("Heading", template.heading, "stampHeading") { onChange(template.copy(heading = it)) }
    StampField("Company", template.organization, "stampCompany") { onChange(template.copy(organization = it)) }
    StampField("Operator", template.worker, "stampOperator") { onChange(template.copy(worker = it)) }
    StampField("Employee number", template.employeeNumber, "stampEmployeeNumber") {
        onChange(template.copy(employeeNumber = it))
    }
    StampField("Site", template.site, "stampSite") { onChange(template.copy(site = it)) }
    StampField("Address", template.address, "stampAddress") { onChange(template.copy(address = it)) }
    StampField("Tags", template.tags, "stampTags") { onChange(template.copy(tags = it)) }

    HorizontalDivider()
    StudioHeading("Include on stamp")
    StampToggle("Device time", template.showTime, "stampShowTime") { onChange(template.copy(showTime = it)) }
    StampToggle("Job note", template.showNote, "stampShowNote") { onChange(template.copy(showNote = it)) }
    StampToggle("Recorded coordinates", template.showCoordinates, "stampShowCoordinates") {
        onChange(template.copy(showCoordinates = it))
    }
    StampToggle("Local photo code", template.showPhotoCode, "stampShowPhotoCode") {
        onChange(template.copy(showPhotoCode = it))
    }
}

@Composable
private fun StampAppearanceEditor(template: StampTemplate, onChange: (StampTemplate) -> Unit) {
    StudioHeading("Layout")
    ChoiceChips(StampLayout.entries, template.layout, StampLayout::label, { "stampLayout" + it.name }) {
        onChange(template.copy(layout = it))
    }

    StudioHeading("Size")
    ChoiceChips(StampSize.entries, template.size, StampSize::label, { "stampSize" + it.name }) {
        onChange(template.copy(size = it))
    }

    StudioHeading("Stamp position")
    ChoiceChips(StampPosition.entries, template.position, StampPosition::label, { "stampPosition" + it.name }) {
        onChange(template.copy(position = it))
    }

    HorizontalDivider()
    StudioHeading("Accent color")
    ChoiceChips(
        StampColor.entries, template.color, StampColor::label, { "stampColor" + it.name },
        swatch = { Color(it.accent) },
    ) { onChange(template.copy(color = it)) }

    StudioHeading("Text color")
    ChoiceChips(
        StampTextColor.entries, template.textColor, StampTextColor::label, { "stampTextColor" + it.name },
        swatch = { Color(it.argb) },
    ) { onChange(template.copy(textColor = it)) }
}

@Composable
private fun StampLogoEditor(
    template: StampTemplate,
    logo: Bitmap?,
    importing: Boolean,
    logoError: Boolean,
    onPick: () -> Unit,
    onChange: (StampTemplate) -> Unit,
) {
    StudioHeading("Logo")
    if (logo != null) {
        val crop = remember(logo, template.logoZoom) { stampLogoSource(logo, template.logoZoom) }
        val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
        Canvas(
            Modifier.size(88.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics { contentDescription = "Logo crop preview" },
        ) {
            drawIntoCanvas {
                it.nativeCanvas.drawBitmap(logo, crop, RectF(0f, 0f, size.width, size.height), paint)
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onPick,
            enabled = !importing,
            modifier = Modifier.testTag("pickStampLogo"),
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (template.logoId == null) "Choose image" else "Replace image", Modifier.padding(start = 8.dp))
        }
        if (template.logoId != null) {
            TextButton(
                onClick = { onChange(template.copy(logoId = null, logoZoom = 1f)) },
                enabled = !importing,
            ) { Text("Remove logo") }
        }
    }
    if (importing) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Importing logo")
        }
    } else if (logoError) {
        Text("Couldn't open this image. Choose another image.", color = MaterialTheme.colorScheme.error)
    }
    if (logo != null) {
        StudioHeading("Logo position")
        ChoiceChips(StampLogoSide.entries, template.logoSide, StampLogoSide::label, { "logoSide" + it.name }) {
            onChange(template.copy(logoSide = it))
        }
        StudioHeading("Crop zoom")
        Slider(
            value = template.logoZoom,
            onValueChange = { onChange(template.copy(logoZoom = it)) },
            valueRange = 1f..3f,
            modifier = Modifier.fillMaxWidth().testTag("logoCropZoom")
                .semantics { contentDescription = "Crop zoom" },
        )
        TextButton(onClick = { onChange(template.copy(logoZoom = 1f)) }, enabled = template.logoZoom != 1f) {
            Text("Reset crop")
        }
    }
}

@Composable
private fun StudioHeading(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StampField(label: String, value: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(64).dropLastWhile(Char::isHighSurrogate)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = true,
    )
}

@Composable
private fun StampToggle(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChoiceChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    tag: (T) -> String,
    swatch: ((T) -> Color)? = null,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (value in values) {
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                leadingIcon = swatch?.let { color ->
                    {
                        Box(
                            Modifier.size(17.dp).background(Color(0xff495042), CircleShape).padding(2.dp)
                                .background(color(value), CircleShape),
                        )
                    }
                },
                modifier = Modifier.testTag(tag(value)),
            )
        }
    }
}
