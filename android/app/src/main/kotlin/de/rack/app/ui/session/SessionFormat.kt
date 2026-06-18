package de.rack.app.ui.session

private const val SECONDS_PER_MINUTE = 60

/** "m:ss" clock from [totalSeconds] (clamped non-negative) for the session stat strip. */
internal fun formatSessionClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / SECONDS_PER_MINUTE, safe % SECONDS_PER_MINUTE)
}

/** A volume/metric value without a trailing ".0" for the session stat strip. */
internal fun formatSessionMetric(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
