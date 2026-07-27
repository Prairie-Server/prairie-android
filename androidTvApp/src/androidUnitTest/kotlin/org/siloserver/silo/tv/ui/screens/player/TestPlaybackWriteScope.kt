package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.repository.port.PlaybackWriteScope

internal val tvTestPlaybackWriteScope = PlaybackWriteScope(
    serverId = "server-test",
    profileId = "profile-test",
    credentialGenerationId = null,
    identityGeneration = 1L,
)
