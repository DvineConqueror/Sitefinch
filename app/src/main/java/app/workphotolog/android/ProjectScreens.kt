package app.workphotolog.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext

@Composable
fun ProjectsScreen(model: WorkPhotoModel, projects: List<Project>) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val selected = model.project?.id

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (model.draft != null) "Choose where to save" else "Choose a capture project",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { creating = !creating }, enabled = !model.busy) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("New project", Modifier.padding(start = 6.dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (creating) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(64) },
                        label = { Text("Project name") },
                        enabled = !model.busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("newProjectName"),
                    )
                    Button(
                        onClick = { model.createProject(name.trim()) },
                        enabled = name.isNotBlank() && !model.busy,
                    ) { Text("Create project") }
                }
            }
            if (projects.isEmpty()) item {
                if (model.projectsFailed) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Couldn't open local storage. Check that this device has free space.")
                        TextButton(onClick = model::retryStorage) { Text("Retry storage") }
                    }
                } else {
                    Text(
                        "Opening local storage",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(projects, key = { it.id }) { project ->
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(project.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (project.id == selected) ({ Text("Selected for capture") }) else null,
                    leadingContent = {
                        Icon(
                            if (project.id == selected) Icons.Outlined.Check else Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = if (model.draft == null) ({
                        IconButton(onClick = { model.openAlbum(project) }, enabled = !model.busy) {
                            Icon(Icons.Outlined.Collections, contentDescription = "Open ${project.name} album")
                        }
                    }) else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(enabled = !model.busy) { model.selectProject(project) },
                )
            }
        }
    }
}

@Composable
fun ReviewScreen(model: WorkPhotoModel, draft: CaptureDraft, project: Project?) {
    var previewStamp by rememberSaveable(draft.file.absolutePath) { mutableStateOf(false) }
    var stampLocation by rememberSaveable(draft.file.absolutePath) { mutableStateOf(false) }
    val stamp = stampDetails(
        project?.name ?: "Project", draft.note, draft.takenAtMillis, draft.utcOffsetMinutes,
        draft.location.takeIf { stampLocation },
    )
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PhotoImage(
                draft.file, "Photo awaiting review",
                Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(4f / 3f),
                if (previewStamp) stamp else null,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Project", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(project?.name ?: "Choose a project", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = model::openProjects, enabled = !model.busy) { Text("Change") }
            }
            Text("Device time · " + displayTime(draft.takenAtMillis, draft.utcOffsetMinutes), style = MaterialTheme.typography.bodyMedium)
            Text(
                draft.location?.let { displayLocation(it) } ?: "No location recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = model::setDraftNote,
                enabled = !model.busy,
                label = { Text("Job note (optional)") },
                minLines = 2,
                maxLines = 4,
                supportingText = { Text(draft.note.length.toString() + "/500 characters") },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ToggleRow(
                "Preview job stamp",
                "You can share a stamped copy after saving.",
                previewStamp,
                { previewStamp = it },
            )
            if (previewStamp && draft.location != null) {
                ToggleRow("Show coordinates in preview", null, stampLocation, { stampLocation = it })
            }
        }
        Button(
            onClick = model::save,
            enabled = !model.busy && project != null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("saveOriginal"),
        ) {
            Text(if (model.busy) "Saving photo" else "Save original")
        }
    }
}

