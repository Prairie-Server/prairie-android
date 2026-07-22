package org.siloserver.silo.android.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.siloserver.silo.common.player.cast.CastMediaSpec

data class SiloCastState(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isPlaying: Boolean = false,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val title: String = "",
    val fileId: Int? = null,
    /** Text tracks declared on the loaded media, for the phone-side CC picker. */
    val subtitleOptions: List<CastSubtitleOption> = emptyList(),
    /** Track id (from [subtitleOptions]) the receiver is rendering, null = off. */
    val activeSubtitleId: Long? = null,
)

/** A selectable receiver text track (id is the declared MediaTrack id). */
data class CastSubtitleOption(
    val id: Long,
    val label: String,
)

data class SiloCastRoute(
    val id: String,
    val name: String,
    val isConnecting: Boolean = false,
    val isSelected: Boolean = false,
)

/**
 * Google Cast (Chromecast) session manager for the phone app.
 *
 * This is the Cast-SDK counterpart to the NSD/mDNS SiloCast device-remote; the
 * two coexist. The Tier-2 cast stream is prepared separately
 * ([org.siloserver.silo.common.player.cast.CastPlaybackPreparer]); this class
 * only owns the Cast session lifecycle, the live remote state, and the route
 * picker plumbing.
 *
 * Robustness fixes over the reference implementation:
 *  1. Live remote state — a [RemoteMediaClient.Callback] + progress listener
 *     keep [castState] (position / isPlaying / duration) current while casting.
 *  2. Google-Play guard — [CastContext.getSharedInstance] throws on devices
 *     without Play Services, so construction is a no-op there and never crashes.
 *  3. Scoped active scan — high-power route discovery runs only while the picker
 *     is shown ([startDiscovery] / [stopDiscovery]); passive otherwise.
 *  6. Position sync-back — the remote position is captured on session end and
 *     exposed via [getLastPosition] so local playback resumes where casting left.
 */
class SiloCastSessionManager(private val context: Context) {
    private companion object {
        private const val TAG = "SiloCastSessionMgr"
    }


    private val playServicesAvailable: Boolean =
        runCatching {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

    // Lazy so no Cast-framework init happens off the main thread at DI creation
    // time. All public entry points funnel through ensureInitialized() first.
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector = MediaRouteSelector.EMPTY
    private var initialized = false
    private var activeScanning = false

    private val _castState = MutableStateFlow(SiloCastState())
    val castState: StateFlow<SiloCastState> = _castState.asStateFlow()
    private val _availableRoutes = MutableStateFlow<List<SiloCastRoute>>(emptyList())
    val availableRoutes: StateFlow<List<SiloCastRoute>> = _availableRoutes.asStateFlow()

    // Pending media set by prepareMedia; loaded when a session connects.
    private var pending: CastMediaSpec? = null
    private var lastPosition: Double = 0.0
    private var listenersAttachedTo: CastSession? = null

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = syncCastState()
        override fun onMetadataUpdated() = syncCastState()
    }

