package org.prairieserver.prairie.android.ui.screens.shuffle

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.prairieserver.prairie.android.ui.navigation.Route
import org.prairieserver.prairie.common.ui.ShuffleLauncher
import org.prairieserver.prairie.model.shuffle.Shuffle

/** A [ShuffleLauncher] that calls [onStarted] with each shuffle it starts. */
@Composable
fun rememberShuffleLauncher(onStarted: (Shuffle) -> Unit): ShuffleLauncher =
    org.prairieserver.prairie.common.ui.rememberShuffleLauncher(koinInject(), onStarted)

/** The player route for a shuffle's first pick, which plays from the beginning. */
fun shufflePlayerRoute(shuffle: Shuffle, libraryId: Int? = null): String =
    Route.Player(
        contentId = shuffle.current.contentId,
        resumePositionSeconds = 0.0,
        libraryId = libraryId,
        shuffleId = shuffle.id,
    ).route
