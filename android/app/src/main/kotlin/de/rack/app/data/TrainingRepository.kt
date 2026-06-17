package de.rack.app.data

import de.rack.app.domain.Plan
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * The single Supabase access point for training data (plans, days, exercises,
 * set logs). All reads run under the signed-in user's JWT, so the RLS owner
 * policy (`user_id = auth.uid()`) scopes every row to that user — the repository
 * never sends or filters on a `user_id` itself. Snake_case columns are mapped to
 * the camelCase domain models via the [TrainingDtos]. Never call this from a
 * Composable; ViewModels added in #22/#23 consume it.
 */
class TrainingRepository(
    private val client: SupabaseClient,
) {
    /** All of the signed-in user's plans, alphabetically by name. */
    suspend fun getPlans(): List<Plan> =
        client.from("plans")
            .select(Columns.list("id", "name", "kind")) {
                order("name", Order.ASCENDING)
            }
            .decodeList<PlanDto>()
            .map(PlanDto::toDomain)

    /** The days of [planId] in ascending `position` order. */
    suspend fun getPlanDays(planId: String): List<PlanDay> =
        client.from("plan_days")
            .select(Columns.list("id", "plan_id", "position", "title", "focus", "tag")) {
                filter { eq("plan_id", planId) }
                order("position", Order.ASCENDING)
            }
            .decodeList<PlanDayDto>()
            .map(PlanDayDto::toDomain)

    /** The exercises of [dayId] in ascending `position` order, with catalog names. */
    suspend fun getPlanExercises(dayId: String): List<PlanExercise> =
        client.from("plan_exercises")
            .select(Columns.raw(PLAN_EXERCISE_COLUMNS)) {
                filter { eq("day_id", dayId) }
                order("position", Order.ASCENDING)
            }
            .decodeList<PlanExerciseDto>()
            .map(PlanExerciseDto::toDomain)

    /** The history for [planExerciseId], most-recent first. */
    suspend fun getSetLogs(planExerciseId: String): List<SetLog> =
        client.from("set_logs")
            .select(Columns.list("id", "plan_exercise_id", "date", "weight", "reps", "rir", "logged_at")) {
                filter { eq("plan_exercise_id", planExerciseId) }
                order("logged_at", Order.DESCENDING)
            }
            .decodeList<SetLogDto>()
            .map(SetLogDto::toDomain)

    private companion object {
        // Explicit columns plus the embedded `exercises(name)` join (Postgrest
        // foreign-table select), so each plan exercise carries its catalog name.
        const val PLAN_EXERCISE_COLUMNS =
            "id, day_id, exercise_id, position, target, rir, cue, superset_label, exercises(name)"
    }
}
