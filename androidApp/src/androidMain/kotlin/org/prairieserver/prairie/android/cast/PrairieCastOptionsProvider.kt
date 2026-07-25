package org.prairieserver.prairie.android.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import org.prairieserver.prairie.android.MainActivity

/**
 * Google Cast (Chromecast) configuration. Uses the Default Media Receiver so no
 * custom receiver app registration is needed.
 *
 * Referenced by name from AndroidManifest.xml
 * (`com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`). This is
 * DISTINCT from the NSD/mDNS PrairieCast device-remote — that casts to Prairie's own
 * TV app and does not use the Cast SDK.
 */
class PrairieCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            // Without media-notification options the Cast SDK tears the session
            // down ~10s after the app leaves the foreground (observed on-device:
            // background → CastService "Disposing ConnectedClient" → receiver
            // stops). The notification keeps the session alive app-wide and
            // gives lock-screen/notification transport controls for free.
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setNotificationOptions(
                        NotificationOptions.Builder()
                            .setTargetActivityClassName(MainActivity::class.java.name)
                            // ±30s alongside play/pause and stop; the compact
                            // (lock-screen) view keeps the three transport
                            // actions. The Cast framework handles the seeks.
                            .setActions(
                                listOf(
                                    MediaIntentReceiver.ACTION_REWIND,
                                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                                    MediaIntentReceiver.ACTION_FORWARD,
                                    MediaIntentReceiver.ACTION_STOP_CASTING,
                                ),
                                intArrayOf(0, 1, 2),
                            )
                            .setSkipStepMs(30_000L)
                            .build(),
                    )
                    .build(),
            )
            .setEnableReconnectionService(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
