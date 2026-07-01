package org.siloserver.silo.common.pairing

import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.repository.AuthRepository

/**
 * Narrow seam over [AuthRepository] for the pairing receiver: the
 * "point the stack at this server URL" call the receiver needs when a phone
 * pushes a server, plus persisting the approved device-login tokens so the TV
 * ends up authenticated (not just navigated to profile selection). Lets the
 * unit test assert the calls without a real token manager / registry.
 * [AuthRepositoryPairingPort] adapts the production repo.
 */
interface PairingAuthPort {
    /** Upsert + switch to [url] (delegates to [AuthRepository.setServerUrl]). */
    suspend fun setServerUrl(url: String)

    /**
     * Persist the approved device-login tokens into the active server's token
     * slot — same as the credential / QR login flow ([TokenManager.saveTokens]).
     * Must run BEFORE the receiver signals SignedIn so downstream auth gating
     * sees the tokens.
     */
    suspend fun persistApprovedTokens(accessToken: String, refreshToken: String, expiresIn: Long)
}

/** Production adapter delegating to the real [AuthRepository] / [TokenManager]. */
class AuthRepositoryPairingPort(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) : PairingAuthPort {
    override suspend fun setServerUrl(url: String) = authRepository.setServerUrl(url)

    override suspend fun persistApprovedTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
    ) = tokenManager.saveTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresIn,
    )
}
