package com.reink.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.ReadingPreferences
import com.reink.data.repository.ArticleRepository
import com.reink.data.repository.FeedRepository
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
    private val feedRepository: FeedRepository,
    private val readLaterRepository: ReadLaterRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val itemType: String = savedStateHandle["itemType"] ?: "article"
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    private val content = MutableStateFlow<Pair<String, String>>("" to "")
    private val savedForLater = MutableStateFlow(false)

    private val footerHtml = """
        <hr class="article-footer-divider">
        <div class="article-footer">
            <a class="article-footer-button" href="reink://back">Back to Feed</a>
        </div>
    """.trimIndent()

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
                        val feed = feedRepository.getById(article.feedId)
                        val titleCard = buildTitleCard(
                            source = feed?.title ?: "",
                            title = article.title,
                            subtitle = article.summary,
                            author = article.author,
                            imageUrl = feed?.imageUrl,
                        )
                        content.value = article.title to (titleCard + article.contentHtml + footerHtml)
                        articleRepository.markRead(itemId)
                    }
                }
                "readlater" -> {
                    val item = readLaterRepository.getById(itemId)
                    if (item != null) {
                        val titleCard = buildTitleCard(
                            source = "",
                            title = item.title,
                            subtitle = "",
                            author = "",
                            imageUrl = null,
                        )
                        content.value = item.title to (titleCard + item.contentHtml + footerHtml)
                        readLaterRepository.markRead(itemId)
                    }
                }
            }
        }
    }

    private fun buildTitleCard(
        source: String,
        title: String,
        subtitle: String,
        author: String,
        imageUrl: String?,
    ): String {
        val imageHtml = if (!imageUrl.isNullOrBlank()) {
            """<img class="article-header-logo" src="${escapeHtml(imageUrl)}" alt="${escapeHtml(source)}">"""
        } else ""
        val sourceHtml = if (source.isNotBlank()) {
            """<div class="article-header-source">${escapeHtml(source)}</div>"""
        } else ""
        val subtitleHtml = if (subtitle.isNotBlank()) {
            """<div class="article-header-subtitle">${escapeHtml(subtitle)}</div>"""
        } else ""
        val authorHtml = if (author.isNotBlank() && !author.equals(source, ignoreCase = true)) {
            """<div class="article-header-author">${escapeHtml(author)}</div>"""
        } else ""
        return """
            <div class="article-header">
                $imageHtml
                $sourceHtml
                <h1 class="article-header-title">${escapeHtml(title)}</h1>
                $subtitleHtml
                $authorHtml
            </div>
            <hr class="article-header-divider">
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    fun saveForLater(url: String) {
        viewModelScope.launch {
            val sourceId = if (itemType == "article") itemId else null
            readLaterRepository.save(url, sourceId)
            savedForLater.value = true
        }
    }

    fun updateReadingPreferences(prefs: ReadingPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateReadingPreferences(prefs)
        }
    }

    fun dismissSavedConfirmation() {
        savedForLater.value = false
    }
}
