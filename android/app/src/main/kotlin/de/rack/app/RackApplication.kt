package de.rack.app

import android.app.Application
import de.rack.app.di.AppContainer

/**
 * Application entry point that owns the process-wide [AppContainer], so the
 * single SupabaseClient and its repositories are constructed once and shared.
 */
class RackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
