package org.siloserver.silo.tv.cast

import org.siloserver.silo.cast.SiloCastControlCommand

class TvSiloCastPlayerAdapter(
    private val play: () -> Unit,
    private val pause: () -> Unit,
    private val playPause: () -> Unit,
    private val seek: (Double) -> Unit,
    private val stop: () -> Unit,
    private val selectAudio: (Long) -> Unit,
    private val selectSubtitle: (Long?) -> Unit,
    private val setPlaybackSpeed: (Double) -> Unit,
    private val setQuality: (String) -> Unit,
    private val setVideoGravity: (String) -> Unit,
    private val setHdrEnabled: (Boolean) -> Unit,
    private val setSubtitleSyncMs: (Int) -> Unit,
    private val setSubtitlePosition: (String) -> Unit,
    private val setVolume: (Double) -> Unit,
    private val setMuted: (Boolean) -> Unit,
    private val playNext: () -> Unit,
) {
    fun handle(command: SiloCastControlCommand) {
        when (command.name) {
            SiloCastControlCommand.Play -> play()
            SiloCastControlCommand.Pause -> pause()
            SiloCastControlCommand.PlayPause -> playPause()
            SiloCastControlCommand.Stop -> stop()
            SiloCastControlCommand.Seek -> command.position?.let(seek)
            SiloCastControlCommand.SelectAudioTrack -> command.trackId?.toLongOrNull()?.let(selectAudio)
            SiloCastControlCommand.SelectSubtitleTrack -> selectSubtitle(command.trackId?.toLongOrNull())
            SiloCastControlCommand.SelectQuality -> command.qualityId?.let(setQuality)
            SiloCastControlCommand.SetPlaybackSpeed -> command.playbackSpeed?.let(setPlaybackSpeed)
            SiloCastControlCommand.SetVideoGravity -> command.videoGravity?.let(setVideoGravity)
            SiloCastControlCommand.SetHdrEnabled -> command.enabled?.let(setHdrEnabled)
            SiloCastControlCommand.SetSubtitleDelay -> command.deltaMs?.let(setSubtitleSyncMs)
            SiloCastControlCommand.SetSubtitlePosition -> command.position?.toString()?.let(setSubtitlePosition)
            SiloCastControlCommand.SetVolume -> command.volume?.let(setVolume)
            SiloCastControlCommand.SetMuted -> command.enabled?.let(setMuted)
            SiloCastControlCommand.NextEpisode -> playNext()
            else -> Unit
        }
    }
}
