package org.prairieserver.prairie.discovery

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.HealthApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class LanScanOptions(
    val extraCidrs: List<String> = emptyList(),
    /** When true, sweep full /24 ranges on :8080. Default false (priority hosts). */
    val deepScan: Boolean = false,
    val maxHostsPerCidr: Int = 254,
    val concurrency: Int = 24,
    val localIps: List<String>? = null,
    val onHit: ((List<DiscoveryHit>) -> Unit)? = null,
    val onProgress: ((done: Int, total: Int) -> Unit)? = null,
)

/**
 * Probe Prairie health across LAN candidates.
 *
 * Prefer a priority pass first, then an optional deep pass from the UI.
 */
class LanDiscovery(
    private val healthApi: HealthApi,
) {

    suspend fun scan(options: LanScanOptions = LanScanOptions()): List<DiscoveryHit> {
        val localIps = options.localIps ?: localIpv4Addresses()
        val candidates = buildCandidates(
            BuildCandidatesOptions(
                extraCidrs = options.extraCidrs,
                deepScan = options.deepScan,
                maxHostsPerCidr = options.maxHostsPerCidr,
                localIps = localIps,
            ),
        )
        if (candidates.isEmpty()) return emptyList()

        val stateMutex = Mutex()
        var hits = emptyList<DiscoveryHit>()
        var done = 0
        val semaphore = Semaphore(options.concurrency.coerceAtLeast(1))

        coroutineScope {
            candidates.map { url ->
                async {
                    semaphore.withPermit {
                        val hit = probeHealth(url)
                        stateMutex.withLock {
                            done += 1
                            options.onProgress?.invoke(done, candidates.size)
                            if (hit != null) {
                                hits = mergeHits(
                                    hits,
                                    hit.url,
                                    HealthIdentity(hit.serverName, hit.serverId),
                                )
                                options.onHit?.invoke(hits)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        return stateMutex.withLock { hits }
    }

    private suspend fun probeHealth(baseUrl: String): DiscoveryHit? {
        val serverUrl = normalizeDiscoveryUrl(baseUrl)
        return when (val result = healthApi.checkHealth(serverUrl)) {
            is ApiResult.Success -> {
                val identity = parseHealth(
                    status = result.data.status,
                    serverName = result.data.serverName,
                    serverId = result.data.serverId,
                ) ?: return null
                DiscoveryHit(
                    url = serverUrl,
                    serverName = identity.serverName,
                    serverId = identity.serverId,
                )
            }
            else -> null
        }
    }
}
