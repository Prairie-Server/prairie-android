package org.prairieserver.prairie.android.ui.screens.cast

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import org.prairieserver.prairie.android.cast.PrairieCastController
import org.prairieserver.prairie.android.cast.PrairieCastLaunchFeedback

/**
 * Haptics for a Play sent to a TV, wherever the user is in the app: a light
 * tick when it goes out, success once the TV reports the title, and an error
 * if it fails. Mirrors prairie-apple's remote.
 */
@Composable
fun PrairieCastLaunchHaptics(controller: PrairieCastController) {
    val view = LocalView.current
    LaunchedEffect(controller, view) {
        controller.launchFeedback.collect { feedback ->
            view.performHapticFeedback(
                when (feedback) {
                    PrairieCastLaunchFeedback.Sent -> HapticFeedbackConstants.CLOCK_TICK
                    PrairieCastLaunchFeedback.Started ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.CONFIRM
                        } else {
                            HapticFeedbackConstants.LONG_PRESS
                        }
                    PrairieCastLaunchFeedback.Failed ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.REJECT
                        } else {
                            HapticFeedbackConstants.LONG_PRESS
                        }
                },
            )
        }
    }
}
