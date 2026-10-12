package org.prairieserver.prairie.android.ui.screens.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.prairieserver.prairie.android.auth.InMemoryAccountChoiceStore
import org.prairieserver.prairie.android.auth.InMemoryPendingNativeSignInStore
import org.prairieserver.prairie.android.auth.NativeSignInCompleter
import org.prairieserver.prairie.android.auth.NativeSignInCoordinator
import org.prairieserver.prairie.android.auth.SignOutTeardown
import org.prairieserver.prairie.common.player.AudiobookSettingsStore
import org.prairieserver.prairie.model.feature.MetadataAiFeatureStore
import org.prairieserver.prairie.model.feature.RequestsFeatureStore
import org.prairieserver.prairie.repository.MetadataAiRepository
import org.prairieserver.prairie.repository.RequestsRepository
import org.prairieserver.prairie.common.settings.CardPresentationUiState
import org.prairieserver.prairie.common.settings.PlayerSettingsStore
import org.prairieserver.prairie.domain.settings.ProfileSettingsController
import org.prairieserver.prairie.model.profile.ActiveProfileStore
import org.prairieserver.prairie.model.profile.Profile
import org.prairieserver.prairie.model.settings.EffectiveSettingValue
import org.prairieserver.prairie.model.settings.EffectiveSettingValuesResponse
import org.prairieserver.prairie.model.settings.SeekIntervalState
import org.prairieserver.prairie.model.settings.SettingKeys
import org.prairieserver.prairie.model.settings.SettingScopeIdentity
import org.prairieserver.prairie.model.settings.SettingsContractCapabilities
import org.prairieserver.prairie.model.settings.StoredSettingValue
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.AuthApi
import org.prairieserver.prairie.network.api.ProfileApi
import org.prairieserver.prairie.network.api.SettingsApi
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.network.apiv2.NotificationsV2Api
import org.prairieserver.prairie.network.apiv2.SettingsV2Api
import org.prairieserver.prairie.repository.AuthRepository
import org.prairieserver.prairie.repository.NotificationsRepository
import org.prairieserver.prairie.repository.ProfileRepository
import org.prairieserver.prairie.repository.SettingsRepository
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SettingsViewModelOfflinePreferencesTest {
    @Test
    fun unrelatedResolvedOverrideDoesNotDiscardAConfirmedSubtitleMode() = scenario { vm, api, profiles ->
        val modeReply = api.queueReply()
        vm.setSubtitleMode(SubtitleMode.ALWAYS)
        runCurrent()
        val metadataReply = api.queueReply()
        vm.setMetadataLanguage("fr")
        runCurrent()

        // The device overrides both keys. The unrelated response replaces the
        // optimistic mode before its own successful response reaches the screen.
        metadataReply.complete(snapshot(mode = "off", metadataLanguage = "en"))
        runCurrent()
        assertEquals(SubtitleMode.OFF, vm.uiState.value.subtitleMode)
        modeReply.complete(snapshot(mode = "off"))
        runCurrent()

        assertEquals("off", profiles.activeProfile.value?.subtitleMode)
    }

    @Test
    fun anOlderResponseCannotWinWhenANewerEditReturnsToTheSameValue() = scenario { vm, api, profiles ->
        val oldReply = api.queueReply()
        vm.setSubtitleMode(SubtitleMode.OFF)
        runCurrent()
        val middleReply = api.queueReply()
        vm.setSubtitleMode(SubtitleMode.ALWAYS)
        runCurrent()
        val newestReply = api.queueReply()
        vm.setSubtitleMode(SubtitleMode.OFF)
        runCurrent()

        newestReply.complete(snapshot(mode = "off"))
        runCurrent()
        middleReply.complete(snapshot(mode = "always"))
        runCurrent()
        // This older write was narrowed by the effective settings response.
        oldReply.complete(snapshot(mode = "auto"))
        runCurrent()

        assertEquals(SubtitleMode.OFF, vm.uiState.value.subtitleMode)
        assertEquals("off", profiles.activeProfile.value?.subtitleMode)
    }

    @Test
    fun aFailedNewerEditDoesNotDiscardAnEarlierConfirmedWrite() = scenario { vm, api, profiles ->
        val earlierReply = api.queueReply()
        vm.setSubtitleMode(SubtitleMode.ALWAYS)
        runCurrent()
        api.failNextWrite = true
        vm.setSubtitleMode(SubtitleMode.OFF)
        runCurrent()
        assertEquals(SubtitleMode.ALWAYS, vm.uiState.value.subtitleMode)

        earlierReply.complete(snapshot(mode = "always"))
        runCurrent()

        assertEquals("always", profiles.activeProfile.value?.subtitleMode)
    }

    @Test
    fun useProfileSettingsReportsWhetherTheClearsLanded() {
        var landed = true
        val cleared = mutableListOf<String>()
        val store = object : PlayerSettingsStore by idleStore<PlayerSettingsStore>() {
            override suspend fun resetAllDeviceSettings() = landed
            override suspend fun resetDeviceSetting(key: String) {
                cleared += key
            }
        }
        scenario(playerSettingsStore = store) { vm, _, _ ->
            vm.resetPlaybackOverrides()
            runCurrent()
            assertEquals(true, vm.uiState.value.playbackOverridesReset)
            vm.onPlaybackOverridesResetShown()
            assertNull(vm.uiState.value.playbackOverridesReset)

            // Unconfirmed (offline, refused): the notice must not claim success.
            landed = false
            vm.resetPlaybackOverrides()
            runCurrent()
            assertEquals(false, vm.uiState.value.playbackOverridesReset)

            vm.useProfileSetting(SettingKeys.PLAYBACK_AUTO_SKIP_CREDITS)
            runCurrent()
            assertEquals(listOf(SettingKeys.PLAYBACK_AUTO_SKIP_CREDITS), cleared)
        }
    }

    private fun scenario(
        playerSettingsStore: PlayerSettingsStore = idleStore(),
        block: suspend TestScope.(SettingsViewModel, PendingSettingsApi, ActiveProfileStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
        val tokens = TokenManagerImpl()
        val api = PendingSettingsApi(client, tokens)
        val profiles = ActiveProfileStore(object : ProfileRepository(ProfileApi(client, ApiV2Gate.Unrestricted), tokens) {
            override suspend fun getActiveProfileId() = "p1"
            override suspend fun listProfiles() = ApiResult.Success(listOf(Profile(id = "p1", name = "Test", subtitleMode = "auto")))
        })
        profiles.refresh()
        val authRepository = AuthRepository(AuthApi(client, ApiV2Gate.Unrestricted), tokens)
        val vm = SettingsViewModel(
            authRepository = authRepository,
            playerSettingsStore = playerSettingsStore,
            activeProfileStore = profiles,
            notificationsRepository = NotificationsRepository(NotificationsV2Api(client, tokens, ApiV2Gate.Unrestricted)),
            profileSettings = ProfileSettingsController(SettingsRepository(api)),
            cardPresentationStore = idleStore(mapOf("getState" to MutableStateFlow(CardPresentationUiState()))),
            seekIntervalStore = idleStore(mapOf(
                "getState" to MutableStateFlow(SeekIntervalState()),
                "getLastError" to MutableStateFlow<String?>(null),
            )),
            episodeSpoilerStore = idleStore(mapOf(
                "getState" to MutableStateFlow(org.prairieserver.prairie.common.settings.EpisodeSpoilerState()),
                "getSaveError" to MutableStateFlow<String?>(null),
            )),
            signOutTeardown = SignOutTeardown(
                authRepository = authRepository,
                playerSettingsStore = idleStore(),
                libraryPlaybackPrefsStore = idleStore(),
                overlayPrefsStore = idleStore(),
                activeProfileStore = profiles,
                cardPresentationStore = idleStore(),
                seekIntervalStore = idleStore(),
                titleArtStore = idleStore(),
                episodeSpoilerStore = idleStore(),
                requestsFeatureStore = RequestsFeatureStore(RequestsRepository(idleStore())),
                metadataAiFeatureStore = MetadataAiFeatureStore(MetadataAiRepository(idleStore())),
                serverRegistry = idleStore(mapOf("getActiveServerId" to MutableStateFlow<String?>(null))),
                nativeSignIn = NativeSignInCoordinator(
                    InMemoryPendingNativeSignInStore(),
                    idleStore<NativeSignInCompleter>(),
                    backgroundScope,
                    InMemoryAccountChoiceStore(),
                ),
            ),
            audiobookSettingsStore = AudiobookSettingsStore(RuntimeEnvironment.getApplication(), { null }),
        )
        try {
            runCurrent()
            block(vm, api, profiles)
        } finally {
            vm.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
    }

    /** Unrelated settings stores do not emit or start network work in these tests. */
    private inline fun <reified T> idleStore(values: Map<String, Any> = emptyMap()): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            values[method.name] ?: if (Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>() else Unit
        } as T

    private class PendingSettingsApi(client: HttpClient, tokens: TokenManagerImpl) :
        SettingsApi(SettingsV2Api(client, tokens, ApiV2Gate.Unrestricted)) {
        private val replies = ArrayDeque<CompletableDeferred<ApiResult<EffectiveSettingValuesResponse>>>()
        var failNextWrite = false
        fun queueReply() = CompletableDeferred<ApiResult<EffectiveSettingValuesResponse>>().also(replies::addLast)

        override suspend fun getContractCapabilities() = ApiResult.Success(SettingsContractCapabilities(manifestRevision = 1))
        override suspend fun getEffectiveValues(keys: List<String>, libraryIds: List<Int>, seriesIds: List<String>, authority: AuthScopeSnapshot?) =
            if (replies.isEmpty()) snapshot() else replies.removeFirst().await()
        override suspend fun putValue(key: String, scope: SettingScopeIdentity, value: JsonElement, profileId: String?, authority: AuthScopeSnapshot?): ApiResult<StoredSettingValue> =
            if (failNextWrite) {
                failNextWrite = false
                ApiResult.Error(503, "unavailable", "Unavailable")
            } else {
                ApiResult.Success(StoredSettingValue(key = key, scope = "profile"))
            }
    }

    companion object {
        private fun snapshot(mode: String = "auto", metadataLanguage: String = "") = ApiResult.Success(
            EffectiveSettingValuesResponse(settings = listOf(
                EffectiveSettingValue(key = SettingKeys.PLAYBACK_SUBTITLE_MODE, value = JsonPrimitive(mode)),
                EffectiveSettingValue(key = SettingKeys.CATALOG_METADATA_LANGUAGE, value = JsonPrimitive(metadataLanguage)),
            )),
        )
    }
}
