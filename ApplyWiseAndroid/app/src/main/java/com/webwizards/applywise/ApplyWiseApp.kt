package com.webwizards.applywise

import android.app.Application
import com.webwizards.applywise.data.local.LocalStore
import com.webwizards.applywise.di.AppContainer
import com.webwizards.applywise.di.StartupState
import kotlinx.coroutines.runBlocking

class ApplyWiseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // One small DataStore read so the first frame already knows the server and who is signed in.
        val startup = runBlocking {
            val store = LocalStore(this@ApplyWiseApp)
            StartupState(store.serverUrlNow(), store.loadCookies(), store.loadUser())
        }
        container = AppContainer(this, startup)
    }
}
