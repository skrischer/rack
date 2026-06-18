package de.rack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Access to the in-progress guided-session cache. Upsert keeps one draft per day. */
@Dao
interface SessionDraftDao {
    /** Save (or replace) the draft for its day, so each tick persists the latest cursor. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionDraftEntity)

    /** The cached draft for [dayId], or null when no session is in progress. */
    @Query("SELECT * FROM session_drafts WHERE dayId = :dayId")
    suspend fun find(dayId: String): SessionDraftEntity?

    /** Drop the draft once the session is confirmed (saved) or abandoned. */
    @Query("DELETE FROM session_drafts WHERE dayId = :dayId")
    suspend fun delete(dayId: String)
}
