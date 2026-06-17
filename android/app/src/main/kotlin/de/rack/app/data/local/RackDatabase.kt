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
 * The local cache database. Holds only the unsynced-log queue ([PendingLogEntity]);
 * all server reads still go through Supabase. Constructed once via [create] and
 * owned by the DI container.
 */
@Database(entities = [PendingLogEntity::class], version = 1, exportSchema = false)
@TypeConverters(RepsConverter::class)
abstract class RackDatabase : RoomDatabase() {
    abstract fun pendingLogDao(): PendingLogDao

    companion object {
        fun create(context: Context): RackDatabase =
            Room.databaseBuilder(context, RackDatabase::class.java, "rack.db").build()
    }
}
