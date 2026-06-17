package de.rack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Access to the unsynced-log queue. Upsert on insert keeps retries idempotent. */
@Dao
interface PendingLogDao {
    /** Queue (or replace) a pending log keyed by its client-generated id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingLogEntity)

    /** All queued logs, oldest first, so a flush replays them in order. */
    @Query("SELECT * FROM pending_logs ORDER BY loggedAt ASC")
    suspend fun all(): List<PendingLogEntity>

    /** Remove a log once its write to Supabase has landed. */
    @Query("DELETE FROM pending_logs WHERE id = :id")
    suspend fun delete(id: String)
}
