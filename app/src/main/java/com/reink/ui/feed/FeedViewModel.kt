package com.reink.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.model.Article
import com.reink.data.model.Feed
import com.reink.data.repository.ArticleRepository
import com.reink.data.repository.EmailSyncRepository
import com.reink.data.repository.FeedRepository
import com.reink.ui.home.groupByDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleSection(
    val dateHeader: String,
    val articles: List<Article>,
)

data class FeedUiState(
    val sections: List<ArticleSection> = emptyList(),
    val feeds: List<Feed> = emptyList(),
    val feedTitles: Map<Long, String> = emptyMap(),
    val selectedFeedId: Long? = null,
    val unreadOnly: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val feedRepository: FeedRepository,
    private val emailSyncRepository: EmailSyncRepository,
    private val emailCredentialsStore: EmailCredentialsStore,
) : ViewModel() {

    private val selectedFeedId = MutableStateFlow<Long?>(null)
    private val unreadOnly = MutableStateFlow(true)
    private val isSyncing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    init {
        sync()
    }

    private data class FilterState(val feedId: Long?, val unreadOnly: Boolean)

    private val filterState = combine(selectedFeedId, unreadOnly) { feedId, unread ->
        FilterState(feedId, unread)
    }

    private val articlesFlow = filterState.flatMapLatest { (feedId, unread) ->
        articleRepository.observe(feedId, unread)
    }

    val uiState: StateFlow<FeedUiState> = combine(
        articlesFlow,
        feedRepository.observeAll(),
        filterState,
        isSyncing,
        error,
    ) { articles, feeds, filter, syncing, err ->
        FeedUiState(
            sections = groupByDate(articles) { it.publishedAt }.map {
                ArticleSection(dateHeader = it.dateHeader, articles = it.items)
            },
            feeds = feeds,
            feedTitles = feeds.associate { it.id to it.title },
            selectedFeedId = filter.feedId,
            unreadOnly = filter.unreadOnly,
            isSyncing = syncing,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedUiState(),
    )

    fun selectFeed(feedId: Long?) {
        selectedFeedId.value = feedId
    }

    fun toggleUnreadOnly() {
        unreadOnly.value = !unreadOnly.value
    }

    fun sync() {
        viewModelScope.launch {
            isSyncing.value = true
            error.value = null
            articleRepository.syncAllFeeds().onFailure { e ->
                error.value = e.message ?: "Feed sync failed"
            }
            if (emailCredentialsStore.isConfigured()) {
                emailSyncRepository.syncEmails().onFailure { e ->
                    error.value = e.message ?: "Email sync failed"
                }
            }
            isSyncing.value = false
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            articleRepository.markAllReadBefore(startOfToday)
        }
    }

    fun archiveArticle(id: Long) {
        viewModelScope.launch {
            articleRepository.archive(id)
        }
    }

    fun deleteArticle(id: Long) {
        viewModelScope.launch {
            articleRepository.delete(id)
        }
    }

    fun dismissError() {
        error.value = null
    }

}
