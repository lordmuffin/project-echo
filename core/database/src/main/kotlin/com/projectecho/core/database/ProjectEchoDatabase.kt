package com.projectecho.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectecho.core.database.converter.Converters
import com.projectecho.core.database.dao.AudioRecordingDao
import com.projectecho.core.database.entity.AudioRecordingEntity

@Database(
    entities = [AudioRecordingEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ProjectEchoDatabase : RoomDatabase() {
    abstract fun audioRecordingDao(): AudioRecordingDao

    companion object {
        const val DATABASE_NAME = "project_echo_database"

        // Migration from version 1 to 2: Add description and tags columns
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add description column (nullable)
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN description TEXT DEFAULT NULL")
                // Add tags column (default to empty list as JSON array)
                db.execSQL("ALTER TABLE audio_recordings ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}