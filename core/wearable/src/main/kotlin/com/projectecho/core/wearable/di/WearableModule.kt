package com.projectecho.core.wearable.di

import android.content.Context
import com.projectecho.core.wearable.client.WearableChannelClient
import com.projectecho.core.wearable.client.WearableDataClient
import com.projectecho.core.wearable.client.WearableMessageClient
import com.projectecho.core.wearable.client.impl.WearableChannelClientImpl
import com.projectecho.core.wearable.client.impl.WearableDataClientImpl
import com.projectecho.core.wearable.client.impl.WearableMessageClientImpl
import com.projectecho.core.wearable.service.WearableSyncService
import com.projectecho.core.wearable.service.impl.WearableSyncServiceImpl
import com.projectecho.core.wearable.service.RemoteRecordingController
import com.projectecho.core.wearable.service.impl.RemoteRecordingControllerImpl
import com.projectecho.core.wearable.service.OfflineSyncManager
import com.projectecho.core.wearable.service.impl.OfflineSyncManagerImpl
import com.projectecho.core.wearable.service.RecordingCompletionListener
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearableModule {
    
    @Binds
    abstract fun bindWearableDataClient(
        wearableDataClientImpl: WearableDataClientImpl
    ): WearableDataClient
    
    @Binds
    abstract fun bindWearableMessageClient(
        wearableMessageClientImpl: WearableMessageClientImpl
    ): WearableMessageClient
    
    @Binds
    abstract fun bindWearableChannelClient(
        wearableChannelClientImpl: WearableChannelClientImpl
    ): WearableChannelClient
    
    @Binds
    abstract fun bindWearableSyncService(
        wearableSyncServiceImpl: WearableSyncServiceImpl
    ): WearableSyncService
    
    @Binds
    abstract fun bindRemoteRecordingController(
        remoteRecordingControllerImpl: RemoteRecordingControllerImpl
    ): RemoteRecordingController
    
    @Binds
    abstract fun bindOfflineSyncManager(
        offlineSyncManagerImpl: OfflineSyncManagerImpl
    ): OfflineSyncManager
    
    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            }
        }
    }
}