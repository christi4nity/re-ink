package com.reink.ui.readlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reink.data.model.ReadLaterItem
import com.reink.data.remote.CloudQueueClient
import com.reink.data.repository.PreferencesRepository
import com.reink.data.repository.ReadLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
            ReadLaterUiState(sections = groupByDate(items), isSyncing = syncing)
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

    companion object {
        private val dayWithDateFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)
        private val dateFormat = SimpleDateFormat("MMM d", Locale.US)

        fun groupByDate(items: List<ReadLaterItem>): List<ReadLaterSection> {
            if (items.isEmpty()) return emptyList()

            val calendar = Calendar.getInstance()
            val today = clearTime(calendar)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = calendar.timeInMillis

            return items.groupBy { item ->
                val itemCal = Calendar.getInstance().apply { timeInMillis = item.savedAt }
                val itemDay = clearTime(itemCal)

                when {
                    itemDay >= today ->
                        "Today, ${dateFormat.format(Date(item.savedAt))}"
                    itemDay >= yesterday ->
                        "Yesterday, ${dateFormat.format(Date(item.savedAt))}"
                    itemDay >= today - 6 * 24 * 60 * 60 * 1000L ->
                        dayWithDateFormat.format(Date(item.savedAt))
                    else -> dateFormat.format(Date(item.savedAt))
                }
            }.map { (header, sectionItems) ->
                ReadLaterSection(dateHeader = header, items = sectionItems)
            }
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
