package app.workphotolog.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val templateSaver = Saver<StampTemplate, String>(
    save = { it.toJson() },
    restore = { StampTemplate.fromJson(it) },
)

private enum class TemplateTab(val label: String, val tag: String) {
    PRESETS("Presets", "presetStyles"), EXPLORE("Explore", "exploreStyles"), SAVED("Saved styles", "savedStyles"),
}

private enum class ExploreCategory(val label: String) {
    ALL("All"), DAILY("Daily work"), INSPECTION("Inspection"), ASSETS("Assets"), HANDOVER("Handover"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampTemplateSheet(
    template: StampTemplate,
    onApply: (StampTemplate) -> Unit,
    onDismiss: () -> Unit,
    library: Map<String, StampTemplate> = emptyMap(),
    onSaveTemplate: (String, StampTemplate) -> Unit = { _, _ -> },
    onDeleteTemplate: (String) -> Unit = {},
    preview: StampPreview? = null,
) {
    var draft by rememberSaveable(stateSaver = templateSaver) { mutableStateOf(template) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(TemplateTab.PRESETS) }
    var category by rememberSaveable { mutableStateOf(ExploreCategory.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var activeName by rememberSaveable { mutableStateOf<String?>(null) }
    var activePreset by rememberSaveable {
        mutableStateOf((starterTemplates + exploreTemplates).firstOrNull { it.template == template }?.tag)
    }
    var saving by rememberSaveable { mutableStateOf(false) }
    var removeName by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().testTag("stampSheet")) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = if (editing) 12.dp else 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    IconButton(onClick = { editing = false }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to templates")
                    }
                }
                Text(
                    if (editing) "Customize stamp" else "Templates",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close templates")
                }
            }

            val body = Modifier.weight(1f, fill = false)
            if (editing) {
                StampStudioEditor(draft, { draft = it }, body, preview)
            } else {
                Column(
                    modifier = body.verticalScroll(rememberScrollState()).fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (destination in TemplateTab.entries) {
                            FilterChip(
                                selected = tab == destination,
                                onClick = {
                                    if (tab != destination) query = ""
                                    tab = destination
                                },
                                label = { Text(destination.label) },
                                modifier = Modifier.testTag(destination.tag),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(64) },
                        label = { Text("Search styles") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) ({
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("searchTemplates"),
                    )
                    if (tab == TemplateTab.EXPLORE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (filter in ExploreCategory.entries) {
                                FilterChip(
                                    selected = category == filter,
                                    onClick = { category = filter },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("exploreCategory" + filter.name),
                                )
                            }
                        }
                    }
                    if (tab == TemplateTab.SAVED) {
                        val entries = library.entries.filter { it.key.contains(query.trim(), ignoreCase = true) }
                            .sortedBy { it.key.lowercase() }
                        if (entries.isEmpty()) {
                            Text(
                                if (library.isEmpty()) "Save a custom stamp to use it again." else "No styles match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((name, saved) in entries) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TemplateCard(
                                        name, saved, activeName == name, "savedStamp",
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        activeName = name
                                        activePreset = null
                                        draft = saved
                                    }
                                    IconButton(onClick = { removeName = name }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete " + name)
                                    }
                                }
                            }
                        }
                    } else {
                        val catalog = if (tab == TemplateTab.EXPLORE) exploreTemplates else starterTemplates
                        val search = query.trim()
                        val options = catalog.filter { option ->
                            val matchesCategory = tab != TemplateTab.EXPLORE ||
                                category == ExploreCategory.ALL || option.category == category
                            matchesCategory && (
                                option.title.contains(search, ignoreCase = true) ||
                                    option.description.contains(search, ignoreCase = true) ||
                                    option.category?.label?.contains(search, ignoreCase = true) == true
                            )
                        }
                        if (options.isEmpty()) {
                            Text("No styles match your search.", modifier = Modifier.padding(vertical = 12.dp))
                        }
                        TemplateGrid(options, draft, activePreset) { starter ->
                            activeName = null
                            if (activePreset != starter.tag) draft = starter.template
                            activePreset = starter.tag
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    TextButton(
                        onClick = { saving = true },
                        modifier = Modifier.weight(1f).testTag("saveTemplateStyle"),
                    ) { Text("Save style") }
                } else {
                    TextButton(
                        onClick = { editing = true },
                        enabled = draft.preset != null,
                        modifier = Modifier.weight(1f).testTag("editTemplate"),
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Customize", Modifier.padding(start = 6.dp))
                    }
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f).testTag("applyTemplate")) {
                    Text("Apply")
                }
            }
        }
    }
    if (saving) {
        SaveStyleDialog(activeName.orEmpty(), library.keys, onDismiss = { saving = false }) { name ->
            onSaveTemplate(name, draft)
            activeName = name
            saving = false
        }
    }
    removeName?.let { name ->
        AlertDialog(
            onDismissRequest = { removeName = null },
            title = { Text("Delete this style?") },
            text = { Text(name + " will be removed from Saved styles.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTemplate(name)
                    if (activeName == name) activeName = null
                    removeName = null
                }) { Text("Delete style") }
            },
            dismissButton = { TextButton(onClick = { removeName = null }) { Text("Cancel") } },
        )
    }
}

private data class StarterTemplate(
    val title: String,
    val description: String,
    val tag: String,
    val template: StampTemplate,
    val category: ExploreCategory? = null,
)

