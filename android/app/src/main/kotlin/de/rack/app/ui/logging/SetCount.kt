package de.rack.app.ui.logging

/**
 * The number of reps inputs to render for an exercise: its typed [sets] count, or
 * [DEFAULT_SET_COUNT] when the plan leaves `sets` unset (spec: default 3).
 */
fun setCount(sets: Int?): Int = sets?.takeIf { it > 0 } ?: DEFAULT_SET_COUNT

const val DEFAULT_SET_COUNT = 3
