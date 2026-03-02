package com.reink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentials
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.model.Feed
import com.reink.data.model.ReadingPreferences
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
) : ViewModel() {

    private val showAddFeedDialog = MutableStateFlow(false)
    private val showEmailConfigDialog = MutableStateFlow(false)
    private val emailTesting = MutableStateFlow(false)
    private val emailTestResult = MutableStateFlow<String?>(null)
    private val emailSyncStatus = MutableStateFlow<String?>(null)
    private val emailConfigured = MutableStateFlow(false)

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

    val uiState: StateFlow<SettingsUiState> = combine(
        feedRepository.observeRssFeeds(),
        preferencesRepository.observeReadingPreferences(),
        showAddFeedDialog,
        emailState,
    ) { feeds, prefs, showDialog, email ->
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
}
