package de.rack.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.rack.app.domain.PendingLog

/**
 * A set log queued in the local cache because its write to Supabase has not yet
 * succeeded. The cache holds only these unsynced rows (never a mirror of server
 * data); a row is deleted once its flush lands. The [id] is the client-generated
 * UUID, so a retry upserts the same `set_logs` row idempotently.
 */
@Entity(tableName = "pending_logs")
data class PendingLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val planExerciseId: String,
    val date: String,
    val weight: Double?,
    val reps: List<Int>,
    val rir: Int?,
    val loggedAt: String,
) {
    fun toDomain(): PendingLog =
        PendingLog(
            id = id,
            userId = userId,
            planExerciseId = planExerciseId,
            date = date,
            weight = weight,
            reps = reps,
            rir = rir,
            loggedAt = loggedAt,
        )

    companion object {
        fun from(log: PendingLog): PendingLogEntity =
            PendingLogEntity(
                id = log.id,
                userId = log.userId,
                planExerciseId = log.planExerciseId,
                date = log.date,
                weight = log.weight,
                reps = log.reps,
                rir = log.rir,
                loggedAt = log.loggedAt,
            )
    }
}
