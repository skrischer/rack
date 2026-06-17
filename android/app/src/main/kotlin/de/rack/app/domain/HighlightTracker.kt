package de.rack.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tracks which row ids carry a transient agent-edit highlight and clears each one
 * after [HIGHLIGHT_DURATION_MS]. The fade is owned here in the ViewModel/repository
 * layer, never in a Composable: the UI only observes [highlighted] from
 * [StateFlow] and renders the volt/lime accent on the ids it contains.
 *
 * [flag] adds an id and schedules its removal; flagging an id again before it
 * fades re-triggers the highlight (the pending clear is cancelled and the timer
 * restarts), so a fresh agent touch of the same row re-highlights it. Only call
 * [flag] for an agent write (`source='agent'`); the app's own `source='app'`
 * echoes call [clear] instead so a self-edit is never highlighted.
 */
class HighlightTracker(
    private val scope: CoroutineScope,
) {
    private val _highlighted = MutableStateFlow<Set<String>>(emptySet())

    /** The ids currently glowing; the screen renders the accent on these. */
    val highlighted: StateFlow<Set<String>> = _highlighted.asStateFlow()

    private val fadeJobs = mutableMapOf<String, Job>()

    /** Highlight [id] now and schedule its fade after [HIGHLIGHT_DURATION_MS]. */
    fun flag(id: String) {
        fadeJobs.remove(id)?.cancel()
        _highlighted.update { it + id }
        fadeJobs[id] =
            scope.launch {
                delay(HIGHLIGHT_DURATION_MS)
                _highlighted.update { it - id }
                fadeJobs.remove(id)
            }
    }

    /** Drop any highlight on [id] immediately (the app's own echo clears it). */
    fun clear(id: String) {
        fadeJobs.remove(id)?.cancel()
        _highlighted.update { it - id }
    }

    companion object {
        /** How long an agent-edit highlight glows before it fades (3 s). */
        const val HIGHLIGHT_DURATION_MS = 3_000L
    }
}
