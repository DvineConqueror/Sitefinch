package app.workphotolog.android

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SitefinchSettings(model: WorkPhotoModel, visible: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val updater = remember(context) { ReleaseUpdater(context) }
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf<Long?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var installerPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun install(path: String) {
        try {
            context.startActivity(updater.installIntent(File(path)))
        } catch (_: Exception) {
            model.message("Couldn't open the Android installer. Try updating again.")
        }
    }
    val allowInstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val path = installerPath
        installerPath = null
        if (path != null && updater.canInstall()) install(path)
        else model.message("Allow installs from Sitefinch to continue the update.")
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) {
            model.writeBackup(it)
            onClose()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            model.restoreBackup(it)
            onClose()
        }
    }

    fun check(manual: Boolean) {
        if (!updater.updatesSupported || checking || progress != null) return
        checking = true
        lastCheck = SystemClock.elapsedRealtime()
        status = if (manual) "Checking for updates…" else null
        scope.launch {
            try {
                val release = updater.check()
                available = release?.takeIf { manual || !updater.isDismissed(it) }
                status = if (manual && release == null) "You’re up to date." else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (manual) status = "Couldn't check GitHub. Check your connection and try again."
            } finally {
                checking = false
            }
        }
    }

    LifecycleResumeEffect(updater) {
        val previous = lastCheck
        if (previous == null || SystemClock.elapsedRealtime() - previous >= 6 * 60 * 60 * 1000L) {
            check(manual = false)
        }
        onPauseOrDispose { }
    }

    if (visible && available == null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Sitefinch settings") },
            text = {
                Column(
                    Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToggleRow(
                        "Save photos to Gallery",
                        "Saves the photo with its selected stamp. Clean photo saves the original.",
                        model.autoSaveToGallery,
                        model::updateAutoSaveToGallery,
                    )
                    ToggleRow(
                        "Include local code on Gallery copies",
                        "Shows the local photo code when the stamp template includes it.",
                        model.includeCodeInGallery,
                        model::updateIncludeCodeInGallery,
                    )
                    HorizontalDivider()
                    Text("Library backup", style = MaterialTheme.typography.titleMedium)
                    Text("Save your projects, captures and styles to a ZIP. Restore merges them into this library.",
                        style = MaterialTheme.typography.bodyMedium)
                    Column {
                        TextButton(enabled = !model.captureLocked, onClick = {
                            backup.launch("Sitefinch-${LocalDate.now()}.zip")
                        }) { Text("Create backup") }
                        TextButton(enabled = !model.captureLocked, onClick = {
                            restore.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                        }) { Text("Restore backup") }
                    }
                    HorizontalDivider()
                    Text("App updates", style = MaterialTheme.typography.titleMedium)
                    Text("Installed version ${updater.installedVersion}", style = MaterialTheme.typography.bodyMedium)
                    if (updater.updatesSupported) {
                        TextButton(enabled = !checking && progress == null, onClick = { check(manual = true) }) {
                            Text("Check for updates")
                        }
                    } else {
                        Text("In-app updates require the signed ARM64 build.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        )
    }

    val release = available ?: return
    if (!visible && (model.page != Page.CAMERA || model.captureLocked || model.quickPhoto != null) && progress == null) return
    fun dismiss() {
        if (progress != null) return
        updater.dismiss(release)
        available = null
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("Sitefinch ${release.version} is available") },
        text = {
            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Download from GitHub, then confirm the update in Android.")
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
                progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… ${(it * 100).toInt()}%")
                }
                if (!checking) status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = progress == null, onClick = {
                status = null
                progress = 0f
                scope.launch {
                    try {
                        val apk = updater.download(release) { progress = it }
                        if (updater.canInstall()) install(apk.path)
                        else {
                            installerPath = apk.path
                            allowInstall.launch(updater.installPermissionIntent())
                        }
                        available = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        installerPath = null
                        status = "Couldn't prepare this update. Check your connection and try again."
                    } finally {
                        progress = null
                    }
                }
            }) { Text("Download update") }
        },
        dismissButton = { TextButton(enabled = progress == null, onClick = ::dismiss) { Text("Later") } },
    )
}
