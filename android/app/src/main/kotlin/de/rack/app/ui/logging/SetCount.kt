package de.rack.app.ui.logging

/**
 * Derives the number of reps inputs from an exercise [target] by reading the
 * count before the `×` (e.g. "4 × 5-8" -> 4), mirroring the prototype's
 * `setCount()`. Falls back to [DEFAULT_SET_COUNT] when the target is null or
 * unparseable (spec: default 3).
 */
fun setCount(target: String?): Int {
    val match = target?.let { SET_COUNT_REGEX.find(it) }
    return match?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_SET_COUNT
}

const val DEFAULT_SET_COUNT = 3

// One or more digits followed (after optional space) by the multiplication sign
// the prototype and seed data use for "sets x reps".
private val SET_COUNT_REGEX = Regex("""(\d+)\s*[×x]""")
