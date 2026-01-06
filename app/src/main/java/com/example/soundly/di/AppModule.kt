package com.example.soundly.di

import android.content.Context
import androidx.room.Room
import com.example.soundly.data.local.PreferencesManager
import com.example.soundly.data.local.SoundlyDatabase
import com.example.soundly.data.local.dao.PlaylistDao
import com.example.soundly.data.local.dao.TrackDao
import com.example.soundly.data.repository.PlaylistRepositoryImpl
import com.example.soundly.data.repository.TrackRepositoryImpl
import com.example.soundly.domain.repository.PlaylistRepository
import com.example.soundly.domain.repository.TrackRepository
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): SoundlyDatabase {
        return Room.databaseBuilder(
            context,
            SoundlyDatabase::class.java,
            "soundly_database"
        )
            .addMigrations(SoundlyDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideTrackDao(database: SoundlyDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: SoundlyDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository
}
