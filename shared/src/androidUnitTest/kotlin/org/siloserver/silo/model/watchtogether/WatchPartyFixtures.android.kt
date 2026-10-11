package org.prairieserver.prairie.model.watchtogether

actual fun readWatchPartyFixture(name: String): String =
    checkNotNull(WatchPartyFixtureAnchor::class.java.classLoader?.getResource("watchtogether/v2/$name")) {
        "Missing Watch Party fixture watchtogether/v2/$name"
    }.readText()
