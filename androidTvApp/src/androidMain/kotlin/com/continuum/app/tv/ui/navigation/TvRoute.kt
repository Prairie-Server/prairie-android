package com.continuum.app.tv.ui.navigation

import android.net.Uri

/**
 * TV navigation routes.
 *
 * Mirrors the phone app's `Route` but drops touch-only screens (Downloads) and
 * reorganizes the main tabs into a single [Main] destination that hosts a
 * left-side modal drawer for Home/Libraries/Search/Settings.
 *
 * Immersive screens (detail, player, collection detail) live outside the drawer
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
     * player uses `auto` selection.
     */
    data class Player(val contentId: String, val fileId: Int? = null) :
        TvRoute("player/$contentId${if (fileId != null) "?fileId=$fileId" else ""}") {
        companion object {
            const val ROUTE = "player/{contentId}?fileId={fileId}"
            const val ARG_CONTENT_ID = "contentId"
            const val ARG_FILE_ID = "fileId"
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

    // --- Admin (admin users only) ---
    data object Admin : TvRoute("admin")
}

/**
 * Sub-routes inside the [TvRoute.Main] drawer. These live in the nested NavHost
 * hosted by `com.continuum.app.tv.ui.shell.TvMainShell`. The drawer gives direct
 * access to every top-level content surface; only immersive screens (item
 * detail, player, collection detail, admin) stay outside.
 */
sealed class TvMainRoute(val route: String) {
    data object Home : TvMainRoute("main/home")
    data object Search : TvMainRoute("main/search")
    data object Libraries : TvMainRoute("main/libraries")
    data object ForYou : TvMainRoute("main/foryou")
    data object Settings : TvMainRoute("main/settings")

    data object Collections : TvMainRoute("main/collections")
    data object Favorites : TvMainRoute("main/favorites")
    data object Watchlist : TvMainRoute("main/watchlist")
    data object History : TvMainRoute("main/history")
}

private fun String.routeEncode(): String = Uri.encode(this)
