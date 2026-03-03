package com.reink.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reink.data.remote.CloudQueueClient
import com.reink.data.repository.PreferencesRepository
import com.reink.data.repository.ReadLaterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloudQueueSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val cloudQueueClient: CloudQueueClient,
    private val readLaterRepository: ReadLaterRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = preferencesRepository.getCloudQueueConfig()
        if (!config.isConfigured) return Result.success()

        return cloudQueueClient.fetchItems(config.baseUrl, config.queueId).fold(
            onSuccess = { items ->
                if (items.isEmpty()) return Result.success()

                for (item in items) {
                    readLaterRepository.save(item.url)
                }

                cloudQueueClient.acknowledge(
                    config.baseUrl,
                    config.queueId,
                    items.map { it.id },
                )
                // Ack failure is non-fatal — items will be re-fetched next sync
                // and deduped by ReadLaterRepository.save()

                Result.success()
            },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val WORK_NAME = "cloud_queue_sync"
    }
}
