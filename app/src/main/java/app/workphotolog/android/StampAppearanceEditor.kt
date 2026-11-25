package app.workphotolog.android

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StampPlacementControls(
    template: StampTemplate,
    group: StampGroup,
    available: Set<StampGroup>,
    placement: StampPreviewGeometry,
    onSelect: (StampGroup) -> Unit,
    onChange: (StampTemplate) -> Unit,
) {
    StudioHeading("Position on photo")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StampGroup.entries.filter { it in available }.forEach { layer ->
            FilterChip(
                selected = layer == group,
                onClick = { onSelect(layer) },
                label = { Text(layer.label) },
                modifier = Modifier.testTag("stampDragGroup" + layer.name),
            )
        }
    }
    val offset = template.offsetFor(group)
    fun shift(x: Float, y: Float) {
        placement.nudge(group, x, y)?.let { onChange(template.withOffset(group, it)) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { shift(-PLACEMENT_STEP, 0f) }, modifier = Modifier.testTag("stampMoveLeft")) {
            Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "Move left")
        }
        IconButton(onClick = { shift(0f, -PLACEMENT_STEP) }, modifier = Modifier.testTag("stampMoveUp")) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up")
        }
        IconButton(onClick = { shift(0f, PLACEMENT_STEP) }, modifier = Modifier.testTag("stampMoveDown")) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down")
        }
        IconButton(onClick = { shift(PLACEMENT_STEP, 0f) }, modifier = Modifier.testTag("stampMoveRight")) {
            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "Move right")
        }
        TextButton(
            onClick = { onChange(template.withOffset(group, template.defaultOffsetFor(group))) },
            enabled = offset != template.defaultOffsetFor(group),
            modifier = Modifier.weight(1f).testTag("stampResetPosition"),
        ) { Text("Reset") }
    }
}

private const val PLACEMENT_STEP = 0.025f

@Composable
internal fun StampAppearanceEditor(
    template: StampTemplate,
    logo: Bitmap?,
    onEditLogo: () -> Unit,
    onChange: (StampTemplate) -> Unit,
) {
    var pickingRole by rememberSaveable { mutableStateOf<PaletteRole?>(null) }

    HorizontalDivider()
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
    StudioHeading("Backdrop")
    ChoiceChips(StampBackground.entries, template.background, StampBackground::label, { "stampBackground" + it.name }) {
        onChange(template.copy(background = it))
    }
    val hasBackdrop = when (template.background) {
        StampBackground.NONE -> false
        StampBackground.PRESET -> template.layout != StampLayout.PLAIN
        StampBackground.SOLID -> true
    }
    if (hasBackdrop) {
        StampPercentSlider("Backdrop opacity", template.backgroundOpacity, 0f..1f, "stampBackgroundOpacity") {
            onChange(template.copy(backgroundOpacity = it))
        }
    }
    if (template.background == StampBackground.SOLID) {
        StampColorAction(PaletteRole.BACKDROP, template) { pickingRole = PaletteRole.BACKDROP }
    }

    HorizontalDivider()
    StudioHeading("Typography")
    ChoiceChips(StampTypography.entries, template.typography, StampTypography::label, { "stampTypography" + it.name }) {
        onChange(template.copy(typography = it))
    }
    StampPercentSlider("Details text scale", template.textScale, 0.75f..1.5f, "stampTextScale") {
        onChange(template.copy(textScale = it))
    }
    StampToggle("Large clock", template.usesLargeClock, "stampLargeClock") { onChange(template.copy(largeClock = it)) }
    if (template.usesLargeClock) {
        StampPercentSlider("Clock scale", template.clockScale, 0.75f..1.6f, "stampClockScale") {
            onChange(template.copy(clockScale = it))
        }
    }

    HorizontalDivider()
    val customAccent = template.accentColorArgb
    val customText = template.textColorArgb
    StudioHeading("Accent color")
    ChoiceChips(
        StampColor.entries, template.color.takeIf { customAccent == null }, StampColor::label, { "stampColor" + it.name },
        swatch = { Color(it.accent) },
    ) { onChange(template.copy(color = it, accentColorArgb = null)) }
    StampColorAction(PaletteRole.ACCENT, template) { pickingRole = PaletteRole.ACCENT }

    StudioHeading("Text color")
    ChoiceChips(
        StampTextColor.entries, template.textColor.takeIf { customText == null },
        StampTextColor::label, { "stampTextColor" + it.name }, swatch = { Color(it.argb) },
    ) { onChange(template.copy(textColor = it, textColorArgb = null)) }
    StampColorAction(PaletteRole.TEXT, template) { pickingRole = PaletteRole.TEXT }

    HorizontalDivider()
    StampToggle("Show logo", template.showLogo, "stampStyleShowLogo") { onChange(template.copy(showLogo = it)) }
    TextButton(onClick = onEditLogo, modifier = Modifier.testTag("stampStyleEditLogo")) { Text("Customize logo") }

    pickingRole?.let { role ->
        StampColorDialog(
            role = role,
            color = role.current(template),
            logo = logo,
            onDismiss = { pickingRole = null },
            onSelect = {
                onChange(role.apply(template, it))
                pickingRole = null
            },
        )
    }
}

