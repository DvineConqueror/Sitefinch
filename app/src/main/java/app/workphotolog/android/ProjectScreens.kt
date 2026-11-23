package app.workphotolog.android

import android.media.MediaMetadataRetriever
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.material.icons.outlined.PlayCircleOutline
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
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
            if (model.draft == null) item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Your workspace", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${projects.size} " + if (projects.size == 1) "project on this device" else "projects on this device",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Image(painterResource(R.drawable.sitefinch_pip), null, Modifier.size(56.dp, 72.dp))
                    }
                }
            }
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
    val configured = remember(draft.stampJson) { StampTemplate.fromJson(draft.stampJson) }
    var previewStamp by rememberSaveable(draft.file.absolutePath) { mutableStateOf(configured.preset != null) }
    var stampLocation by rememberSaveable(draft.file.absolutePath) { mutableStateOf(configured.showCoordinates) }
    var editStamp by rememberSaveable { mutableStateOf(false) }
    val template = if (configured.preset == null) StampTemplate.DEFAULT_COPY else configured
    val stamp = stampDetails(
        project?.name ?: "Project", draft.note, draft.takenAtMillis, draft.utcOffsetMinutes,
        draft.location.takeIf { stampLocation }, template.copy(showCoordinates = stampLocation),
        logo = rememberStampLogo(template.logoId),
    )
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val mediaSize = Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(4f / 3f)
            if (draft.isVideo) {
                ClipPreview(draft.file, mediaSize)
            } else {
                PhotoImage(
                    draft.file, "Photo awaiting review", mediaSize,
                    if (previewStamp) stamp else null,
                )
            }
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
            if (draft.isVideo) {
                Text(
                    configured.displayName?.let { "Recorded with $it stamp" } ?: "Clean clip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        configured.displayName ?: "Clean photo",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editStamp = true }, enabled = !model.busy) { Text("Templates") }
                }
                ToggleRow(
                    "Preview job stamp",
                    "Save original keeps the photo clean. Share a stamped copy after saving.",
                    previewStamp,
                    { previewStamp = it },
                )
                if (previewStamp && draft.location != null) {
                    ToggleRow("Show coordinates in preview", null, stampLocation, { stampLocation = it })
                }
            }
        }
        Button(
            onClick = model::save,
            enabled = !model.busy && project != null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("saveOriginal"),
        ) {
            Text(when {
                model.busy -> "Saving"
                draft.isVideo -> "Save clip"
                else -> "Save original"
            })
        }
    }
    if (editStamp) {
        val image = rememberStampBackdrop(draft.file)
        StampTemplateSheet(
            configured,
            onApply = {
                model.applyDraftStamp(it)
                previewStamp = it.preset != null
                stampLocation = it.showCoordinates
                editStamp = false
            },
            onDismiss = { editStamp = false },
            library = model.stampLibrary,
            onSaveTemplate = model::saveStamp,
            onDeleteTemplate = model::deleteStamp,
            preview = StampPreview(
                image, project?.name ?: "Project", draft.note, draft.takenAtMillis, draft.utcOffsetMinutes, draft.location,
            ),
        )
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
    val photoCount = photos.count { !it.isVideo }
    val clipCount = photos.size - photoCount
    var showShare by rememberSaveable(project.id) { mutableStateOf(false) }
    var includeLocation by rememberSaveable(project.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val countLabel = buildList {
                add("$photoCount " + if (photoCount == 1) "photo" else "photos")
                if (clipCount > 0) add("$clipCount " + if (clipCount == 1) "clip" else "clips")
            }.joinToString(" · ")
            Text(countLabel, style = MaterialTheme.typography.titleSmall)
            TextButton(
                onClick = {
                    includeLocation = false
                    showShare = true
                },
                enabled = photoCount > 0 && !model.busy,
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
                        Text("No captures in this project", style = MaterialTheme.typography.titleMedium)
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
                            Text(
                                photo.note.ifBlank { if (photo.isVideo) "Work clip" else "Work photo" },
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text(displayTime(photo.takenAtMillis, photo.utcOffsetMinutes)) },
                        leadingContent = {
                            MediaThumbnail(model.repository.originalFile(photo), photo.isVideo, Modifier.size(62.dp))
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
                    Text("Shares $photoCount original JPEGs with device times, job notes, photo IDs, and file hashes.")
                    if (clipCount > 0) Text("Share videos individually from the album.")
                    if (photos.any { !it.isVideo && it.location != null }) {
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
    var editStamp by rememberSaveable(photo.id) { mutableStateOf(false) }
    var editMarks by rememberSaveable(photo.id) { mutableStateOf(false) }
    var marks by remember(photo.id) { mutableStateOf(emptyList<PhotoMark>()) }
    val recordedTemplate = remember(photo.stampJson) { StampTemplate.fromJson(photo.stampJson) }
    var copyStampJson by rememberSaveable(photo.id) {
        mutableStateOf((if (recordedTemplate.preset == null) StampTemplate.DEFAULT_COPY else recordedTemplate).toJson())
    }
    var showStamp by rememberSaveable(photo.id) { mutableStateOf(!photo.isVideo && recordedTemplate.preset != null) }
    val template = remember(copyStampJson) { StampTemplate.fromJson(copyStampJson) }
    val projectName = project?.name ?: "Project"
    val logo = rememberStampLogo(template.logoId)
    val stamp = if (showStamp && template.preset != null) {
        stampDetails(
            projectName, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes,
            photo.location.takeIf { includeLocation }, template.copy(showCoordinates = includeLocation),
            photoCode = photo.id, logo = logo,
        )
    } else null
    val file = model.repository.originalFile(photo)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val mediaSize = Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(4f / 3f)
        if (photo.isVideo) {
            ClipPreview(file, mediaSize)
            Text(recordedTemplate.displayName?.let { "Recorded with $it stamp" } ?: "Clean clip")
        } else {
            PhotoImage(
                file, if (stamp != null || marks.isNotEmpty()) "Photo copy preview" else "Original work photo",
                mediaSize, stamp, marks = marks,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editStamp = true }, enabled = !model.busy, modifier = Modifier.weight(1f)) {
                    Text("Edit stamp")
                }
                OutlinedButton(onClick = { editMarks = true }, enabled = !model.busy, modifier = Modifier.weight(1f)) {
                    Text("Markup")
                }
            }
        }
        Text(projectName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Device time · " + displayTime(photo.takenAtMillis, photo.utcOffsetMinutes))
        photo.location?.let { Text(displayLocation(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (photo.note.isNotBlank()) Text(photo.note)
        HorizontalDivider()
        Text(if (photo.isVideo) "Clip ID" else "Photo ID", style = MaterialTheme.typography.labelMedium)
        Text(photo.id, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { model.checkIntegrity(photo) }, enabled = !model.busy) { Text("Check original file") }
        model.integrityResult?.let { matches ->
            Text(
                if (matches) "Original matches its saved SHA-256 hash." else "This file differs from its saved SHA-256 hash.",
                color = if (matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()
        Text(if (photo.isVideo) "Sharing" else "Share photo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Button(onClick = { model.shareOriginal(photo) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (photo.isVideo) "Share clip" else "Share original", Modifier.padding(start = 8.dp))
        }
        if (!photo.isVideo) {
            if (template.preset != null) ToggleRow("Include stamp on copy", null, showStamp, { showStamp = it })
            if (stamp != null && photo.location != null) {
                ToggleRow("Include GPS coordinates on copy", null, includeLocation, { includeLocation = it })
            }
            if (stamp != null || marks.isNotEmpty()) {
                OutlinedButton(
                    onClick = { model.sharePhotoCopy(photo, stamp, marks) },
                    enabled = !model.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (marks.isEmpty()) "Share stamped copy" else "Share marked-up copy") }
            }
        }
        if (model.busy) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally))
    }
    if (editStamp) {
        val image = rememberStampBackdrop(file)
        StampTemplateSheet(
            if (showStamp) template else StampTemplate(),
            onApply = {
                copyStampJson = it.toJson()
                showStamp = it.preset != null
                editStamp = false
            },
            onDismiss = { editStamp = false },
            library = model.stampLibrary,
            onSaveTemplate = model::saveStamp,
            onDeleteTemplate = model::deleteStamp,
            preview = StampPreview(
                image, projectName, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes, photo.location, photo.id,
            ),
        )
    }
    if (editMarks) {
        PhotoMarkupEditor(file, stamp, marks, onApply = {
            marks = it
            editMarks = false
        }, onDismiss = { editMarks = false })
    }
}

@Composable
private fun rememberStampBackdrop(file: File): android.graphics.Bitmap? {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                decodeJpeg(file, 660)
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
    return bitmap
}

@Composable
internal fun rememberStampLogo(id: String?): android.graphics.Bitmap? {
    val context = LocalContext.current
    val logo by produceState<Pair<String?, android.graphics.Bitmap?>>(null to null, id) {
        value = id to StampLogoStore(context).loadLogo(id)
    }
    return logo.second.takeIf { logo.first == id }
}

@Composable
internal fun MediaThumbnail(file: File, video: Boolean, modifier: Modifier) {
    if (!video) {
        PhotoImage(file, null, modifier, reduceSize = true)
        return
    }
    val frame by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(file.absolutePath)
                reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, 200)?.asImageBitmap()
            } catch (_: RuntimeException) {
                null
            } finally {
                reader.release()
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center) {
        frame?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Icon(
            Icons.Outlined.PlayCircleOutline, null, Modifier.size(20.dp),
            tint = if (frame != null) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ClipPreview(file: File, modifier: Modifier) {
    var failed by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center) {
        if (failed) {
            Text("Video preview unavailable")
        } else if (playing) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        contentDescription = "Work clip player"
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener {
                            start()
                            controller.show()
                        }
                        setOnCompletionListener {
                            controller.hide()
                            playing = false
                        }
                        setOnErrorListener { _, _, _ ->
                            controller.hide()
                            failed = true
                            true
                        }
                        setVideoPath(file.absolutePath)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.stopPlayback() },
            )
        } else {
            MediaThumbnail(file, true, Modifier.fillMaxSize())
            Button(onClick = { playing = true }) { Text("Play clip") }
        }
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
    marks: List<PhotoMark> = emptyList(),
) {
    val preview by produceState(PhotoPreview(), file, stamp, reduceSize, marks) {
        if (stamp != null) delay(140)
        value = withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeJpeg(file, if (reduceSize) 200 else 1400, mutable = stamp != null || marks.isNotEmpty())
                if (stamp != null) drawStamp(bitmap, stamp)
                if (marks.isNotEmpty()) drawPhotoMarks(android.graphics.Canvas(bitmap), bitmap.width, bitmap.height, marks)
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
