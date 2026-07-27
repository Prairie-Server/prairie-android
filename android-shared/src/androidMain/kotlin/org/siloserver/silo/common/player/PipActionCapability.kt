package org.siloserver.silo.common.player

import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

internal object PipActionCapability {
    const val EXTRA_CAPABILITY =
        "org.siloserver.silo.common.player.extra.PIP_ACTION_CAPABILITY"

    private const val CAPABILITY_BYTES = 32
    private val secureRandom = SecureRandom()
    private val currentCapability = AtomicReference(generateCapability())

    fun intent(context: Context, action: String): Intent =
        Intent(context, SiloPlaybackService::class.java)
            .setAction(action)
            .putExtra(
                EXTRA_CAPABILITY,
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(currentCapability.get()),
            )

    fun isAuthorized(intent: Intent): Boolean {
        val encoded = intent.getStringExtra(EXTRA_CAPABILITY) ?: return false
        val supplied = runCatching {
            Base64.getUrlDecoder().decode(encoded)
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(currentCapability.get(), supplied)
    }

    fun rotate() {
        currentCapability.set(generateCapability())
    }

    private fun generateCapability(): ByteArray =
        ByteArray(CAPABILITY_BYTES).also(secureRandom::nextBytes)
}
