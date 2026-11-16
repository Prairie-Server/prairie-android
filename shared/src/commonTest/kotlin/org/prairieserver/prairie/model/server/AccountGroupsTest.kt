package org.prairieserver.prairie.model.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountGroupsTest {
    private fun server(id: String, lastUsed: Long) = ServerEntry(id = id, url = "https://$id.example", lastUsedAtEpochMs = lastUsed)

    private fun account(id: String, serverId: String, added: Long, profiles: Int = 0) = AccountEntry(
        localAccountId = id,
        serverId = serverId,
        addedAtEpochMs = added,
        cachedProfiles = List(profiles) { CachedProfile(id = "$id-p$it", name = "P$it") },
    )

    @Test
    fun activeServerFirstThenByLastUseWithAccountsInAddedOrder() {
        val groups = buildAccountGroups(
            servers = listOf(server("a", lastUsed = 30), server("b", lastUsed = 10), server("c", lastUsed = 20)),
            accounts = listOf(
                account("b2", "b", added = 2, profiles = 3),
                account("b1", "b", added = 1),
                account("c1", "c", added = 5),
            ),
            activeServerId = "b",
            activeAccountId = "b2",
        )

        assertEquals(listOf("b", "a", "c"), groups.map { it.server.id })
        assertEquals(listOf("b1", "b2"), groups[0].accounts.map { it.account.localAccountId })
        assertEquals(listOf(false, true), groups[0].accounts.map { it.isCurrent })
        assertEquals(3, groups[0].accounts[1].profileCount)
        assertTrue(groups[1].accounts.isEmpty())
        assertEquals(listOf("c1"), groups[2].accounts.map { it.account.localAccountId })
    }

    @Test
    fun noActiveServerOrdersByLastUse() {
        val groups = buildAccountGroups(
            servers = listOf(server("a", lastUsed = 1), server("b", lastUsed = 2)),
            accounts = emptyList(),
            activeServerId = null,
            activeAccountId = null,
        )
        assertEquals(listOf("b", "a"), groups.map { it.server.id })
    }
}
