package com.continuum.app.tv

import android.app.Application
import com.continuum.app.common.di.playerInfraModule
import com.continuum.app.common.di.playerModule
import com.continuum.app.di.sharedModules
import com.continuum.app.tv.di.androidTvModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ContinuumTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ContinuumTvApplication)
            modules(sharedModules() + playerModule + playerInfraModule + androidTvModule)
        }
    }
}
