package app.workphotolog.android

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val headingDegrees: Float? = null,
    val recordedAddress: Map<AddressPart, String> = emptyMap(),
)

private enum class StudioSection(val label: String) {
    DETAILS("Details"), LAYERS("Layers"), STYLE("Style"), LOGO("Logo"),
}

private val sampleStampLocation = LocationFix(16.045, 120.343, 12.0, 0, "precise", altitudeMeters = 24.0)

@Composable
private fun StampLayerInspector(
    template: StampTemplate,
    group: StampGroup,
    onChange: (StampTemplate) -> Unit,
) {
    val style = template.styleFor(group)
    fun update(value: StampLayerStyle) = onChange(template.withLayerStyle(group, value))
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(group.label, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        TextButton(
            onClick = {
                val reset = template.withLayerStyle(group, StampLayerStyle())
                onChange(if (group == StampGroup.MAP) reset.copy(mapZoom = 1f) else reset)
            },
            enabled = style != StampLayerStyle() || (group == StampGroup.MAP && template.mapZoom != 1f),
        ) { Text("Reset style") }
    }
    StampPercentSlider("Size", style.scale, 0.5f..2f, "stampLayerSize") { update(style.copy(scale = it)) }
    StampPercentSlider("Opacity", style.opacity, 0f..1f, "stampLayerOpacity") { update(style.copy(opacity = it)) }
    when (group) {
        StampGroup.MAP -> StampPercentSlider("Map zoom", template.mapZoom, 0.6f..2f, "stampMapZoom") {
            onChange(template.copy(mapZoom = it))
        }
        StampGroup.LOGO -> Unit
        else -> {
            StudioHeading("Font")
            ChoiceChips(listOf(null) + StampTypography.entries, style.typography,
                { it?.label ?: "Template" }, { "stampLayerFont" + (it?.name ?: "DEFAULT") },
            ) { update(style.copy(typography = it)) }
            StudioHeading("Text color")
            ChoiceChips(listOf(null) + StampTextColor.entries,
                StampTextColor.entries.find { it.argb == style.colorArgb },
                { it?.label ?: "Template" }, { "stampLayerColor" + (it?.name ?: "DEFAULT") },
            ) { update(style.copy(colorArgb = it?.argb)) }
        }
    }
}

