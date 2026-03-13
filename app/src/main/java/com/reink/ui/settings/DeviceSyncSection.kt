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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reink.data.model.SyncConfig

@Composable
fun DeviceSyncSection(
    config: SyncConfig,
    connectInProgress: Boolean,
    syncInProgress: Boolean,
    status: String?,
    lastSyncTime: String?,
    onConnect: (serverUrl: String, apiKey: String) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "DEVICE SYNC",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (config.isConfigured) {
            ConfiguredSyncContent(
                config = config,
                syncInProgress = syncInProgress,
                status = status,
                lastSyncTime = lastSyncTime,
                onDisconnect = onDisconnect,
                onSyncNow = onSyncNow,
            )
        } else {
            NotConfiguredSyncContent(
                connectInProgress = connectInProgress,
                status = status,
                onConnect = onConnect,
            )
        }
    }
}

@Composable
private fun ConfiguredSyncContent(
    config: SyncConfig,
    syncInProgress: Boolean,
    status: String?,
    lastSyncTime: String?,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = config.serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastSyncTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last sync: $lastSyncTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onSyncNow,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    color = MaterialTheme.colorScheme.surface,
                    enabled = !syncInProgress,
                ) {
                    Text(
                        text = if (syncInProgress) "Syncing..." else "Sync now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Surface(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "Disconnect",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConfiguredSyncContent(
    connectInProgress: Boolean,
    status: String?,
    onConnect: (serverUrl: String, apiKey: String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sync read state across devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.x:8073") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            if (status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onConnect(serverUrl.trim(), apiKey.trim()) },
                enabled = serverUrl.isNotBlank() && apiKey.isNotBlank() && !connectInProgress,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            ) {
                Text(
                    text = if (connectInProgress) "Connecting..." else "Connect",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
