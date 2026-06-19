package de.rack.app.ui.theme

import androidx.compose.ui.graphics.Color
import de.rack.app.domain.GroupType

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

/** A run of consecutive plan-exercises sharing a non-null `superset_id`. */
enum class SupersetKind {
    /** Not part of a multi-exercise group (standalone exercise). */
    NONE,

    /** A two-or-more member group authored as a superset. */
    SUPERSET,

    /** A two-or-more member group authored as a circuit. */
    CIRCUIT,
}

private const val SUPERSET_SIZE = 2
private const val CIRCUIT_MIN_SIZE = 3

/**
 * Classifies a grouped run from its explicit [groupType] and [runSize]: a run of
 * fewer than two members is always [SupersetKind.NONE]; otherwise the authored
 * [GroupType] decides superset vs circuit. When the group type is missing the size
 * is the fallback (2 = superset, 3+ = circuit). Both grouped kinds render with the
 * violet (`--ss`) treatment per docs/design-tokens.md.
 */
fun supersetKind(
    groupType: GroupType?,
    runSize: Int,
): SupersetKind =
    when {
        runSize < SUPERSET_SIZE -> SupersetKind.NONE
        groupType == GroupType.SUPERSET -> SupersetKind.SUPERSET
        groupType == GroupType.CIRCUIT -> SupersetKind.CIRCUIT
        runSize >= CIRCUIT_MIN_SIZE -> SupersetKind.CIRCUIT
        else -> SupersetKind.SUPERSET
    }
