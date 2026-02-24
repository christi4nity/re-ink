package com.reink.ui.readlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.ReadLaterItem
import com.reink.data.repository.ReadLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReadLaterUiState(
    val items: List<ReadLaterItem> = emptyList(),
)

@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val readLaterRepository: ReadLaterRepository,
) : ViewModel() {

    val uiState: StateFlow<ReadLaterUiState> =
        readLaterRepository.observeAll()
            .map { items -> ReadLaterUiState(items = items) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ReadLaterUiState(),
            )

    init {
        viewModelScope.launch {
            readLaterRepository.fetchPendingContent()
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            readLaterRepository.delete(id)
        }
    }
}
