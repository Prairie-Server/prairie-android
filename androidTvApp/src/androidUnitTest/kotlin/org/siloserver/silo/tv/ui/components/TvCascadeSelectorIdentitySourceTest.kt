package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class TvCascadeSelectorIdentitySourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvCascadeSelector.kt",
    ).readText()

    @Test
    fun eagerAndLazyLibraryRowsUseLibraryIdentity() {
        assertContains(source, "key(library.id) {")
        assertContains(source, "items(libraries, key = { it.id }) { library ->")
    }
}
