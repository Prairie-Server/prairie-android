package org.prairieserver.prairie.android.ui.screens.player

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.prairieserver.prairie.model.catalog.WatchDetail
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.repository.CatalogRepository

/** Optional enrichment only: a refusal leaves the local sidecar authoritative. */
internal suspend fun loadLocalWatchMetadata(
    repository: CatalogRepository,
    owner: AuthScopeSnapshot?,
    serverId: String,
    profileId: String,
    contentId: String,
    libraryId: Int? = null,
    stillCurrent: () -> Boolean,
): WatchDetail? {
    if (owner == null || owner.serverId != serverId || owner.profileId != profileId) return null
    suspend fun current(): Boolean {
        val valid = repository.isWatchAuthorityCurrent(owner)
        currentCoroutineContext().ensureActive()
        return valid && stillCurrent()
    }
    if (!current()) return null
    val result = repository.getWatchDetail(contentId, owner, libraryId)
    if (!current()) return null
    return (result as? ApiResult.Success)?.data
}
