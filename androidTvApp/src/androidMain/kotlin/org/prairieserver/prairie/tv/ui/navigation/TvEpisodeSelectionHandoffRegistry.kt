package org.prairieserver.prairie.tv.ui.navigation

import java.util.LinkedHashMap
import java.util.UUID
import org.prairieserver.prairie.common.player.video.EpisodeSelectionHandoff

private const val MIN_HANDOFF_NONCE_LENGTH = 16
private const val MAX_HANDOFF_NONCE_LENGTH = 32
private const val DEFAULT_MAX_HANDOFFS = 32
private const val DEFAULT_HANDOFF_TTL_MILLIS = 5 * 60 * 1_000L
private val handoffNoncePattern = Regex("^[A-Za-z0-9_-]+$")

internal fun isValidTvEpisodeSelectionHandoffNonce(nonce: String?): Boolean =
    nonce != null &&
        nonce.length in MIN_HANDOFF_NONCE_LENGTH..MAX_HANDOFF_NONCE_LENGTH &&
        handoffNoncePattern.matches(nonce)

/**
 * Process-only transport for the semantic episode handoff.
 *
 * Navigation persists only an opaque nonce. Entries are target-content bound,
 * single-use, short-lived, and capacity bounded so a restored back stack cannot
 * recreate selection intent after process death.
 */
internal class TvEpisodeSelectionHandoffRegistry(
    private val maxEntries: Int = DEFAULT_MAX_HANDOFFS,
    private val ttlMillis: Long = DEFAULT_HANDOFF_TTL_MILLIS,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nonceFactory: () -> String = {
        UUID.randomUUID().toString().replace("-", "")
    },
) {
    private data class Entry(
        val targetContentId: String,
        val handoff: EpisodeSelectionHandoff,
        val expiresAtMillis: Long,
    )

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(maxEntries > 0)
        require(ttlMillis > 0L)
    }

    @Synchronized
    fun register(
        targetContentId: String,
        handoff: EpisodeSelectionHandoff,
    ): String {
        require(targetContentId.isNotBlank())
        val now = nowMillis()
        removeExpired(now)
        while (entries.size >= maxEntries) {
            val eldest = entries.keys.firstOrNull() ?: break
            entries.remove(eldest)
        }
        repeat(MAX_NONCE_GENERATION_ATTEMPTS) {
            val nonce = nonceFactory()
            if (isValidTvEpisodeSelectionHandoffNonce(nonce) && nonce !in entries) {
                entries[nonce] = Entry(
                    targetContentId = targetContentId,
                    handoff = handoff,
                    expiresAtMillis = now + ttlMillis,
                )
                return nonce
            }
        }
        error("Could not allocate a unique episode handoff nonce.")
    }

    /** Claim removes the entry before checking its target, making every attempt single-use. */
    @Synchronized
    fun claim(
        nonce: String?,
        targetContentId: String,
    ): EpisodeSelectionHandoff? {
        if (!isValidTvEpisodeSelectionHandoffNonce(nonce)) return null
        removeExpired(nowMillis())
        val entry = entries.remove(nonce) ?: return null
        return entry.handoff.takeIf { entry.targetContentId == targetContentId }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun removeExpired(now: Long) {
        entries.entries.removeAll { (_, entry) -> now >= entry.expiresAtMillis }
    }

    private companion object {
        const val MAX_NONCE_GENERATION_ATTEMPTS = 8
    }
}

/** Empty in a recreated process by construction; never persisted or saved. */
internal val processTvEpisodeSelectionHandoffRegistry = TvEpisodeSelectionHandoffRegistry()
