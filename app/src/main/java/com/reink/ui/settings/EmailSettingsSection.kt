package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EmailSettingsSection(
    emailConfigured: Boolean,
    emailHost: String,
    emailUsername: String,
    emailTesting: Boolean,
    emailTestResult: String?,
    emailSyncStatus: String?,
    showConfigDialog: Boolean,
    onShowConfigDialog: () -> Unit,
    onDismissConfigDialog: () -> Unit,
    onSaveConfig: (com.reink.data.email.EmailCredentials) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "EMAIL INBOX",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (emailConfigured) {
                    ConfiguredContent(
                        host = emailHost,
                        username = emailUsername,
                        testing = emailTesting,
                        testResult = emailTestResult,
                        syncStatus = emailSyncStatus,
                        onTestConnection = onTestConnection,
                        onSyncNow = onSyncNow,
                        onRemove = onRemove,
                    )
                } else {
                    NotConfiguredContent(
                        onSetup = onShowConfigDialog,
                    )
                }
            }
        }

        Text(
            text = "Connect a dedicated email inbox to receive full paid Substack content. " +
                "Forward your Substack emails to this inbox.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showConfigDialog) {
        EmailConfigDialog(
            onDismiss = onDismissConfigDialog,
            onSave = onSaveConfig,
        )
    }
}

@Composable
private fun ConfiguredContent(
    host: String,
    username: String,
    testing: Boolean,
    testResult: String?,
    syncStatus: String?,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onRemove: () -> Unit,
) {
    Text(
        text = username,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = host,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        onClick = onTestConnection,
        enabled = !testing,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = if (testing) "Testing..." else "Test connection",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (testResult != null) {
        Text(
            text = testResult,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        onClick = onSyncNow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Sync email now",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (syncStatus != null) {
        Text(
            text = syncStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        onClick = onRemove,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Remove email inbox",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NotConfiguredContent(
    onSetup: () -> Unit,
) {
    Surface(
        onClick = onSetup,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Set up email inbox",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
