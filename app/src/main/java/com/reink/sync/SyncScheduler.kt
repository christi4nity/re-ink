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

        val emailSyncRequest = PeriodicWorkRequestBuilder<EmailSyncWorker>(
            4, TimeUnit.HOURS,
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            EmailSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            emailSyncRequest,
        )

        val cloudQueueSyncRequest = PeriodicWorkRequestBuilder<CloudQueueSyncWorker>(
            4, TimeUnit.HOURS,
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            CloudQueueSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cloudQueueSyncRequest,
        )

        val deviceSyncRequest = PeriodicWorkRequestBuilder<DeviceSyncWorker>(
            4, TimeUnit.HOURS,
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            DeviceSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            deviceSyncRequest,
        )
    }

    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val feedSync = OneTimeWorkRequestBuilder<FeedSyncWorker>()
            .setConstraints(constraints)
            .build()

        val emailSync = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setConstraints(constraints)
            .build()

        val cloudQueueSync = OneTimeWorkRequestBuilder<CloudQueueSyncWorker>()
            .setConstraints(constraints)
            .build()

        val deviceSync = OneTimeWorkRequestBuilder<DeviceSyncWorker>()
            .setConstraints(constraints)
            .build()

        val readLaterSync = OneTimeWorkRequestBuilder<ReadLaterSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.beginUniqueWork(
            "immediate_sync",
            ExistingWorkPolicy.REPLACE,
            feedSync,
        ).then(emailSync).then(cloudQueueSync).then(deviceSync).then(readLaterSync).enqueue()
    }
}
