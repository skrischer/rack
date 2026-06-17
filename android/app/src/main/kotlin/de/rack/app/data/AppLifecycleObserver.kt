package de.rack.app.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Exposes whether the app is in the foreground as a [StateFlow], driven by
 * [ProcessLifecycleOwner]: `true` between `onStart` and `onStop` of the whole
 * process, `false` otherwise. The Realtime subscription binds to this so it
 * subscribes on foreground and unsubscribes (no leaked socket, no battery drain)
 * on background, reconnecting and re-syncing on return — the lifecycle business
 * logic lives here and in the ViewModel, never in a Composable.
 *
 * Observation registers on the process lifecycle eagerly (constructed in the DI
 * container at app start) and is never removed for the app's lifetime, so there is
 * nothing to unregister.
 */
class AppLifecycleObserver(
    lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) {
    private val _isForeground = MutableStateFlow(false)

    /** `true` while the app process is in the foreground; flips on start/stop. */
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _isForeground.value = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    _isForeground.value = false
                }
            },
        )
    }
}

/**
 * Collect [source] only while the app is in the foreground: the upstream is
 * collected when [isForeground] reports foreground and cancelled on background, then
 * re-collected from scratch on the next foreground. Used to bind the Realtime
 * subscription (and its catch-up re-read) to the app lifecycle — subscribe on
 * foreground, unsubscribe on background, resubscribe + re-sync on return — without
 * any lifecycle code in a Composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> AppLifecycleObserver.whileForeground(source: () -> Flow<T>): Flow<T> =
    isForeground.flatMapLatest { foreground -> if (foreground) source() else emptyFlow() }
