package de.rack.app.domain

/**
 * The execution detail of a catalog exercise (`exercises`), as shown on the
 * Phase 7 detail screen: how-to instructions, target muscles, equipment, and an
 * image.
 *
 * The camelCase Kotlin representation of an enriched `exercises` row (see
 * docs/specs/spec-exercise-detail.md). [imageUrl] is the resolved public Storage
 * URL for the catalog's `image_path`, or `null` when the exercise has no licensed
 * image (the screen then falls back to a deterministic placeholder in #50).
 * [license]/[licenseAuthor] carry the CC-BY-SA attribution the screen renders
 * whenever wger-sourced content is shown. The catalog is global, public-read seed
 * data — not user-owned — so it carries no `source`/`updated_at`.
 */
data class ExerciseDetail(
    val id: String,
    val name: String,
    val category: String?,
    val muscles: List<String>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: List<String>,
    val instructions: List<String>,
    val imageUrl: String?,
    val license: String?,
    val licenseAuthor: String?,
)
