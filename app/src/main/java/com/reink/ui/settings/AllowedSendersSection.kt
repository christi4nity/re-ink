package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AllowedSendersSection(
    domains: Set<String>,
    showAddDialog: Boolean,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "EMAIL SOURCES",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (domains.isEmpty()) {
            Text(
                text = "Add a sender domain to receive non-Substack newsletters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        domains.sorted().forEach { domain ->
            DomainRow(
                domain = domain,
                onRemove = { onRemoveDomain(domain) },
            )
        }

        OutlinedButton(
            onClick = onShowAddDialog,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Add Domain", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showAddDialog) {
        AddDomainDialog(
            onDismiss = onDismissAddDialog,
            onConfirm = onAddDomain,
        )
    }
}

@Composable
private fun DomainRow(
    domain: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = domain,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Remove", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AddDomainDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var domain by remember { mutableStateOf("") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Email Source", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the domain to accept emails from (e.g. mydomain.com)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(domain)
                    onDismiss()
                },
                enabled = domain.isNotBlank() && '.' in domain,
            ) {
                Text(
                    "Add",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
