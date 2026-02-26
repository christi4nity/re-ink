package com.reink.ui.readlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.ReadLaterItem
import com.reink.data.repository.ReadLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReadLaterUiState(
    val items: List<ReadLaterItem> = emptyList(),
    val isSyncing: Boolean = false,
)

@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val readLaterRepository: ReadLaterRepository,
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)

    val uiState: StateFlow<ReadLaterUiState> =
        combine(
            readLaterRepository.observeAll(),
            isSyncing,
        ) { items, syncing ->
            ReadLaterUiState(items = items, isSyncing = syncing)
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
            readLaterRepository.fetchPendingContent()
            isSyncing.value = false
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            readLaterRepository.delete(id)
        }
    }
}
