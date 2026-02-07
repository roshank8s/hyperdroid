package com.hyperdroid.di

import android.content.Context
import com.hyperdroid.data.ImageRepository
import com.hyperdroid.data.VMRepository
import com.hyperdroid.service.VMServiceManager
import com.hyperdroid.vm.VMEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VMModule {

    @Provides
    @Singleton
    fun provideVMEngine(
        @ApplicationContext context: Context,
        vmRepository: VMRepository
    ): VMEngine {
        return VMEngine(context, vmRepository)
    }

    @Provides
    @Singleton
    fun provideVMServiceManager(
        @ApplicationContext context: Context
    ): VMServiceManager {
        return VMServiceManager(context)
    }

    @Provides
    @Singleton
    fun provideImageRepository(
        @ApplicationContext context: Context
    ): ImageRepository {
        return ImageRepository(context)
    }
}
