package com.reink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.webkit.CookieManager
import com.reink.data.email.EmailContentSource
import com.reink.data.email.EmailCredentials
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.model.Feed
import com.reink.data.model.ReadingPreferences
import com.reink.data.remote.SubstackAuthInterceptor
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
    val substackSid: String = "",
    val readingPreferences: ReadingPreferences = ReadingPreferences(),
    val showAddFeedDialog: Boolean = false,
    val tokenSyncStatus: TokenSyncStatus = TokenSyncStatus.Idle,
    val emailConfigured: Boolean = false,
    val emailHost: String = "",
    val emailUsername: String = "",
    val emailTesting: Boolean = false,
    val emailTestResult: String? = null,
    val emailSyncStatus: String? = null,
    val showEmailConfigDialog: Boolean = false,
)

sealed interface TokenSyncStatus {
    data object Idle : TokenSyncStatus
    data object Syncing : TokenSyncStatus
    data class Success(val imported: Int, val matched: Int) : TokenSyncStatus
    data class Error(val message: String) : TokenSyncStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val preferencesRepository: PreferencesRepository,
    private val authInterceptor: SubstackAuthInterceptor,
    private val emailCredentialsStore: EmailCredentialsStore,
    private val emailContentSource: EmailContentSource,
    private val emailSyncRepository: EmailSyncRepository,
) : ViewModel() {

    private val showAddFeedDialog = MutableStateFlow(false)
    private val tokenSyncStatus = MutableStateFlow<TokenSyncStatus>(TokenSyncStatus.Idle)
    private val showEmailConfigDialog = MutableStateFlow(false)
    private val emailTesting = MutableStateFlow(false)
    private val emailTestResult = MutableStateFlow<String?>(null)
    private val emailSyncStatus = MutableStateFlow<String?>(null)
    private val emailConfigured = MutableStateFlow(false)

    init {
        emailConfigured.value = emailCredentialsStore.isConfigured()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        feedRepository.observeAll(),
        preferencesRepository.observeSubstackSid(),
        preferencesRepository.observeReadingPreferences(),
        showAddFeedDialog,
        tokenSyncStatus,
    ) { feeds, sid, prefs, showDialog, syncStatus ->
        val creds = emailCredentialsStore.get()
        SettingsUiState(
            feeds = feeds,
            substackSid = sid,
            readingPreferences = prefs,
            showAddFeedDialog = showDialog,
            tokenSyncStatus = syncStatus,
            emailConfigured = emailConfigured.value,
            emailHost = creds?.host ?: "",
            emailUsername = creds?.username ?: "",
            emailTesting = emailTesting.value,
            emailTestResult = emailTestResult.value,
            emailSyncStatus = emailSyncStatus.value,
            showEmailConfigDialog = showEmailConfigDialog.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun updateSubstackSid(sid: String) {
        viewModelScope.launch {
            preferencesRepository.setSubstackSid(sid)
            authInterceptor.clearCache()
            if (sid.isNotBlank()) {
                syncTokens(sid)
            }
        }
    }

    fun syncTokens() {
        viewModelScope.launch {
            val sid = preferencesRepository.getSubstackSid()
            if (sid.isNotBlank()) {
                syncTokens(sid)
            }
        }
    }

    private suspend fun syncTokens(sid: String) {
        tokenSyncStatus.value = TokenSyncStatus.Syncing
        feedRepository.syncSubscriptions(sid).fold(
            onSuccess = { result ->
                tokenSyncStatus.value = TokenSyncStatus.Success(
                    imported = result.imported,
                    matched = result.matched,
                )
            },
            onFailure = { error ->
                tokenSyncStatus.value = TokenSyncStatus.Error(
                    error.message ?: "Sync failed",
                )
            },
        )
    }

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
            // Match the new feed against subscriptions if SID is available
            val sid = preferencesRepository.getSubstackSid()
            if (sid.isNotBlank()) {
                syncTokens(sid)
            }
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

    fun signOut() {
        viewModelScope.launch {
            preferencesRepository.setSubstackSid("")
            authInterceptor.clearCache()
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            tokenSyncStatus.value = TokenSyncStatus.Idle
        }
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
