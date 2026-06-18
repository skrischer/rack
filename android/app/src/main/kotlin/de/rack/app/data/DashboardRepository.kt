package de.rack.app.data

import de.rack.app.domain.LoggedSet
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * The single Supabase access point for the read-only dashboard data (Phase 11,
 * docs/specs/spec-dashboards.md). It reads `set_logs` joined through
 * `plan_exercises` to the catalog `exercises` (muscles, category) and `plan_days`
 * (tag, title) so the pure metric functions in
 * [de.rack.app.domain.DashboardMetrics] can aggregate volume, streak, sessions,
 * and per-exercise progress client-side.
 *
 * The read runs under the signed-in user's JWT (anon key + user JWT only — never
 * the service-role key), so the RLS owner policy (`user_id = auth.uid()`) scopes
 * every `set_logs` row to that user; the repository never sends or filters on a
 * `user_id` itself. Snake_case columns and the embedded joins are mapped to the
 * camelCase [LoggedSet] via [DashboardSetLogDto]. Never call this from a
 * Composable; the dashboard ViewModels (#70-72) consume it.
 */
class DashboardRepository(
    private val client: SupabaseClient,
) {
    /**
     * All of the signed-in user's logged sets, oldest first, each carrying its
     * exercise muscles/category and plan-day tag/title from the embedded joins.
     * Rows whose `date` is null are dropped (they cannot be attributed to a week
     * or session). Aggregation is done by the pure metric functions, not here.
     */
    suspend fun getLoggedSets(): List<LoggedSet> =
        client.from("set_logs")
            .select(Columns.raw(LOGGED_SET_COLUMNS)) {
                order("date", Order.ASCENDING)
            }
            .decodeList<DashboardSetLogDto>()
            .mapNotNull(DashboardSetLogDto::toDomain)

    private companion object {
        // The own columns plus the nested embeds: plan_exercises (for exercise_id),
        // then its exercises(muscles, category) and plan_days(tag, title). Postgrest
        // resolves the embeds from the foreign keys set_logs -> plan_exercises ->
        // {exercises, plan_days}.
        const val LOGGED_SET_COLUMNS =
            "id, date, weight, reps, rir, " +
                "plan_exercises(exercise_id, exercises(name, category, muscles), plan_days(tag, title))"
    }
}
