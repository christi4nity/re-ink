package com.reink.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.ReadingPreferences
import com.reink.data.repository.ArticleRepository
import com.reink.data.repository.PreferencesRepository
import com.reink.data.repository.ReadLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val title: String = "",
    val contentHtml: String = "",
    val preferences: ReadingPreferences = ReadingPreferences(),
    val isLoading: Boolean = true,
    val savedForLater: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val articleRepository: ArticleRepository,
    private val readLaterRepository: ReadLaterRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val itemType: String = savedStateHandle["itemType"] ?: "article"
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    private val content = MutableStateFlow<Pair<String, String>>("" to "")
    private val savedForLater = MutableStateFlow(false)

    val uiState: StateFlow<ReaderUiState> = combine(
        content,
        preferencesRepository.observeReadingPreferences(),
        savedForLater,
    ) { (title, html), prefs, saved ->
        ReaderUiState(
            title = title,
            contentHtml = html,
            preferences = prefs,
            isLoading = html.isEmpty(),
            savedForLater = saved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderUiState(),
    )

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            when (itemType) {
                "article" -> {
                    val article = articleRepository.getById(itemId)
                    if (article != null) {
                        content.value = article.title to article.contentHtml
                        articleRepository.markRead(itemId)
                    }
                }
                "readlater" -> {
                    val item = readLaterRepository.getById(itemId)
                    if (item != null) {
                        content.value = item.title to item.contentHtml
                        readLaterRepository.markRead(itemId)
                    }
                }
            }
        }
    }

    fun saveForLater(url: String) {
        viewModelScope.launch {
            val sourceId = if (itemType == "article") itemId else null
            readLaterRepository.save(url, sourceId)
            savedForLater.value = true
        }
    }

    fun dismissSavedConfirmation() {
        savedForLater.value = false
    }
}
