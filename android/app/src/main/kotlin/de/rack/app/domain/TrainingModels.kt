package de.rack.app.domain

/*
 * Domain models for the training data the app reads and logs.
 *
 * These are the camelCase Kotlin representations of the Phase 1 `snake_case`
 * Postgres tables (see /supabase/migrations). They are plain immutable data
 * carriers with no Supabase or serialization concerns — the repository layer
 * maps the wire rows onto them. The plan/day/exercise rows are read-only in this
 * phase (the agent authors); only SetLog is written by the app.
 */

/** A user-owned training plan (`plans`). */
data class Plan(
    val id: String,
    val name: String,
    val kind: String?,
)

/** An ordered day within a plan (`plan_days`). */
data class PlanDay(
    val id: String,
    val planId: String,
    val position: Int,
    val title: String?,
    val focus: String?,
    val tag: String?,
)

/** The kind of explicit exercise grouping carried by `plan_exercises.group_type`. */
enum class GroupType {
    SUPERSET,
    CIRCUIT,
}

/**
 * An exercise placed on a plan day (`plan_exercises`), with its catalog name,
 * [category], and [equipment]. The latter two feed the Phase-9 compound/isolation
 * classification that selects the rest default (docs/specs/spec-session-player.md).
 *
 * The prescription is typed (Phase 15, docs/specs/spec-structured-prescription.md):
 * [sets], a [repMin]–[repMax] rep range, a [rirLow]–[rirHigh] RIR range, and a
 * per-exercise [restSeconds]. Grouping is explicit: [supersetId] is a per-day group
 * key binding consecutive members and [groupType] distinguishes a superset from a
 * circuit. All prescription/grouping fields are nullable (an authored plan may omit
 * any of them).
 */
data class PlanExercise(
    val id: String,
    val dayId: String,
    val exerciseId: String,
    val name: String,
    val category: String?,
    val position: Int,
    val sets: Int?,
    val repMin: Int?,
    val repMax: Int?,
    val rirLow: Int?,
    val rirHigh: Int?,
    val restSeconds: Int?,
    val cue: String?,
    val supersetId: Int?,
    val groupType: GroupType?,
    val equipment: List<String> = emptyList(),
)

/** One logged exercise entry (`set_logs`): weight, per-set reps, and RIR. */
data class SetLog(
    val id: String,
    val planExerciseId: String,
    val date: String?,
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
    val loggedAt: String,
)

/**
 * A set log that has been entered but may not yet be persisted to Supabase. It
 * carries a client-generated [id] (UUID) and the [userId] so a flush upserts the
 * same row idempotently. Held in the Room cache only while unsynced; on success
 * it is reconciled to a [SetLog] and removed from the cache.
 */
data class PendingLog(
    val id: String,
    val userId: String,
    val planExerciseId: String,
    val date: String,
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
    val loggedAt: String,
) {
    /** The optimistic [SetLog] shown in history while this log is in flight. */
    fun toSetLog(): SetLog =
        SetLog(
            id = id,
            planExerciseId = planExerciseId,
            date = date,
            weight = weight,
            reps = reps,
            rir = rir,
            loggedAt = loggedAt,
        )
}
