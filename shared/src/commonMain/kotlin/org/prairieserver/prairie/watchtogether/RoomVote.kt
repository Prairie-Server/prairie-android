package org.prairieserver.prairie.watchtogether

import org.prairieserver.prairie.model.watchtogether.RoomSelectionMode
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.Suggestion
import org.prairieserver.prairie.util.parseRfc3339ToEpochMillis

/**
 * The server's vote order: most votes first, then the oldest suggestion.
 * HTTP pages arrive in creation order, so display and winner selection must
 * rank the merged list rather than trust the order it arrived in.
 */
fun rankSuggestions(suggestions: List<Suggestion>): List<Suggestion> =
    suggestions.sortedWith(
        compareByDescending<Suggestion> { it.voteCount }
            .thenBy { parseRfc3339ToEpochMillis(it.createdAt) ?: Long.MAX_VALUE }
            .thenBy { it.id },
    )

/**
 * The leading suggestion once somebody has voted. An unvoted list has no
 * winner. The tally is advisory: a host with the override capability may
 * promote any suggestion.
 */
fun roomVoteWinner(suggestions: List<Suggestion>): Suggestion? =
    rankSuggestions(suggestions).firstOrNull()?.takeIf { it.voteCount > 0 }

/** Whether this room decides its selection by vote. */
fun RoomSnapshot?.isVoteRoom(): Boolean =
    this != null && selectionMode == RoomSelectionMode.Vote
