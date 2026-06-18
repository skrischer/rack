package de.rack.app.ui.session

import de.rack.app.data.LoggingRepository
import de.rack.app.domain.WeightUnit
import de.rack.app.domain.displayToKg

/*
 * Helpers the SessionPlayerViewModel uses to confirm-save a finished session to
 * `set_logs` (issue #60, docs/specs/spec-session-player.md). The pure input parsing is
 * kept out of the ViewModel so it stays unit-testable and the ViewModel holds only
 * orchestration; [saveSession] reuses the existing Phase-3 logging mutation per exercise.
 */

/**
 * The parsed inputs for one exercise's `set_logs` row: the scalar [weight] (kg), the
 * scalar [rir], and the [reps] of its ticked sets. Matches the documented `set_logs`
 * shape (one row per `plan_exercise_id`); the repository stamps id/user/date/source.
 */
data class LoggedExerciseInput(
    val planExerciseId: String,
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
)

/**
 * The per-exercise inputs to persist for the finished [SessionPlayerUiState]: one entry
 * per summary line (so skipped exercises are already excluded), each carrying the
 * exercise's single weight (entered in [unit], converted to canonical kg) and RIR parsed
 * from its entries and the reps of its ticked sets. Empty when the session has no summary
 * (still running) or logged nothing.
 */
fun SessionPlayerUiState.loggedInputs(unit: WeightUnit): List<LoggedExerciseInput> =
    summary?.lines.orEmpty().map { line ->
        val entries = entriesFor(line.planExerciseId)
        LoggedExerciseInput(
            planExerciseId = line.planExerciseId,
            weight = entries.weight.trim().toDoubleOrNull()?.let { displayToKg(it, unit) },
            reps = loggedReps(line.planExerciseId, done, entries),
            rir = entries.rir.trim().toIntOrNull(),
        )
    }

/**
 * Write one `set_logs` row per [input] through the existing Phase-3 logging path: each
 * row carries the scalar weight, scalar rir, and reps[] of the exercise's ticked sets
 * with `source='app'` and a server-stamped `updated_at`. The user is resolved from the
 * JWT inside [LoggingRepository.buildLog] (no `user_id` from the client); an input that
 * cannot be attributed (no restored session) is skipped. Rethrows on a failed write so
 * the caller can surface the error.
 */
suspend fun LoggingRepository.saveSession(inputs: List<LoggedExerciseInput>) {
    inputs.forEach { input ->
        buildLog(input.planExerciseId, input.weight, input.reps, input.rir)?.let { log(it) }
    }
}
