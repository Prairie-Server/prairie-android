package org.prairieserver.prairie.android.ui.screens.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MobilePlayerLoadOwnerTest {
    @Test
    fun `same content file and quality completions publish only newest owner`() = runTest {
        val registry = MobilePlayerLoadOwnerRegistry()
        val firstOwner = registry.begin("content-a", 11, "original")
        val firstCompletion = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        val firstLoad = launch {
            firstCompletion.await()
            if (!registry.runIfOwned(firstOwner) { published += "first" }) {
                rejected += "first"
            }
        }

        val newestOwner = registry.begin("content-a", 22, "720p")
        val newestCompletion = CompletableDeferred<Unit>()
        val newestLoad = launch {
            newestCompletion.await()
            if (!registry.runIfOwned(newestOwner) { published += "newest" }) {
                rejected += "newest"
            }
        }

        newestCompletion.complete(Unit)
        runCurrent()
        firstCompletion.complete(Unit)
        runCurrent()

        assertEquals(listOf("newest"), published)
        assertEquals(listOf("first"), rejected)
        firstLoad.join()
        newestLoad.join()
    }

    @Test
    fun `different content stale completion is rejected for explicit cleanup`() = runTest {
        val registry = MobilePlayerLoadOwnerRegistry()
        val oldOwner = registry.begin("content-a", 11, "original")
        val staleReady = CompletableDeferred<Unit>()
        val stoppedSessions = mutableListOf<String>()
        val oldLoad = launch {
            staleReady.await()
            if (!registry.runIfOwned(oldOwner) { error("stale load published") }) {
                stoppedSessions += "stale-session"
            }
        }

        val currentOwner = registry.begin("content-b", 33, "auto")
        staleReady.complete(Unit)
        runCurrent()

        assertTrue(registry.owns(currentOwner))
        assertEquals(listOf("stale-session"), stoppedSessions)
        oldLoad.join()
    }

    @Test
    fun `out of order content version and quality loads accept only newest owner`() {
        val type = runCatching {
            Class.forName(
                "org.prairieserver.prairie.android.ui.screens.player.MobilePlayerLoadOwnerRegistry",
            )
        }.getOrNull()
        assertNotNull(type, "Player loads need an explicit generation owner.")
        val registry = type.getDeclaredConstructor().newInstance()
        val begin = type.declaredMethods.single { it.name == "begin" }
        val owns = type.declaredMethods.single { it.name == "owns" }

        val first = begin.invoke(registry, "content-a", 11, "original")
        val versionQualityRestart = begin.invoke(registry, "content-a", 22, "720p")
        val nextContent = begin.invoke(registry, "content-b", 33, "auto")

        assertFalse(owns.invoke(registry, first) as Boolean)
        assertFalse(owns.invoke(registry, versionQualityRestart) as Boolean)
        assertTrue(owns.invoke(registry, nextContent) as Boolean)
    }

    @Test
    fun `exit invalidates current load owner`() {
        val type = runCatching {
            Class.forName(
                "org.prairieserver.prairie.android.ui.screens.player.MobilePlayerLoadOwnerRegistry",
            )
        }.getOrNull()
        assertNotNull(type, "Player loads need an explicit generation owner.")
        val registry = type.getDeclaredConstructor().newInstance()
        val begin = type.declaredMethods.single { it.name == "begin" }
        val owns = type.declaredMethods.single { it.name == "owns" }
        val invalidate = type.declaredMethods.single { it.name == "invalidate" }
        val owner = begin.invoke(registry, "content-a", 11, "original")

        invalidate.invoke(registry)

        assertFalse(owns.invoke(registry, owner) as Boolean)
    }
}
