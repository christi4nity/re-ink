package com.reink.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reink.data.email.EmailCredentialsStore
import com.reink.data.repository.EmailSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val credentialsStore: EmailCredentialsStore,
    private val emailSyncRepository: EmailSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!credentialsStore.isConfigured()) return Result.success()

        return emailSyncRepository.syncEmails().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val WORK_NAME = "email_sync"
    }
}
