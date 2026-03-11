package com.reink.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.repository.ArticleRepository
import com.reink.data.repository.ReadLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ArchiveItem(val archivedAt: Long?) {
    data class ArticleItem(val id: Long, val title: String, val source: String, val archived: Long?) : ArchiveItem(archived)
    data class ReadLaterArchiveItem(val id: Long, val title: String, val source: String, val archived: Long?) : ArchiveItem(archived)
}

data class ArchiveUiState(
    val items: List<ArchiveItem> = emptyList(),
)

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val readLaterRepository: ReadLaterRepository,
) : ViewModel() {

    val uiState: StateFlow<ArchiveUiState> =
        combine(
            articleRepository.observeArchived(),
            readLaterRepository.observeArchived(),
        ) { articles, readLaterItems ->
            val merged = buildList {
                articles.forEach { article ->
                    add(ArchiveItem.ArticleItem(
                        id = article.id,
                        title = article.title,
                        source = article.author.ifBlank { "" },
                        archived = article.archivedAt,
                    ))
                }
                readLaterItems.forEach { item ->
                    add(ArchiveItem.ReadLaterArchiveItem(
                        id = item.id,
                        title = item.title.ifBlank { item.url },
                        source = item.sourceDomain ?: "",
                        archived = item.archivedAt,
                    ))
                }
            }.sortedByDescending { it.archivedAt ?: 0L }

            ArchiveUiState(items = merged)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ArchiveUiState(),
        )

    fun unarchive(item: ArchiveItem) {
        viewModelScope.launch {
            when (item) {
                is ArchiveItem.ArticleItem -> articleRepository.unarchive(item.id)
                is ArchiveItem.ReadLaterArchiveItem -> readLaterRepository.unarchive(item.id)
            }
        }
    }

    fun delete(item: ArchiveItem) {
        viewModelScope.launch {
            when (item) {
                is ArchiveItem.ArticleItem -> articleRepository.delete(item.id)
                is ArchiveItem.ReadLaterArchiveItem -> readLaterRepository.delete(item.id)
            }
        }
    }
}
