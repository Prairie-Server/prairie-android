package com.continuum.app.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.continuum.app.android.di.androidModule
import com.continuum.app.android.downloads.AppWorkerFactory
import com.continuum.app.android.notifications.NotificationsForegroundStarter
import com.continuum.app.common.di.playerInfraModule
import com.continuum.app.common.di.playerModule
import com.continuum.app.common.downloads.DownloadWorker
import com.continuum.app.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Implements `Configuration.Provider` rather than calling
 * `workManagerFactory()` inside `startKoin { … }`. The latter only wins if
 * WorkManager hasn't been auto-initialised yet (and the androidx.startup
 * provider initialises it before `Application.onCreate` runs on many devices),
 * which silently leaves WorkManager using its reflection-based factory and
 * crashes when constructing DI-built workers like DownloadWorker. The
 * Provider path is the modern, reliable recipe.
 */
class ContinuumApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        val koinApp = startKoin {
            androidContext(this@ContinuumApplication)
            modules(sharedModules() + playerModule + playerInfraModule + androidModule)
        }
        // Drive notifications realtime off the app foreground lifecycle. Guarded:
        // it's a foreground accelerator, never load-bearing for cold start.
        runCatching {
            NotificationsForegroundStarter(
                repository = koinApp.koin.get(),
            ).register()
        }.onFailure {
            android.util.Log.w("ContinuumApplication", "Notifications starter init failed", it)
        }
        // Flush offline audiobook + ebook progress back to the server on
        // reconnect / foreground. Guarded — never load-bearing for cold start.
        runCatching {
            val audiobookSyncer =
                koinApp.koin.get<com.continuum.app.common.audiobook.AudiobookProgressSyncer>()
            val ebookSyncer =
                koinApp.koin.get<com.continuum.app.common.ebook.EbookProgressSyncer>()
            com.continuum.app.android.sync.ProgressSyncStarter(
                context = this@ContinuumApplication,
                flushers = listOf(audiobookSyncer::requestFlush, ebookSyncer::requestFlush),
            ).register()
        }.onFailure {
            android.util.Log.w("ContinuumApplication", "Progress sync init failed", it)
        }
        // Configuration.Provider wasn't reliably picked up by WM's androidx.startup
        // auto-init (the auto-init seemed to win the race, leaving WM with its
        // default reflection-based WorkerFactory). Force-initialise explicitly
        // with our AppWorkerFactory now that Koin is up.
        runCatching {
            WorkManager.initialize(this, workManagerConfiguration)
            android.util.Log.i("ContinuumApplication", "WorkManager.initialize called with AppWorkerFactory")
        }.onFailure {
            android.util.Log.w("ContinuumApplication", "WorkManager.initialize failed (already initialised?)", it)
        }
        registerDownloadsNotificationChannel()
    }

    override val workManagerConfiguration: Configuration
        get() {
            android.util.Log.i("ContinuumApplication", "workManagerConfiguration accessed; installing AppWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(AppWorkerFactory())
                .build()
        }

    /**
     * Channel for offline download progress / completion notifications.
     * Required on API 26+ before any notification can be posted. Low
     * importance so the user isn't interrupted while we silently
     * download bytes in the background.
     */
    private fun registerDownloadsNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            DownloadWorker.NOTIFICATION_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress and completion for offline downloads"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }
}
