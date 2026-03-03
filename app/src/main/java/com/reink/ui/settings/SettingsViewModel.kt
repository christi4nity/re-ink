package com.reink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentials
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.model.CloudQueueConfig
import com.reink.data.model.Feed
import com.reink.data.model.ReadingPreferences
import com.reink.data.remote.CloudQueueClient
import com.reink.data.repository.EmailSyncRepository
import com.reink.data.repository.FeedRepository
import com.reink.data.repository.PreferencesRepository
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
) : ViewModel() {

    private val showAddFeedDialog = MutableStateFlow(false)
    private val showEmailConfigDialog = MutableStateFlow(false)
    private val emailTesting = MutableStateFlow(false)
    private val emailTestResult = MutableStateFlow<String?>(null)
    private val emailSyncStatus = MutableStateFlow<String?>(null)
    private val emailConfigured = MutableStateFlow(false)
    private val cloudQueueSetupInProgress = MutableStateFlow(false)
    private val cloudQueueStatus = MutableStateFlow<String?>(null)

    init {
        emailConfigured.value = emailCredentialsStore.isConfigured()
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

    val uiState: StateFlow<SettingsUiState> = combine(
        feedRepository.observeRssFeeds(),
        preferencesRepository.observeReadingPreferences(),
        showAddFeedDialog,
        emailState,
        cloudQueueState,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val feeds = flows[0] as List<Feed>
        val prefs = flows[1] as ReadingPreferences
        val showDialog = flows[2] as Boolean
        val email = flows[3] as EmailState
        val cloud = flows[4] as CloudQueueState
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
}
