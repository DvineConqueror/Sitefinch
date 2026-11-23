package app.workphotolog.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickPhotoSheet(
    capture: QuickPhoto,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Photo saved",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    val status = when {
                        capture.file == null -> "The stamp couldn’t be rendered. You can retry in photo details."
                        capture.gallerySaved == false -> "Saved in Sitefinch. Gallery copy couldn’t be saved."
                        capture.gallerySaved == true && capture.stamped -> "Stamped copy saved to Gallery"
                        capture.gallerySaved == true -> "Clean photo saved to Gallery"
                        else -> "Saved in Sitefinch"
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss photo preview") }
            }
            capture.file?.let { file ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PhotoImage(
                        file, if (capture.stamped) "Stamped photo preview" else "Clean photo preview",
                        Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 280.dp).size(height = 250.dp, width = 520.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("Photo details") }
                Button(
                    onClick = onShare,
                    enabled = capture.file != null,
                    modifier = Modifier.weight(1.4f).testTag("shareQuickPhoto"),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Share", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
