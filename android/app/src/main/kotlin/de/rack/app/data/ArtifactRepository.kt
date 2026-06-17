package de.rack.app.data

import de.rack.app.domain.Artifact
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * The single Supabase access point for visualization artifacts (`artifacts`). The
 * read runs under the signed-in user's JWT (anon key + user JWT only — never the
 * service-role key), so the RLS owner policy (`user_id = auth.uid()`) scopes every
 * row to that user; the repository never sends or filters on a `user_id` itself.
 * Snake_case columns are mapped to the camelCase [Artifact] via [ArtifactDto].
 * Never call this from a Composable; the [de.rack.app.ui.artifacts.ArtifactViewModel]
 * consumes it. The signed-URL fetch of artifact bytes is the viewer's concern (#44).
 */
class ArtifactRepository(
    private val client: SupabaseClient,
) {
    /** The signed-in user's artifacts, newest first. */
    suspend fun getArtifacts(): List<Artifact> =
        client.from("artifacts")
            .select(Columns.list("id", "name", "type", "storage_path", "source", "created_at", "updated_at")) {
                order("created_at", Order.DESCENDING)
            }
            .decodeList<ArtifactDto>()
            .map(ArtifactDto::toDomain)
}
