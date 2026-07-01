package org.siloserver.silo.common.settings

import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.overlays.CardOverlayPrefs
import org.siloserver.silo.overlays.OverlaySchema
import org.siloserver.silo.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cached card-overlay configuration for the signed-in profile. Mirrors
 * iOS `OverlayPrefsStore.swift` (Networking/OverlayPrefsStore.swift).
 *
 * Resolves a single rendered [CardOverlayPrefs] from one of two sources,
 * in this priority:
 *   1. The user's saved prefs (`GET /settings/card_overlays`) — if
 *      present, this is the entire source of truth.
 *   2. Otherwise, the admin-configured baseline JSON from
 *      `GET /settings/overlay-config` (`defaults` field).
 *   3. Otherwise, registry defaults ([OverlaySchema.buildDefaults]).
 *
 * Winner-take-all, not layered merging — [setPrefs] always saves a full
 * document (not a diff), keeping the wire format compatible with web,
 * iOS, and tvOS. Hydrated lazily on first read and refreshed after every
 * save so card views always see the shape they just persisted.
 */
interface OverlayPrefsStore {
    /**
     * `true` when the server allows overlays at all. An admin can flip
     * this off globally; when `false`, cards should not render overlays
     * even if the user has prefs configured.
     */
    val enabled: StateFlow<Boolean>

    /** Resolved prefs (user value > admin defaults > registry defaults). */
    val prefs: StateFlow<CardOverlayPrefs>

    val isLoading: StateFlow<Boolean>
    val lastError: StateFlow<String?>

    /** Whether the user has any saved override vs. running on admin defaults. */
    val hasUserOverride: Boolean

    /** Idempotent first-load. Safe to call on every view that wants overlays. */
    suspend fun hydrateIfNeeded()

    /** Re-fetch admin config + user setting and recompute [prefs]. */
    suspend fun refresh()

    /** Optimistically update local state, then persist (coalesced writes). */
    fun setPrefs(next: CardOverlayPrefs)

    /** Drop the user's override and fall back to the admin baseline. */
    suspend fun resetToDefaults()

    /** Wipe local state on sign-out so the next user gets a clean hydration. */
    fun clear()
}

