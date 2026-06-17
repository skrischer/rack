package de.rack.app.data

import de.rack.app.domain.Artifact
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.seconds

/**
 * The single Supabase access point for visualization artifacts (`artifacts`). The
 * read runs under the signed-in user's JWT (anon key + user JWT only — never the
 * service-role key), so the RLS owner policy (`user_id = auth.uid()`) scopes every
 * row to that user; the repository never sends or filters on a `user_id` itself.
 * Snake_case columns are mapped to the camelCase [Artifact] via [ArtifactDto].
 * Never call this from a Composable; the [de.rack.app.ui.artifacts.ArtifactViewModel]
 * consumes it. The viewer (#44) reads an artifact's bytes through a short-lived
 * signed URL minted by [signedUrlFor] from the private bucket.
 */
class ArtifactRepository(
    private val client: SupabaseClient,
    private val http: HttpClient = defaultHttpClient(),
) {
    /** The signed-in user's artifacts, newest first. */
    suspend fun getArtifacts(): List<Artifact> =
        client.from("artifacts")
            .select(Columns.list("id", "name", "type", "storage_path", "source", "created_at", "updated_at")) {
                order("created_at", Order.DESCENDING)
            }
            .decodeList<ArtifactDto>()
            .map(ArtifactDto::toDomain)

    /** A single artifact by id, or null when no owned row matches (RLS-scoped). */
    suspend fun getArtifact(id: String): Artifact? =
        client.from("artifacts")
            .select(Columns.list("id", "name", "type", "storage_path", "source", "created_at", "updated_at")) {
                filter { eq("id", id) }
            }
            .decodeList<ArtifactDto>()
            .map(ArtifactDto::toDomain)
            .firstOrNull()

    /**
     * A short-lived signed URL for the artifact's bytes in the private bucket. The
     * stored [storagePath] is `artifacts/{user_id}/{...}` (bucket-prefixed); the
     * bucket segment is stripped to the object path the Storage API expects. The
     * URL is minted under the user's JWT, so RLS confines it to the caller's own
     * objects — no service-role key is used.
     */
    suspend fun signedUrlFor(storagePath: String): String =
        client.storage.from(BUCKET).createSignedUrl(objectPath(storagePath), expiresIn = SIGNED_URL_TTL)

    /**
     * Downloads the bytes behind a signed [url], used by the viewer to decode a PNG
     * into an image. The URL already carries the access token, so no further auth is
     * attached; a non-2xx response throws so the viewer can fall back.
     */
    suspend fun downloadBytes(url: String): ByteArray {
        val response = http.get(url)
        check(response.status.isSuccess()) { "artifact download failed: ${response.status}" }
        return response.bodyAsBytes()
    }

    /** Strip the leading `artifacts/` bucket segment so only the in-bucket object path remains. */
    private fun objectPath(storagePath: String): String = storagePath.removePrefix("$BUCKET/")

    private companion object {
        const val BUCKET = "artifacts"

        /** Signed-URL TTL: enough headroom for a slow fetch + render, short enough to stay private. */
        val SIGNED_URL_TTL = 60.seconds

        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp)
    }
}
