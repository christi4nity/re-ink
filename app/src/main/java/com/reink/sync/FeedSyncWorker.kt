package com.reink.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reink.data.repository.ArticleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FeedSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleRepository: ArticleRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Clean up articles older than 90 days
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        articleRepository.deleteOlderThan(ninetyDaysAgo)

        return articleRepository.syncAllFeeds().fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val WORK_NAME = "feed_sync"
    }
}
