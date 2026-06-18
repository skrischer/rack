package de.rack.app.data

import de.rack.app.domain.UserSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

/**
 * The single Supabase access point for the user's `user_settings` row.
 *
 * Every read/write runs under the signed-in user's JWT (anon key + user JWT only
 * — never the service-role key), so the RLS owner policy (`user_id = auth.uid()`)
 * scopes the row to that user; the repository never accepts or filters on a
 * `user_id` from a caller. Snake_case columns map to the camelCase
 * [UserSettings] via [UserSettingsDto]. Never call this from a Composable; the
 * SettingsViewModel (#77) consumes it.
 *
 * First access provisions a default row lazily via upsert-on-read (see
 * docs/specs/spec-settings.md): an insert-or-ignore keyed on the `user_id` PK
 * makes the row idempotent, so concurrent first reads converge on one row and an
 * existing row is never overwritten with defaults.
 */
class SettingsRepository(
    private val client: SupabaseClient,
) {
    /**
     * The user's settings, provisioning a default row on first access. Returns
     * `null` when no session is restored (no user to attribute the row to).
     */
    suspend fun getSettings(): UserSettings? {
        val userId = currentUserId() ?: return null
        ensureRow(userId)
        return selectRow()
    }

    /**
     * Persist [settings] for the signed-in user, stamping `source='app'`
     * (`updated_at` is set by the DB trigger). Returns the persisted
     * [UserSettings], or `null` when no session is restored.
     */
    suspend fun updateSettings(settings: UserSettings): UserSettings? {
        val userId = currentUserId() ?: return null
        return client.from(TABLE)
            .upsert(UserSettingsUpsertDto.from(userId, settings)) {
                onConflict = USER_ID
                select(Columns.list(COLUMNS))
            }
            .decodeSingle<UserSettingsDto>()
            .toDomain()
    }

    /**
     * Insert the default row for [userId] if absent. `ignoreDuplicates` makes a
     * pre-existing row a no-op rather than overwriting it with defaults, so a
     * lazy provision never clobbers stored settings.
     */
    private suspend fun ensureRow(userId: String) {
        client.from(TABLE).upsert(UserSettingsUpsertDto.from(userId, UserSettings.DEFAULT)) {
            onConflict = USER_ID
            ignoreDuplicates = true
        }
    }

    private suspend fun selectRow(): UserSettings? =
        client.from(TABLE)
            .select(Columns.list(COLUMNS))
            .decodeList<UserSettingsDto>()
            .map(UserSettingsDto::toDomain)
            .firstOrNull()

    private fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    private companion object {
        const val TABLE = "user_settings"
        const val USER_ID = "user_id"
        const val COLUMNS =
            "weight_unit, theme, rest_compound_seconds, rest_isolation_seconds, " +
                "rest_superset_seconds, rest_circuit_seconds, display_name"
    }
}
