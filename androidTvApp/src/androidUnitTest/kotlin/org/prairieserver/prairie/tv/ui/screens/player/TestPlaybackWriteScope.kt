package org.prairieserver.prairie.tv.ui.screens.player

import org.prairieserver.prairie.repository.port.PlaybackWriteScope

internal val tvTestPlaybackWriteScope = PlaybackWriteScope(
    serverId = "server-test",
    profileId = "profile-test",
    credentialGenerationId = null,
    identityGeneration = 1L,
)
