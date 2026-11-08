package app.klean

import android.app.Application
import app.klean.core.notifications.ChannelRegistrar
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KleanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ChannelRegistrar.ensure(this)
    }
}
