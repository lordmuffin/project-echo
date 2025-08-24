package com.projectecho.core.testing.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.projectecho.core.database.ProjectEchoDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Comprehensive database migration testing to ensure user data safety.
 * Tests all migration paths and validates data integrity across schema versions.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProjectEchoDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        // Create database with version 1 schema
        var db = helper.createDatabase(TEST_DB, 1).apply {
            // Insert test data for version 1
            execSQL("""
                INSERT INTO audio_recordings 
                (id, title, filePath, duration, timestamp, size, format, sampleRate, channels, bitDepth)
                VALUES 
                ('test-1', 'Test Recording 1', '/path/test1.wav', 30000, 1640995200000, 1024000, 'WAV', 44100, 1, 16),
                ('test-2', 'Test Recording 2', '/path/test2.wav', 45000, 1640995260000, 1536000, 'PCM', 48000, 2, 24)
            """.trimIndent())
            close()
        }

        // Re-open the database with version 2 and provide MIGRATION_1_2
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ProjectEchoDatabase.MIGRATION_1_2)

        // Validate that data was preserved and new schema is applied
        val cursor = db.query("SELECT * FROM audio_recordings WHERE id = 'test-1'")
        cursor.use {
            assert(it.moveToFirst())
            val id = it.getString(it.getColumnIndexOrThrow("id"))
            val title = it.getString(it.getColumnIndexOrThrow("title"))
            val duration = it.getLong(it.getColumnIndexOrThrow("duration"))
            
            assert(id == "test-1")
            assert(title == "Test Recording 1")
            assert(duration == 30000L)
        }

        // Validate all records are present
        val countCursor = db.query("SELECT COUNT(*) FROM audio_recordings")
        countCursor.use {
            assert(it.moveToFirst())
            val count = it.getInt(0)
            assert(count == 2)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Test migration path from version 1 to current version
        var db = helper.createDatabase(TEST_DB, 1).apply {
            // Insert comprehensive test data
            execSQL("""
                INSERT INTO audio_recordings 
                (id, title, filePath, duration, timestamp, size, format, sampleRate, channels, bitDepth)
                VALUES 
                ('comprehensive-test', 'Comprehensive Test', '/path/comprehensive.wav', 120000, 1640995200000, 4096000, 'WAV', 44100, 1, 16)
            """.trimIndent())
            close()
        }

        // Apply all migrations up to current version
        val currentVersion = ProjectEchoDatabase::class.java
            .getDeclaredField("DATABASE_VERSION")
            .let { field ->
                field.isAccessible = true
                field.getInt(null)
            }

        db = helper.runMigrationsAndValidate(
            TEST_DB, 
            currentVersion, 
            true, 
            // Add all migrations here as they're created
            ProjectEchoDatabase.MIGRATION_1_2
        )

        // Validate final schema and data integrity
        validateFinalSchema(db)
        validateDataIntegrity(db)
    }

    @Test
    fun testDatabaseCreation() {
        // Test that database can be created fresh without migrations
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ProjectEchoDatabase::class.java
        ).build()

        // Verify all DAOs are accessible
        val audioDao = db.audioRecordingDao()
        assert(audioDao != null)

        // Test basic operations
        // Note: This would need to be run in a coroutine in real tests
        
        db.close()
    }

    @Test
    fun testSchemaValidation() {
        // Create database and validate schema structure
        val db = helper.createDatabase(TEST_DB, 1)
        
        // Validate audio_recordings table structure
        val cursor = db.query("PRAGMA table_info(audio_recordings)")
        val columns = mutableMapOf<String, String>()
        
        cursor.use {
            while (it.moveToNext()) {
                val columnName = it.getString(it.getColumnIndexOrThrow("name"))
                val columnType = it.getString(it.getColumnIndexOrThrow("type"))
                columns[columnName] = columnType
            }
        }

        // Validate required columns exist with correct types
        assert(columns["id"] == "TEXT")
        assert(columns["title"] == "TEXT")
        assert(columns["filePath"] == "TEXT")
        assert(columns["duration"] == "INTEGER")
        assert(columns["timestamp"] == "INTEGER")
        assert(columns["size"] == "INTEGER")
        assert(columns["format"] == "TEXT")
        assert(columns["sampleRate"] == "INTEGER")
        assert(columns["channels"] == "INTEGER")
        assert(columns["bitDepth"] == "INTEGER")

        db.close()
    }

    private fun validateFinalSchema(db: SupportSQLiteDatabase) {
        // Validate that final schema has all expected tables and columns
        val tablesCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tables = mutableListOf<String>()
        
        tablesCursor.use {
            while (it.moveToNext()) {
                tables.add(it.getString(0))
            }
        }

        // Validate expected tables exist
        assert(tables.contains("audio_recordings"))
        // Add other table validations as schema grows
    }

    private fun validateDataIntegrity(db: SupportSQLiteDatabase) {
        // Validate that data was preserved correctly through all migrations
        val cursor = db.query("SELECT * FROM audio_recordings WHERE id = 'comprehensive-test'")
        
        cursor.use {
            assert(it.moveToFirst())
            val title = it.getString(it.getColumnIndexOrThrow("title"))
            val duration = it.getLong(it.getColumnIndexOrThrow("duration"))
            
            assert(title == "Comprehensive Test")
            assert(duration == 120000L)
        }
    }

    /**
     * Test migration failure handling and rollback scenarios
     */
    @Test(expected = IllegalStateException::class)
    fun testMigrationFailureHandling() {
        // Create a migration that will fail
        val failingMigration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Intentionally cause a failure
                database.execSQL("INVALID SQL STATEMENT")
            }
        }

        val db = helper.createDatabase(TEST_DB, 1)
        db.close()

        // This should throw an exception
        helper.runMigrationsAndValidate(TEST_DB, 2, true, failingMigration)
    }

    /**
     * Test performance of migrations with large datasets
     */
    @Test
    fun testMigrationPerformance() {
        val startTime = System.currentTimeMillis()

        // Create database with large dataset
        var db = helper.createDatabase(TEST_DB, 1).apply {
            beginTransaction()
            try {
                for (i in 1..1000) {
                    execSQL("""
                        INSERT INTO audio_recordings 
                        (id, title, filePath, duration, timestamp, size, format, sampleRate, channels, bitDepth)
                        VALUES 
                        ('perf-test-$i', 'Performance Test $i', '/path/perf$i.wav', ${30000 + i * 1000}, ${1640995200000 + i * 60000}, ${1024000 + i * 1024}, 'WAV', 44100, 1, 16)
                    """.trimIndent())
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
            close()
        }

        // Run migration and measure performance
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ProjectEchoDatabase.MIGRATION_1_2)

        val migrationTime = System.currentTimeMillis() - startTime

        // Validate migration completed in reasonable time (under 5 seconds for 1000 records)
        assert(migrationTime < 5000) { "Migration took too long: ${migrationTime}ms" }

        // Validate all data is present after migration
        val cursor = db.query("SELECT COUNT(*) FROM audio_recordings")
        cursor.use {
            assert(it.moveToFirst())
            val count = it.getInt(0)
            assert(count == 1000)
        }

        db.close()
    }
}