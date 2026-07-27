package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.notifications.PushDeviceRegisterRequest
import org.prairieserver.prairie.model.notifications.PushDeviceRegisterResponse
import org.prairieserver.prairie.model.profile.CreateProfileRequest
import org.prairieserver.prairie.model.profile.UpdateProfileRequest
import org.prairieserver.prairie.model.settings.EffectiveSetting
import org.prairieserver.prairie.model.settings.EffectiveSettingsResponse
import org.prairieserver.prairie.model.settings.EffectiveSubtitleAppearance
import org.prairieserver.prairie.model.settings.LibraryPlaybackPref
import org.prairieserver.prairie.model.settings.LibraryPlaybackPrefRequest
import org.prairieserver.prairie.model.settings.LibraryPlaybackPrefsResponse
import org.prairieserver.prairie.model.settings.SettingEntry
import org.prairieserver.prairie.model.settings.SettingsListResponse
import org.prairieserver.prairie.model.settings.SubtitleAppearance
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.LibraryPlaybackPrefsApi
import org.prairieserver.prairie.network.api.OverlayConfigResponse
import org.prairieserver.prairie.network.api.ProfileApi
import org.prairieserver.prairie.network.api.PushRegistrationApi
import org.prairieserver.prairie.network.api.SettingsApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SettingsProfileAndPushRepositoryCoverageTest {

    private fun mockClient(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = HttpClient(
        MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) { install(ContentNegotiation) { json(PrairieJson) } }

    @Test
    fun settingsRepositoryMapsApiResponses() = runTest {
        val api = object : SettingsApi(mockClient()) {
            override suspend fun getSettings() = ApiResult.Success(
                SettingsListResponse(settings = listOf(SettingEntry(key = "a", value = "1"))),
            )
            override suspend fun getSetting(key: String) = ApiResult.Success(SettingEntry(key = key, value = "v"))
            override suspend fun setSetting(key: String, value: String) = ApiResult.Success(Unit)
            override suspend fun deleteSetting(key: String) = ApiResult.Success(Unit)
            override suspend fun overlayConfig() = ApiResult.Success(OverlayConfigResponse(enabled = true))
            override suspend fun getDeviceSetting(key: String) = ApiResult.Success(SettingEntry(key = key, value = "d"))
            override suspend fun setDeviceSetting(key: String, value: String, profileId: String?) = ApiResult.Success(Unit)
            override suspend fun deleteDeviceSetting(key: String) = ApiResult.Success(Unit)
            override suspend fun getEffectiveSettings(keys: List<String>) = ApiResult.Success(
                EffectiveSettingsResponse(
                    settings = listOf(EffectiveSetting(key = "a", effectiveValue = "1", source = "default")),
                ),
            )
            override suspend fun getEffectiveSubtitleAppearance() = ApiResult.Success(
                EffectiveSubtitleAppearance(key = "subtitle_appearance", globalValue = "{}", effectiveValue = "{}"),
            )
            override suspend fun setDeviceSubtitleAppearanceOverride(
                appearance: SubtitleAppearance,
                profileId: String?,
            ) = ApiResult.Success(Unit)
            override suspend fun deleteDeviceSubtitleAppearanceOverride() = ApiResult.Success(Unit)
        }
        val repo = SettingsRepository(api)
        assertEquals(mapOf("a" to "1"), (repo.listSettings() as ApiResult.Success).data)
        assertEquals("v", (repo.getSetting("a") as ApiResult.Success).data)
        assertIs<ApiResult.Success<*>>(repo.setSetting("a", "1"))
        assertIs<ApiResult.Success<*>>(repo.deleteSetting("a"))
        assertIs<ApiResult.Success<*>>(repo.overlayConfig())
        assertEquals("d", (repo.getDeviceSetting("a") as ApiResult.Success).data)
        assertIs<ApiResult.Success<*>>(repo.setDeviceSetting("a", "1"))
        assertIs<ApiResult.Success<*>>(repo.deleteDeviceSetting("a"))
        assertEquals("1", (repo.getEffectiveSettings(listOf("a")) as ApiResult.Success).data["a"]?.effectiveValue)
        assertIs<ApiResult.Success<*>>(repo.getEffectiveSubtitleAppearance())
        assertIs<ApiResult.Success<*>>(repo.setDeviceSubtitleAppearanceOverride(SubtitleAppearance()))
        assertIs<ApiResult.Success<*>>(repo.deleteDeviceSubtitleAppearanceOverride())
    }

    @Test
    fun libraryPlaybackPrefsRepositoryMapsByLibraryId() = runTest {
        val api = object : LibraryPlaybackPrefsApi(mockClient()) {
            override suspend fun list() = ApiResult.Success(
                LibraryPlaybackPrefsResponse(
                    preferences = listOf(LibraryPlaybackPref(profileId = "p", libraryId = 7, audioLanguage = "en")),
                ),
            )
            override suspend fun set(libraryId: Int, request: LibraryPlaybackPrefRequest) = ApiResult.Success(Unit)
            override suspend fun delete(libraryId: Int) = ApiResult.Success(Unit)
        }
        val repo = LibraryPlaybackPrefsRepository(api)
        assertEquals("en", (repo.list() as ApiResult.Success).data[7]?.audioLanguage)
        assertIs<ApiResult.Success<*>>(
            repo.set(7, audioLanguage = "en", subtitleLanguage = null, subtitleMode = "auto", showForcedSubtitles = true),
        )
        assertIs<ApiResult.Success<*>>(repo.delete(7))
    }

    @Test
    fun pushRegistrationRepositoryBuildsAndroidPayload() = runTest {
        var registered: PushDeviceRegisterRequest? = null
        val api = object : PushRegistrationApi {
            override suspend fun register(request: PushDeviceRegisterRequest): ApiResult<PushDeviceRegisterResponse> {
                registered = request
                return ApiResult.Success(PushDeviceRegisterResponse(id = "1"))
            }
            override suspend fun delete(deviceId: String) = ApiResult.Success(Unit)
        }
        val repo = PushRegistrationRepository(api)
        assertIs<ApiResult.Success<*>>(repo.registerAndroidDevice(token = "t", deviceId = "d1"))
        assertEquals(PushRegistrationRepository.PLATFORM_ANDROID, registered?.platform)
        assertEquals(PushRegistrationRepository.PUSH_MODE_PRIVATE, registered?.pushMode)
        assertIs<ApiResult.Success<*>>(repo.unregisterDevice("d1"))
    }

    @Test
    fun profileRepositoryActiveLookupsAndMutations() = runTest {
        val tokens = TokenManagerImpl().apply { setProfileId("p1") }
        val profileJson = """{"id":"p1","name":"Main","is_child":false}"""
        val listClient = mockClient(body = """{"profiles":[$profileJson]}""")
        val repo = ProfileRepository(profileApi = ProfileApi(listClient), tokenManager = tokens)

        assertEquals("Main", (repo.listProfiles() as ApiResult.Success).data.single().name)
        assertEquals("Main", repo.getActiveProfile()?.name)
        assertEquals("Main", (repo.getActiveProfileResult() as ApiResult.Success).data.name)

        assertIs<ApiResult.Success<*>>(
            ProfileRepository(
                profileApi = ProfileApi(mockClient(body = profileJson)),
                tokenManager = tokens,
            ).createProfile(CreateProfileRequest(name = "Main")),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileRepository(
                profileApi = ProfileApi(mockClient(body = profileJson)),
                tokenManager = tokens,
            ).updateProfile("p1", UpdateProfileRequest(name = "Main")),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileRepository(
                profileApi = ProfileApi(mockClient(body = profileJson)),
                tokenManager = tokens,
            ).updateActiveProfile(UpdateProfileRequest(name = "Main")),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileRepository(
                profileApi = ProfileApi(mockClient(status = HttpStatusCode.NoContent, body = "")),
                tokenManager = tokens,
            ).deleteProfile("p1"),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileRepository(
                profileApi = ProfileApi(mockClient(body = """{"valid":true,"profile_token":"pt"}""")),
                tokenManager = tokens,
            ).verifyPin("p1", "1234"),
        )
        assertEquals("pt", tokens.getProfileToken())

        tokens.setProfileId(null)
        val cleared = ProfileRepository(profileApi = ProfileApi(listClient), tokenManager = tokens)
        assertNull(cleared.getActiveProfile())
        assertIs<ApiResult.Error>(cleared.getActiveProfileResult())
        assertIs<ApiResult.Error>(cleared.updateActiveProfile(UpdateProfileRequest(name = "x")))
    }
}
