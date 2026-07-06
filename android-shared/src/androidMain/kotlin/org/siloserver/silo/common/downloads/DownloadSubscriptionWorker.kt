package org.siloserver.silo.common.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.siloserver.silo.repository.DownloadSubscriptionRepository

class DownloadSubscriptionWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: DownloadSubscriptionRepository,
    private val evaluatorFactory: DownloadSubscriptionEvaluatorFactory,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.success()
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return Result.success()
        val now = System.currentTimeMillis()
        val evaluator = evaluatorFactory.create()

        repository.active(serverId, profileId).forEach { subscription ->
            runCatching {
                evaluator.evaluate(subscription)
                repository.updateEvaluation(serverId, profileId, subscription.id, now, null, now)
            }.onFailure { error ->
                repository.updateEvaluation(
                    serverId = serverId,
                    profileId = profileId,
                    id = subscription.id,
                    evaluatedAt = now,
                    error = error.message ?: error::class.simpleName,
                    updatedAt = now,
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_PROFILE_ID = "profile_id"

        fun enqueueNow(context: Context, serverId: String, profileId: String) {
            val request = OneTimeWorkRequestBuilder<DownloadSubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SERVER_ID to serverId,
                        KEY_PROFILE_ID to profileId,
                    ),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "download-subscriptions-$serverId-$profileId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
