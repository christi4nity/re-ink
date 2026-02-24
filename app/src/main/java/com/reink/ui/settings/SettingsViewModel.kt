package com.reink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.Feed
import com.reink.data.model.ReadingPreferences
import com.reink.data.remote.SubstackAuthInterceptor
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val preferencesRepository: PreferencesRepository,
    private val authInterceptor: SubstackAuthInterceptor,
) : ViewModel() {

    private val showAddFeedDialog = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        feedRepository.observeAll(),
        preferencesRepository.observeSubstackSid(),
        preferencesRepository.observeReadingPreferences(),
        showAddFeedDialog,
    ) { feeds, sid, prefs, showDialog ->
        SettingsUiState(
            feeds = feeds,
            substackSid = sid,
            readingPreferences = prefs,
            showAddFeedDialog = showDialog,
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
        }
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

    fun updateReadingPreferences(prefs: ReadingPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateReadingPreferences(prefs)
        }
    }
}
