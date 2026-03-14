package com.reink.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToArchive: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AppUpdateSection(
                    currentVersion = state.currentVersion,
                    availableUpdate = state.availableUpdate,
                    checkInProgress = state.updateCheckInProgress,
                    downloadInProgress = state.updateDownloadInProgress,
                    updateStatus = state.updateStatus,
                    onCheckForUpdate = { viewModel.checkForUpdate() },
                    onDownload = {
                        state.availableUpdate?.let { viewModel.downloadUpdate(it.downloadUrl) }
                    },
                    onDismiss = {
                        state.availableUpdate?.let { viewModel.dismissUpdate(it.versionName) }
                    },
                )
            }

            item {
                EmailSourcesSection(
                    emailConfigured = state.emailConfigured,
                    emailHost = state.emailHost,
                    emailUsername = state.emailUsername,
                    emailTesting = state.emailTesting,
                    emailTestResult = state.emailTestResult,
                    emailSyncStatus = state.emailSyncStatus,
                    showEmailConfigDialog = state.showEmailConfigDialog,
                    onShowEmailConfigDialog = { viewModel.showEmailConfigDialog() },
                    onDismissEmailConfigDialog = { viewModel.dismissEmailConfigDialog() },
                    onSaveEmailConfig = { viewModel.saveEmailConfig(it) },
                    onTestConnection = { viewModel.testEmailConnection() },
                    onSyncNow = { viewModel.syncEmail() },
                    onRemoveEmail = { viewModel.clearEmailConfig() },
                    domains = state.allowedSenderDomains,
                    showAddDomainDialog = state.showAddDomainDialog,
                    onShowAddDomainDialog = { viewModel.showAddDomainDialog() },
                    onDismissAddDomainDialog = { viewModel.dismissAddDomainDialog() },
                    onAddDomain = { viewModel.addAllowedSenderDomain(it) },
                    onRemoveDomain = { viewModel.removeAllowedSenderDomain(it) },
                )
            }

            item {
                DeviceSyncSection(
                    config = state.syncConfig,
                    connectInProgress = state.syncConnectInProgress,
                    syncInProgress = state.syncInProgress,
                    status = state.syncStatus,
                    lastSyncTime = state.syncLastSyncTime,
                    onConnect = { url, key -> viewModel.connectSync(url, key) },
                    onDisconnect = { viewModel.disconnectSync() },
                    onSyncNow = { viewModel.syncNow() },
                )
            }

            item {
                CrossDeviceSharingSection(
                    config = state.cloudQueueConfig,
                    setupInProgress = state.cloudQueueSetupInProgress,
                    status = state.cloudQueueStatus,
                    onSetup = { viewModel.setupCloudQueue() },
                    onDisable = { viewModel.disableCloudQueue() },
                )
            }

            item {
                FeedManagementSection(
                    feeds = state.feeds,
                    showAddDialog = state.showAddFeedDialog,
                    onShowAddDialog = { viewModel.showAddFeedDialog() },
                    onDismissAddDialog = { viewModel.dismissAddFeedDialog() },
                    onAddFeed = { title, url, requiresAuth ->
                        viewModel.addFeed(title, url, requiresAuth)
                    },
                    onDeleteFeed = { viewModel.deleteFeed(it) },
                )
            }

            item {
                Surface(
                    onClick = { onNavigateToArchive() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "Archive",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
