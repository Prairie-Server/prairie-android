package org.prairieserver.prairie.tv.cast

import org.prairieserver.prairie.cast.PrairieCastControlCommand

class TvPrairieCastPlayerAdapter(
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
    fun handle(command: PrairieCastControlCommand) {
        // Field mapping follows Apple's PrairieControlCommand exactly: seek uses
        // `seconds`, quality/gravity/subtitle-position share `value`, subtitle
        // delay is `milliseconds`, mute rides `enabled`.
        when (command.name) {
            PrairieCastControlCommand.Play -> play()
            PrairieCastControlCommand.Pause -> pause()
            PrairieCastControlCommand.PlayPause -> playPause()
            PrairieCastControlCommand.Stop -> stop()
            PrairieCastControlCommand.Seek -> command.seconds?.let(seek)
            PrairieCastControlCommand.SelectAudioTrack -> command.trackId?.let(selectAudio)
            PrairieCastControlCommand.SelectSubtitleTrack -> selectSubtitle(command.trackId)
            PrairieCastControlCommand.SetQuality -> command.value?.let(setQuality)
            PrairieCastControlCommand.SetPlaybackSpeed -> command.speed?.let(setPlaybackSpeed)
            PrairieCastControlCommand.SetVideoGravity -> command.value?.let(setVideoGravity)
            PrairieCastControlCommand.SetHdrEnabled -> command.enabled?.let(setHdrEnabled)
            PrairieCastControlCommand.SetSubtitleSyncMs -> command.milliseconds?.let(setSubtitleSyncMs)
            PrairieCastControlCommand.SetSubtitlePosition -> command.value?.let(setSubtitlePosition)
            PrairieCastControlCommand.SetVolume -> command.volume?.let(setVolume)
            PrairieCastControlCommand.SetMuted -> command.enabled?.let(setMuted)
            PrairieCastControlCommand.PlayNext -> playNext()
            else -> Unit
        }
    }
}
