package com.example.geminimultimodalliveapi.di

import android.content.Context
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.data.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiModules {

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences {
        return AppPreferences.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        appPreferences: AppPreferences
    ): AppDatabase {
        val password = appPreferences.getOrCreateDatabasePassword()
        return AppDatabase.getInstance(context, password.toByteArray(Charsets.UTF_8))
    }

    @Provides
    @Singleton
    fun provideMemoryDao(database: AppDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    @Singleton
    fun provideVehicleDao(database: AppDatabase): VehicleDao {
        return database.vehicleDao()
    }

    @Provides
    @Singleton
    fun provideDateProfileDao(database: AppDatabase): DateProfileDao {
        return database.dateProfileDao()
    }

    @Provides
    @Singleton
    fun provideMeetingDao(database: AppDatabase): MeetingDao {
        return database.meetingDao()
    }
}
