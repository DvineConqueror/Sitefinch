package app.workphotolog.android

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun CaptureNoteDialog(note: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note for next capture") },
        text = {
            OutlinedTextField(
                value, { value = it.take(500) }, label = { Text("Job note") },
                minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text("Save note") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun CaptureAddressDialog(
    address: String,
    automaticAddress: String,
    gpsEnabled: Boolean,
    onToggleGps: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by rememberSaveable { mutableStateOf(address) }
    var lookingUp by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf(emptyList<String>()) }
    fun lookUp(query: String?) {
        scope.launch {
            lookingUp = true
            matches = emptyList()
            result = null
            try {
                val found = withTimeoutOrNull(8_000) {
                    val fix = if (query == null) currentFix(context) else null
                    findAddresses(context, query, fix).mapNotNull {
                        it.takeIf { candidate -> candidate.maxAddressLineIndex >= 0 }?.getAddressLine(0)
                    }.filter(String::isNotBlank).distinct()
                }.orEmpty()
                matches = found
                if (found.isEmpty()) result = "No address found. Enter it manually."
            } finally {
                lookingUp = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Capture location") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onToggleGps) { Text(if (gpsEnabled) "Turn GPS off" else "Turn GPS on") }
                OutlinedTextField(
                    value, { value = it.take(140) }, label = { Text("Manual address (optional)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2,
                )
                Text(if (gpsEnabled && automaticAddress.isNotBlank()) "Automatic address · $automaticAddress"
                    else "Sitefinch fills the stamp address when GPS and an address lookup are available.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = gpsEnabled && !lookingUp,
                    onClick = { lookUp(null) },
                ) { Text("Find nearby addresses") }
                TextButton(
                    enabled = value.trim().length >= 3 && !lookingUp,
                    onClick = { lookUp(value.trim()) },
                ) { Text("Search by name") }
                matches.forEach { match ->
                    TextButton(onClick = {
                        value = match.take(140).dropLastWhile(Char::isHighSurrogate)
                        matches = emptyList()
                    }) { Text(match, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
                if (lookingUp) CircularProgressIndicator()
                result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text("Set address") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

internal suspend fun findAddresses(context: Context, query: String?, fix: LocationFix?): List<Address> {
    if (!Geocoder.isPresent() || (query == null && fix == null)) return emptyList()
    val geocoder = Geocoder(context)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }
                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
                if (query != null) geocoder.getFromLocationName(query, 3, listener)
                else if (fix != null) geocoder.getFromLocation(fix.latitude, fix.longitude, 3, listener)
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                when {
                    query != null -> geocoder.getFromLocationName(query, 3).orEmpty()
                    fix != null -> geocoder.getFromLocation(fix.latitude, fix.longitude, 3).orEmpty()
                    else -> emptyList()
                }
            }
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }
}
