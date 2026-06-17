package de.rack.app.data

import de.rack.app.domain.ExerciseDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for an enriched `exercises` Postgrest row, mapping its `snake_case`
 * columns to a serializable Kotlin type. An internal detail of the repository
 * layer; callers receive the camelCase [ExerciseDetail] domain model. The raw
 * `image_path` is a relative path into the public `exercise-images` bucket; the
 * repository resolves it to a public URL and passes it to [toDomain] (Storage
 * access stays in the repository, not here). See docs/specs/spec-exercise-detail.md.
 */
@Serializable
internal data class ExerciseDetailDto(
    val id: String,
    val name: String,
    val category: String? = null,
    val muscles: List<String>? = null,
    @SerialName("primary_muscles") val primaryMuscles: List<String>? = null,
    @SerialName("secondary_muscles") val secondaryMuscles: List<String>? = null,
    val equipment: List<String>? = null,
    val instructions: List<String>? = null,
    @SerialName("image_path") val imagePath: String? = null,
    val license: String? = null,
    @SerialName("license_author") val licenseAuthor: String? = null,
) {
    fun toDomain(imageUrl: String?): ExerciseDetail =
        ExerciseDetail(
            id = id,
            name = name,
            category = category,
            muscles = muscles.orEmpty(),
            primaryMuscles = primaryMuscles.orEmpty(),
            secondaryMuscles = secondaryMuscles.orEmpty(),
            equipment = equipment.orEmpty(),
            instructions = instructions.orEmpty(),
            imageUrl = imageUrl,
            license = license,
            licenseAuthor = licenseAuthor,
        )
}
