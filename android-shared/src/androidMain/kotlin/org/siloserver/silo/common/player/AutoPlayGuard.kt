package org.siloserver.silo.common.player

/**
 * Pass-out protection. Counts consecutive AUTO-advanced episodes and reports
 * when the next auto-advance should be gated behind a "Still watching?" prompt.
 * A threshold of 0 (or negative) disables gating. Any user-initiated action
 * resets the count.
 *
 * **Required call sequence** (both the mobile and TV ViewModels must follow it,
 * so the count means the same thing on both):
 * ```
 * if (guard.shouldGate()) { showStillWatchingPrompt(); return }   // check FIRST
 * guard.recordAutoAdvance()                                        // record only when NOT gated
 * advanceToNextEpisode()
 * ```
 * With this sequence and threshold N, **N consecutive auto-advances are allowed
 * and the (N+1)-th is gated** (e.g. N=3 → episodes 2,3,4 auto-play, then the
 * prompt appears before the 5th). A manual transport action (play/seek/next) or
 * tapping "Continue" calls [recordUserAction] and resets the streak.
 *
 * The threshold is supplied lazily ([threshold]) so it always reflects the
 * current per-profile "pass-out" setting without rebuilding the guard.
 *
 * Framework-free and stateful — NOT thread-safe; confine to the player's main
 * scope (Dispatchers.Main).
 */
class AutoPlayGuard(private val threshold: () -> Int) {
    private var consecutiveAutoAdvances: Int = 0

    /** Record that an episode was auto-advanced (not user-chosen). */
    fun recordAutoAdvance() {
        consecutiveAutoAdvances += 1
    }

    /** Any deliberate user action (manual play/seek/next, or tapping "Continue"). */
    fun recordUserAction() {
        consecutiveAutoAdvances = 0
    }

    /**
     * True when the next auto-advance should be blocked by the prompt. Call
     * BEFORE [recordAutoAdvance] (see the class doc's required sequence).
     */
    fun shouldGate(): Boolean = threshold().let { it > 0 && consecutiveAutoAdvances >= it }
}