    // Fix 1: 1s progress ticks drive the live scrubber position while casting.
    private val progressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        val position = progressMs / 1000.0
        lastPosition = position
        _castState.value = _castState.value.copy(
            position = position,
            duration = if (durationMs > 0) durationMs / 1000.0 else _castState.value.duration,
        )
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            attachRemoteListeners(session)
            syncCastState()
            loadPendingMedia(session)
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            detachRemoteListeners()
            _castState.value = SiloCastState()
        }
        override fun onSessionEnding(session: CastSession) {
            // Capture the remote position while the client is still reachable.
            captureRemotePosition(session)
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            captureRemotePosition(session)
            detachRemoteListeners()
            _castState.value = SiloCastState()
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            attachRemoteListeners(session)
            syncCastState()
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            detachRemoteListeners()
            _castState.value = SiloCastState()
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            captureRemotePosition(session)
            detachRemoteListeners()
        }
    }

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            syncCastState()
            publishRoutes()
        }
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            syncCastState()
            publishRoutes()
        }
    }

    /**
     * Idempotently initializes the Cast framework and registers the session
     * listener + a passive route callback. Called from every UI entry point so
     * initialization always happens on the main thread. No-op without Play
     * Services (Fix 2).
     */
    private fun ensureInitialized() {
        if (initialized || !playServicesAvailable) return
        val ctx = runCatching { CastContext.getSharedInstance(context) }.getOrNull() ?: return
        castContext = ctx
        sessionManager = ctx.sessionManager
        mediaRouter = MediaRouter.getInstance(context)
        routeSelector = ctx.mergedSelector ?: MediaRouteSelector.EMPTY
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        // Passive discovery keeps the list fresh without the active-scan battery
        // cost; startDiscovery() escalates while the picker is open.
        mediaRouter?.addCallback(routeSelector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        initialized = true
        // If a session is already live (resumed), pick it up.
        sessionManager?.currentCastSession?.let { attachRemoteListeners(it) }
        syncCastState()
        publishRoutes()
    }

    /** Fix 3: escalate to active scan while the route picker is visible. */
    fun startDiscovery() {
        ensureInitialized()
        val router = mediaRouter ?: return
        if (activeScanning) return
        router.removeCallback(routeCallback)
        router.addCallback(
            routeSelector,
            routeCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        activeScanning = true
        publishRoutes()
    }

    /** Fix 3: drop back to passive discovery once the picker is dismissed. */
    fun stopDiscovery() {
        val router = mediaRouter ?: return
        if (!activeScanning) return
        router.removeCallback(routeCallback)
        router.addCallback(routeSelector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        activeScanning = false
    }

    /**
     * Stage the cast-ready media. Call before/while the user picks a device; the
     * media loads automatically once a session connects. Safe to call while
     * already connected (loads immediately).
     */
    fun prepareMedia(spec: CastMediaSpec) {
        ensureInitialized()
        pending = spec
        lastPosition = spec.positionSeconds
        _castState.value = _castState.value.copy(title = spec.title, fileId = spec.fileId)
        val session = sessionManager?.currentCastSession
        if (session != null && session.isConnected) loadPendingMedia(session)
    }

    private fun loadPendingMedia(session: CastSession) {
        val spec = pending ?: return
        val remoteClient = session.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, spec.title)
            spec.posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }

        // Fix 5: each subtitle track is text/vtt; assign stable ids for activation.
        val tracks = spec.subtitles.mapIndexed { index, sub ->
            MediaTrack.Builder((index + 1).toLong(), MediaTrack.TYPE_TEXT)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(sub.url)
                .setContentType("text/vtt")
                .setName(sub.label)
                .setLanguage(sub.language ?: "")
                .build()
        }
        val activeTrackIds = spec.subtitles
            .mapIndexedNotNull { index, sub -> if (sub.selected) (index + 1).toLong() else null }
            .toLongArray()

        // Fix 4: real container mime + VOD stream type.
        val mediaInfo = MediaInfo.Builder(spec.streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(spec.mimeType)
            .setMetadata(metadata)
            .apply { if (tracks.isNotEmpty()) setMediaTracks(tracks) }
            // Without an explicit style the Default Media Receiver renders its
            // stock boxed look (opaque black band behind every cue). Match the
            // app's default subtitle appearance instead: white text with a
            // black outline, no background, no window.
            .setTextTrackStyle(castTextTrackStyle())
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime((spec.positionSeconds * 1000).toLong())
            .apply { if (activeTrackIds.isNotEmpty()) setActiveTrackIds(activeTrackIds) }
            .build()

        remoteClient.load(loadRequest).setResultCallback { result ->
            Log.i(TAG, "cast load result success=${result.status.isSuccess} code=${result.status.statusCode}")
            // The MediaInfo-embedded style alone doesn't reach the receiver's
            // renderer; re-assert via the tracks channel once the load lands.
            if (result.status.isSuccess) {
                remoteClient.setTextTrackStyle(castTextTrackStyle()).setResultCallback { styleResult ->
                    Log.i(
                        TAG,
                        "cast setTextTrackStyle result success=${styleResult.status.isSuccess} " +
                            "code=${styleResult.status.statusCode} msg=${styleResult.status.statusMessage}",
                    )
                }
            }
        }
    }

    /** White text, black outline, no background box — the app's default look. */
    private fun castTextTrackStyle(): TextTrackStyle = TextTrackStyle().apply {
        // GMS gotcha (verified against play-services-cast bytecode):
        // TextTrackStyle.COLOR_UNSPECIFIED == 0 == Color.TRANSPARENT, so a
        // pure-transparent color is treated as "unset", omitted from the
        // payload, and the receiver keeps its opaque black default band.
        // Alpha-1 black is visually transparent but survives serialization.
        val nearTransparent = android.graphics.Color.argb(1, 0, 0, 0)
        foregroundColor = android.graphics.Color.WHITE
        backgroundColor = nearTransparent
        windowColor = nearTransparent
        windowType = TextTrackStyle.WINDOW_TYPE_NONE
        edgeType = TextTrackStyle.EDGE_TYPE_OUTLINE
        edgeColor = android.graphics.Color.BLACK
        // The receiver's default caption typeface is monospaced, which renders
        // punctuation (apostrophes, commas) oddly wide; use a normal
        // proportional face. (Font sentinel is -1, so 0 == SANS_SERIF is safe
        // to send — unlike the color sentinel above.)
        fontGenericFamily = TextTrackStyle.FONT_FAMILY_SANS_SERIF
    }

    private fun attachRemoteListeners(session: CastSession) {
        val remoteClient = session.remoteMediaClient ?: return
        if (listenersAttachedTo === session) return
        detachRemoteListeners()
        remoteClient.registerCallback(remoteCallback)
        remoteClient.addProgressListener(progressListener, 1000L)
        listenersAttachedTo = session
    }

    private fun detachRemoteListeners() {
        val session = listenersAttachedTo ?: return
        session.remoteMediaClient?.let { client ->
            client.unregisterCallback(remoteCallback)
            client.removeProgressListener(progressListener)
        }
        listenersAttachedTo = null
    }

    private fun captureRemotePosition(session: CastSession) {
        val position = session.remoteMediaClient?.approximateStreamPosition?.let { it / 1000.0 }
        if (position != null && position >= 0.0) lastPosition = position
    }

    /** Fix 6: last known remote position, so local playback resumes there. */
    fun getLastPosition(): Double = lastPosition

    fun isConnected(): Boolean = sessionManager?.currentCastSession?.isConnected == true

    fun disconnect() {
        val session = sessionManager?.currentCastSession
        if (session != null) {
            captureRemotePosition(session)
            sessionManager?.endCurrentSession(true)
        }
        syncCastState()
    }

    /** Seeks the remote receiver. Optimistically updates local state so the
     * scrubber doesn't snap back while the receiver applies the seek. */
    fun seekTo(seconds: Double) {
        val remoteClient = sessionManager?.currentCastSession?.remoteMediaClient ?: return
        remoteClient.seek(
            MediaSeekOptions.Builder()
                .setPosition((seconds * 1000).toLong())
                .build(),
        )
        lastPosition = seconds
        _castState.value = _castState.value.copy(position = seconds)
    }

    fun togglePlayback() {
        val remoteClient = sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val isPlayingNow = remoteClient.playerState == MediaStatus.PLAYER_STATE_PLAYING
        if (isPlayingNow) remoteClient.pause() else remoteClient.play()
        _castState.value = _castState.value.copy(isPlaying = !isPlayingNow)
    }

    /** Activates a declared receiver text track; null turns captions off. */
    fun selectSubtitleTrack(trackId: Long?) {
        val remoteClient = sessionManager?.currentCastSession?.remoteMediaClient ?: return
        remoteClient.setActiveMediaTracks(
            if (trackId == null) longArrayOf() else longArrayOf(trackId),
        )
        if (trackId != null) remoteClient.setTextTrackStyle(castTextTrackStyle())
        _castState.value = _castState.value.copy(activeSubtitleId = trackId)
    }

    /** Relative seek from the receiver's live position, clamped to the item. */
    fun skipBy(deltaSeconds: Double) {
        val remoteClient = sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val current = remoteClient.approximateStreamPosition
            .takeIf { it > 0 }?.let { it / 1000.0 }
            ?: _castState.value.position
        val duration = _castState.value.duration
        var target = current + deltaSeconds
        if (target < 0.0) target = 0.0
        if (duration > 0.0 && target > duration) target = duration
        seekTo(target)
    }

    fun refreshRoutes() {
        ensureInitialized()
        syncCastState()
        publishRoutes()
    }

    fun selectRoute(routeId: String) {
        val router = mediaRouter ?: return
        val route = router.routes.firstOrNull { it.id == routeId } ?: return
        route.select()
        syncCastState()
        publishRoutes()
    }

    fun release() {
        detachRemoteListeners()
        mediaRouter?.removeCallback(routeCallback)
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        initialized = false
        activeScanning = false
    }

    private fun publishRoutes() {
        val router = mediaRouter ?: run {
            _availableRoutes.value = emptyList()
            return
        }
        _availableRoutes.value = router.routes
            .asSequence()
            .filter { it.isEnabled }
            .filter { it.matchesSelector(routeSelector) }
            .filterNot { it.isDefaultOrBluetooth }
            .map { route ->
                SiloCastRoute(
                    id = route.id,
                    name = route.name.toString(),
                    isConnecting = route.isConnecting,
                    isSelected = route.isSelected,
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    private fun syncCastState() {
        val session = sessionManager?.currentCastSession
        val remoteClient = session?.remoteMediaClient
        _castState.value = if (session != null && session.isConnected) {
            val textTracks = remoteClient?.mediaInfo?.mediaTracks.orEmpty()
                .filter { it.type == MediaTrack.TYPE_TEXT }
            val subtitleOptions = textTracks.mapIndexed { index, track ->
                CastSubtitleOption(
                    id = track.id,
                    label = track.name?.takeIf { it.isNotBlank() }
                        ?: track.language?.takeIf { it.isNotBlank() }
                        ?: "Subtitle ${index + 1}",
                )
            }
            val activeIds = remoteClient?.mediaStatus?.activeTrackIds
            SiloCastState(
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                isPlaying = remoteClient?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
                position = remoteClient?.approximateStreamPosition?.div(1000.0) ?: lastPosition,
                duration = remoteClient?.mediaInfo?.streamDuration?.takeIf { it > 0 }?.div(1000.0)
                    ?: _castState.value.duration,
                title = pending?.title ?: _castState.value.title,
                // Preserve the staged file id: rebuilding without it re-arms
                // the player's auto-stage effect on every SDK callback, which
                // cancels in-flight prepares (LaunchedEffect restart), orphans
                // their server sessions, and burns the start rate limit.
                fileId = pending?.fileId ?: _castState.value.fileId,
                subtitleOptions = subtitleOptions,
                activeSubtitleId = subtitleOptions
                    .firstOrNull { option -> activeIds?.contains(option.id) == true }
                    ?.id,
            )
        } else {
            _castState.value.copy(isConnected = false, deviceName = null, isPlaying = false)
        }
    }
}
