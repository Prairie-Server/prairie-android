package com.continuum.app.repository

import com.continuum.app.model.profile.CreateProfileRequest
import com.continuum.app.model.profile.Profile
import com.continuum.app.model.profile.UpdateProfileRequest
import com.continuum.app.model.profile.VerifyPinResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import com.continuum.app.network.api.ProfileApi
import com.continuum.app.network.map

open class ProfileRepository(
    private val profileApi: ProfileApi,
    private val tokenManager: TokenManager,
    private val serverRegistry: ServerRegistry? = null,
) {
    /** Lists all profiles for the current user. */
    open suspend fun listProfiles(): ApiResult<List<Profile>> =
        profileApi.listProfiles().map { it.profiles }

    /** Creates a new profile. */
    open suspend fun createProfile(request: CreateProfileRequest): ApiResult<Profile> =
        profileApi.createProfile(request)

    /** Updates an existing profile. */
    open suspend fun updateProfile(id: String, request: UpdateProfileRequest): ApiResult<Profile> =
        profileApi.updateProfile(id, request)

    suspend fun updateActiveProfile(request: UpdateProfileRequest): ApiResult<Profile> {
        val profileId = getActiveProfileId()
            ?: return ApiResult.Error(
                code = 400,
                error = "bad_request",
                message = "No active profile selected",
            )
        return updateProfile(profileId, request)
    }

    /** Deletes a profile by ID. */
    suspend fun deleteProfile(id: String): ApiResult<Unit> =
        profileApi.deleteProfile(id)

    /**
     * Verifies a profile's PIN.
     * On success, persists the profile token via [TokenManager].
     */
    suspend fun verifyPin(profileId: String, pin: String): ApiResult<VerifyPinResponse> {
        val result = profileApi.verifyPin(profileId, pin)
        if (result is ApiResult.Success) {
            result.data.profileToken?.let { token ->
                tokenManager.setProfileToken(token)
            }
        }
        return result
    }

    /**
     * Selects a profile as the active profile.
     *
     * Persists the profile id on the active [TokenManager] slot AND on the
     * matching [ServerRegistry] entry — the latter is what restores the
     * "last used profile" when the user hops back to this server.
     */
    suspend fun selectProfile(profileId: String) {
        tokenManager.setProfileId(profileId)
        val activeServerId = tokenManager.getCurrentServerId()
        if (activeServerId != null) {
            serverRegistry?.setProfileId(activeServerId, profileId)
        }
    }

    /** Returns the currently active profile ID, if any. */
    open suspend fun getActiveProfileId(): String? =
        tokenManager.getProfileId()

    /**
     * Returns the currently active [Profile] by looking up the stored active
     * id against [listProfiles]. Intended for read-mostly consumers (e.g. the
     * player needing `language` / `subtitleLanguage` for track selection) —
     * callers that need to react to profile changes should observe the state
     * they drive the switch from.
     */
    suspend fun getActiveProfile(): Profile? {
        val activeId = getActiveProfileId() ?: return null
        val profiles = when (val result = listProfiles()) {
            is ApiResult.Success -> result.data
            else -> return null
        }
        return profiles.firstOrNull { it.id == activeId }
    }

    suspend fun getActiveProfileResult(): ApiResult<Profile> {
        val activeId = getActiveProfileId()
            ?: return ApiResult.Error(
                code = 400,
                error = "bad_request",
                message = "No active profile selected",
            )
        return when (val result = listProfiles()) {
            is ApiResult.Success -> {
                result.data.firstOrNull { it.id == activeId }?.let { ApiResult.Success(it) }
                    ?: ApiResult.Error(
                        code = 404,
                        error = "not_found",
                        message = "Active profile not found",
                    )
            }
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }
    }

    /** Clears the active profile selection and its token. */
    suspend fun clearProfile() {
        val activeServerId = tokenManager.getCurrentServerId()
        tokenManager.setProfileId(null)
        tokenManager.setProfileToken(null)
        if (activeServerId != null) {
            serverRegistry?.setProfileId(activeServerId, null)
        }
    }
}
