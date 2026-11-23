package app.workphotolog.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkPhotoTheme { WorkPhotoApp() }
        }
    }
}

private val DayColors = lightColorScheme(
    primary = Color(0xff284e39),
    onPrimary = Color.White,
    primaryContainer = Color(0xffdcebd5),
    onPrimaryContainer = Color(0xff163223),
    secondaryContainer = Color(0xffe4ebd9),
    onSecondaryContainer = Color(0xff243821),
    surface = Color(0xfff8f5ec),
    onSurface = Color(0xff202a22),
    surfaceVariant = Color(0xffe4e7d8),
    onSurfaceVariant = Color(0xff424739),
    outline = Color(0xff727766),
    outlineVariant = Color(0xffc5cbba),
    background = Color(0xfff8f5ec),
    onBackground = Color(0xff22251b),
    surfaceContainer = Color(0xffebeee0),
    surfaceContainerHighest = Color(0xffe4e7d8),
)

private val NightColors = darkColorScheme(
    primary = Color(0xffc0d692),
    onPrimary = Color(0xff273810),
    primaryContainer = Color(0xff374d20),
    onPrimaryContainer = Color(0xffe0efbf),
    secondaryContainer = Color(0xff3d4d36),
    onSecondaryContainer = Color(0xffe3eedb),
    surface = Color(0xff1d2118),
    onSurface = Color(0xffeef0e7),
    surfaceVariant = Color(0xff404638),
    onSurfaceVariant = Color(0xffc5ccba),
    outline = Color(0xff8d9684),
    outlineVariant = Color(0xff535b4c),
    background = Color(0xff1d2118),
    onBackground = Color(0xffeef0e7),
    surfaceContainer = Color(0xff282d22),
    surfaceContainerHighest = Color(0xff404638),
)

@Composable
private fun WorkPhotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) NightColors else DayColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkPhotoApp(model: WorkPhotoModel = viewModel()) {
    val context = LocalContext.current
    val projects by model.projects.collectAsStateWithLifecycle()
    val currentProject = projects.firstOrNull { it.id == model.selectedProjectId } ?: projects.firstOrNull()
    val snackbar = remember { SnackbarHostState() }
    var showPhotoSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = model.page != Page.CAMERA || model.captureLocked) {
        if (model.page == Page.CAMERA) model.stopVideo() else model.back()
    }
    LaunchedEffect(model.notice) {
        val message = model.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        if (model.notice == message) model.clearNotice()
    }
    LaunchedEffect(model.shareRequest) {
        val share = model.shareRequest ?: return@LaunchedEffect
        model.clearShareRequest()
        try {
            context.shareFile(share)
        } catch (_: RuntimeException) {
            model.message("Couldn't open Android sharing.")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    val title = when (model.page) {
                        Page.CAMERA -> "Sitefinch"
                        Page.REVIEW -> if (model.draft?.isVideo == true) "Review clip" else "Review photo"
                        Page.PROJECTS -> "Projects"
                        Page.ALBUM -> currentProject?.name ?: "Project photos"
                        Page.PHOTO -> if (model.activePhoto?.isVideo == true) "Clip details" else "Photo details"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (model.page == Page.CAMERA) {
                            Image(
                                painterResource(R.drawable.sitefinch_mark), contentDescription = null,
                                modifier = Modifier.size(34.dp).background(Color(0xfff0e6ce), RoundedCornerShape(10.dp)),
                            )
                        }
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    if (model.page != Page.CAMERA) {
                        IconButton(onClick = model::back, enabled = !model.busy) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (model.page == Page.CAMERA) {
                        IconButton(onClick = { showPhotoSettings = true }, enabled = !model.captureLocked) {
                            Icon(Icons.Outlined.Settings, "Photo settings")
                        }
                    }
                },
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when (model.page) {
                Page.CAMERA -> CameraPage(model, currentProject)
                Page.REVIEW -> model.draft?.let { ReviewScreen(model, it, currentProject) }
                Page.PROJECTS -> ProjectsScreen(model, projects)
                Page.ALBUM -> currentProject?.let { AlbumScreen(model, it) }
                Page.PHOTO -> model.activePhoto?.let { PhotoDetailsScreen(model, it, currentProject) }
            }
        }
    }

    model.quickPhoto?.let { capture ->
        QuickPhotoSheet(capture, model::shareQuickPhoto, model::openQuickPhotoDetails, model::dismissQuickPhoto)
    }
    if (showPhotoSettings) {
        AlertDialog(
            onDismissRequest = { showPhotoSettings = false },
            title = { Text("Photo settings") },
            text = {
                ToggleRow(
                    "Save photos to Gallery automatically",
                    "Saves the photo with its selected stamp. Clean photo saves the original.",
                    model.autoSaveToGallery,
                    model::updateAutoSaveToGallery,
                )
            },
            confirmButton = { TextButton(onClick = { showPhotoSettings = false }) { Text("Done") } },
        )
    }

    if (model.showDiscard) {
        AlertDialog(
            onDismissRequest = model::dismissDiscard,
            title = { Text(if (model.draft?.isVideo == true) "Discard this clip?" else "Discard this photo?") },
            text = { Text("The capture and your review notes will be deleted.") },
            confirmButton = { TextButton(onClick = model::discard) { Text("Discard capture") } },
            dismissButton = { TextButton(onClick = model::dismissDiscard) { Text("Keep reviewing") } },
        )
    }
}