@Composable
internal fun StampStudioEditor(
    template: StampTemplate,
    onChange: (StampTemplate) -> Unit,
    modifier: Modifier = Modifier,
    preview: StampPreview? = null,
) {
    var section by rememberSaveable { mutableStateOf(if (template.layerEditing) StudioSection.LAYERS else StudioSection.DETAILS) }
    var movingGroup by rememberSaveable { mutableStateOf(StampGroup.WHOLE) }
    val placement = remember { StampPreviewGeometry() }
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
    val movableGroups = buildSet {
        add(StampGroup.WHOLE)
        if (!template.layerEditing) add(StampGroup.DETAILS)
        if (logo != null && template.showLogo) add(StampGroup.LOGO)
        if (template.showTime) add(StampGroup.CLOCK)
        if (template.showMap) add(StampGroup.MAP)
        if (template.layerEditing) {
            if (template.showTitle) add(StampGroup.TITLE)
            if (template.projectVisible) add(StampGroup.PROJECT)
            if (template.showCompany) add(StampGroup.COMPANY)
            if (template.showEmployeeName) add(StampGroup.EMPLOYEE)
            if (template.showEmployeeNumber) add(StampGroup.EMPLOYEE_NUMBER)
            if (template.showSite) add(StampGroup.SITE)
            if (template.showAddress) add(StampGroup.ADDRESS)
            if (template.showCoordinates) add(StampGroup.COORDINATES)
            if (template.showAltitude) add(StampGroup.ALTITUDE)
            if (template.showCompass) add(StampGroup.COMPASS)
            if (template.showWeather) add(StampGroup.WEATHER)
            if (template.showTags) add(StampGroup.TAGS)
            if (template.showNote) add(StampGroup.NOTE)
            if (template.showStyledNote) add(StampGroup.STYLED_NOTE)
            if (template.showBusinessCard) add(StampGroup.CONTACT)
            if (template.showPhotoCode) add(StampGroup.PHOTO_CODE)
        }
    }
    val selectedGroup = movingGroup.takeIf { it in movableGroups } ?: StampGroup.WHOLE

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val editingPlacement = section == StudioSection.LAYERS || section == StudioSection.STYLE
        val pinPreview = editingPlacement && maxHeight >= 430.dp && LocalDensity.current.fontScale < 1.4f
        val previewHeight = when {
            !pinPreview -> 170.dp
            section == StudioSection.LAYERS -> minOf(240.dp, maxHeight * 0.39f)
            else -> minOf(170.dp, maxHeight * 0.34f)
        }
        val mainScroll = rememberScrollState()
        val styleScroll = rememberScrollState()
        val appearance: @Composable () -> Unit = {
            if (section == StudioSection.LAYERS) {
                TextButton(onClick = { section = StudioSection.DETAILS }) { Text("Choose fields") }
                StampToggle("Separate text layers", template.layerEditing, "stampSeparateLayers",
                    detail = "Move and style each field on its own.",
                ) { onChange(template.copy(layerEditing = it)) }
                StampToggle("Show map", template.showMap, "stampLayersShowMap") {
                    onChange(template.copy(showMap = it))
                    if (it) movingGroup = StampGroup.MAP
                }
            }
            StampPlacementControls(template, selectedGroup, movableGroups, placement, { movingGroup = it }, onChange)
            if (section == StudioSection.LAYERS && selectedGroup !in listOf(StampGroup.WHOLE, StampGroup.DETAILS)) {
                StampLayerInspector(template, selectedGroup, onChange)
                when (selectedGroup) {
                    StampGroup.LOGO -> TextButton(onClick = { section = StudioSection.LOGO }) {
                        Text("Edit logo and crop")
                    }
                    StampGroup.STYLED_NOTE -> StampField("Custom text", template.styledNote, "stampLayerCustomText",
                        maxCharacters = 160,
                    ) { onChange(template.copy(styledNote = it)) }
                    StampGroup.MAP -> if (preview != null && preview.location == null) {
                        Text(
                            "This is a sample map. Captures use your recorded location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else Unit
                    StampGroup.ADDRESS, StampGroup.CONTACT -> TextButton(onClick = { section = StudioSection.DETAILS }) {
                        Text("Edit fields in Details")
                    }
                    else -> Unit
                }
            }
            if (section == StudioSection.STYLE) {
                StampAppearanceEditor(template, logo, { section = StudioSection.LOGO }, onChange)
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .then(if (pinPreview) Modifier else Modifier.testTag("stampEditorScroll").verticalScroll(mainScroll))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    preview == null -> "Sample preview"
                    template.showMap && preview.location == null -> "Capture preview · Sample map"
                    else -> "Capture preview"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudioPreviewFrame(
                template,
                Modifier.fillMaxWidth().height(previewHeight),
                logo = logo,
                preview = preview,
                movingGroup = selectedGroup.takeIf { editingPlacement },
                placement = placement,
                onMove = { group, offset -> onChange(currentTemplate.withOffset(group, offset)) },
                showSampleMap = true,
                onSelectLayer = if (section == StudioSection.LAYERS) {
                    { group -> movingGroup = group }
                } else null,
            )
            ChoiceChips(
                values = StudioSection.entries,
                selected = section,
                label = StudioSection::label,
                tag = { "stampEditor" + it.name },
                onSelect = { section = it },
            )
            HorizontalDivider()
            when (section) {
                StudioSection.DETAILS -> StampDetailsEditor(template, preview, onChange)
                StudioSection.LAYERS, StudioSection.STYLE -> {
                    if (pinPreview) {
                        Column(
                            Modifier.weight(1f, fill = false).testTag("stampEditorScroll").verticalScroll(styleScroll),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = { appearance() },
                        )
                    } else {
                        appearance()
                    }
                }
                StudioSection.LOGO -> StampLogoEditor(
                    template, logo, importing, logoError,
                    onPick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onChange = onChange,
                )
            }
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
    movingGroup: StampGroup? = null,
    onMove: (StampGroup, StampOffset) -> Unit = { _, _ -> },
    placement: StampPreviewGeometry? = null,
    sampleSubject: String? = null,
    onSelectLayer: ((StampGroup) -> Unit)? = null,
    showSampleMap: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val image = preview?.image
    val mapFix = preview?.location ?: sampleStampLocation.takeIf { preview == null || showSampleMap }
    val map = rememberStampMap(template.showMap, mapFix, template.mapZoom, template.onlineMap && preview?.location != null)
    val details = remember(template, logo, compact, preview, map, sampleSubject) {
        val source = preview ?: StampPreview(
            project = sampleSubject ?: if (compact) "North roof" else "North roof inspection",
            note = if (sampleSubject != null) "" else if (compact) "Flashing checked" else "Flashing inspected",
            time = 1_794_302_520_000L,
            offset = 0,
            location = sampleStampLocation,
            photoCode = "A1B2C3D4",
            headingDegrees = 42f,
            recordedAddress = if (sampleSubject != null) mapOf(AddressPart.CITY to "Harbor District") else emptyMap(),
        )
        val displayTemplate = if (preview == null) template.copy(
            styledNote = template.styledNote.ifBlank { "Check before handover" },
            businessCardName = template.businessCardName.ifBlank { "Reese Lopez" },
            businessCardRole = template.businessCardRole.ifBlank { "Site lead" },
            weather = template.weather.ifBlank { "Cloudy · 24 °C" },
            recordedAddress = source.recordedAddress,
        ) else template.copy(recordedAddress = source.recordedAddress)
        stampDetails(
            project = source.project,
            note = source.note,
            time = source.time,
            offset = source.offset,
            location = source.location,
            template = displayTemplate,
            photoCode = source.photoCode,
            logo = logo,
            headingDegrees = source.headingDegrees,
            map = map,
        )
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    val geometry = placement ?: remember { StampPreviewGeometry() }
    val activeTemplate by rememberUpdatedState(template)
    val move by rememberUpdatedState(onMove)
    val selectedGroup by rememberUpdatedState(movingGroup)
    val selectLayer by rememberUpdatedState(onSelectLayer)
    val dragTarget = Modifier
        .testTag("stampDragCanvas")
        .pointerInput(geometry) {
            awaitEachGesture {
                val first = awaitFirstDown()
                val target = if (selectLayer != null) {
                    geometry.hitLayer(first.position) ?: selectedGroup
                } else selectedGroup
                if (target == null) return@awaitEachGesture
                val bounds = geometry.groups[target] ?: return@awaitEachGesture
                val frame = geometry.frame ?: return@awaitEachGesture
                if (frame.width() <= 0f || frame.height() <= 0f) return@awaitEachGesture
                val hitArea = RectF(bounds).apply { inset(-12.dp.toPx(), -12.dp.toPx()) }
                if (!hitArea.contains(first.position.x, first.position.y)) return@awaitEachGesture
                selectLayer?.invoke(target)

                val start = geometry.offsets[target] ?: activeTemplate.offsetFor(target)
                val minX = minOf(0f, frame.left - bounds.left)
                val maxX = maxOf(0f, frame.right - bounds.right)
                val minY = minOf(0f, frame.top - bounds.top)
                val maxY = maxOf(0f, frame.bottom - bounds.bottom)
                var moved = Offset.Zero
                fun applyDrag(delta: Offset) {
                    moved = Offset(
                        (moved.x + delta.x).coerceIn(minX, maxX),
                        (moved.y + delta.y).coerceIn(minY, maxY),
                    )
                    move(target, StampOffset(
                        (start.x + moved.x / frame.width()).coerceIn(-1f, 1f),
                        (start.y + moved.y / frame.height()).coerceIn(-1f, 1f),
                    ))
                }
                val afterSlop = awaitTouchSlopOrCancellation(first.id) { change, overSlop ->
                    change.consume()
                    applyDrag(overSlop)
                }
                if (afterSlop != null) {
                    drag(afterSlop.id) { change ->
                        applyDrag(change.positionChange())
                        change.consume()
                    }
                }
            }
        }
    Box(
        modifier.clip(shape).background(Color(0xff263024)),
        contentAlignment = Alignment.Center,
    ) {
        if (template.preset == null && image == null) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = Color(0xffd5dec7), modifier = Modifier.size(26.dp))
        } else {
            Canvas(
                Modifier.matchParentSize().then(if (movingGroup == null) Modifier else dragTarget).then(
                    if (compact) Modifier else Modifier.semantics {
                        contentDescription = when {
                            preview == null -> "Sample " + template.preset?.label.orEmpty() + " stamp"
                            template.preset == null -> "Capture without a stamp"
                            else -> "Capture with " + template.preset.label + " stamp"
                        }
                    },
                ),
            ) {
                val bounds = geometry.groups
                bounds.clear()
                geometry.offsets.clear()
                val onGroupBounds: ((StampGroup, RectF) -> Unit)? = movingGroup?.let {
                    { group, rect -> bounds[group] = RectF(rect) }
                }
                val onGroupOffset: ((StampGroup, StampOffset) -> Unit)? = movingGroup?.let {
                    { group, offset -> geometry.offsets[group] = offset }
                }
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
                        geometry.frame = RectF(0f, offset, size.width, previewHeight + offset)
                        it.nativeCanvas.withTranslation(y = offset) {
                            drawStamp(this, width, previewHeight, details, onGroupBounds, onGroupOffset)
                        }
                        for (rect in bounds.values) rect.offset(0f, offset)
                    }
                } else {
                    val scale = minOf(size.width / image.width, size.height / image.height)
                    val width = image.width * scale
                    val height = image.height * scale
                    val left = (size.width - width) / 2
                    val top = (size.height - height) / 2
                    geometry.frame = RectF(left, top, left + width, top + height)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.withTranslation(left, top) {
                            drawBitmap(image, null, RectF(0f, 0f, width, height), paint)
                            drawStamp(this, width.toInt(), height.toInt(), details, onGroupBounds, onGroupOffset)
                        }
                    }
                    for (rect in bounds.values) rect.offset(left, top)
                }
                val selectedBounds = movingGroup?.let(bounds::get)
                if (selectedBounds != null && !selectedBounds.isEmpty) {
                    val topLeft = Offset(selectedBounds.left, selectedBounds.top)
                    val outline = Size(selectedBounds.width(), selectedBounds.height())
                    val corner = CornerRadius(5.dp.toPx())
                    drawRoundRect(Color(0xcc16291a), topLeft, outline, corner, style = Stroke(3.dp.toPx()))
                    drawRoundRect(Color(0xffe1edac), topLeft, outline, corner, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

internal class StampPreviewGeometry {
    var frame: RectF? = null
    val groups = mutableMapOf<StampGroup, RectF>()
    val offsets = mutableMapOf<StampGroup, StampOffset>()

    fun hitLayer(position: Offset): StampGroup? {
        if (groups[StampGroup.MAP]?.contains(position.x, position.y) == true) return StampGroup.MAP
        return groups.entries.toList().asReversed().firstOrNull { (group, bounds) ->
            group != StampGroup.WHOLE && bounds.contains(position.x, position.y)
        }?.key ?: StampGroup.WHOLE.takeIf {
            groups[it]?.contains(position.x, position.y) == true
        }
    }

    fun nudge(group: StampGroup, dx: Float, dy: Float): StampOffset? {
        val position = offsets[group] ?: return null
        val rect = groups[group] ?: return null
        val image = frame ?: return null
        val width = image.width()
        val height = image.height()
        if (width <= 0f || height <= 0f) return null
        val x = dx.coerceIn(minOf(0f, (image.left - rect.left) / width), maxOf(0f, (image.right - rect.right) / width))
        val y = dy.coerceIn(minOf(0f, (image.top - rect.top) / height), maxOf(0f, (image.bottom - rect.bottom) / height))
        if (x == 0f && y == 0f) return null
        return StampOffset((position.x + x).coerceIn(-1f, 1f), (position.y + y).coerceIn(-1f, 1f))
    }
}

@Composable
private fun StampDetailsEditor(
    template: StampTemplate,
    preview: StampPreview?,
    onChange: (StampTemplate) -> Unit,
) {
    StudioHeading("Title")
    StampToggle("Show title", template.showTitle, "stampShowTitle") { onChange(template.copy(showTitle = it)) }
    StampField("Heading", template.heading, "stampHeading") { onChange(template.copy(heading = it)) }
    StampToggle("Show project", template.projectVisible, "stampShowProject") { onChange(template.copy(showProject = it)) }

    HorizontalDivider()
    StudioDisclosure("People and place", "stampPeoplePlace") {
        StampTextOption("Company", template.showCompany, template.organization, "stampCompany",
            onToggle = { onChange(template.copy(showCompany = it)) },
            onEdit = { onChange(template.copy(organization = it)) },
        )
        StampTextOption("Employee name", template.showEmployeeName, template.worker, "stampOperator",
            onToggle = { onChange(template.copy(showEmployeeName = it)) },
            onEdit = { onChange(template.copy(worker = it)) },
        )
        StampTextOption("Employee number", template.showEmployeeNumber, template.employeeNumber, "stampEmployeeNumber",
            onToggle = { onChange(template.copy(showEmployeeNumber = it)) },
            onEdit = { onChange(template.copy(employeeNumber = it)) },
        )
        StampTextOption("Site", template.showSite, template.site, "stampSite",
            onToggle = { onChange(template.copy(showSite = it)) },
            onEdit = { onChange(template.copy(site = it)) },
        )
        StampTextOption("Tags", template.showTags, template.tags, "stampTags",
            onToggle = { onChange(template.copy(showTags = it)) },
            onEdit = { onChange(template.copy(tags = it)) },
        )
    }

    HorizontalDivider()
    StudioDisclosure("Address", "stampAddressOptions") {
        StampToggle("Show address", template.showAddress, "stampShowAddress") {
            onChange(template.copy(showAddress = it))
        }
        if (template.showAddress) {
            Text("With Location on, Sitefinch fills the selected details.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AddressPart.entries.forEach { part ->
                StampToggle(part.label, part in template.addressParts, "stampAddress" + part.name) { checked ->
                    val selected = if (checked) template.addressParts + part else template.addressParts - part
                    onChange(template.copy(addressParts = selected))
                }
            }
            StampField("Manual address (optional)", template.address, "stampAddress", maxCharacters = 140) {
                onChange(template.copy(address = it))
            }
        }
    }

    HorizontalDivider()
    StudioDisclosure("Capture details", "stampCaptureFields") {
        StampToggle("Device time", template.showTime, "stampShowTime") { onChange(template.copy(showTime = it)) }
        StampToggle("Job note", template.showNote, "stampShowNote") { onChange(template.copy(showNote = it)) }
        StampToggle("Recorded coordinates", template.showCoordinates, "stampShowCoordinates") {
            onChange(template.copy(showCoordinates = it))
        }
        StampToggle("Local photo code", template.showPhotoCode, "stampShowPhotoCode") {
            onChange(template.copy(showPhotoCode = it))
        }
        StampToggle(
            "Map", template.showMap, "stampShowMap",
            detail = when {
                preview?.location != null -> "Uses the recorded position."
                else -> "Includes a map when a position is recorded."
            },
        ) { onChange(template.copy(showMap = it)) }
        if (template.showMap) {
            StampToggle(
                "Online street maps", template.onlineMap, "stampOnlineMap",
                detail = "OpenStreetMap receives requests for map tiles around your location. A regional map is available offline.",
            ) { onChange(template.copy(onlineMap = it)) }
        }
        StampToggle(
            "Altitude", template.showAltitude, "stampShowAltitude",
            detail = if (preview?.location?.altitudeMeters != null) {
                "Uses the recorded altitude."
            } else {
                "Shown when the device records altitude."
            },
        ) { onChange(template.copy(showAltitude = it)) }
        StampToggle(
            "Compass", template.showCompass, "stampShowCompass",
            detail = if (preview?.headingDegrees != null) {
                "Uses the recorded heading."
            } else {
                "Shown when the device records a compass heading."
            },
        ) { onChange(template.copy(showCompass = it)) }
    }

    HorizontalDivider()
    StudioDisclosure("Extra fields", "stampExtraFields") {
        StampToggle("Business card", template.showBusinessCard, "stampShowBusinessCard") {
            onChange(template.copy(showBusinessCard = it))
        }
        if (template.showBusinessCard) {
            StampField("Name", template.businessCardName, "stampBusinessCardName") {
                onChange(template.copy(businessCardName = it))
            }
            StampField("Role", template.businessCardRole, "stampBusinessCardRole") {
                onChange(template.copy(businessCardRole = it))
            }
            StampField("Contact", template.businessCardContact, "stampBusinessCardContact") {
                onChange(template.copy(businessCardContact = it))
            }
        }
        StampToggle("Styled note", template.showStyledNote, "stampShowStyledNote") {
            onChange(template.copy(showStyledNote = it))
        }
        if (template.showStyledNote) {
            StampField("Note text", template.styledNote, "stampStyledNote", maxCharacters = 160) {
                onChange(template.copy(styledNote = it))
            }
            ChoiceChips(
                StampNoteStyle.entries, template.styledNoteStyle, StampNoteStyle::label, { "stampNoteStyle" + it.name },
            ) { onChange(template.copy(styledNoteStyle = it)) }
        }
        StampToggle(
            "Weather", template.showWeather, "stampShowWeather",
            detail = "Enter conditions to print on this stamp.",
        ) { onChange(template.copy(showWeather = it)) }
        if (template.showWeather) {
            StampField("Conditions", template.weather, "stampWeather") { onChange(template.copy(weather = it)) }
        }
    }
}

@Composable
private fun StudioDisclosure(title: String, tag: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
        )
    }
    if (expanded) content()
}

@Composable
private fun StampTextOption(
    label: String,
    shown: Boolean,
    value: String,
    tag: String,
    onToggle: (Boolean) -> Unit,
    onEdit: (String) -> Unit,
) {
    StampToggle(label, shown, tag + "Visible", onChange = onToggle)
    if (shown) StampField(label, value, tag, onChange = onEdit)
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
    StampToggle("Show logo", template.showLogo, "stampShowLogo") { onChange(template.copy(showLogo = it)) }
    if (logo != null) {
        val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
        Canvas(
            Modifier.size(88.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics { contentDescription = "Logo crop preview" },
        ) {
            drawIntoCanvas {
                drawStampLogo(
                    it.nativeCanvas, logo, template.logoZoom, template.logoShape,
                    RectF(0f, 0f, size.width, size.height), paint,
                )
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
        StudioHeading("Logo shape")
        ChoiceChips(StampLogoShape.entries, template.logoShape, StampLogoShape::label, { "logoShape" + it.name }) {
            onChange(template.copy(logoShape = it))
        }
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
internal fun StudioHeading(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StampField(
    label: String,
    value: String,
    tag: String,
    maxCharacters: Int = 64,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(maxCharacters).dropLastWhile(Char::isHighSurrogate)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = true,
    )
}

@Composable
internal fun StampToggle(
    label: String,
    checked: Boolean,
    tag: String,
    detail: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun <T> ChoiceChips(
    values: List<T>,
    selected: T?,
    label: (T) -> String,
    tag: (T) -> String,
    swatch: ((T) -> Color)? = null,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (value in values) {
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                enabled = enabled(value),
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
