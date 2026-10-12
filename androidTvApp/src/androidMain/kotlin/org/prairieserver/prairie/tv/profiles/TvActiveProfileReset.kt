package org.prairieserver.prairie.tv.profiles

import org.prairieserver.prairie.common.settings.CardPresentationStore
import org.prairieserver.prairie.common.settings.EpisodeSpoilerStore
import org.prairieserver.prairie.common.settings.LibraryPlaybackPrefsStore
import org.prairieserver.prairie.common.settings.OverlayPrefsStore
import org.prairieserver.prairie.common.settings.SeekIntervalStore
import org.prairieserver.prairie.common.settings.TitleArtStore
import org.prairieserver.prairie.repository.ProfileRepository
import org.prairieserver.prairie.tv.watchnext.WatchNextSeeder

/**
 * Drops the active profile and everything cached for it, so the next profile
 * starts clean. Shared by Switch Profile and every Profile Selection path
 * (launch, return to Prairie, server switch) so they all leave the same state.
 */
class TvActiveProfileReset(
    private val profileRepository: ProfileRepository,
    private val libraryPlaybackPrefsStore: LibraryPlaybackPrefsStore,
    private val overlayPrefsStore: OverlayPrefsStore,
    private val cardPresentationStore: CardPresentationStore,
    private val seekIntervalStore: SeekIntervalStore,
    private val titleArtStore: TitleArtStore,
    private val episodeSpoilerStore: EpisodeSpoilerStore,
    private val watchNextSeeder: WatchNextSeeder,
) {
    suspend fun clearActiveProfile() {
        // The profile id and its PIN proof go together, so a protected
        // profile asks for its PIN again.
        profileRepository.clearProfile()
        // Library/overlay prefs are per-profile — drop the caches
        // so the next profile's prefs don't ghost-render the
        // previous user's rows. Parity with the Settings
        // switch-profile path.
        libraryPlaybackPrefsStore.clear()
        overlayPrefsStore.clear()
        cardPresentationStore.clear()
        seekIntervalStore.clear()
        titleArtStore.clear()
        episodeSpoilerStore.clear()
        // Clear the previous profile's Watch Next rows before
        // landing on the picker; the new profile will re-seed
        // when it is selected.
        watchNextSeeder.clear()
    }
}
