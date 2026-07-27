package org.prairieserver.prairie.common.pairing

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.CleartextOriginConsent
import org.prairieserver.prairie.network.CleartextOriginNotApprovedException
import org.prairieserver.prairie.network.requiresApproval

/**
 * Narrow commit seam for the pairing receiver after a candidate server approves device
 * login. Candidate requests do not mutate global auth state; this seam writes
 * the server and credentials only after approval. Lets tests assert the commit
 * without a real token manager / registry.
 */
interface PairingAuthPort {
    /**
     * Commit an approved account session. Reauthorizing the same URL is an
     * account boundary, so old profile selection/token state must not survive.
     */
    suspend fun persistApprovedSession(
        serverUrl: String,
        serverName: String?,
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
    )
}

/** Production adapter matching Apple's receiver-side persist-on-success behavior. */
class RegistryPairingAuthPort(
    private val tokenManager: TokenManager,
    private val serverRegistry: ServerRegistry,
    private val cleartextOriginConsent: CleartextOriginConsent? = null,
) : PairingAuthPort {
    private val commitMutex = Mutex()

    override suspend fun persistApprovedSession(
        serverUrl: String,
        serverName: String?,
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
    ) = withContext(NonCancellable) {
        commitMutex.withLock {
            if (cleartextOriginConsent?.requiresApproval(serverUrl) == true) {
                throw CleartextOriginNotApprovedException(serverUrl)
            }
            val previousServerId = serverRegistry.activeServerId.value
            val serverId = serverRegistry.addOrUpdate(serverUrl, fetchedName = serverName)
            try {
                // Prepare the token slot before publishing the registry switch.
                // The HTTP client follows ServerRegistry, so observers can
                // never see the approved server active without credentials.
                serverRegistry.setProfileId(serverId, null)
                tokenManager.switchActiveServer(serverId)
                tokenManager.setProfileId(null)
                tokenManager.setProfileToken(null)
                tokenManager.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                )
                serverRegistry.switchTo(serverId)
            } catch (error: Throwable) {
                if (previousServerId != null) {
                    serverRegistry.switchTo(previousServerId)
                    tokenManager.switchActiveServer(previousServerId)
                } else {
                    serverRegistry.remove(serverId)
                }
                throw error
            }
        }
    }
}
