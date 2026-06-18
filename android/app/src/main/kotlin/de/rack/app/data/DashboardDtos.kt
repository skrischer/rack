package de.rack.app.data

import de.rack.app.domain.LoggedSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * Wire DTOs for the dashboards read: a `set_logs` row with its nested
 * `plan_exercises -> exercises` (muscles, category) and `plan_days` (tag, title)
 * embeds (Postgrest foreign-table select). They are an internal detail of the
 * repository layer; callers receive the flattened camelCase [LoggedSet] domain
 * model. See docs/specs/spec-dashboards.md (Phase 11).
 */

@Serializable
internal data class DashboardSetLogDto(
    val id: String,
    val date: String? = null,
    val weight: Double? = null,
    val reps: List<Int>? = null,
    val rir: Int? = null,
    @SerialName("plan_exercises") val planExercise: DashboardPlanExerciseDto? = null,
) {
    /** Flatten into a [LoggedSet], or `null` when the row has no parseable date. */
    fun toDomain(): LoggedSet? {
        val parsedDate = date?.let(LocalDate::parse) ?: return null
        val exercise = planExercise?.exercise
        val day = planExercise?.planDay
        return LoggedSet(
            id = id,
            date = parsedDate,
            weight = weight,
            reps = reps.orEmpty(),
            rir = rir,
            exerciseId = planExercise?.exerciseId.orEmpty(),
            exerciseName = exercise?.name.orEmpty(),
            muscles = exercise?.muscles.orEmpty(),
            category = exercise?.category,
            planDayTag = day?.tag,
            planDayTitle = day?.title,
        )
    }
}

@Serializable
internal data class DashboardPlanExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercises") val exercise: DashboardExerciseDto? = null,
    @SerialName("plan_days") val planDay: DashboardPlanDayDto? = null,
)

@Serializable
internal data class DashboardExerciseDto(
    val name: String,
    val category: String? = null,
    val muscles: List<String>? = null,
)

@Serializable
internal data class DashboardPlanDayDto(
    val tag: String? = null,
    val title: String? = null,
)
