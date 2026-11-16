package org.prairieserver.prairie.android.cast

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.prairieserver.prairie.common.R as CommonR
import org.prairieserver.prairie.common.i18n.EnglishText
import org.prairieserver.prairie.common.i18n.getStringWithNames
import org.prairieserver.prairie.common.i18n.stringResourceWithNames

/**
 * Text the remote shows: a catalog message resolved when it is drawn, so a
 * language change applies to an error already on screen, or text a TV or the
 * server sent, shown as it arrived.
 */
sealed interface PrairieCastText {
    data class Res(@StringRes val id: Int, val args: List<Any>) : PrairieCastText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Verbatim(val text: String) : PrairieCastText

    /** The text as shown; TV names and other text arguments are isolated in a right-to-left interface. */
    fun resolve(resources: Resources): String = when (this) {
        is Res -> if (args.isNotEmpty() && args.all { it is String }) {
            resources.getStringWithNames(id, *args.map { it as String }.toTypedArray())
        } else {
            resources.getString(id, *args.toTypedArray())
        }
        is Verbatim -> text
    }

    /** The text in English, for logs and protocol messages a TV reads (null in unit tests). */
    fun english(): String? = when (this) {
        is Res -> EnglishText.string(id, *args.toTypedArray())
        is Verbatim -> text
    }
}

@Composable
fun PrairieCastText.resolve(): String {
    return resolve(LocalResources.current)
}

/**
 * "Connected to Living Room" while this phone has a TV, "Reconnecting…"
 * while the link to it is down, and null with no TV. Non-null exactly when
 * [PrairieCastController.launchOnConnectedTarget] takes Play for the TV instead
 * of leaving it to the phone, so Play and the Remote menus can name the TV.
 */
@Composable
fun PrairieCastControllerState.engagedStatus(): String? {
    val target = connectedTarget ?: return null
    return if (isReconnecting) {
        stringResource(CommonR.string.cast_reconnecting)
    } else {
        stringResourceWithNames(CommonR.string.cast_remote_connectedTo, target.name)
    }
}

/**
 * A Remote Control failure the user sees. The exception message is its text in
 * English ([english], when the shown text is a translation of a server's
 * message), which logs and the handoff cancel sent to the TV carry.
 */
open class PrairieCastFailure(val text: PrairieCastText, cause: Throwable? = null, english: String? = text.english()) :
    IllegalStateException(english, cause) {
    constructor(@StringRes id: Int) : this(PrairieCastText.Res(id))
}

/** What to show for this failure: its [PrairieCastFailure.text], else its message. */
internal fun Throwable.castTextOrNull(): PrairieCastText? =
    (this as? PrairieCastFailure)?.text ?: message?.let(PrairieCastText::Verbatim)

internal fun Throwable.castText(@StringRes fallback: Int): PrairieCastText =
    castTextOrNull() ?: PrairieCastText.Res(fallback)
