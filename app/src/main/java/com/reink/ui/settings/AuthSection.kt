package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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

// Retained for potential future use — email-based ingestion replaced cookie auth.
sealed interface TokenSyncStatus {
    data object Idle : TokenSyncStatus
    data object Syncing : TokenSyncStatus
    data class Success(val imported: Int, val matched: Int) : TokenSyncStatus
    data class Error(val message: String) : TokenSyncStatus
}

@Composable
fun AuthSection(
    substackSid: String,
    tokenSyncStatus: TokenSyncStatus,
    onSidChanged: (String) -> Unit,
    onSyncTokens: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSignedIn = substackSid.isNotBlank()
    var showAdvanced by remember { mutableStateOf(false) }
    var localSid by remember(substackSid) { mutableStateOf(substackSid) }

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "SUBSTACK AUTHENTICATION",
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
                if (isSignedIn) {
                    SignedInContent(
                        tokenSyncStatus = tokenSyncStatus,
                        onSyncTokens = onSyncTokens,
                        onSignOut = onSignOut,
                    )
                } else {
                    SignedOutContent(
                        onNavigateToSignIn = onNavigateToSignIn,
                    )
                }

                // Advanced: manual SID entry
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = if (showAdvanced) "Hide advanced" else "Advanced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = localSid,
                        onValueChange = { newValue ->
                            localSid = newValue
                            onSidChanged(newValue)
                        },
                        label = { Text("substack.sid cookie") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )

                    Text(
                        text = "To get your cookie: open substack.com in Chrome, " +
                            "press F12, go to Application > Cookies, " +
                            "find \"substack.sid\" and copy its value.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedInContent(
    tokenSyncStatus: TokenSyncStatus,
    onSyncTokens: () -> Unit,
    onSignOut: () -> Unit,
) {
    Text(
        text = "Signed in",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )

    val syncStatusText = when (tokenSyncStatus) {
        is TokenSyncStatus.Idle -> "Tap to import subscriptions"
        is TokenSyncStatus.Syncing -> "Syncing..."
        is TokenSyncStatus.Success -> buildString {
            if (tokenSyncStatus.imported > 0) {
                append("${tokenSyncStatus.imported} imported")
            }
            if (tokenSyncStatus.matched > 0) {
                if (isNotEmpty()) append(", ")
                append("${tokenSyncStatus.matched} matched")
            }
            if (isEmpty()) append("No new subscriptions")
        }
        is TokenSyncStatus.Error -> "Error: ${tokenSyncStatus.message}"
    }

    Surface(
        onClick = onSyncTokens,
        enabled = tokenSyncStatus !is TokenSyncStatus.Syncing,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = syncStatusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Surface(
        onClick = onSignOut,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Sign out",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SignedOutContent(
    onNavigateToSignIn: () -> Unit,
) {
    Surface(
        onClick = onNavigateToSignIn,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Sign in with browser",
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    Text(
        text = "Opens Substack sign-in page. You'll receive a magic link via email.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
