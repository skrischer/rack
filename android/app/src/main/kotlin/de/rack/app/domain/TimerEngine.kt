package de.rack.app.domain

/*
 * The pure, drift-free timer engine for Phase 8 (see docs/specs/spec-timers.md).
 *
 * Both timers are anchored on a monotonic elapsed-realtime clock (milliseconds,
 * e.g. SystemClock.elapsedRealtime) rather than on accumulated UI ticks: the
 * rest countdown stores the end-instant and recomputes remaining = end - now on
 * every read, and the session count-up stores the start-instant and recomputes
 * elapsed = now - start. Backgrounding therefore causes no drift — the next read
 * after the app resumes reflects real elapsed time, not a stuck tick count.
 *
 * This file holds no Android, no coroutines, and no Supabase access: every value
 * is computed from a caller-supplied `now`, which makes the math fully unit
 * testable and keeps all business logic out of the ViewModel and Composables.
 */

/** The rest control increment/decrement step in seconds (the de-facto tracker step). */
const val REST_STEP_SECONDS = 15

private const val MILLIS_PER_SECOND = 1000L

/**
 * An anchored rest countdown. [endElapsedMs] is the elapsed-realtime instant the
 * rest is due to finish; remaining time is always derived from it against the
 * current clock, never accumulated, so the countdown survives backgrounding
 * without drift. The state is immutable — every control returns a new instance.
 */
data class RestTimer(
    val endElapsedMs: Long,
) {
    /**
     * Whole seconds left until [endElapsedMs] at [nowMs], clamped to a
     * non-negative value (a finished or skipped rest reads as 0, never negative).
     * Rounds up so the displayed value only hits 0 once the rest is truly over.
     */
    fun remainingSeconds(nowMs: Long): Int {
        val remainingMs = (endElapsedMs - nowMs).coerceAtLeast(0L)
        return ((remainingMs + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    /** True once [nowMs] has reached or passed [endElapsedMs]. */
    fun isFinished(nowMs: Long): Boolean = nowMs >= endElapsedMs

    /** Add [REST_STEP_SECONDS] to the remaining time by pushing the end-instant out. */
    fun addStep(): RestTimer = shiftEndBy(REST_STEP_SECONDS, nowMs = null)

    /**
     * Subtract [REST_STEP_SECONDS] from the remaining time, clamped so the new end
     * never lands before [nowMs] (subtracting past zero yields a finished rest, not
     * a negative remaining).
     */
    fun subtractStep(nowMs: Long): RestTimer = shiftEndBy(-REST_STEP_SECONDS, nowMs = nowMs)

    /** Skip the rest: end it now so remaining reads 0 immediately. */
    fun skip(nowMs: Long): RestTimer = RestTimer(endElapsedMs = nowMs)

    private fun shiftEndBy(
        deltaSeconds: Int,
        nowMs: Long?,
    ): RestTimer {
        val shifted = endElapsedMs + deltaSeconds * MILLIS_PER_SECOND
        val clamped = if (nowMs != null) shifted.coerceAtLeast(nowMs) else shifted
        return RestTimer(endElapsedMs = clamped)
    }

    companion object {
        /**
         * Start a rest of [durationSeconds] from [nowMs]: anchors the end-instant at
         * now + duration. [durationSeconds] is clamped to be non-negative.
         */
        fun start(
            durationSeconds: Int,
            nowMs: Long,
        ): RestTimer = RestTimer(endElapsedMs = nowMs + durationSeconds.coerceAtLeast(0) * MILLIS_PER_SECOND)
    }
}

/**
 * An anchored count-up session timer. [startElapsedMs] is the elapsed-realtime
 * instant the session began; elapsed time is always derived from it, so the
 * session count-up survives backgrounding without drift. A running rest reaching
 * zero or being skipped never touches this — only an explicit stop ends it.
 */
data class SessionTimer(
    val startElapsedMs: Long,
) {
    /** Whole seconds elapsed since [startElapsedMs] at [nowMs], never negative. */
    fun elapsedSeconds(nowMs: Long): Int = ((nowMs - startElapsedMs).coerceAtLeast(0L) / MILLIS_PER_SECOND).toInt()

    companion object {
        /** Begin a session anchored at [nowMs]. */
        fun start(nowMs: Long): SessionTimer = SessionTimer(startElapsedMs = nowMs)
    }
}
