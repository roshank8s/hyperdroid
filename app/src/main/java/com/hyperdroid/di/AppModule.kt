package com.hyperdroid.di

import android.content.Context
import com.hyperdroid.permission.AVFChecker
import com.hyperdroid.permission.PermissionManager
import com.hyperdroid.permission.ShizukuHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAVFChecker(@ApplicationContext context: Context): AVFChecker {
        return AVFChecker(context)
    }

    @Provides
    @Singleton
    fun provideShizukuHelper(@ApplicationContext context: Context): ShizukuHelper {
        return ShizukuHelper(context)
    }

    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context,
        avfChecker: AVFChecker,
        shizukuHelper: ShizukuHelper
    ): PermissionManager {
        return PermissionManager(context, avfChecker, shizukuHelper)
    }
}
