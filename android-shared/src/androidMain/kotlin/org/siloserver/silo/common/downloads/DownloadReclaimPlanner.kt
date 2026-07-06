package org.siloserver.silo.common.downloads

data class DownloadReclaimCandidate(
    val recordId: String,
    val contentId: String,
    val mediaFileId: Int,
    val title: String,
    val status: String,
    val fileSizeBytes: Long,
    val completed: Boolean,
    val updatedAtMs: Long,
)

data class DownloadReclaimItem(
    val recordId: String,
    val mediaFileId: Int,
    val title: String,
    val fileSizeBytes: Long,
)

data class DownloadReclaimPlan(
    val items: List<DownloadReclaimItem>,
) {
    val totalBytes: Long = items.sumOf { it.fileSizeBytes }
    val count: Int = items.size
}

class DownloadReclaimPlanner {
    fun plan(
        rows: List<DownloadReclaimCandidate>,
        keepNewestCompleted: Int = 0,
    ): DownloadReclaimPlan {
        val items = rows
            .asSequence()
            .filter { it.status.lowercase() == COMPLETED_STATUS }
            .filter { it.completed }
            .sortedByDescending { it.updatedAtMs }
            .drop(keepNewestCompleted.coerceAtLeast(0))
            .map {
                DownloadReclaimItem(
                    recordId = it.recordId,
                    mediaFileId = it.mediaFileId,
                    title = it.title,
                    fileSizeBytes = it.fileSizeBytes.coerceAtLeast(0L),
                )
            }
            .toList()

        return DownloadReclaimPlan(items)
    }

    private companion object {
        const val COMPLETED_STATUS = "completed"
    }
}
