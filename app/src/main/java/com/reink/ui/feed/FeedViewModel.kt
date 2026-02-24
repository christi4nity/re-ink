package com.reink.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.Article
import com.reink.data.model.Feed
import com.reink.data.repository.ArticleRepository
import com.reink.data.repository.ArticleRepository.Companion.PAGE_SIZE
import com.reink.data.repository.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ArticleSection(
    val dateHeader: String,
    val articles: List<Article>,
)

data class FeedUiState(
    val sections: List<ArticleSection> = emptyList(),
    val feeds: List<Feed> = emptyList(),
    val selectedFeedId: Long? = null,
    val unreadOnly: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val selectedFeedId = MutableStateFlow<Long?>(null)
    private val unreadOnly = MutableStateFlow(false)
    private val currentPage = MutableStateFlow(0)
    private val isSyncing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private data class FilterState(val feedId: Long?, val unreadOnly: Boolean, val page: Int)

    private val filterState = combine(selectedFeedId, unreadOnly, currentPage) { feedId, unread, page ->
        FilterState(feedId, unread, page)
    }

    private val articlesFlow = filterState.flatMapLatest { (feedId, unread, page) ->
        articleRepository.observe(feedId, unread, page).map { articles -> articles to page }
    }

    val uiState: StateFlow<FeedUiState> = combine(
        articlesFlow,
        feedRepository.observeAll(),
        filterState,
        isSyncing,
        error,
    ) { articlesWithPage, feeds, filter, syncing, err ->
        val (articles, page) = articlesWithPage
        val hasNext = articles.size > PAGE_SIZE
        val displayArticles = if (hasNext) articles.dropLast(1) else articles
        val sections = groupByDate(displayArticles)

        FeedUiState(
            sections = sections,
            feeds = feeds,
            selectedFeedId = filter.feedId,
            unreadOnly = filter.unreadOnly,
            isSyncing = syncing,
            error = err,
            currentPage = page,
            hasPrevious = page > 0,
            hasNext = hasNext,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedUiState(),
    )

    fun selectFeed(feedId: Long?) {
        selectedFeedId.value = feedId
        currentPage.value = 0
    }

    fun toggleUnreadOnly() {
        unreadOnly.value = !unreadOnly.value
        currentPage.value = 0
    }

    fun nextPage() {
        if (uiState.value.hasNext) {
            currentPage.value = currentPage.value + 1
        }
    }

    fun previousPage() {
        if (uiState.value.hasPrevious) {
            currentPage.value = currentPage.value - 1
        }
    }

    fun sync() {
        viewModelScope.launch {
            isSyncing.value = true
            error.value = null
            articleRepository.syncAllFeeds().onFailure { e ->
                error.value = e.message ?: "Sync failed"
            }
            isSyncing.value = false
        }
    }

    fun dismissError() {
        error.value = null
    }

    companion object {
        private val dayFormat = SimpleDateFormat("EEEE", Locale.US)
        private val dateFormat = SimpleDateFormat("MMM d", Locale.US)

        fun groupByDate(articles: List<Article>): List<ArticleSection> {
            if (articles.isEmpty()) return emptyList()

            val calendar = Calendar.getInstance()
            val today = clearTime(calendar)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = calendar.timeInMillis

            return articles.groupBy { article ->
                val articleCal = Calendar.getInstance().apply { timeInMillis = article.publishedAt }
                val articleDay = clearTime(articleCal)

                when {
                    articleDay >= today -> "Today"
                    articleDay >= yesterday -> "Yesterday"
                    articleDay >= today - 6 * 24 * 60 * 60 * 1000L ->
                        dayFormat.format(Date(article.publishedAt))
                    else -> dateFormat.format(Date(article.publishedAt))
                }
            }.map { (header, items) -> ArticleSection(dateHeader = header, articles = items) }
        }

        private fun clearTime(cal: Calendar): Long {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
