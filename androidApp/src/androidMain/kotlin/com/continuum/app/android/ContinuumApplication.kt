package com.continuum.app.android

import android.app.Application
import com.continuum.app.android.di.androidModule
import com.continuum.app.common.di.playerInfraModule
import com.continuum.app.common.di.playerModule
import com.continuum.app.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ContinuumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ContinuumApplication)
            modules(sharedModules() + playerModule + playerInfraModule + androidModule)
        }
    }
}
