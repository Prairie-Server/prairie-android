package com.continuum.app.tv.ui.navigation

import android.net.Uri

/**
 * TV navigation routes.
 *
 * Mirrors the phone app's `Route` but drops touch-only screens (Downloads) and
 * reorganizes the main tabs into a single [Main] destination that hosts the
 * top-menu shell for Home/Search/Libraries/For You/Requests/Settings.
 *
 * Immersive screens (detail, player, collection detail) live outside the shell
 * so they can use the full screen.
 */
sealed class TvRoute(val route: String) {

    // --- Auth flow (no drawer) ---
    data object ServerSetup : TvRoute("server_setup")
    data object ServerList : TvRoute("server_list")
    data object Login : TvRoute("login")

    // --- Profile selection (no drawer) ---
    data object ProfileSelection : TvRoute("profiles")

    // --- Main (drawer + nested nav: home, libraries, search, settings) ---
    data object Main : TvRoute("main")

    // --- Detail & player (no drawer, immersive) ---
    data class ItemDetail(val contentId: String, val seasonNumber: Int? = null) :
        TvRoute(
            if (seasonNumber != null) {
                "item/$contentId?seasonNumber=$seasonNumber"
            } else {
                "item/$contentId"
            },
        ) {
        companion object {
            const val ROUTE = "item/{contentId}?seasonNumber={seasonNumber}"
            const val ARG_CONTENT_ID = "contentId"
            const val ARG_SEASON_NUMBER = "seasonNumber"
        }
    }

    /**
     * Playback route. An optional `fileId` query param lets the detail screen
     * pre-select a specific file version (e.g. 4K vs 1080p). When absent, the
     * player uses `auto` selection. An optional `roomId` query param binds the
     * player to a Watch Together room (synced playback); absent for solo play.
     */
    data class Player(
        val contentId: String,
        val fileId: Int? = null,
        val roomId: String? = null,
    ) : TvRoute(
        buildString {
            append("player/$contentId")
            val query = buildList {
                if (fileId != null) add("fileId=$fileId")
                if (roomId != null) add("roomId=${roomId.routeEncode()}")
            }
            if (query.isNotEmpty()) append("?").append(query.joinToString("&"))
        },
    ) {
        companion object {
            const val ROUTE = "player/{contentId}?fileId={fileId}&roomId={roomId}"
            const val ARG_CONTENT_ID = "contentId"
            const val ARG_FILE_ID = "fileId"
            const val ARG_ROOM_ID = "roomId"
        }
    }

    /**
     * Audiobook playback route. Optional `fileId` query param pre-selects a
     * specific version (mirrors [Player]); absent ⇒ the VM auto-selects the
     * first version. No `roomId` — audiobooks have no synced playback.
     */
    data class AudiobookPlayer(
        val contentId: String,
        val fileId: Int? = null,
    ) : TvRoute(
        buildString {
            append("audiobook/$contentId")
            if (fileId != null) append("?fileId=$fileId")
        },
    ) {
        companion object {
            const val ROUTE = "audiobook/{contentId}?fileId={fileId}"
            const val ARG_CONTENT_ID = "contentId"
            const val ARG_FILE_ID = "fileId"
        }
    }

    /**
     * Watch Together lobby — the waiting/vote/pick surface for a room that has
     * no selection yet. Reached from the entry dialog's Host/Join when the room
     * snapshot carries no `selectedContentId`.
     */
    data class WatchTogetherLobby(val roomId: String) :
        TvRoute("watch_together/lobby/${roomId.routeEncode()}") {
        companion object {
            const val ROUTE = "watch_together/lobby/{roomId}"
            const val ARG_ROOM_ID = "roomId"
        }
    }

    // --- Library collections (reachable from the Libraries landing) ---
    data class LibraryCollectionDetail(
        val libraryId: Int,
        val collectionId: String,
        val title: String,
    ) : TvRoute(
        "library/$libraryId/collection/${collectionId.routeEncode()}?title=${title.routeEncode()}"
    ) {
        companion object {
            const val ROUTE = "library/{libraryId}/collection/{collectionId}?title={title}"
            const val ARG_LIBRARY_ID = "libraryId"
            const val ARG_COLLECTION_ID = "collectionId"
            const val ARG_TITLE = "title"
        }
    }

    // --- Personal data grids (reachable from Settings) ---
    data object Favorites : TvRoute("favorites")
    data object Watchlist : TvRoute("watchlist")
    data object History : TvRoute("history")

    // --- Collections ---
    data object Collections : TvRoute("collections")
    data class CollectionDetail(val collectionId: String, val title: String) :
        TvRoute("collection/${collectionId.routeEncode()}?title=${title.routeEncode()}") {
        companion object {
            const val ROUTE = "collection/{collectionId}?title={title}"
            const val ARG_COLLECTION_ID = "collectionId"
            const val ARG_TITLE = "title"
        }
    }

}

/**
 * Sub-routes inside the [TvRoute.Main] shell. These live in the nested NavHost
 * hosted by `com.continuum.app.tv.ui.shell.TvMainShell`. The top menu gives
 * direct access to root content surfaces; settings-linked utility surfaces
 * stay nested so they keep the profile menu and focus behavior.
 */
sealed class TvMainRoute(val route: String) {
    data object Home : TvMainRoute("main/home")
    data object Video : TvMainRoute("main/video")
    data object Search : TvMainRoute("main/search")
    data object Libraries : TvMainRoute("main/libraries")
    data object Audio : TvMainRoute("main/audio")
    data object ForYou : TvMainRoute("main/foryou")
    data object Requests : TvMainRoute("main/requests")
    data object MyRequests : TvMainRoute("main/requests/mine")
    data object Settings : TvMainRoute("main/settings")

    /** Notifications inbox — opened from the profile menu's "Notifications" row. */
    data object Inbox : TvMainRoute("main/inbox")

    data object Collections : TvMainRoute("main/collections")
    data object Favorites : TvMainRoute("main/favorites")
    data object Watchlist : TvMainRoute("main/watchlist")
    data object History : TvMainRoute("main/history")

    /** Admin stats dashboard — reachable from Settings when acting-admin gate passes. */
    data object Admin : TvMainRoute("main/admin")
}

private fun String.routeEncode(): String = Uri.encode(this)
