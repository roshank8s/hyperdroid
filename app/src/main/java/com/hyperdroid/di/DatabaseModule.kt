package com.hyperdroid.di

import android.content.Context
import androidx.room.Room
import com.hyperdroid.db.AppDatabase
import com.hyperdroid.db.VMDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hyperdroid_database"
        ).build()
    }

    @Provides
    fun provideVMDao(database: AppDatabase): VMDao {
        return database.vmDao()
    }
}
