package org.prairieserver.prairie.android.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.feature.MetadataAiFeatureStore
import org.prairieserver.prairie.model.feature.RequestsFeatureStore
import org.prairieserver.prairie.model.profile.ActiveProfileStore
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.AuthApi
import org.prairieserver.prairie.network.api.ProfileApi
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.repository.AuthRepository
import org.prairieserver.prairie.repository.MetadataAiRepository
import org.prairieserver.prairie.repository.ProfileRepository
import org.prairieserver.prairie.repository.RequestsRepository
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An explicit sign-out asks the server's next provider sign-in to offer
 * another account (`prompt=select_account`): the provider's browser session
 * outlives the Prairie one, and would otherwise sign the same person straight
 * back in.
 */
class SignOutTeardownTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) })

    @AfterTest fun tearDown() = http.close()

    @Test
    fun signingOutAsksTheSignedOutServerForAnAccountChoice() = runTest {
        val tokens = TokenManagerImpl()
        val coordinator = NativeSignInCoordinator(
            InMemoryPendingNativeSignInStore(),
            idle(),
            backgroundScope,
            InMemoryAccountChoiceStore(),
        )
        val registry = idle<ServerRegistry>(mapOf("getActiveServerId" to MutableStateFlow<String?>("home")))
        val teardown = SignOutTeardown(
            authRepository = AuthRepository(AuthApi(http, ApiV2Gate.Unrestricted), tokens),
            playerSettingsStore = idle(),
            libraryPlaybackPrefsStore = idle(),
            overlayPrefsStore = idle(),
            activeProfileStore = ActiveProfileStore(ProfileRepository(ProfileApi(http, ApiV2Gate.Unrestricted), tokens)),
            cardPresentationStore = idle(),
            seekIntervalStore = idle(),
            titleArtStore = idle(),
            episodeSpoilerStore = idle(),
            requestsFeatureStore = RequestsFeatureStore(RequestsRepository(idle())),
            metadataAiFeatureStore = MetadataAiFeatureStore(MetadataAiRepository(idle())),
            serverRegistry = registry,
            nativeSignIn = coordinator,
        )
        assertFalse(coordinator.accountChoiceRequested("home"))

        teardown.signOut()

        assertTrue(coordinator.accountChoiceRequested("home"))
        assertFalse(coordinator.accountChoiceRequested("cabin"), "only the server signed out of")
    }

    /** Collaborators the sign-out doesn't depend on: they answer nothing and emit nothing. */
    private inline fun <reified T> idle(values: Map<String, Any> = emptyMap()): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            values[method.name] ?: if (Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>() else Unit
        } as T
}
