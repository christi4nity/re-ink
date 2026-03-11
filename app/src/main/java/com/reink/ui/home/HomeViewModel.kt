package com.reink.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.model.Feed
import com.reink.data.remote.CloudQueueClient
import com.reink.data.repository.ArticleRepository
import com.reink.data.repository.EmailSyncRepository
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

data class HomeUiState(
    val sections: List<DateSection<HomeItem>> = emptyList(),
    val isSyncing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val readLaterRepository: ReadLaterRepository,
    private val feedRepository: FeedRepository,
    private val emailSyncRepository: EmailSyncRepository,
    private val emailCredentialsStore: EmailCredentialsStore,
    private val cloudQueueClient: CloudQueueClient,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        articleRepository.observe(feedId = null, unreadOnly = true),
        readLaterRepository.observeUnread(),
        feedRepository.observeAll(),
        isSyncing,
        error,
    ) { articles, readLaterItems, feeds, syncing, err ->
        val feedTitles = feeds.associate { it.id to it.title }

        val homeItems = articles.map { article ->
            HomeItem.ArticleItem(
                article = article,
                feedTitle = feedTitles[article.feedId] ?: "",
            )
        } + readLaterItems.map { item ->
            HomeItem.ReadLaterHomeItem(item = item)
        }

        val sorted = homeItems.sortedByDescending { it.timestamp }
        val sections = groupByDate(sorted) { it.timestamp }

        HomeUiState(
            sections = sections,
            isSyncing = syncing,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            isSyncing.value = true
            error.value = null

            // Feed sync
            articleRepository.syncAllFeeds().onFailure { e ->
                error.value = e.message ?: "Feed sync failed"
            }

            // Email sync
            if (emailCredentialsStore.isConfigured()) {
                emailSyncRepository.syncEmails().onFailure { e ->
                    error.value = e.message ?: "Email sync failed"
                }
            }

            // Cloud queue + read-later content fetch
            pullCloudQueue()
            readLaterRepository.fetchPendingContent()

            isSyncing.value = false
        }
    }

    private suspend fun pullCloudQueue() {
        val config = preferencesRepository.getCloudQueueConfig()
        if (!config.isConfigured) return

        cloudQueueClient.fetchItems(config.baseUrl, config.queueId)
            .onSuccess { items ->
                if (items.isEmpty()) return
                for (item in items) {
                    readLaterRepository.save(item.url)
                }
                cloudQueueClient.acknowledge(
                    config.baseUrl,
                    config.queueId,
                    items.map { it.id },
                )
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

    fun archiveReadLater(id: Long) {
        viewModelScope.launch {
            readLaterRepository.archive(id)
        }
    }

    fun removeReadLater(id: Long) {
        viewModelScope.launch {
            readLaterRepository.delete(id)
        }
    }

    fun dismissError() {
        error.value = null
    }
}
