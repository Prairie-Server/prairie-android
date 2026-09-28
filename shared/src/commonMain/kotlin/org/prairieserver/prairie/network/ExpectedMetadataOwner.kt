package org.prairieserver.prairie.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.prairieserver.prairie.network.apiv2.OwnerPolicy
import org.prairieserver.prairie.network.apiv2.stillOwns

suspend fun TokenManager?.acceptsMetadataOwner(owner: AuthScopeSnapshot?, profileId: String): Boolean {
    currentCoroutineContext().ensureActive()
    if (owner == null) return true
    if (owner.profileId != profileId || profileId.isBlank() || this == null) return false
    return owner.stillOwns(this, OwnerPolicy.FULL).also { currentCoroutineContext().ensureActive() }
}
