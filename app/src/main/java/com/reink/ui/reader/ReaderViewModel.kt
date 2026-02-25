package com.reink.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.Article
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
    val articleUrl: String? = null,
    val substackSid: String = "",
    val isExtracting: Boolean = false,
    val extractionFailed: Boolean = false,
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
    private val articleUrl = MutableStateFlow<String?>(null)
    private val substackSid = MutableStateFlow("")
    private val isExtracting = MutableStateFlow(false)
    private val extractionFailed = MutableStateFlow(false)

    /** Cached title card HTML, reused when extraction completes and we swap to local rendering. */
    private var titleCardHtml: String = ""

    val uiState: StateFlow<ReaderUiState> = combine(
        content,
        preferencesRepository.observeReadingPreferences(),
        savedForLater,
        combine(articleUrl, substackSid, isExtracting, extractionFailed, ::toExtractState),
    ) { (title, html), prefs, saved, extractState ->
        ReaderUiState(
            title = title,
            contentHtml = html,
            preferences = prefs,
            isLoading = html.isEmpty(),
            savedForLater = saved,
            articleUrl = extractState.url,
            substackSid = extractState.sid,
            isExtracting = extractState.extracting,
            extractionFailed = extractState.failed,
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
                        titleCardHtml = buildTitleCard(
                            source = feed?.title ?: "",
                            title = article.title,
                            author = article.author,
                            imageUrl = feed?.imageUrl,
                        )

                        // Always show whatever content we have immediately
                        content.value = article.title to (titleCardHtml + article.contentHtml)

                        when (article.contentStatus) {
                            Article.CONTENT_TRUNCATED,
                            Article.CONTENT_FAILED -> {
                                startWebViewExtraction(article.url, feed?.substackSubdomain)
                            }
                        }
                        articleRepository.markRead(itemId)
                    }
                }
                "readlater" -> {
                    val item = readLaterRepository.getById(itemId)
                    if (item != null) {
                        titleCardHtml = buildTitleCard(
                            source = "",
                            title = item.title,
                            author = "",
                            imageUrl = null,
                        )
                        content.value = item.title to (titleCardHtml + item.contentHtml)
                        readLaterRepository.markRead(itemId)
                    }
                }
            }
        }
    }

    private suspend fun startWebViewExtraction(url: String, @Suppress("UNUSED_PARAMETER") substackSubdomain: String?) {
        val sid = preferencesRepository.getSubstackSid()
        substackSid.value = sid
        articleUrl.value = url
        isExtracting.value = true
        extractionFailed.value = false
    }

    /**
     * Called by SubstackWebView when extraction completes.
     */
    fun onContentExtracted(html: String?, success: Boolean) {
        viewModelScope.launch {
            isExtracting.value = false
            if (success && !html.isNullOrBlank()) {
                articleRepository.updateExtractedContent(
                    itemId,
                    html,
                    Article.CONTENT_EXTRACTED,
                )
                content.value = content.value.first to (titleCardHtml + html)
                articleUrl.value = null
                extractionFailed.value = false
            } else {
                articleRepository.updateExtractedContent(
                    itemId,
                    "",
                    Article.CONTENT_FAILED,
                )
                extractionFailed.value = true
                articleUrl.value = null
            }
        }
    }

    /**
     * Retry extraction for a failed article.
     */
    fun retryExtraction() {
        viewModelScope.launch {
            val article = articleRepository.getById(itemId) ?: return@launch
            val feed = feedRepository.getById(article.feedId)
            startWebViewExtraction(article.url, feed?.substackSubdomain)
        }
    }

    private fun buildTitleCard(
        source: String,
        title: String,
        author: String,
        imageUrl: String?,
    ): String {
        val imageHtml = if (!imageUrl.isNullOrBlank()) {
            """<img class="article-header-logo" src="${escapeHtml(imageUrl)}" alt="${escapeHtml(source)}">"""
        } else ""
        val sourceHtml = if (source.isNotBlank()) {
            """<div class="article-header-source">${escapeHtml(source)}</div>"""
        } else ""
        val authorHtml = if (author.isNotBlank()) {
            """<div class="article-header-author">${escapeHtml(author)}</div>"""
        } else ""
        return """
            <div class="article-header">
                $imageHtml
                $sourceHtml
                <h1 class="article-header-title">${escapeHtml(title)}</h1>
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

    fun dismissSavedConfirmation() {
        savedForLater.value = false
    }
}

private data class ExtractState(
    val url: String?,
    val sid: String,
    val extracting: Boolean,
    val failed: Boolean,
)

private fun toExtractState(
    url: String?,
    sid: String,
    extracting: Boolean,
    failed: Boolean,
) = ExtractState(url, sid, extracting, failed)
