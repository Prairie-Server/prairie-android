package org.prairieserver.prairie.common.di

import android.content.SharedPreferences
import org.prairieserver.prairie.network.DurableLoginAuthorityProvider
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.api.WatchTogetherApi
import org.prairieserver.prairie.network.apiv2.PlaybackV2Api
import org.prairieserver.prairie.repository.WatchTogetherRepository
import org.prairieserver.prairie.watchtogether.RecentPartyOwner
import org.prairieserver.prairie.watchtogether.RecentWatchParties
import org.prairieserver.prairie.watchtogether.SharedPreferencesRecentWatchPartyStorage
import org.prairieserver.prairie.watchtogether.WatchPartyAvailabilityRepository
import org.prairieserver.prairie.watchtogether.WatchPartyRecentsRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Watch Party wiring shared by the phone and TV apps: capability discovery
 * and the recent-party store. The room owner itself lives in the shared
 * repository module.
 */
val watchPartyModule = module {
    single {
        val tokens = get<TokenManager>()
        val playback = get<PlaybackV2Api>()
        val api = get<WatchTogetherApi>()
        WatchPartyAvailabilityRepository(
            roomCapabilities = { scope -> api.capabilities(scope) },
            playbackCapabilities = { scope -> playback.capabilities(scope) },
            authScopeProvider = { tokens.snapshotCurrentScope() },
        )
    }
    single {
        val authorities = get<TokenManager>() as? DurableLoginAuthorityProvider
        RecentWatchParties(
            // The app-level SharedPreferences binding is the encrypted store.
            storage = SharedPreferencesRecentWatchPartyStorage(get<SharedPreferences>()),
            owner = {
                authorities?.snapshotDurableLoginAuthority()?.let { authority ->
                    val serverId = authority.scope.serverId
                    val profileId = authority.scope.profileId
                    if (serverId.isBlank() || profileId.isNullOrBlank()) null
                    else RecentPartyOwner(serverId, authority.loginId, profileId)
                }
            },
        )
    }
    single(createdAtStart = true) {
        val repository = get<WatchTogetherRepository>()
        WatchPartyRecentsRecorder(
            recents = get(),
            room = repository.roomSnapshot,
            ended = repository.ended,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            identityTransitions = get(),
            closedByThisDevice = repository.closedByThisDevice,
        )
    }
}
