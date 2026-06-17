package de.rack.app.data

import de.rack.app.domain.ExerciseDetail
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage

/**
 * The single Supabase access point for the global exercise catalog (`exercises`).
 *
 * The catalog is public-read seed data (no owner RLS), so the read runs under the
 * anon key alone — never the service-role key. Snake_case columns are mapped to
 * the camelCase [ExerciseDetail] via [ExerciseDetailDto]; the row's relative
 * `image_path` is resolved to a public URL from the public `exercise-images`
 * bucket here, so all Postgrest/Storage access stays in the repository (never in a
 * Composable or ViewModel). The detail ViewModel/screen (#50) consume this. See
 * docs/specs/spec-exercise-detail.md.
 */
class ExerciseRepository(
    private val client: SupabaseClient,
) {
    /**
     * The execution detail for catalog [id], or `null` when no exercise matches.
     * The stored `image_path` is resolved to a public Storage URL (or `null` when
     * the exercise carries no licensed image, so #50 can fall back to a
     * deterministic placeholder).
     */
    suspend fun getExerciseDetail(id: String): ExerciseDetail? {
        val dto =
            client.from("exercises")
                .select(Columns.list(EXERCISE_DETAIL_COLUMNS)) {
                    filter { eq("id", id) }
                }
                .decodeList<ExerciseDetailDto>()
                .firstOrNull()
                ?: return null
        return dto.toDomain(imageUrl = publicImageUrl(dto.imagePath))
    }

    /** Resolve a relative [imagePath] to its public URL, or `null` when absent. */
    private fun publicImageUrl(imagePath: String?): String? =
        imagePath?.takeIf(String::isNotBlank)?.let { path ->
            client.storage.from(IMAGE_BUCKET).publicUrl(path)
        }

    private companion object {
        const val IMAGE_BUCKET = "exercise-images"

        const val EXERCISE_DETAIL_COLUMNS =
            "id, name, category, muscles, primary_muscles, secondary_muscles, " +
                "equipment, instructions, image_path, license, license_author"
    }
}
