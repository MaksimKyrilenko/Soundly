package com.example.soundly.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.soundly.data.local.dao.TrackDao
import com.example.soundly.data.local.entity.TrackEntity
import com.example.soundly.data.remote.SupabaseClientProvider
import com.example.soundly.data.remote.dto.FavoriteDto
import com.example.soundly.data.remote.dto.ListeningHistoryDto
import com.example.soundly.domain.model.Track
import com.example.soundly.domain.repository.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val authRepository: AuthRepository
) : TrackRepository {

    private val postgrest = SupabaseClientProvider.postgrest

    override fun getAllTracks(): Flow<List<Track>> =
        trackDao.getAllTracks().map { entities -> entities.map { it.toDomain() } }

    override fun getLocalTracks(): Flow<List<Track>> =
        trackDao.getLocalTracks().map { entities -> entities.map { it.toDomain() } }

    override fun getFavoriteTracks(): Flow<List<Track>> =
        trackDao.getFavoriteTracks().map { entities -> entities.map { it.toDomain() } }

    override fun searchTracks(query: String): Flow<List<Track>> =
        trackDao.searchTracks(query).map { entities -> entities.map { it.toDomain() } }

    override fun getTopTracks(limit: Int): Flow<List<Track>> =
        trackDao.getTopTracks(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTrackById(id: String): Track? =
        trackDao.getTrackById(id)?.toDomain()

    override suspend fun toggleFavorite(trackId: String) {
        val track = trackDao.getTrackById(trackId)
        track?.let {
            val newFavoriteState = !it.isFavorite
            trackDao.updateFavorite(trackId, newFavoriteState)
            
            // Sync to cloud
            syncFavoriteToCloud(trackId, newFavoriteState)
        }
    }

    override suspend fun incrementPlayCount(trackId: String) {
        trackDao.incrementPlayCount(trackId)
        
        // Record to listening history
        recordListeningHistory(trackId)
    }

    override suspend fun scanLocalMusic(): List<Track> = withContext(Dispatchers.IO) {
        val tracksWithPaths = mutableListOf<Pair<Track, String>>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // Сортируем по дате добавления (новые первыми)
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn) ?: "Unknown"
                var artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val filePath = cursor.getString(dataColumn) ?: ""
                // DATE_ADDED в секундах, конвертируем в миллисекунды
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ).toString()
                
                // Для треков из папки Soundly пробуем распарсить имя файла
                if (filePath.contains("/Soundly/") && (artist == "Unknown Artist" || artist == "<unknown>")) {
                    val fileName = filePath.substringAfterLast("/").substringBeforeLast(".")
                    if (fileName.contains(" - ")) {
                        val parts = fileName.split(" - ", limit = 2)
                        if (parts.size == 2) {
                            artist = parts[0].trim()
                            title = parts[1].trim()
                        }
                    }
                }

                val track = Track(
                    id = id.toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    artworkUri = artworkUri,
                    uri = contentUri.toString(),
                    isLocal = true,
                    dateAdded = dateAdded
                )
                // Сохраняем пару (трек, путь к файлу) для проверки дубликатов
                tracksWithPaths.add(Pair(track, filePath))
            }
        }

        // Получаем существующие треки чтобы сохранить их состояние (избранное, playCount, и YouTube треки)
        val existingTracks = trackDao.getAllTracksOnce().associateBy { it.id }
        
        // Собираем пути файлов YouTube треков для проверки дубликатов
        val youtubeTrackPaths = existingTracks.values
            .filter { it.id.startsWith("yt_") }
            .mapNotNull { entity ->
                // Извлекаем путь из URI (поддерживаем разные форматы)
                extractFilePath(entity.uri)
            }
            .toSet()
        
        android.util.Log.d("TrackRepo", "YouTube track paths: $youtubeTrackPaths")
        
        // Фильтруем MediaStore треки - исключаем те, что уже есть как YouTube треки
        val filteredTracks = tracksWithPaths
            .filter { (_, filePath) -> 
                val dominated = youtubeTrackPaths.contains(filePath)
                if (dominated) {
                    android.util.Log.d("TrackRepo", "Skipping MediaStore track (duplicate of YT): $filePath")
                }
                !dominated
            }
            .map { it.first }
        
        // Собираем ID треков из отфильтрованного MediaStore
        val mediaStoreIds = filteredTracks.map { it.id }.toSet()
        
        // Сохраняем YouTube треки (которых нет в MediaStore)
        val youtubeTracksToKeep = existingTracks.values.filter { 
            it.id.startsWith("yt_") || !mediaStoreIds.contains(it.id)
        }
        
        // Обновляем треки из MediaStore, сохраняя состояние
        val tracksToInsert = filteredTracks.map { track ->
            val existing = existingTracks[track.id]
            TrackEntity.fromDomain(track.copy(
                isFavorite = existing?.isFavorite ?: false,
                playCount = existing?.playCount ?: 0,
                lastPlayedAt = existing?.lastPlayedAt
            ))
        } + youtubeTracksToKeep
        
        trackDao.insertTracks(tracksToInsert)
        
        // Sync favorites from cloud
        syncFavoritesFromCloud()
        
        filteredTracks
    }

    override suspend fun insertTrack(track: Track) {
        trackDao.insertTrack(TrackEntity.fromDomain(track))
    }

    override suspend fun deleteTrack(trackId: String) {
        // Получаем трек перед удалением
        val track = trackDao.getTrackById(trackId)
        
        // Удаляем из базы данных
        trackDao.deleteTrackById(trackId)
        
        // Пытаемся удалить файл
        track?.let {
            try {
                val uri = it.uri
                
                // Для YouTube треков (файл по пути или file:// URI)
                if (it.id.startsWith("yt_") || uri.startsWith("/") || uri.startsWith("file:")) {
                    val filePath = extractFilePath(uri)
                    if (filePath != null) {
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            file.delete()
                            android.util.Log.d("TrackRepo", "Deleted file: $filePath")
                        }
                    }
                } else {
                    // Для MediaStore треков
                    try {
                        context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
                    } catch (e: SecurityException) {
                        // На Android 10+ нужно разрешение на удаление
                        android.util.Log.w("TrackRepo", "Cannot delete file: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackRepo", "Error deleting file", e)
            }
        }
    }
    
    /**
     * Извлекает путь к файлу из различных форматов URI
     */
    private fun extractFilePath(uri: String): String? {
        return when {
            uri.startsWith("file:///") -> uri.removePrefix("file://")
            uri.startsWith("file:/") -> uri.removePrefix("file:")
            uri.startsWith("/") -> uri
            else -> null
        }
    }

    override suspend fun updateTrack(trackId: String, title: String, artist: String, album: String) {
        trackDao.updateTrackInfo(trackId, title, artist, album)
    }
    
    // ===== Cloud Sync Methods =====
    
    private suspend fun syncFavoriteToCloud(trackId: String, isFavorite: Boolean) {
        val userId = authRepository.getCurrentUserId() ?: return
        try {
            if (isFavorite) {
                postgrest.from("favorites").insert(
                    FavoriteDto(userId = userId, trackId = trackId)
                )
            } else {
                postgrest.from("favorites").delete {
                    filter {
                        eq("user_id", userId)
                        eq("track_id", trackId)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackRepo", "Favorite sync failed", e)
        }
    }
    
    private suspend fun syncFavoritesFromCloud() {
        val userId = authRepository.getCurrentUserId() ?: return
        try {
            val cloudFavorites = postgrest.from("favorites")
                .select { filter { eq("user_id", userId) } }
                .decodeList<FavoriteDto>()
            
            // Update local favorites based on cloud
            cloudFavorites.forEach { favorite ->
                trackDao.updateFavorite(favorite.trackId, true)
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackRepo", "Favorites sync from cloud failed", e)
        }
    }
    
    private suspend fun recordListeningHistory(trackId: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        val track = trackDao.getTrackById(trackId) ?: return
        try {
            postgrest.from("listening_history").insert(
                ListeningHistoryDto(
                    userId = userId,
                    trackId = trackId,
                    duration = track.duration
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("TrackRepo", "History record failed", e)
        }
    }
    
    suspend fun getListeningHistory(limit: Int = 50): List<ListeningHistoryDto> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            postgrest.from("listening_history")
                .select {
                    filter { eq("user_id", userId) }
                    order("played_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun getListeningStats(): ListeningStats {
        val userId = authRepository.getCurrentUserId() ?: return ListeningStats()
        return try {
            val history = postgrest.from("listening_history")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ListeningHistoryDto>()
            
            ListeningStats(
                totalPlays = history.size,
                totalDuration = history.sumOf { it.duration },
                uniqueTracks = history.map { it.trackId }.distinct().size
            )
        } catch (e: Exception) {
            ListeningStats()
        }
    }
}

data class ListeningStats(
    val totalPlays: Int = 0,
    val totalDuration: Long = 0,
    val uniqueTracks: Int = 0
)
