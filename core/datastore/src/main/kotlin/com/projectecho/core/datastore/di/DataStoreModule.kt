package com.projectecho.core.datastore.di

import com.projectecho.core.datastore.PreferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindPreferencesDataStore(
        preferencesDataStore: PreferencesDataStore
    ): PreferencesDataStore
}