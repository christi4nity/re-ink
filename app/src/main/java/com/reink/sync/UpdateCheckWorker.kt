package com.reink.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reink.data.remote.UpdateChecker
import com.reink.data.repository.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        updateChecker.check().fold(
            onSuccess = { update ->
                if (update != null) {
                    preferencesRepository.setAvailableUpdate(
                        versionName = update.versionName,
                        downloadUrl = update.downloadUrl,
                        releaseNotes = update.releaseNotes,
                    )
                }
            },
            onFailure = {
                return Result.retry()
            },
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "update_check"
    }
}
