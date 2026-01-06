package com.example.soundly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.soundly.data.local.dao.PlaylistDao
import com.example.soundly.data.local.dao.TrackDao
import com.example.soundly.data.local.entity.ListeningHistoryEntity
import com.example.soundly.data.local.entity.PlaylistEntity
import com.example.soundly.data.local.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        ListeningHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SoundlyDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем колонку dateAdded с текущим временем по умолчанию
                db.execSQL("ALTER TABLE tracks ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            }
        }
    }
}