private val starterTemplates = listOf(
    StarterTemplate("Clean photo", "Capture without a stamp", "templateClean", StampTemplate()),
    StarterTemplate("Field note", "Record an observation", "templateFieldNote", StampTemplate.DEFAULT_COPY),
    StarterTemplate(
        "Time & place", "Show time and address", "templateClock",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.JADE, heading = "Time & place", layout = StampLayout.CLOCK),
    ),
    StarterTemplate(
        "Site visit", "Document a site check", "templateSiteVisit",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.BLUE, layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Completion", "Mark work finished", "templateCompletion",
        StampTemplate(StampPreset.COMPLETION, StampColor.AMBER, layout = StampLayout.PLAIN),
    ),
    StarterTemplate(
        "Inspection", "Record a finding", "templateInspection",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.JADE, heading = "Inspection", layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Arrival", "Log a crew’s arrival", "templateArrival",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.AMBER, heading = "Arrival", size = StampSize.COMPACT),
    ),
    StarterTemplate(
        "Safety round", "Document a safety check", "templateSafety",
        StampTemplate(StampPreset.FIELD_NOTE, StampColor.COPPER, heading = "Safety round"),
    ),
    StarterTemplate(
        "Asset record", "Identify equipment on site", "templateAsset",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.LILAC, heading = "Asset record", showPhotoCode = true),
    ),
    StarterTemplate(
        "Service visit", "Log a maintenance call", "templateService",
        StampTemplate(StampPreset.COMPLETION, StampColor.JADE, heading = "Service visit", layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Work report", "Capture a progress update", "templateReport",
        StampTemplate(StampPreset.FIELD_NOTE, StampColor.BLUE, heading = "Work report", layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Cleaning", "Record a finished clean", "templateCleaning",
        StampTemplate(StampPreset.COMPLETION, StampColor.LILAC, heading = "Cleaning", layout = StampLayout.PLAIN),
    ),
)

private val exploreTemplates = listOf(
    StarterTemplate(
        "Shift log", "Clock a crew onto the site", "exploreShiftLog",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.JADE, heading = "Shift log", layout = StampLayout.CLOCK,
            showNote = false,
        ),
        ExploreCategory.DAILY,
    ),
    StarterTemplate(
        "Progress journal", "Record a daily milestone and note", "exploreProgress",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.BLUE, heading = "Progress journal", layout = StampLayout.LEDGER,
            size = StampSize.LARGE, position = StampPosition.BOTTOM_RIGHT,
        ),
        ExploreCategory.DAILY,
    ),
    StarterTemplate(
        "Hazard report", "Make a safety note easy to spot", "exploreHazard",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.COPPER, heading = "Hazard report", layout = StampLayout.PANEL,
            showStyledNote = true, styledNoteStyle = StampNoteStyle.ALERT,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Condition survey", "Record an inspection and site conditions", "exploreCondition",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.LILAC, heading = "Condition survey", layout = StampLayout.LEDGER,
            showWeather = true, showPhotoCode = true,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Equipment audit", "Match an asset to its photo code", "exploreEquipment",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.OLIVE, heading = "Equipment audit", layout = StampLayout.PLAIN,
            showPhotoCode = true, textColor = StampTextColor.MINT,
        ),
        ExploreCategory.ASSETS,
    ),
    StarterTemplate(
        "Utility locate", "Show a recorded position and direction", "exploreUtility",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.BLUE, heading = "Utility locate", layout = StampLayout.LEDGER,
            showCoordinates = true, showMap = true, showCompass = true,
        ),
        ExploreCategory.ASSETS,
    ),
    StarterTemplate(
        "Handover brief", "Include a person and contact for follow-up", "exploreHandover",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.JADE, heading = "Handover brief", layout = StampLayout.PANEL,
            showBusinessCard = true, position = StampPosition.TOP_RIGHT,
        ),
        ExploreCategory.HANDOVER,
    ),
    StarterTemplate(
        "Closeout record", "File a finished result with its photo code", "exploreCloseout",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.AMBER, heading = "Closeout record", layout = StampLayout.PLAIN,
            showPhotoCode = true, position = StampPosition.TOP_LEFT,
        ),
        ExploreCategory.HANDOVER,
    ),
)

@Composable
private fun TemplateGrid(
    options: List<StarterTemplate>,
    draft: StampTemplate,
    activePreset: String?,
    onSelect: (StarterTemplate) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth < 290.dp || fontScale >= 1.4f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val isSelected = activePreset == option.tag
                        TemplateCard(
                            option.title, if (isSelected) draft else option.template, isSelected,
                            option.tag, Modifier.weight(1f), option.description,
                        ) { onSelect(option) }
                    }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    name: String,
    template: StampTemplate,
    selected: Boolean,
    tag: String,
    modifier: Modifier,
    description: String? = null,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val logo = rememberStampLogo(template.logoId)
        StudioPreviewFrame(template, Modifier.fillMaxWidth().heightIn(min = 88.dp), compact = true, logo = logo)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimaryContainer)
            }
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaveStyleDialog(
    initialName: String,
    names: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val existing = names.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save style") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48).dropLastWhile(Char::isHighSurrogate) },
                label = { Text("Style name") },
                supportingText = if (existing != null) ({ Text("Replaces the saved style with this name.") }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("styleName"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(existing ?: name.trim()) }, enabled = name.isNotBlank()) {
                Text(if (existing == null) "Save" else "Replace")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
