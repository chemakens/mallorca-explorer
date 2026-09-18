package com.mallorca.explorer.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mallorca.explorer.core.data.preferences.NotificationPreferences
import com.mallorca.explorer.core.data.sync.SeedDataWorker.Companion.CURRENT_SEED_VERSION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class NewGemCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationPreferences: NotificationPreferences,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val gemsEnabled = notificationPreferences.gemNotificationsEnabled.first()
            if (!gemsEnabled) return Result.success()

            val lastSeen = notificationPreferences.lastSeenSeedVersion.first()
            if (CURRENT_SEED_VERSION > lastSeen && lastSeen > 0) {
                sendNewGemNotification(context)
            }
            notificationPreferences.setLastSeenSeedVersion(CURRENT_SEED_VERSION)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "new_gem_check"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<NewGemCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
