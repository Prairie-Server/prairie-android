package com.continuum.app.repository

import com.continuum.app.model.admin.AdminSession
import com.continuum.app.model.admin.AdminSettingValue
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.Library
import com.continuum.app.model.admin.ScanRequest
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.AdminApi
import com.continuum.app.network.map

class AdminRepository(
    private val adminApi: AdminApi,
) {
    /** Fetches server-wide statistics (for the admin dashboard). */
    suspend fun getStats(): ApiResult<AdminStats> =
        adminApi.getStats()

    /** Lists all users on the server. */
    suspend fun getUsers(): ApiResult<List<AdminUser>> =
        adminApi.getUsers().map { it.users }

    /** Creates a new user account. */
    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> =
        adminApi.createUser(request)

    /** Updates an existing user account. */
    suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> =
        adminApi.updateUser(id, request)

    /** Deletes a user account. */
    suspend fun deleteUser(id: Int): ApiResult<Unit> =
        adminApi.deleteUser(id)

    /** Lists all media libraries configured on the server. */
    suspend fun getLibraries(): ApiResult<List<Library>> =
        adminApi.getLibraries().map { it.libraries }

    /**
     * Triggers a library scan.
     * @param libraryId optional library ID; if null, scans all libraries.
     */
    suspend fun triggerScan(libraryId: Int? = null): ApiResult<Unit> =
        adminApi.triggerScan(ScanRequest(libraryId = libraryId))
}
