package de.rack.app.data

import de.rack.app.data.local.PendingLogDao
import de.rack.app.data.local.PendingLogEntity
import de.rack.app.domain.PendingLog
import de.rack.app.domain.SetLog
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Writes set logs and owns the unsynced-log cache. A log is upserted to Supabase
 * with `source='app'`; if the write fails it is queued in the Room cache and
 * flushed later (on reconnect / foreground / login). Idempotency comes from the
 * client-generated row id: a retried upsert lands on the same `set_logs` row, so
 * a flaky network never duplicates a logged set. Reads still come from Supabase
 * via the [TrainingRepository].
 */
class LoggingRepository(
    private val training: TrainingRepository,
    private val pendingLogDao: PendingLogDao,
    private val nowProvider: () -> NowStamp = ::systemNow,
) {
    /**
     * Build a [PendingLog] for [planExerciseId] from the entered [weight]/[reps]/
     * [rir], stamped with a fresh UUID and the current user. Returns `null` when
     * no session is restored (the caller cannot attribute the row to a user).
     */
    fun buildLog(
        planExerciseId: String,
        weight: Double?,
        reps: List<Int>,
        rir: Int?,
    ): PendingLog? {
        val userId = training.currentUserId() ?: return null
        val stamp = nowProvider()
        return PendingLog(
            id = UUID.randomUUID().toString(),
            userId = userId,
            planExerciseId = planExerciseId,
            date = stamp.date,
            weight = weight,
            reps = reps,
            rir = rir,
            loggedAt = stamp.loggedAt,
        )
    }

    /**
     * Persist [log]: upsert to Supabase and return the reconciled server [SetLog].
     * On failure the log is queued in the cache (so the optimistic entry survives)
     * and the failure is rethrown for the caller to surface.
     */
    suspend fun log(log: PendingLog): SetLog =
        runCatching { upsertAndUncache(log) }
            .getOrElse { error ->
                pendingLogDao.upsert(PendingLogEntity.from(log))
                throw error
            }

    /**
     * Flush every queued log to Supabase, deleting each on success. Idempotent and
     * safe to call repeatedly (reconnect, foreground, login). Returns the synced
     * [SetLog]s so callers can reconcile their in-memory history.
     */
    suspend fun flushPending(): List<SetLog> =
        pendingLogDao.all().mapNotNull { entity ->
            runCatching { upsertAndUncache(entity.toDomain()) }.getOrNull()
        }

    private suspend fun upsertAndUncache(log: PendingLog): SetLog {
        val persisted = training.upsertSetLog(log)
        pendingLogDao.delete(log.id)
        return persisted
    }

    private companion object
}

/** A logged-at instant split into the `date` and `logged_at` column values. */
data class NowStamp(
    val date: String,
    val loggedAt: String,
)

/** The current instant as the `date` (ISO local date) and `logged_at` (UTC ISO) pair. */
fun systemNow(): NowStamp {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    return NowStamp(
        date = now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
        loggedAt = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
}
