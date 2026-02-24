package com.reink.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val feedSyncRequest = PeriodicWorkRequestBuilder<FeedSyncWorker>(
            4, TimeUnit.HOURS,
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            FeedSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            feedSyncRequest,
        )
    }

    fun triggerImmediateSync() {
        val feedSync = OneTimeWorkRequestBuilder<FeedSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        val readLaterSync = OneTimeWorkRequestBuilder<ReadLaterSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.beginUniqueWork(
            "immediate_sync",
            ExistingWorkPolicy.REPLACE,
            feedSync,
        ).then(readLaterSync).enqueue()
    }
}
