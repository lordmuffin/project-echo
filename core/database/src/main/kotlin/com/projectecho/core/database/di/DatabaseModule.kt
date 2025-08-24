package com.projectecho.core.database.di

import android.content.Context
import androidx.room.Room
import com.projectecho.core.database.ProjectEchoDatabase
import com.projectecho.core.database.dao.AudioRecordingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideProjectEchoDatabase(
        @ApplicationContext context: Context
    ): ProjectEchoDatabase {
        return Room.databaseBuilder(
            context,
            ProjectEchoDatabase::class.java,
            ProjectEchoDatabase.DATABASE_NAME
        )
            .addMigrations(
                ProjectEchoDatabase.MIGRATION_1_2
            )
            .build()
    }

    @Provides
    fun provideAudioRecordingDao(database: ProjectEchoDatabase): AudioRecordingDao {
        return database.audioRecordingDao()
    }
}