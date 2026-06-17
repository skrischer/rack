package de.rack.app.ui.exercise

import de.rack.app.domain.ExerciseDetail

/**
 * The deterministic placeholder bucket for an exercise that carries no licensed
 * image. The same exercise always resolves to the same bucket so its placeholder is
 * stable across re-entry (spec-exercise-detail.md): the key is the primary muscle,
 * falling back to the category, then to a fixed `generic` bucket when neither is
 * populated. The value is a normalized, lowercased token used both as the label and
 * as the seed for the placeholder's accent.
 */
const val GENERIC_PLACEHOLDER_KEY: String = "generic"

/** Resolve the stable placeholder key: primary muscle, then category, then generic. */
fun placeholderKey(detail: ExerciseDetail): String =
    detail.primaryMuscles.firstOrNull()?.normalizeKey()
        ?: detail.category?.normalizeKey()
        ?: GENERIC_PLACEHOLDER_KEY

/**
 * The CC-BY-SA attribution line shown whenever wger-sourced text or image is
 * displayed, or `null` when the exercise carries no license metadata (nothing to
 * attribute). Renders the persisted [ExerciseDetail.license] and, when present, the
 * [ExerciseDetail.licenseAuthor] — the share-alike/attribution obligation from the
 * wger prior-art entry (spec-exercise-detail.md).
 */
fun attributionLine(detail: ExerciseDetail): String? {
    val license = detail.license?.normalizeText() ?: return null
    val author = detail.licenseAuthor?.normalizeText()
    return if (author == null) license else "$license · $author"
}

private fun String.normalizeKey(): String? = trim().lowercase().takeIf { it.isNotBlank() }

private fun String.normalizeText(): String? = trim().takeIf { it.isNotBlank() }
