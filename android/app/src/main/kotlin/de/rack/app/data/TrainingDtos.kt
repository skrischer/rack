package de.rack.app.data

import de.rack.app.domain.Plan
import de.rack.app.domain.PlanDay
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.SetLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire DTOs for the Postgrest rows, mapping the Phase 1 `snake_case` columns to
 * serializable Kotlin types. They are an internal detail of the repository layer;
 * callers receive the camelCase domain models, so these never leak outward. The
 * toDomain() mappers translate each row into its de.rack.app.domain model.
 */

@Serializable
internal data class PlanDto(
    val id: String,
    val name: String,
    val kind: String? = null,
) {
    fun toDomain(): Plan = Plan(id = id, name = name, kind = kind)
}

@Serializable
internal data class PlanDayDto(
    val id: String,
    @SerialName("plan_id") val planId: String,
    val position: Int,
    val title: String? = null,
    val focus: String? = null,
    val tag: String? = null,
) {
    fun toDomain(): PlanDay =
        PlanDay(id = id, planId = planId, position = position, title = title, focus = focus, tag = tag)
}

@Serializable
internal data class PlanExerciseDto(
    val id: String,
    @SerialName("day_id") val dayId: String,
    @SerialName("exercise_id") val exerciseId: String,
    val position: Int,
    val target: String? = null,
    val rir: Int? = null,
    val cue: String? = null,
    @SerialName("superset_label") val supersetLabel: String? = null,
    val exercises: ExerciseCatalogDto? = null,
) {
    fun toDomain(): PlanExercise =
        PlanExercise(
            id = id,
            dayId = dayId,
            exerciseId = exerciseId,
            name = exercises?.name.orEmpty(),
            category = exercises?.category,
            position = position,
            target = target,
            rir = rir,
            cue = cue,
            supersetLabel = supersetLabel,
            equipment = exercises?.equipment.orEmpty(),
        )
}

/** The embedded catalog name, category, and equipment from the `exercises` join on `plan_exercises`. */
@Serializable
internal data class ExerciseCatalogDto(
    val name: String,
    val category: String? = null,
    val equipment: List<String>? = null,
)

@Serializable
internal data class SetLogDto(
    val id: String,
    @SerialName("plan_exercise_id") val planExerciseId: String,
    val date: String? = null,
    val weight: Double? = null,
    val reps: List<Int>? = null,
    val rir: Int? = null,
    @SerialName("logged_at") val loggedAt: String,
) {
    fun toDomain(): SetLog =
        SetLog(
            id = id,
            planExerciseId = planExerciseId,
            date = date,
            weight = weight,
            reps = reps.orEmpty(),
            rir = rir,
            loggedAt = loggedAt,
        )
}

/**
 * The row written when logging a set. The client supplies `id` (a UUID) and
 * `user_id` so a retried upsert lands on the same row (idempotent) under the RLS
 * `with check (user_id = auth.uid())` policy; `source` is always `'app'`.
 */
@Serializable
internal data class SetLogInsertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("plan_exercise_id") val planExerciseId: String,
    val date: String,
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
    @SerialName("logged_at") val loggedAt: String,
    val source: String = "app",
)
