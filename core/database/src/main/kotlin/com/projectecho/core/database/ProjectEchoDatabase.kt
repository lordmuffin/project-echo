package com.projectecho.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectecho.core.database.dao.AudioRecordingDao
import com.projectecho.core.database.entity.AudioRecordingEntity

@Database(
    entities = [AudioRecordingEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ProjectEchoDatabase : RoomDatabase() {
    abstract fun audioRecordingDao(): AudioRecordingDao

    companion object {
        const val DATABASE_NAME = "project_echo_database"

        // Migration strategy for future versions
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration - add new column
                // database.execSQL("ALTER TABLE audio_recordings ADD COLUMN new_column TEXT")
            }
        }
    }
}