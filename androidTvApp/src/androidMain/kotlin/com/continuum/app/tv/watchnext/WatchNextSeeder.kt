package com.continuum.app.tv.watchnext

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the WorkManager surface for the Watch Next channel.
 *
 * - [seedNow] enqueues an expedited one-shot sync — call right after
 *   auth-success and profile-select so the launcher row reflects the
 *   user's actual continue-watching / next-up immediately.
 * - [enqueuePeriodic] keeps a 1h cadence refresh in place; uses KEEP so
 *   repeated calls don't reset the interval.
 * - [clear] wipes our rows from the WatchNextPrograms provider and
 *   cancels the periodic work — call on profile switch and sign-out so
 *   the previous user's progress doesn't linger on the launcher.
 */
class WatchNextSeeder(
    private val context: Context,
    private val repository: WatchNextRepository,
) {

    fun seedNow() {
        val request = OneTimeWorkRequestBuilder<WatchNextSyncWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WatchNextSyncWorker.UNIQUE_NAME_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueuePeriodic() {
        val request = PeriodicWorkRequestBuilder<WatchNextSyncWorker>(
            1, TimeUnit.HOURS,
        )
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WatchNextSyncWorker.UNIQUE_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun clear() {
        repository.clearAll()
        // Cancel BOTH the periodic refresh and any in-flight one-shot seed —
        // without the one-shot cancel, a seedNow() that's mid-flight when
        // the user signs out (or switches profile) can repopulate the
        // launcher rows immediately after [WatchNextRepository.clearAll].
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(WatchNextSyncWorker.UNIQUE_NAME_PERIODIC)
            cancelUniqueWork(WatchNextSyncWorker.UNIQUE_NAME_ONESHOT)
        }
    }

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