@Composable
private fun CameraPage(model: WorkPhotoModel, project: Project?) {
    val activity = LocalActivity.current ?: return
    val lifecycle = LocalLifecycleOwner.current
    var cameraGranted by remember { mutableStateOf(activity.hasPermission(Manifest.permission.CAMERA)) }
    var locationGranted by remember { mutableStateOf(activity.hasLocationPermission()) }
    var askedForCamera by rememberSaveable { mutableStateOf(false) }
    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    var recordAudio by rememberSaveable { mutableStateOf(false) }
    var askedForAudio by rememberSaveable { mutableStateOf(false) }
    var session by remember { mutableStateOf<BoundCamera?>(null) }
    var frontFacing by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var showZoom by rememberSaveable { mutableStateOf(false) }
    var studioImage by remember { mutableStateOf<Bitmap?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    val status = model.recordingStatus
    val mode = model.captureMode
    val canConfigure = !model.captureLocked
    val recentFlow = remember(project?.id) {
        project?.let { model.repository.recent(it.id).catch { emit(null) } } ?: flowOf(null)
    }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = null)
    var bindFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val activeLocation = model.useLocation && locationGranted
    val template = model.stampTemplate
    val lensFacing = session?.camera?.cameraInfo?.lensFacing
    val heading = rememberCaptureHeading(
        cameraGranted && (template.showCompass || showTemplates),
        frontFacing = lensFacing?.let { it == CameraSelector.LENS_FACING_FRONT } ?: frontFacing,
    )
    val needsAddress = activeLocation && (showTemplates || showAddress || (template.preset != null && template.showAddress))
    val showLocationPreview = activeLocation && (template.usesRecordedLocation || showTemplates || needsAddress)
    val viewfinderFix by produceState<LocationFix?>(null, showLocationPreview) {
        value = null
        while (showLocationPreview) {
            value = currentFix(activity)
            delay(30_000)
        }
    }
    val previewFix = viewfinderFix.takeIf { showLocationPreview }
    val addressFix = previewFix.takeIf { needsAddress }
    val automaticAddress by produceState<Map<AddressPart, String>>(
        emptyMap(), addressFix?.latitude, addressFix?.longitude, addressFix?.precision,
    ) {
        value = emptyMap()
        value = resolveStampAddress(activity, addressFix)
        while (addressFix != null && value.isEmpty()) {
            delay(30_000)
            value = resolveStampAddress(activity, addressFix)
        }
    }
    val previewAddress = automaticAddress.takeIf { addressFix != null }.orEmpty()
    val previewMap = rememberStampMap(template.showMap || showTemplates, previewFix)
    val cameraRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = activity.hasLocationPermission()
        model.setLocation(locationGranted)
    }

    val audioRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        recordAudio = it
    }

    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cameraGranted = activity.hasPermission(Manifest.permission.CAMERA)
                    locationGranted = activity.hasLocationPermission()
                    if (!activity.hasPermission(Manifest.permission.RECORD_AUDIO)) recordAudio = false
                }
                Lifecycle.Event.ON_STOP -> model.stopVideo()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = model::openProjects,
                enabled = canConfigure,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f, fill = false).testTag("projectPicker"),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    project?.name ?: "Projects",
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { showAddress = true },
                enabled = canConfigure,
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Icon(
                    if (activeLocation) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                    null, Modifier.size(18.dp),
                )
                Text(
                    if (activeLocation) "Location on" else "Location off",
                    Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xff22281d)),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraGranted && !bindFailed) {
                CameraPreview(
                    retry, mode, frontFacing, Modifier.fillMaxSize(),
                    onBound = { session = it },
                    onError = {
                        session = null
                        bindFailed = true
                    },
                    onRecordingError = {
                        model.stopVideo()
                        model.message("Couldn't draw the video stamp.")
                    },
                )
                val bound = session
                if (bound != null) {
                    if (project != null && model.stampTemplate.preset != null) {
                        LiveStamp(
                            project.name, template.copy(recordedAddress = previewAddress), model.nextCaptureNote,
                            previewFix, heading, previewMap,
                            Modifier.matchParentSize(),
                        )
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (bound.camera.cameraInfo.hasFlashUnit()) {
                            PreviewTool(
                                if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                                if (torchOn) "Turn light off" else "Turn light on", canConfigure,
                            ) {
                                torchOn = !torchOn
                                bound.camera.cameraControl.enableTorch(torchOn)
                            }
                        }
                        if (mode == CaptureMode.VIDEO) {
                            PreviewTool(
                                if (recordAudio) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                                if (recordAudio) "Mute clip recording" else "Record clip with sound", canConfigure,
                            ) {
                                if (activity.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                    recordAudio = !recordAudio
                                } else if (askedForAudio && !activity.shouldShowRequestPermissionRationale(
                                        Manifest.permission.RECORD_AUDIO,
                                    )
                                ) {
                                    activity.openApplicationSettings()
                                } else {
                                    askedForAudio = true
                                    audioRequest.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        if (bound.canFlip) {
                            PreviewTool(Icons.Outlined.Cameraswitch, "Switch camera", canConfigure) {
                                session = null
                                torchOn = false
                                zoom = 1f
                                frontFacing = !frontFacing
                            }
                        }
                        PreviewTool(
                            Icons.Outlined.EditNote,
                            if (model.nextCaptureNote.isBlank()) "Add capture note" else "Edit capture note",
                            canConfigure,
                        ) { showNote = true }
                    }
                    val limits = bound.camera.cameraInfo.zoomState.value
                    if (limits != null && limits.maxZoomRatio > limits.minZoomRatio) {
                        TextButton(
                            onClick = { showZoom = true },
                            enabled = canConfigure,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                                .background(Color(0xcc20271d), CircleShape),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(String.format(Locale.ROOT, "%.1f×", zoom), color = Color.White)
                        }
                    }
                    if (status != null) {
                        val seconds = status.elapsedSeconds
                        Text(
                            (if (status.paused) "Paused " else "") +
                                String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                                .background(Color(0xdd762b26), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (bound == null || model.busy || model.recordingFinishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = Color(0xffe1ebcc),
                    )
                }
            } else {
                val cameraIsBlocked = askedForCamera &&
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xffd5e0c4),
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        if (bindFailed) "Camera unavailable" else "Camera access needed",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = Color(0xfff0f3e9),
                    )
                    Button(
                        onClick = {
                            when {
                                bindFailed -> {
                                    bindFailed = false
                                    retry++
                                }
                                cameraIsBlocked -> activity.openApplicationSettings()
                                else -> {
                                    askedForCamera = true
                                    cameraRequest.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                    ) {
                        Text(
                            when {
                                bindFailed -> "Retry camera"
                                cameraIsBlocked -> "Open camera settings"
                                else -> "Allow camera"
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { project?.let(model::openAlbum) },
                enabled = project != null && canConfigure,
                modifier = Modifier.weight(1f),
            ) {
                if (recent != null) {
                    val item = recent ?: return@TextButton
                    CameraControl("Album") {
                        MediaThumbnail(model.repository.originalFile(item), item.isVideo, Modifier.size(28.dp))
                    }
                } else {
                    CameraControl("Album") {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(22.dp))
                    }
                }
            }
            val ready = cameraGranted && session != null && project != null && !model.busy && !model.recordingFinishing
            val shutterLabel = when {
                status != null -> "Stop recording"
                mode == CaptureMode.VIDEO -> "Record clip"
                else -> "Take photo"
            }
            FilledIconButton(
                onClick = {
                    val bound = session
                    when {
                        status != null -> model.stopVideo()
                        bound?.video != null -> model.startVideo(bound.video, recordAudio, heading)
                        bound?.image != null -> model.capture(bound.image, heading)
                    }
                },
                enabled = ready || (status != null && !model.recordingFinishing),
                modifier = Modifier.size(74.dp).semantics { contentDescription = shutterLabel }
                    .testTag("shutter"),
            ) {
                Box(
                    modifier = Modifier.size(54.dp).border(
                        2.dp, shutterRingColor(ready || status != null), CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (status != null) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(32.dp))
                    } else if (mode == CaptureMode.VIDEO) {
                        Box(Modifier.size(34.dp).background(Color(0xffff9c8e), CircleShape))
                    }
                }
            }
            TextButton(
                onClick = {
                    if (status != null) {
                        model.toggleVideoPause()
                    } else {
                        studioImage = session?.view?.stampBackdrop()
                        showTemplates = true
                    }
                },
                enabled = !model.busy && !model.recordingFinishing,
                modifier = Modifier.weight(1f).testTag("templates"),
            ) {
                val label = when {
                    status?.paused == true -> "Resume"
                    status != null -> "Pause"
                    else -> "Templates"
                }
                CameraControl(label) {
                    val icon = when {
                        status?.paused == true -> Icons.Outlined.PlayArrow
                        status != null -> Icons.Outlined.Pause
                        else -> Icons.Outlined.Description
                    }
                    Icon(icon, null, Modifier.size(22.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            CaptureMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = {
                        if (mode != option) {
                            session = null
                            bindFailed = false
                            torchOn = false
                            zoom = 1f
                            model.setCaptureMode(option)
                        }
                    },
                    label = { Text(if (option == CaptureMode.PHOTO) "Photo" else "Video") },
                    enabled = canConfigure,
                    modifier = Modifier.testTag(if (option == CaptureMode.PHOTO) "photoMode" else "videoMode"),
                )
            }
        }
    }
    if (showTemplates) {
        StampTemplateSheet(
            model.stampTemplate,
            onApply = {
                model.applyStampTemplate(it)
                showTemplates = false
                studioImage = null
            },
            onDismiss = { showTemplates = false; studioImage = null },
            library = model.stampLibrary,
            onSaveTemplate = model::saveStamp,
            onDeleteTemplate = model::deleteStamp,
            preview = project?.let {
                val instant = remember { Instant.now() }
                StampPreview(
                    image = studioImage,
                    project = it.name,
                    note = model.nextCaptureNote,
                    time = instant.toEpochMilli(),
                    offset = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / 60,
                    location = previewFix,
                    headingDegrees = heading,
                    map = previewMap,
                    recordedAddress = previewAddress,
                )
            },
        )
    }
    if (showNote) {
        CaptureNoteDialog(model.nextCaptureNote, onSave = {
            model.updateNextCaptureNote(it)
            showNote = false
        }, onDismiss = { showNote = false })
    }
    if (showAddress) {
        CaptureAddressDialog(
            address = model.stampTemplate.address,
            automaticAddress = template.copy(address = "", showAddress = true, recordedAddress = previewAddress)
                .displayAddress(includeRecorded = true),
            gpsEnabled = activeLocation,
            onToggleGps = {
                when {
                    locationGranted -> model.setLocation(!model.useLocation)
                    askedForLocation && !activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) -> activity.openApplicationSettings()
                    else -> {
                        askedForLocation = true
                        locationRequest.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                }
            },
            onSave = {
                val stamp = model.stampTemplate
                val selection = if (stamp.preset == null && it.isNotEmpty()) {
                    stamp.copy(preset = StampPreset.FIELD_NOTE, address = it)
                } else {
                    stamp.copy(address = it)
                }
                model.applyStampTemplate(selection)
                showAddress = false
            },
            onDismiss = { showAddress = false },
        )
    }
    val zoomCamera = session?.camera
    val zoomRange = zoomCamera?.cameraInfo?.zoomState?.value
    if (showZoom && zoomCamera != null && zoomRange != null && zoomRange.minZoomRatio < zoomRange.maxZoomRatio) {
        val minimum = zoomRange.minZoomRatio
        val maximum = zoomRange.maxZoomRatio
        AlertDialog(
            onDismissRequest = { showZoom = false },
            title = { Text("Camera zoom") },
            text = {
                Column {
                    Text(String.format(Locale.ROOT, "%.1f×", zoom))
                    Slider(
                        value = zoom.coerceIn(minimum, maximum),
                        onValueChange = {
                            zoom = it
                            zoomCamera.cameraControl.setZoomRatio(it)
                        },
                        valueRange = minimum..maximum,
                        modifier = Modifier.semantics { contentDescription = "Camera zoom" },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(minimum, 1f, 2f).filter { it in minimum..maximum }.distinct().forEach { ratio ->
                            TextButton(onClick = {
                                zoom = ratio
                                zoomCamera.cameraControl.setZoomRatio(ratio)
                            }) { Text(String.format(Locale.ROOT, "%.1f×", ratio)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showZoom = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun LiveStamp(
    project: String,
    template: StampTemplate,
    note: String,
    location: LocationFix?,
    heading: Float?,
    map: Bitmap?,
    modifier: Modifier,
) {
    val logo = rememberStampLogo(template.logoId.takeIf { template.showLogo })
    val instant by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000L - value.toEpochMilli() % 60_000L)
        }
    }
    val details = remember(project, template, note, instant, logo, location, heading, map) {
        val offset = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / 60
        stampDetails(
            project, note, instant.toEpochMilli(), offset, location, template,
            logo = logo, headingDegrees = heading, map = map,
        )
    }
    Canvas(modifier) {
        drawIntoCanvas { canvas -> drawStamp(canvas.nativeCanvas, size.width.toInt(), size.height.toInt(), details) }
    }
}

@Composable
private fun PreviewTool(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.background(Color(0xcc20271d), CircleShape)) {
        Icon(icon, label, tint = Color.White)
    }
}

@Composable
private fun CameraControl(label: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Text(label, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun shutterRingColor(enabled: Boolean): Color = if (enabled) {
    MaterialTheme.colorScheme.onPrimary
} else {
    IconButtonDefaults.filledIconButtonColors().disabledContentColor
}

private fun Activity.openApplicationSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
}

private data class BoundCamera(
    val camera: Camera,
    val image: ImageCapture?,
    val video: VideoCamera?,
    val canFlip: Boolean,
    val view: PreviewView,
)

private fun PreviewView.stampBackdrop(): Bitmap? {
    val frame = bitmap ?: return null
    val scale = minOf(1f, 512f / maxOf(frame.width, frame.height))
    if (scale == 1f) return frame
    val small = Bitmap.createScaledBitmap(
        frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true,
    )
    if (small !== frame) frame.recycle()
    return small
}

@Composable
private fun CameraPreview(
    retry: Int,
    mode: CaptureMode,
    frontFacing: Boolean,
    modifier: Modifier,
    onBound: (BoundCamera?) -> Unit,
    onError: () -> Unit,
    onRecordingError: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context, retry, mode, frontFacing) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    key(view) {
        AndroidView(factory = { view }, modifier = modifier)
    }

    DisposableEffect(owner, view) {
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build()
        val image = if (mode == CaptureMode.PHOTO) ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() else null
        val video = if (mode == CaptureMode.VIDEO) VideoCamera { onRecordingError() } else null
        val capture = image ?: checkNotNull(video).capture
        var provider: ProcessCameraProvider? = null
        var bound: BoundCamera? = null
        var disposed = false
        val executor = ContextCompat.getMainExecutor(context)
        val orientation = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                val rotation = when (degrees) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    ORIENTATION_UNKNOWN -> return
                    else -> Surface.ROTATION_0
                }
                image?.targetRotation = rotation
                if (video != null && bound != null) video.updateRotation(rotation)
            }
        }
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            onBound(bound.takeIf { state == PreviewView.StreamState.STREAMING })
        }
        view.previewStreamState.observe(owner, streamObserver)
        future.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val selector = if ((frontFacing && hasFront) || !hasBack) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val rotation = view.display?.rotation ?: Surface.ROTATION_0
                    image?.targetRotation = rotation
                    video?.updateRotation(rotation)
                    preview.setSurfaceProvider(view.surfaceProvider)
                    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture)
                    if (video != null) {
                        group.addEffect(video.effect)
                        view.viewPort?.let(group::setViewPort)
                    }
                    val camera = cameraProvider.bindToLifecycle(owner, selector, group.build())
                    bound = BoundCamera(camera, image, video, hasBack && hasFront, view)
                    if (view.previewStreamState.value == PreviewView.StreamState.STREAMING) onBound(bound)
                    if (orientation.canDetectOrientation()) orientation.enable()
                } catch (_: Exception) {
                    onError()
                }
            }
        }, executor)

        onDispose {
            disposed = true
            orientation.disable()
            view.previewStreamState.removeObserver(streamObserver)
            provider?.unbind(preview, capture)
            video?.close()
            onBound(null)
        }
    }
}
