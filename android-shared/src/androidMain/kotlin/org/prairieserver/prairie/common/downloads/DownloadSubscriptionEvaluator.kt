package org.prairieserver.prairie.common.downloads

import kotlinx.coroutines.sync.withLock
import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.model.download.DownloadSubscription
import org.prairieserver.prairie.model.download.DownloadSubscriptionMediaKind

data class DownloadSubscriptionCandidate(
    val contentId: String,
    val mediaFileId: Int,
    val title: String,
    val completed: Boolean,
    val seriesContentId: String? = null,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val posterUrl: String? = null,
)

interface DownloadSubscriptionCandidateProvider {
    suspend fun candidatesFor(subscription: DownloadSubscription): List<DownloadSubscriptionCandidate>
}

class DownloadSubscriptionEvaluator(
    private val candidateProvider: DownloadSubscriptionCandidateProvider,
    private val existingFileIds: suspend (DownloadSubscription) -> Set<Int>,
    private val enqueue: suspend (DownloadSubscriptionCandidate, DownloadQuality) -> Unit,
    private val allowReading: Boolean = true,
) {
    suspend fun evaluate(subscription: DownloadSubscription): Int = evaluationMutex.withLock {
        evaluateLocked(subscription)
    }

    /**
     * Serialized globally: the periodic worker and the Downloads screen's
     * "run now" use different WorkManager namespaces (one-time vs periodic
     * unique names never collide), so without this two evaluations could
     * snapshot the same `existingFileIds` and enqueue the same episode twice.
     */
    private suspend fun evaluateLocked(subscription: DownloadSubscription): Int {
        if (!subscription.enabled) return 0
        if (!allowReading && subscription.mediaKind == DownloadSubscriptionMediaKind.Reading) return 0

        val existing = existingFileIds(subscription)
        val limit = subscription.keepUnwatchedLimit.coerceAtLeast(0)
        if (limit == 0) return 0

        var queued = 0
        for (candidate in candidateProvider.candidatesFor(subscription)) {
            if (candidate.mediaFileId in existing) continue
            if (candidate.completed && !subscription.includeExisting) continue

            enqueue(candidate, subscription.quality)
            queued += 1
            if (queued >= limit) break
        }
        return queued
    }

    private companion object {
        val evaluationMutex = kotlinx.coroutines.sync.Mutex()
    }
}
