package de.rack.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Maps a plan-day tag to its Recomp accent color. The palette is limited to the
 * four defined tokens (push/pull/legs + superset violet); any unknown tag — including
 * fullbody — reuses [RecompColors.volt], matching the prototype (docs/design-tokens.md,
 * spec-android-app.md "no separate fullbody color").
 */
fun RecompColors.tagColor(tag: String?): Color =
    when (tag?.trim()?.lowercase()) {
        "push" -> push
        "pull" -> pull
        "legs" -> legs
        else -> volt
    }

/** A run of consecutive plan-exercises sharing a non-null `superset_label`. */
enum class SupersetKind {
    /** Not part of a multi-exercise group (standalone exercise). */
    NONE,

    /** Exactly two consecutive exercises with the same label. */
    SUPERSET,

    /** Three or more consecutive exercises with the same label. */
    CIRCUIT,
}

private const val SUPERSET_SIZE = 2
private const val CIRCUIT_MIN_SIZE = 3

/**
 * Classifies a superset run by its size: 2 exercises = superset, 3+ = circuit, and
 * a single exercise (or no label) = none. Both grouped kinds render with the violet
 * (`--ss`) treatment per docs/design-tokens.md.
 */
fun supersetKind(runSize: Int): SupersetKind =
    when {
        runSize >= CIRCUIT_MIN_SIZE -> SupersetKind.CIRCUIT
        runSize == SUPERSET_SIZE -> SupersetKind.SUPERSET
        else -> SupersetKind.NONE
    }
