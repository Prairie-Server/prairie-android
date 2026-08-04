package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Both list-size branches must key library rows by library identity, so focus
 * requesters stay bound to the same library across a reorder. Matched
 * whitespace-insensitively: this asserts the rule, not the formatting.
 */
class TvCascadeSelectorIdentitySourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvCascadeSelector.kt",
    ).readText().replace(Regex("\\s+"), " ")

    @Test
    fun eagerAndLazyLibraryRowsUseLibraryIdentity() {
        assertTrue(source.contains("key(library.id) {"), "eager rows need a stable key")
        assertTrue(
            source.contains("items(libraries, key = { it.id }) { library ->"),
            "lazy rows need a stable key",
        )
    }
}
