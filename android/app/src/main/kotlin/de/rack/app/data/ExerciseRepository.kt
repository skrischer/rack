package de.rack.app.data

import de.rack.app.domain.ExerciseDetail
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess

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
    private val http: HttpClient = defaultHttpClient(),
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

    /**
     * Downloads the bytes behind a public image [url] (from [getExerciseDetail]'s
     * resolved `imageUrl`) so the detail screen can decode them into an image. The
     * URL is public-read over the anon key, so no further auth is attached; a non-2xx
     * response throws so the screen can fall back to the deterministic placeholder.
     */
    suspend fun downloadImageBytes(url: String): ByteArray {
        val response = http.get(url)
        check(response.status.isSuccess()) { "exercise image download failed: ${response.status}" }
        return response.bodyAsBytes()
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

        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp)
    }
}
