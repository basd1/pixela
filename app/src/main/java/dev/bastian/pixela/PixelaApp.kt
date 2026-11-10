package dev.bastian.pixela

import android.app.Application
import dev.bastian.pixela.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PixelaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PixelaApp)
            modules(appModules)
        }
    }
}
