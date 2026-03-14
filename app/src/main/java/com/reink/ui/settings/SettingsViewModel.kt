package com.reink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.BuildConfig
import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentials
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.model.AppUpdate
import com.reink.data.model.CloudQueueConfig
import com.reink.data.model.Feed
import com.reink.data.model.ReadingPreferences
import com.reink.data.model.SyncConfig
import com.reink.data.remote.CloudQueueClient
import com.reink.data.remote.SyncClient
import com.reink.data.remote.UpdateChecker
import com.reink.data.repository.EmailSyncRepository
import com.reink.data.repository.FeedRepository
import com.reink.data.repository.PreferencesRepository
import com.reink.data.repository.SyncRepository
import com.reink.update.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val feeds: List<Feed> = emptyList(),
    val readingPreferences: ReadingPreferences = ReadingPreferences(),
    val showAddFeedDialog: Boolean = false,
    val emailConfigured: Boolean = false,
    val emailHost: String = "",
    val emailUsername: String = "",
    val emailTesting: Boolean = false,
    val emailTestResult: String? = null,
    val emailSyncStatus: String? = null,
    val showEmailConfigDialog: Boolean = false,
    val cloudQueueConfig: CloudQueueConfig = CloudQueueConfig(),
    val cloudQueueSetupInProgress: Boolean = false,
    val cloudQueueStatus: String? = null,
    val syncConfig: SyncConfig = SyncConfig(),
    val syncConnectInProgress: Boolean = false,
    val syncInProgress: Boolean = false,
    val syncStatus: String? = null,
    val syncLastSyncTime: String? = null,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val availableUpdate: AppUpdate? = null,
    val updateCheckInProgress: Boolean = false,
    val updateDownloadInProgress: Boolean = false,
    val updateStatus: String? = null,
)