@Composable
fun AlbumScreen(model: WorkPhotoModel, project: Project) {
    var attempt by remember(project.id) { mutableIntStateOf(0) }
    var loadingFailed by remember(project.id) { mutableStateOf(false) }
    val flow = remember(model, project.id, attempt) {
        model.repository.photos(project.id).catch {
            loadingFailed = true
            emit(emptyList())
        }
    }
    val photos by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showShare by rememberSaveable(project.id) { mutableStateOf(false) }
    var includeLocation by rememberSaveable(project.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(photos.size.toString() + (if (photos.size == 1) " photo" else " photos"), style = MaterialTheme.typography.titleSmall)
            TextButton(
                onClick = {
                    includeLocation = false
                    showShare = true
                },
                enabled = photos.isNotEmpty() && !model.busy,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(if (model.busy) "Preparing ZIP" else "Export ZIP", Modifier.padding(start = 6.dp))
            }
        }
        if (photos.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        Icons.Outlined.Image, contentDescription = null,
                        modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary,
                    )
                    if (loadingFailed) {
                        Text("Couldn't open this album", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = {
                            loadingFailed = false
                            attempt++
                        }) { Text("Retry album") }
                    } else {
                        Text("No photos in this project", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = model::openCamera, enabled = !model.busy) { Text("Open camera") }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(photos, key = { it.id }) { photo ->
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(photo.note.ifBlank { "Work photo" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(displayTime(photo.takenAtMillis, photo.utcOffsetMinutes)) },
                        leadingContent = {
                            PhotoImage(
                                model.repository.originalFile(photo), null, Modifier.size(62.dp), reduceSize = true,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable(enabled = !model.busy) { model.showPhoto(photo) },
                    )
                }
            }
        }
    }
    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text("Export project ZIP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Shares " + photos.size + " original JPEGs, device times, job notes, photo IDs, and file hashes.")
                    if (photos.any { it.location != null }) {
                        ToggleRow(
                            "Include recorded locations", "Adds coordinates, accuracy, and fix age.",
                            includeLocation, { includeLocation = it },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showShare = false
                    model.shareProject(project, includeLocation)
                }) { Text("Share ZIP") }
            },
            dismissButton = { TextButton(onClick = { showShare = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PhotoDetailsScreen(model: WorkPhotoModel, photo: Photo, project: Project?) {
    var includeLocation by rememberSaveable(photo.id) { mutableStateOf(false) }
    var showStamp by rememberSaveable(photo.id) { mutableStateOf(false) }
    val projectName = project?.name ?: "Project"
    val stamp = stampDetails(
        projectName, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes,
        photo.location.takeIf { includeLocation },
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PhotoImage(
            model.repository.originalFile(photo), if (showStamp) "Stamped copy preview" else "Original work photo",
            Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(4f / 3f),
            if (showStamp) stamp else null,
        )
        Text(projectName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Device time · " + displayTime(photo.takenAtMillis, photo.utcOffsetMinutes))
        photo.location?.let { Text(displayLocation(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (photo.note.isNotBlank()) Text(photo.note)
        HorizontalDivider()
        Text("Photo ID", style = MaterialTheme.typography.labelMedium)
        Text(photo.id, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { model.checkIntegrity(photo) }, enabled = !model.busy) { Text("Check original file") }
        model.integrityResult?.let { matches ->
            Text(
                if (matches) "Original matches its saved SHA-256 hash." else "This file differs from its saved SHA-256 hash.",
                color = if (matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()
        Text("Share photo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Button(onClick = { model.shareOriginal(photo) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Share original", Modifier.padding(start = 8.dp))
        }
        ToggleRow("Preview stamped copy", null, showStamp, { showStamp = it })
        if (showStamp) {
            if (photo.location != null) {
                ToggleRow("Include coordinates on copy", null, includeLocation, { includeLocation = it })
            }
            OutlinedButton(
                onClick = { model.shareStamped(photo, projectName, includeLocation) },
                enabled = !model.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share stamped copy") }
        }
        if (model.busy) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally))
    }
}

private fun displayLocation(location: LocationFix): String =
    location.precision.replaceFirstChar(Char::uppercase) + " location · " + displayPosition(location)

@Composable
private fun ToggleRow(title: String, detail: String?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

private data class PhotoPreview(val image: ImageBitmap? = null, val unavailable: Boolean = false)

@Composable
private fun PhotoImage(
    file: File,
    description: String?,
    modifier: Modifier,
    stamp: StampDetails? = null,
    reduceSize: Boolean = false,
) {
    val preview by produceState(PhotoPreview(), file, stamp, reduceSize) {
        if (stamp != null) delay(140)
        value = withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeJpeg(file, if (reduceSize) 200 else 1400, mutable = stamp != null)
                if (stamp != null) drawStamp(bitmap, stamp)
                PhotoPreview(bitmap.asImageBitmap())
            } catch (_: java.io.IOException) {
                PhotoPreview(unavailable = true)
            } catch (_: IllegalArgumentException) {
                PhotoPreview(unavailable = true)
            }
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(if (reduceSize) 10.dp else 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = preview.image
        if (image != null) {
            Image(image, description, Modifier.fillMaxSize(), contentScale = if (reduceSize) ContentScale.Crop else ContentScale.Fit)
        } else if (preview.unavailable) {
            if (!reduceSize) Text("Photo preview unavailable")
            else Icon(Icons.Outlined.Image, contentDescription = null)
        } else {
            CircularProgressIndicator(Modifier.size(if (reduceSize) 18.dp else 30.dp))
        }
    }
}