@Composable
internal fun StampPercentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onChange: (Float) -> Unit,
) {
    val percent = (value * 100).roundToInt().toString() + "%"
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(percent, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
                contentDescription = label
                stateDescription = percent
            },
        )
    }
}

private enum class PaletteRole(val label: String) {
    BACKDROP("Backdrop"), ACCENT("Accent"), TEXT("Text");

    fun current(template: StampTemplate): Int = when (this) {
        BACKDROP -> template.primaryColorArgb ?: template.color.background
        ACCENT -> template.accentColorArgb ?: template.color.accent
        TEXT -> template.textColorArgb ?: template.textColor.argb
    }

    fun apply(template: StampTemplate, argb: Int): StampTemplate = when (this) {
        BACKDROP -> template.copy(primaryColorArgb = argb)
        ACCENT -> template.copy(accentColorArgb = argb)
        TEXT -> template.copy(textColorArgb = argb)
    }
}

@Composable
private fun StampColorAction(role: PaletteRole, template: StampTemplate, onClick: () -> Unit) {
    val color = role.current(template)
    TextButton(onClick = onClick, modifier = Modifier.testTag("stampPickColor" + role.name)) {
        PaletteSwatch(color)
        Text(role.label + " color · " + color.hex(), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun StampColorDialog(
    role: PaletteRole,
    color: Int,
    logo: Bitmap?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var hex by rememberSaveable { mutableStateOf(color.hex()) }
    val selected = hex.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.or(0xff000000.toInt())
    val logoColors by produceState(emptyList<Int>(), logo) {
        value = withContext(Dispatchers.Default) { logo?.let(::sampleLogoColors).orEmpty() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(role.label + " color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        val raw = text.removePrefix("#")
                        if (raw.length <= 6 && raw.all { it.digitToIntOrNull(16) != null }) {
                            hex = "#" + raw.uppercase(Locale.ROOT)
                        }
                    },
                    label = { Text("Hex color") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("stampHexColor"),
                )
                if (logoColors.isNotEmpty()) {
                    StudioHeading("From your logo")
                    ChoiceChips(
                        logoColors, selected, Int::hex, { "stampLogoColor" + it.hex() },
                        swatch = { Color(it) },
                    ) { hex = it.hex() }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onSelect) },
                enabled = selected != null,
                modifier = Modifier.testTag("stampUseColor"),
            ) { Text("Use color") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PaletteSwatch(argb: Int) {
    Box(
        Modifier.size(18.dp).background(MaterialTheme.colorScheme.outline, CircleShape)
            .padding(2.dp).background(Color(argb), CircleShape),
    )
}

private fun Int.hex(): String = String.format(Locale.ROOT, "#%06X", this and 0xffffff)
