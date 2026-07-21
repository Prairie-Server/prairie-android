package org.siloserver.silo.android.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Google Cast (Chromecast) configuration. Uses the Default Media Receiver so no
 * custom receiver app registration is needed.
 *
 * Referenced by name from AndroidManifest.xml
 * (`com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`). This is
 * DISTINCT from the NSD/mDNS SiloCast device-remote — that casts to Silo's own
 * TV app and does not use the Cast SDK.
 */
class SiloCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
