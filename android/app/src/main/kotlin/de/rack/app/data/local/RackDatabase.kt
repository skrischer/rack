package de.rack.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** Serializes the per-set reps list as a comma-joined string for storage. */
class RepsConverter {
    @TypeConverter
    fun fromReps(reps: List<Int>): String = reps.joinToString(separator = ",")

    @TypeConverter
    fun toReps(value: String): List<Int> = if (value.isEmpty()) emptyList() else value.split(",").map(String::toInt)
}

/**
 * The local cache database. Holds only the unsynced-log queue ([PendingLogEntity]) and
 * the in-progress guided-session draft ([SessionDraftEntity]); all server reads still go
 * through Supabase. Constructed once via [create] and owned by the DI container.
 *
 * The cache mirrors no server-authoritative data, so a schema bump falls back to a
 * destructive recreate: at worst an unsynced log or an in-progress draft is dropped on
 * upgrade, never a confirmed `set_logs` row (those live in Supabase).
 */
@Database(entities = [PendingLogEntity::class, SessionDraftEntity::class], version = 2, exportSchema = false)
@TypeConverters(RepsConverter::class)
abstract class RackDatabase : RoomDatabase() {
    abstract fun pendingLogDao(): PendingLogDao

    abstract fun sessionDraftDao(): SessionDraftDao

    companion object {
        fun create(context: Context): RackDatabase =
            Room.databaseBuilder(context, RackDatabase::class.java, "rack.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
