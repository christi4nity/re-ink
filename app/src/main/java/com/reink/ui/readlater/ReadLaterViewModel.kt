package com.reink.ui.readlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.ReadLaterItem
import com.reink.data.remote.CloudQueueClient
import com.reink.data.repository.PreferencesRepository
import com.reink.data.repository.ReadLaterRepository
import com.reink.ui.home.groupByDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReadLaterSection(
    val dateHeader: String,
    val items: List<ReadLaterItem>,
)

data class ReadLaterUiState(
    val sections: List<ReadLaterSection> = emptyList(),
    val isSyncing: Boolean = false,
)

@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val readLaterRepository: ReadLaterRepository,
    private val cloudQueueClient: CloudQueueClient,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)

    val uiState: StateFlow<ReadLaterUiState> =
        combine(
            readLaterRepository.observeAll(),
            isSyncing,
        ) { items, syncing ->
            ReadLaterUiState(
                sections = groupByDate(items) { it.savedAt }.map {
                    ReadLaterSection(dateHeader = it.dateHeader, items = it.items)
                },
                isSyncing = syncing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReadLaterUiState(),
        )

    init {
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            isSyncing.value = true
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

    fun archive(id: Long) {
        viewModelScope.launch {
            readLaterRepository.archive(id)
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            readLaterRepository.delete(id)
        }
    }

}
