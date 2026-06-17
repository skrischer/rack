package de.rack.app.data

import de.rack.app.domain.Artifact
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for an `artifacts` Postgrest row, mapping its `snake_case` columns to a
 * serializable Kotlin type. An internal detail of the repository layer; callers
 * receive the camelCase [Artifact] domain model. The toDomain() mapper translates a
 * row into its domain model.
 */
@Serializable
internal data class ArtifactDto(
    val id: String,
    val name: String? = null,
    val type: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    val source: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toDomain(): Artifact =
        Artifact(
            id = id,
            name = name,
            type = type,
            storagePath = storagePath,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
