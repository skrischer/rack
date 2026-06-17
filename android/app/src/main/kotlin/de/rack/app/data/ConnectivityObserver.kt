package de.rack.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits an event whenever a network becomes available, used to flush the
 * unsynced-log cache on reconnect (spec: ConnectivityManager callback). Dependency
 * free — no WorkManager; foreground/login flushes cover the remaining cases.
 */
class ConnectivityObserver(
    private val connectivityManager: ConnectivityManager,
) {
    constructor(context: Context) : this(
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
    )

    /** Cold flow that emits [Unit] each time a network connection is established. */
    fun onAvailable(): Flow<Unit> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(Unit)
                    }
                }
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }
}
