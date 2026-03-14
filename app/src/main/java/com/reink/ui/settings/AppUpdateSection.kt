package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reink.data.model.AppUpdate

@Composable
fun AppUpdateSection(
    currentVersion: String,
    availableUpdate: AppUpdate?,
    checkInProgress: Boolean,
    downloadInProgress: Boolean,
    updateStatus: String?,
    onCheckForUpdate: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "APP VERSION",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "v$currentVersion",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (availableUpdate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Update available: v${availableUpdate.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (availableUpdate.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = availableUpdate.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                            color = MaterialTheme.colorScheme.surface,
                            enabled = !downloadInProgress,
                        ) {
                            Text(
                                text = if (downloadInProgress) "Downloading..." else "Download",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else {
                    if (updateStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        onClick = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                        enabled = !checkInProgress,
                    ) {
                        Text(
                            text = if (checkInProgress) "Checking..." else "Check for updates",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
