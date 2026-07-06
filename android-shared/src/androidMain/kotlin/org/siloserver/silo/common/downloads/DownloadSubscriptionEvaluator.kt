package org.siloserver.silo.common.downloads

import org.siloserver.silo.model.download.DownloadQuality
import org.siloserver.silo.model.download.DownloadSubscription
import org.siloserver.silo.model.download.DownloadSubscriptionMediaKind

data class DownloadSubscriptionCandidate(
    val contentId: String,
    val mediaFileId: Int,
    val title: String,
    val completed: Boolean,
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
    suspend fun evaluate(subscription: DownloadSubscription): Int {
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
}
