package app.workphotolog.android

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    primary = Color(0xff465d28),
    onPrimary = Color.White,
    primaryContainer = Color(0xffd9e7ba),
    onPrimaryContainer = Color(0xff172609),
    surface = Color(0xfff6f4eb),
    onSurface = Color(0xff22251b),
    surfaceVariant = Color(0xffe4e7d8),
    onSurfaceVariant = Color(0xff424739),
    outline = Color(0xff727766),
    background = Color(0xfff6f4eb),
    onBackground = Color(0xff22251b),
    surfaceContainer = Color(0xffebeee0),
)

private val NightColors = darkColorScheme(
    primary = Color(0xffc0d692),
    onPrimary = Color(0xff273810),
    primaryContainer = Color(0xff374d20),
    onPrimaryContainer = Color(0xffe0efbf),
    surface = Color(0xff1d2118),
    onSurface = Color(0xffeef0e7),
    surfaceVariant = Color(0xff404638),
    onSurfaceVariant = Color(0xffc5ccba),
    outline = Color(0xff8d9684),
    background = Color(0xff1d2118),
    onBackground = Color(0xffeef0e7),
    surfaceContainer = Color(0xff282d22),
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

    BackHandler(enabled = model.page != Page.CAMERA || model.busy, onBack = model::back)
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
                        Page.CAMERA -> "Work Photo Log"
                        Page.REVIEW -> "Review photo"
                        Page.PROJECTS -> "Projects"
                        Page.ALBUM -> currentProject?.name ?: "Project photos"
                        Page.PHOTO -> "Photo details"
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (model.page != Page.CAMERA) {
                        IconButton(onClick = model::back, enabled = !model.busy) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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

    if (model.showDiscard) {
        AlertDialog(
            onDismissRequest = model::dismissDiscard,
            title = { Text("Discard this photo?") },
            text = { Text("The photo and your review notes will be deleted.") },
            confirmButton = { TextButton(onClick = model::discard) { Text("Discard photo") } },
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
    var useCase by remember { mutableStateOf<ImageCapture?>(null) }
    var bindFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val activeLocation = model.useLocation && locationGranted
    val cameraRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = activity.hasLocationPermission()
        model.setLocation(locationGranted)
    }

    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = activity.hasPermission(Manifest.permission.CAMERA)
                locationGranted = activity.hasLocationPermission()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(
            onClick = model::openProjects,
            enabled = !model.busy,
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.testTag("projectPicker"),
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                project?.name ?: "Projects",
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xff22281d)),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraGranted && !bindFailed) {
                CameraPreview(retry, Modifier.fillMaxSize(), onBound = { useCase = it }, onError = {
                    useCase = null
                    bindFailed = true
                })
                if (useCase == null || model.busy) {
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { project?.let(model::openAlbum) },
                enabled = project != null && !model.busy,
                modifier = Modifier.weight(1f),
            ) {
                CameraControl(Icons.Outlined.FolderOpen, "Album")
            }
            val shutter = useCase
            val shutterReady = cameraGranted && shutter != null && project != null && !model.busy
            FilledIconButton(
                onClick = { shutter?.let(model::capture) },
                enabled = shutterReady,
                modifier = Modifier.size(74.dp).semantics { contentDescription = "Take photo" }
                    .testTag("shutter"),
            ) {
                Box(
                    modifier = Modifier.size(54.dp).border(
                        2.dp, shutterRingColor(shutterReady), CircleShape,
                    ),
                )
            }
            TextButton(
                onClick = {
                    if (locationGranted) {
                        model.setLocation(!model.useLocation)
                    } else if (askedForLocation && !activity.shouldShowRequestPermissionRationale(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    ) {
                        activity.openApplicationSettings()
                    } else {
                        askedForLocation = true
                        locationRequest.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                },
                enabled = !model.busy,
                modifier = Modifier.weight(1f),
            ) {
                CameraControl(
                    if (activeLocation) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                    if (activeLocation) "Location on" else "Location off",
                )
            }
        }
    }
}

@Composable
private fun CameraControl(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
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

@Composable
private fun CameraPreview(
    retry: Int,
    modifier: Modifier,
    onBound: (ImageCapture?) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { view }, modifier = modifier)

    DisposableEffect(owner, view, retry) {
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val executor = ContextCompat.getMainExecutor(context)
        val orientation = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                capture.targetRotation = when (degrees) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    ORIENTATION_UNKNOWN -> return
                    else -> Surface.ROTATION_0
                }
            }
        }
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            onBound(capture.takeIf { state == PreviewView.StreamState.STREAMING })
        }
        view.previewStreamState.observe(owner, streamObserver)
        future.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val camera = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                    preview.setSurfaceProvider(view.surfaceProvider)
                    cameraProvider.bindToLifecycle(owner, camera, preview, capture)
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
            onBound(null)
        }
    }
}