class DefaultOverlayPrefsStore(
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
) : OverlayPrefsStore {

    private val _enabled = MutableStateFlow(true)
    private val _prefs = MutableStateFlow(OverlaySchema.buildDefaults())
    private val _isLoading = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)

    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    override val prefs: StateFlow<CardOverlayPrefs> = _prefs.asStateFlow()
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    override var hasUserOverride: Boolean = false
        private set

    @Volatile
    private var hasHydrated: Boolean = false

    @Volatile
    private var adminDefaultsRaw: String? = null

    private val refreshLock = Mutex()

    // Coalesced-write state. `writeMutex` guards the mutable bookkeeping
    // below; the actual PUT happens inside the drain coroutine.
    private val writeMutex = Mutex()

    @Volatile
    private var pendingWrite: Job? = null

    @Volatile
    private var pendingSnapshot: CardOverlayPrefs? = null

    // Monotonic token bumped by [clear]/[resetToDefaults]. Each drain captures
    // the value live when it starts; if [clear] bumps it mid-flight the drain
    // sees the mismatch and bails before serializing or issuing a PUT, so a
    // queued write for the previous auth scope can't land under the next one.
    // `@Volatile` so [clear] (which must run synchronously, off the write
    // coroutine) is visible to the drain without taking `writeMutex`.
    @Volatile
    private var writeGeneration: Int = 0

    override suspend fun hydrateIfNeeded() {
        if (hasHydrated || _isLoading.value) return
        refresh()
    }

    /**
     * Re-fetch both the admin config and the user setting, then recompute
     * [prefs].
     *
     * Failure semantics mirror iOS:
     * - A 404 on the user setting means "not set yet" and is treated as
     *   success — `userRaw` stays null and we render from admin defaults
     *   or registry defaults.
     * - Any other transport error on either endpoint leaves
     *   [hasHydrated] false so the next [hydrateIfNeeded] retries. This is
     *   critical for the admin kill-switch: if `/overlay-config` errors
     *   but the user setting resolves, we MUST NOT mark hydrated, or
     *   [enabled] is stuck at `true` and the admin's "disable globally"
     *   toggle is silently ignored for the session.
     */
    override suspend fun refresh() = refreshLock.withLock {
        _isLoading.value = true
        _lastError.value = null
        try {
            var resolvedEnabled = true
            var resolvedAdminDefaults: String? = null
            var configFetchFailed = false
            when (val config = repository.overlayConfig()) {
                is ApiResult.Success -> {
                    resolvedEnabled = config.data.enabled
                    resolvedAdminDefaults = config.data.defaults
                }
                is ApiResult.Error -> {
                    _lastError.value = config.message
                    configFetchFailed = true
                }
                is ApiResult.NetworkError -> {
                    _lastError.value = config.exception.message
                    configFetchFailed = true
                }
            }

            var userRaw: String? = null
            var userFetchFailed = false
            when (val entry = repository.getSetting(OVERLAY_SETTING_KEY)) {
                is ApiResult.Success -> userRaw = entry.data
                is ApiResult.Error ->
                    if (entry.code == 404) {
                        userRaw = null
                    } else {
                        _lastError.value = entry.message
                        userFetchFailed = true
                    }
                is ApiResult.NetworkError -> {
                    _lastError.value = entry.exception.message
                    userFetchFailed = true
                }
            }

            // Preserve cached config state on transient failures. The
            // sentinel `resolvedEnabled = true` is only valid when the
            // fetch actually succeeded.
            if (!configFetchFailed) {
                _enabled.value = resolvedEnabled
                adminDefaultsRaw = resolvedAdminDefaults
            }
            if (!userFetchFailed) {
                hasUserOverride = userRaw != null
                val defaults = if (configFetchFailed) adminDefaultsRaw else resolvedAdminDefaults
                _prefs.value = OverlaySchema.parse(userRaw ?: defaults)
            }
            // Only complete hydration when BOTH endpoints gave a
            // definitive answer.
            if (!configFetchFailed && !userFetchFailed) {
                hasHydrated = true
            }
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Optimistically update local state, then persist. Writes are
     * serialized and coalesced: rapid changes (e.g. flipping presets)
     * issue one PUT at a time and intermediate snapshots are dropped,
     * preventing the stale-overwrite race where a slower earlier PUT
     * lands after a faster later one.
     */
    override fun setPrefs(next: CardOverlayPrefs) {
        _prefs.value = next
        hasUserOverride = true
        scope.launch {
            writeMutex.withLock {
                pendingSnapshot = next
                if (pendingWrite?.isActive != true) {
                    val generation = writeGeneration
                    pendingWrite = scope.launch { flushPendingWrites(generation) }
                }
            }
        }
    }

    private suspend fun flushPendingWrites(generation: Int) {
        while (true) {
            // Bail if our coroutine was cancelled OR `clear()`/`resetToDefaults()`
            // bumped the generation out from under us — the queued snapshot
            // belongs to a session that is being torn down.
            if (currentCoroutineContext()[Job]?.isActive != true) return
            if (writeGeneration != generation) return
            val snapshot = writeMutex.withLock {
                val s = pendingSnapshot
                pendingSnapshot = null
                if (s == null) pendingWrite = null
                s
            } ?: return

            // Re-check immediately before serializing and before issuing the
            // PUT: `clear()` may have fired after we took the snapshot above.
            // Mirrors OverlayPrefsStore.swift's cancellation checks so no PUT
            // for the cleared session reaches the wire.
            if (currentCoroutineContext()[Job]?.isActive != true) return
            if (writeGeneration != generation) return
            val json = OverlaySchema.serialize(snapshot)
            if (currentCoroutineContext()[Job]?.isActive != true) return
            if (writeGeneration != generation) return
            when (val result = repository.setSetting(OVERLAY_SETTING_KEY, json)) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> {
                    if (writeGeneration != generation) return
                    _lastError.value = result.message
                    refresh()
                }
                is ApiResult.NetworkError -> {
                    if (writeGeneration != generation) return
                    _lastError.value = result.exception.message
                    refresh()
                }
            }
        }
    }

    /**
     * Drop the user's override and fall back to the admin baseline.
     * Cancels and awaits any in-flight write before issuing the DELETE so
     * a slower earlier PUT can't land server-side after the DELETE and
     * recreate the document the user just asked us to drop.
     */
    override suspend fun resetToDefaults() {
        // Bump first so any drain that's mid-flight (already past its snapshot
        // grab) sees the generation change and bails before its PUT lands.
        writeGeneration += 1
        writeMutex.withLock {
            pendingSnapshot = null
            pendingWrite?.cancel()
        }
        pendingWrite?.join()
        writeMutex.withLock { pendingWrite = null }

        when (val result = repository.deleteSetting(OVERLAY_SETTING_KEY)) {
            is ApiResult.Success -> hasUserOverride = false
            is ApiResult.Error ->
                if (result.code == 404) {
                    hasUserOverride = false
                } else {
                    _lastError.value = result.message
                }
            is ApiResult.NetworkError -> _lastError.value = result.exception.message
        }
        refresh()
    }

    override fun clear() {
        // Synchronously invalidate the current drain so no further PUT for this
        // (now-ending) session can reach the wire. `writeGeneration` is
        // `@Volatile`, so a drain coroutine sees the bump at its next
        // cancellation check (immediately before serialize and before the PUT)
        // even though we don't hold `writeMutex` here. We also null the pending
        // snapshot synchronously so a drain that's about to grab it gets null
        // and exits. Cancelling the Job + nulling `pendingWrite` still happens
        // under the mutex on the write coroutine, but correctness no longer
        // depends on that running before the session boundary.
        writeGeneration += 1
        pendingSnapshot = null
        val inflight = pendingWrite
        pendingWrite = null
        inflight?.cancel()
        _enabled.value = true
        _prefs.value = OverlaySchema.buildDefaults()
        adminDefaultsRaw = null
        hasUserOverride = false
        hasHydrated = false
        _lastError.value = null
    }

    companion object {
        const val OVERLAY_SETTING_KEY = "card_overlays"
    }
}
