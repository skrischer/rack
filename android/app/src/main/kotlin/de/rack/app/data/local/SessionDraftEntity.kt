package de.rack.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An in-progress guided session cached locally so it survives backgrounding / process
 * death (logging is offline-resilient per the constitution). One row per [dayId]: the
 * [doneCount] cursor (how many steps have been ticked, since the step sequence is rebuilt
 * deterministically from the day's exercises) and the per-exercise working entries
 * serialized in [entriesJson] (kg, RIR, per-set reps). The row is deleted when the session
 * is confirmed (saved) or abandoned; it is never a mirror of server data.
 */
@Entity(tableName = "session_drafts")
data class SessionDraftEntity(
    @PrimaryKey val dayId: String,
    val doneCount: Int,
    val entriesJson: String,
)
