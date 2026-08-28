package org.prairieserver.prairie.common.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.session.MediaSessionService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the decisions that keep [PrairiePlaybackService] from being cold-started
 * and then killed by the platform's 10-second start-foreground watchdog.
 *
 * A media key on a TV remote is delivered to this service through a
 * `PendingIntent.getForegroundService()` that Media3 mints for the session, so
 * a stray key press with nothing playing used to start the service, produce no
 * notification, and take the whole app down with a RemoteServiceException.
 */
@RunWith(RobolectricTestRunner::class)
class PrairiePlaybackServiceStartPolicyTest {

    @Test
    fun mediaButtonFallbackCallerIsRecognisedFromConnectionHints() {
        val hints = Bundle().apply {
            putString(
                MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE,
                Intent.ACTION_MEDIA_BUTTON,
            )
        }

        assertTrue(PrairiePlaybackService.isMediaButtonFallbackCaller(hints))
    }

    @Test
    fun realControllersAreNotMistakenForTheMediaButtonCaller() {
        assertFalse(
            PrairiePlaybackService.isMediaButtonFallbackCaller(Bundle()),
            "an ordinary MediaController connection carries no controller-info hint",
        )
        assertFalse(
            PrairiePlaybackService.isMediaButtonFallbackCaller(
                Bundle().apply {
                    putString(
                        MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE,
                        "androidx.media3.session.MediaBrowserService",
                    )
                },
            ),
            "only ACTION_MEDIA_BUTTON identifies the synthetic media-button caller",
        )
    }

    @Test
    fun coldMediaButtonStartWithNothingQueuedHasNothingToServe() {
        assertTrue(
            PrairiePlaybackService.hasNothingToServe(
                queuedMediaItemCount = 0,
                isPlaybackOngoing = false,
                connectedControllerCount = 0,
            ),
            "an idle player, no foreground playback and no controller is the crash state",
        )
    }

    @Test
    fun queuedMediaKeepsTheServiceAlive() {
        assertFalse(
            PrairiePlaybackService.hasNothingToServe(
                queuedMediaItemCount = 1,
                isPlaybackOngoing = false,
                connectedControllerCount = 0,
            ),
            "a media button that can resume queued content must still be honoured",
        )
    }

    @Test
    fun ongoingForegroundPlaybackKeepsTheServiceAlive() {
        assertFalse(
            PrairiePlaybackService.hasNothingToServe(
                queuedMediaItemCount = 0,
                isPlaybackOngoing = true,
                connectedControllerCount = 0,
            ),
            "a running foreground playback service must never be torn down",
        )
    }

    @Test
    fun connectedControllerKeepsTheServiceAlive() {
        assertFalse(
            PrairiePlaybackService.hasNothingToServe(
                queuedMediaItemCount = 0,
                isPlaybackOngoing = false,
                connectedControllerCount = 1,
            ),
            "a player screen that has just bound the service must not be stopped under it",
        )
    }

    @Test
    fun pictureInPictureActionsMustNotBeDeliveredAsForegroundServiceStarts() {
        val source = File(
            "src/androidMain/kotlin/org/prairieserver/prairie/common/pip/PrairiePictureInPictureCoordinator.kt",
        ).readText()

        assertTrue(
            source.contains("PendingIntent.getService("),
            "PiP transport actions must stay plain startService() sends",
        )
        assertFalse(
            source.contains("PendingIntent.getForegroundService("),
            "getForegroundService() would arm the start-foreground watchdog on the PiP path, " +
                "which PrairiePlaybackService.onStartCommand deliberately does not satisfy",
        )
    }
}
