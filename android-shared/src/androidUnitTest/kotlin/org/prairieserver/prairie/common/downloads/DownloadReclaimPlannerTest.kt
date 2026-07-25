package org.prairieserver.prairie.common.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadReclaimPlannerTest {
    @Test
    fun reclaimExcludesIncompleteFailedAndUnwatchedRows() {
        val plan = DownloadReclaimPlanner().plan(
            rows = listOf(
                row(id = "watched", status = "completed", bytes = 100, completed = true),
                row(id = "unwatched", status = "completed", bytes = 200, completed = false),
                row(id = "downloading", status = "downloading", bytes = 300, completed = true),
                row(id = "failed", status = "failed", bytes = 400, completed = true),
            ),
        )

        assertEquals(listOf("watched"), plan.items.map { it.recordId })
        assertEquals(100L, plan.totalBytes)
        assertEquals(1, plan.count)
    }

    @Test
    fun reclaimKeepsMostRecentCompletedLimit() {
        val plan = DownloadReclaimPlanner().plan(
            rows = listOf(
                row(id = "old-watched", status = "completed", bytes = 100, completed = true, updatedAt = 1),
                row(id = "new-watched", status = "completed", bytes = 200, completed = true, updatedAt = 10),
            ),
            keepNewestCompleted = 1,
        )

        assertEquals(listOf("old-watched"), plan.items.map { it.recordId })
    }

    private fun row(
        id: String,
        status: String,
        bytes: Long,
        completed: Boolean,
        updatedAt: Long = 1,
    ) = DownloadReclaimCandidate(
        recordId = id,
        contentId = "content-$id",
        mediaFileId = id.hashCode(),
        title = id,
        status = status,
        fileSizeBytes = bytes,
        completed = completed,
        updatedAtMs = updatedAt,
    )
}
