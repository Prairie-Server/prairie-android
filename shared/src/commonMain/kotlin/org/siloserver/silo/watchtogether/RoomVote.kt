package org.siloserver.silo.watchtogether

import org.siloserver.silo.model.watchtogether.RoomSelectionMode
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.model.watchtogether.Suggestion

/**
 * Returns the winner from the server-ordered suggestion list.
 *
 * The server sorts by vote count and then age, so the first suggestion wins
 * only after somebody has voted. An unvoted list intentionally has no winner.
 */
fun roomVoteWinner(suggestions: List<Suggestion>): Suggestion? =
    suggestions.firstOrNull()?.takeIf { it.voteCount > 0 }

/** Whether this room decides its selection by vote. */
fun RoomSnapshot?.isVoteRoom(): Boolean =
    this != null && selectionMode == RoomSelectionMode.Vote
