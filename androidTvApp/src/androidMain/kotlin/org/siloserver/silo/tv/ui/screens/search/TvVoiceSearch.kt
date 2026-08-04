package org.siloserver.silo.tv.ui.screens.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Voice search on TV, spoken into the remote.
 *
 * This deliberately hands off to the system recogniser rather than recording
 * anything itself. On a Shield the recogniser listens through the remote's
 * microphone, which is the hardware the viewer expects to be talking into, and
 * because the recording happens in that app rather than this one Silo needs no
 * RECORD_AUDIO permission at all — nothing here can listen, only ask.
 *
 * The remote's own mic BUTTON cannot be used to start this: Android TV binds it
 * to the system assistant before any app sees it. An on-screen affordance is
 * the only way an app can offer voice, which is why the mic lives beside the
 * search field.
 */
internal class TvVoiceSearchController(
    /**
     * False when no recogniser is installed, which is ordinary on a bare AOSP
     * TV box. Callers hide the affordance rather than offering a button that
     * cannot do anything.
     */
    val isAvailable: Boolean,
    private val launch: () -> Unit,
) {
    fun start() {
        if (isAvailable) launch()
    }
}

@Composable
internal fun rememberTvVoiceSearch(
    prompt: String,
    onResult: (String) -> Unit,
): TvVoiceSearchController {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    // Resolved once. Installing a recogniser mid-session is not a case worth
    // recomposing for, and re-querying the package manager on every frame is.
    val isAvailable = remember(context) { isTvSpeechRecognitionAvailable(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            // The list is ordered by confidence, so the first entry is the
            // recogniser's own best guess. Silo has no better way to choose
            // between alternates than the engine that produced them.
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        // A cancelled or empty recognition must not wipe a query the viewer
        // already typed.
        if (spoken.isNotEmpty()) currentOnResult(spoken)
    }

    return remember(isAvailable, prompt, launcher) {
        TvVoiceSearchController(isAvailable = isAvailable) {
            runCatching { launcher.launch(tvSpeechRecognizerIntent(prompt)) }
        }
    }
}

private fun tvSpeechRecognizerIntent(prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        // Free-form rather than web search: these are film, series and book
        // titles, not queries, and the web-search model rewrites them toward
        // whatever it thinks you meant to google.
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        // One result is all the caller uses; asking for more only makes the
        // recogniser work harder for output that gets discarded.
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

/**
 * Whether anything on this device can handle a recognition request.
 *
 * Needs the matching `<queries>` element in the manifest — from Android 11 an
 * app cannot see packages it has not declared an interest in, so without it
 * this returns false on every modern device and the mic silently never appears.
 */
private fun isTvSpeechRecognitionAvailable(context: Context): Boolean =
    context.packageManager
        .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        .isNotEmpty()
