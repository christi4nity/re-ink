package com.reink.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reink.data.repository.PreferencesRepository
import com.reink.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DeviceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = preferencesRepository.getSyncConfig()
        if (!config.isConfigured) return Result.success()

        return syncRepository.sync().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val WORK_NAME = "device_sync"
    }
}
