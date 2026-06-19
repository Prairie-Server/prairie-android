package com.continuum.app.tv.ui.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvProfileAvatarParityTest {
    private val shell = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt",
    ).readText()

    private val topMenu = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt",
    ).readText()

    @Test
    fun accountSnapshotResolvesProfileAvatarImageUrlsForTopChrome() {
        assertTrue(shell.contains("import com.continuum.app.common.ui.components.isImageAvatar"))
        assertTrue(shell.contains("import com.continuum.app.common.ui.components.resolveAvatarUrl"))
        assertTrue(shell.contains("val avatarUrl = activeProfile?.avatar"))
        assertTrue(shell.contains("?.takeIf(::isImageAvatar)"))
        assertTrue(shell.contains("?.let { resolveAvatarUrl(activeServerEntry?.url.orEmpty(), it) }"))
        assertTrue(shell.contains("avatarUrl = avatarUrl,"))
    }

    @Test
    fun profileChromeUsesResolvedAvatarImagesBeforeInitials() {
        assertTrue(topMenu.contains("if (accountState.avatarUrl != null)"))
        assertTrue(shell.contains("if (accountState.avatarUrl != null)"))
        assertTrue(shell.contains("url = accountState.avatarUrl"))
    }
}
