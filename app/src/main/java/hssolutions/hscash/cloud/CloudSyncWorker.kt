package hssolutions.hscash.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hssolutions.hscash.ui.AppSettings
import hssolutions.hscash.ui.CloudSyncState
import java.util.concurrent.TimeUnit

class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!SupabaseProvider.isConfigured || CloudAuthRepository.currentUser() == null) return Result.success()
        AppSettings.setCloudSyncState(applicationContext, CloudSyncState.SYNCING)
        return runCatching {
            CloudSyncRepository.sync(applicationContext)
            AppSettings.setLastCloudSyncAt(applicationContext, System.currentTimeMillis())
            AppSettings.setCloudSyncState(applicationContext, CloudSyncState.SYNCED)
            Result.success()
        }.getOrElse { error ->
            AppSettings.setCloudSyncState(applicationContext, CloudSyncState.ERROR, cloudSyncErrorMessage(error))
            Result.retry()
        }
    }
}

object CloudSyncScheduler {
    private const val IMMEDIATE_WORK = "hs-cash-cloud-sync"
    private const val PERIODIC_WORK = "hs-cash-cloud-sync-periodic"

    private val onlineConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueue(context: Context) {
        if (!SupabaseProvider.isConfigured || CloudAuthRepository.currentUser() == null) return
        AppSettings.setCloudSyncState(context, CloudSyncState.WAITING)
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(onlineConstraints)
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(onlineConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