private data class EmailState(
    val showDialog: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val syncStatus: String? = null,
    val configured: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val preferencesRepository: PreferencesRepository,
    private val emailCredentialsStore: EmailCredentialsStore,
    private val emailContentSource: EmailContentSource,
    private val emailSyncRepository: EmailSyncRepository,
    private val cloudQueueClient: CloudQueueClient,
    private val syncClient: SyncClient,
    private val syncRepository: SyncRepository,
    private val updateChecker: UpdateChecker,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val showAddFeedDialog = MutableStateFlow(false)
    private val showEmailConfigDialog = MutableStateFlow(false)
    private val emailTesting = MutableStateFlow(false)
    private val emailTestResult = MutableStateFlow<String?>(null)
    private val emailSyncStatus = MutableStateFlow<String?>(null)
    private val emailConfigured = MutableStateFlow(false)
    private val cloudQueueSetupInProgress = MutableStateFlow(false)
    private val cloudQueueStatus = MutableStateFlow<String?>(null)
    private val syncConnectInProgress = MutableStateFlow(false)
    private val syncInProgress = MutableStateFlow(false)
    private val syncStatus = MutableStateFlow<String?>(null)
    private val syncLastSyncTime = MutableStateFlow<String?>(null)
    private val updateCheckInProgress = MutableStateFlow(false)
    private val updateDownloadInProgress = MutableStateFlow(false)
    private val updateStatus = MutableStateFlow<String?>(null)

    init {
        emailConfigured.value = emailCredentialsStore.isConfigured()
        viewModelScope.launch {
            val lastSync = preferencesRepository.getSyncLastSyncedAt()
            if (lastSync > 0) {
                syncLastSyncTime.value = formatSyncTime(lastSync)
            }
        }
    }

    private val emailState = combine(
        showEmailConfigDialog,
        emailTesting,
        emailTestResult,
        emailSyncStatus,
        emailConfigured,
    ) { showDialog, testing, testResult, syncStatus, configured ->
        EmailState(showDialog, testing, testResult, syncStatus, configured)
    }

    private data class CloudQueueState(
        val config: CloudQueueConfig = CloudQueueConfig(),
        val setupInProgress: Boolean = false,
        val status: String? = null,
    )

    private val cloudQueueState = combine(
        preferencesRepository.observeCloudQueueConfig(),
        cloudQueueSetupInProgress,
        cloudQueueStatus,
    ) { config, setting, status ->
        CloudQueueState(config, setting, status)
    }

    private data class DeviceSyncState(
        val config: SyncConfig = SyncConfig(),
        val connectInProgress: Boolean = false,
        val inProgress: Boolean = false,
        val status: String? = null,
        val lastSyncTime: String? = null,
    )

    private val deviceSyncState = combine(
        preferencesRepository.observeSyncConfig(),
        syncConnectInProgress,
        syncInProgress,
        syncStatus,
        syncLastSyncTime,
    ) { config, connecting, syncing, status, lastSync ->
        DeviceSyncState(config, connecting, syncing, status, lastSync)
    }

    private data class UpdateState(
        val availableUpdate: AppUpdate? = null,
        val checkInProgress: Boolean = false,
        val downloadInProgress: Boolean = false,
        val status: String? = null,
    )

    private val updateState = combine(
        preferencesRepository.observeAvailableUpdate(),
        updateCheckInProgress,
        updateDownloadInProgress,
        updateStatus,
    ) { update, checking, downloading, status ->
        UpdateState(update, checking, downloading, status)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        feedRepository.observeRssFeeds(),
        preferencesRepository.observeReadingPreferences(),
        showAddFeedDialog,
        emailState,
        cloudQueueState,
        deviceSyncState,
        updateState,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val feeds = flows[0] as List<Feed>
        val prefs = flows[1] as ReadingPreferences
        val showDialog = flows[2] as Boolean
        val email = flows[3] as EmailState
        val cloud = flows[4] as CloudQueueState
        val sync = flows[5] as DeviceSyncState
        val update = flows[6] as UpdateState
        val creds = emailCredentialsStore.get()
        SettingsUiState(
            feeds = feeds,
            readingPreferences = prefs,
            showAddFeedDialog = showDialog,
            emailConfigured = email.configured,
            emailHost = creds?.host ?: "",
            emailUsername = creds?.username ?: "",
            emailTesting = email.testing,
            emailTestResult = email.testResult,
            emailSyncStatus = email.syncStatus,
            showEmailConfigDialog = email.showDialog,
            cloudQueueConfig = cloud.config,
            cloudQueueSetupInProgress = cloud.setupInProgress,
            cloudQueueStatus = cloud.status,
            syncConfig = sync.config,
            syncConnectInProgress = sync.connectInProgress,
            syncInProgress = sync.inProgress,
            syncStatus = sync.status,
            syncLastSyncTime = sync.lastSyncTime,
            availableUpdate = update.availableUpdate,
            updateCheckInProgress = update.checkInProgress,
            updateDownloadInProgress = update.downloadInProgress,
            updateStatus = update.status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun addFeed(title: String, url: String, requiresAuth: Boolean) {
        viewModelScope.launch {
            feedRepository.add(
                Feed(
                    title = title,
                    url = url,
                    requiresAuth = requiresAuth,
                ),
            )
            showAddFeedDialog.value = false
        }
    }

    fun deleteFeed(id: Long) {
        viewModelScope.launch {
            feedRepository.delete(id)
        }
    }

    fun showAddFeedDialog() {
        showAddFeedDialog.value = true
    }

    fun dismissAddFeedDialog() {
        showAddFeedDialog.value = false
    }

    fun showEmailConfigDialog() {
        showEmailConfigDialog.value = true
    }

    fun dismissEmailConfigDialog() {
        showEmailConfigDialog.value = false
    }

    fun saveEmailConfig(credentials: EmailCredentials) {
        viewModelScope.launch {
            emailCredentialsStore.save(credentials)
            emailConfigured.value = true
            showEmailConfigDialog.value = false
            testEmailConnection()
        }
    }

    fun testEmailConnection() {
        viewModelScope.launch {
            emailTesting.value = true
            emailTestResult.value = null
            emailContentSource.testConnection().fold(
                onSuccess = { emailTestResult.value = it },
                onFailure = { emailTestResult.value = "Error: ${it.message}" },
            )
            emailTesting.value = false
        }
    }

    fun syncEmail() {
        viewModelScope.launch {
            emailSyncStatus.value = "Syncing..."
            emailSyncRepository.syncEmails().fold(
                onSuccess = { result ->
                    emailSyncStatus.value = buildString {
                        if (result.upgraded > 0) append("${result.upgraded} upgraded")
                        if (result.inserted > 0) {
                            if (isNotEmpty()) append(", ")
                            append("${result.inserted} new")
                        }
                        if (isEmpty()) append("No new articles from email")
                    }
                },
                onFailure = { emailSyncStatus.value = "Error: ${it.message}" },
            )
        }
    }

    fun clearEmailConfig() {
        emailCredentialsStore.clear()
        emailConfigured.value = false
        emailTestResult.value = null
        emailSyncStatus.value = null
    }

    fun updateReadingPreferences(prefs: ReadingPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateReadingPreferences(prefs)
        }
    }

    fun setupCloudQueue() {
        viewModelScope.launch {
            cloudQueueSetupInProgress.value = true
            cloudQueueStatus.value = null
            cloudQueueClient.createQueue().fold(
                onSuccess = { queueId ->
                    preferencesRepository.setCloudQueueConfig(
                        CloudQueueConfig(
                            enabled = true,
                            queueId = queueId,
                            baseUrl = CloudQueueClient.DEFAULT_BASE_URL,
                        ),
                    )
                    cloudQueueStatus.value = "Cloud queue created"
                },
                onFailure = {
                    cloudQueueStatus.value = "Setup failed: ${it.message}"
                },
            )
            cloudQueueSetupInProgress.value = false
        }
    }

    fun disableCloudQueue() {
        viewModelScope.launch {
            preferencesRepository.clearCloudQueue()
            cloudQueueStatus.value = null
        }
    }

    fun connectSync(serverUrl: String, apiKey: String) {
        viewModelScope.launch {
            syncConnectInProgress.value = true
            syncStatus.value = null
            syncClient.healthCheck(serverUrl).fold(
                onSuccess = {
                    val deviceId = java.util.UUID.randomUUID().toString().take(8)
                    preferencesRepository.setSyncConfig(
                        SyncConfig(
                            enabled = true,
                            serverUrl = serverUrl,
                            apiKey = apiKey,
                            deviceId = deviceId,
                        ),
                    )
                    syncStatus.value = "Connected"
                },
                onFailure = {
                    syncStatus.value = "Connection failed: ${it.message}"
                },
            )
            syncConnectInProgress.value = false
        }
    }

    fun disconnectSync() {
        viewModelScope.launch {
            preferencesRepository.clearSyncConfig()
            syncStatus.value = null
            syncLastSyncTime.value = null
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncInProgress.value = true
            syncStatus.value = null
            syncRepository.sync().fold(
                onSuccess = { response ->
                    val total = response.feeds.size + response.articles.size + response.readLater.size
                    syncStatus.value = if (total > 0) {
                        "Synced $total changes"
                    } else {
                        "Up to date"
                    }
                    syncLastSyncTime.value = formatSyncTime(response.syncedAt)
                },
                onFailure = {
                    syncStatus.value = "Sync failed: ${it.message}"
                },
            )
            syncInProgress.value = false
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            updateCheckInProgress.value = true
            updateStatus.value = null
            updateChecker.check().fold(
                onSuccess = { update ->
                    if (update != null) {
                        preferencesRepository.setAvailableUpdate(
                            versionName = update.versionName,
                            downloadUrl = update.downloadUrl,
                            releaseNotes = update.releaseNotes,
                        )
                    } else {
                        updateStatus.value = "Up to date"
                    }
                },
                onFailure = {
                    updateStatus.value = "Check failed: ${it.message}"
                },
            )
            updateCheckInProgress.value = false
        }
    }

    fun downloadUpdate(downloadUrl: String) {
        viewModelScope.launch {
            updateDownloadInProgress.value = true
            apkInstaller.download(downloadUrl).fold(
                onSuccess = {
                    preferencesRepository.setUpdateReady(true)
                    updateDownloadInProgress.value = false
                    apkInstaller.install()
                },
                onFailure = {
                    updateStatus.value = "Download failed: ${it.message}"
                    updateDownloadInProgress.value = false
                },
            )
        }
    }

    fun dismissUpdate(versionName: String) {
        viewModelScope.launch {
            preferencesRepository.dismissUpdate(versionName)
        }
    }

    private fun formatSyncTime(timestamp: Long): String {
        val formatter = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }
}
