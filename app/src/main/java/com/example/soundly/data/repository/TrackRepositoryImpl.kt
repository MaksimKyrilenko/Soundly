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
    
    // Список путей файлов которые были удалены - чтобы не добавлять их обратно при сканировании
    private val deletedFilePaths = mutableSetOf<String>()

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
                
                // Для треков из папки Soundly пробуем распарсить имя файла и создаём специальный ID
                var trackId = id.toString()
                if (filePath.contains("/Soundly/") && (artist == "Unknown Artist" || artist == "<unknown>")) {
                    val fileName = filePath.substringAfterLast("/").substringBeforeLast(".")
                    if (fileName.contains(" - ")) {
                        val parts = fileName.split(" - ", limit = 2)
                        if (parts.size == 2) {
                            artist = parts[0].trim()
                            title = parts[1].trim()
                        }
                    }
                    // Создаём стабильный ID на основе пути файла для треков из Soundly
                    trackId = "soundly_${filePath.hashCode()}"
                }
                
                // Для треков из Soundly папки ищем локальную обложку
                var finalArtworkUri = artworkUri
                if (filePath.contains("/Soundly/")) {
                    // Имя аудиофайла без расширения
                    val audioFileName = filePath.substringAfterLast("/").substringBeforeLast(".")
                    
                    // Определяем где искать обложку
                    val soundlyDir = filePath.substringBefore("/Soundly/") + "/Soundly"
                    
                    // Для экспортов ищем в Exports/.thumbnails, для остальных в .thumbnails
                    val thumbnailDir = if (filePath.contains("/Exports/")) {
                        java.io.File(soundlyDir, "Exports/.thumbnails")
                    } else {
                        java.io.File(soundlyDir, ".thumbnails")
                    }
                    
                    if (thumbnailDir.exists()) {
                        // Ищем обложку с точно таким же именем
                        val exactMatch = thumbnailDir.listFiles()?.firstOrNull { thumbFile ->
                            val thumbName = thumbFile.name.substringBeforeLast(".")
                            thumbName == audioFileName && 
                            (thumbFile.name.endsWith(".jpg") || thumbFile.name.endsWith(".png"))
                        }
                        
                        if (exactMatch != null) {
                            finalArtworkUri = "file://${exactMatch.absolutePath}"
                            android.util.Log.d("TrackRepo", "Found exact thumbnail for $audioFileName: ${exactMatch.absolutePath}")
                        } else {
                            android.util.Log.d("TrackRepo", "No thumbnail found for $audioFileName in ${thumbnailDir.absolutePath}")
                        }
                    }
                }

                val track = Track(
                    id = trackId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    artworkUri = finalArtworkUri,
                    uri = contentUri.toString(),
                    isLocal = true,
                    dateAdded = dateAdded
                )
                // Сохраняем пару (трек, путь к файлу) для проверки дубликатов
                tracksWithPaths.add(Pair(track, filePath))
            }
        }

        // Получаем существующие треки чтобы сохранить их состояние (избранное, playCount, artworkUri и т.д.)
        val existingTracks = trackDao.getAllTracksOnce().associateBy { it.id }
        
        android.util.Log.d("TrackRepo", "Existing tracks count: ${existingTracks.size}")
        
        // Собираем пути файлов существующих soundly треков для проверки
        val existingSoundlyPaths = mutableMapOf<String, TrackEntity>()
        existingTracks.values.filter { it.id.startsWith("soundly_") }.forEach { entity ->
            extractFilePath(entity.uri)?.let { path ->
                existingSoundlyPaths[path] = entity
            }
        }
        
        // Обрабатываем треки из MediaStore
        val processedTracks = mutableListOf<TrackEntity>()
        val processedPaths = mutableSetOf<String>()
        
        for ((track, filePath) in tracksWithPaths) {
            // Пропускаем файлы которые были удалены
            if (deletedFilePaths.contains(filePath)) {
                android.util.Log.d("TrackRepo", "Skipping deleted file: $filePath")
                // Проверяем существует ли файл - если нет, убираем из списка удалённых
                if (!java.io.File(filePath).exists()) {
                    deletedFilePaths.remove(filePath)
                }
                continue
            }
            
            // Пропускаем дубликаты по пути
            if (processedPaths.contains(filePath)) {
                android.util.Log.d("TrackRepo", "Skipping duplicate path: $filePath")
                continue
            }
            processedPaths.add(filePath)
            
            // Для треков из Soundly папки - проверяем есть ли уже в БД
            if (track.id.startsWith("soundly_")) {
                val existingEntity = existingTracks[track.id]
                if (existingEntity != null) {
                    // Сохраняем существующие метаданные (artworkUri, title, artist) но обновляем uri
                    val updatedEntity = existingEntity.copy(
                        uri = track.uri // Обновляем URI на случай если изменился
                    )
                    processedTracks.add(updatedEntity)
                    android.util.Log.d("TrackRepo", "Keeping existing soundly track: ${track.id}")
                } else {
                    // Новый трек из Soundly папки - добавляем с распарсенными метаданными
                    processedTracks.add(TrackEntity.fromDomain(track))
                    android.util.Log.d("TrackRepo", "Adding new soundly track: ${track.id}")
                }
            } else {
                // Обычный MediaStore трек
                val existing = existingTracks[track.id]
                processedTracks.add(TrackEntity.fromDomain(track.copy(
                    isFavorite = existing?.isFavorite ?: false,
                    playCount = existing?.playCount ?: 0,
                    lastPlayedAt = existing?.lastPlayedAt
                )))
            }
        }
        
        // Добавляем soundly треки которых нет в MediaStore (файл удалён?)
        existingTracks.values
            .filter { it.id.startsWith("soundly_") && !processedPaths.contains(extractFilePath(it.uri)) }
            .forEach { entity ->
                // Проверяем существует ли файл
                val filePath = extractFilePath(entity.uri)
                if (filePath != null && java.io.File(filePath).exists()) {
                    processedTracks.add(entity)
                    android.util.Log.d("TrackRepo", "Keeping soundly track not in MediaStore: ${entity.id}")
                }
            }
        
        trackDao.insertTracks(processedTracks)
        
        // Sync favorites from cloud
        syncFavoritesFromCloud()
        
        processedTracks.map { it.toDomain() }
    }

    override suspend fun insertTrack(track: Track) {
        trackDao.insertTrack(TrackEntity.fromDomain(track))
    }

    override suspend fun deleteTrack(trackId: String) {
        withContext(Dispatchers.IO) {
            val track = trackDao.getTrackById(trackId)
            
            android.util.Log.d("TrackRepo", "=== DELETE TRACK ===")
            android.util.Log.d("TrackRepo", "Track ID: $trackId")
            
            track?.let {
                val uri = it.uri
                android.util.Log.d("TrackRepo", "URI: $uri")
                
                // Извлекаем путь к файлу
                var filePath: String? = null
                var mediaStoreUri: android.net.Uri? = null
                
                if (uri.startsWith("file://")) {
                    filePath = uri.removePrefix("file://")
                } else if (uri.startsWith("/")) {
                    filePath = uri
                } else if (uri.startsWith("content://")) {
                    mediaStoreUri = android.net.Uri.parse(uri)
                    try {
                        context.contentResolver.query(
                            mediaStoreUri!!,
                            arrayOf(MediaStore.Audio.Media.DATA),
                            null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                filePath = cursor.getString(0)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TrackRepo", "Query error: ${e.message}")
                    }
                }
                
                android.util.Log.d("TrackRepo", "File path: $filePath")
                
                // Ищем MediaStore URI по пути если ещё не нашли
                if (filePath != null && mediaStoreUri == null) {
                    try {
                        context.contentResolver.query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Audio.Media._ID),
                            "${MediaStore.Audio.Media.DATA} = ?",
                            arrayOf(filePath),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val id = cursor.getLong(0)
                                mediaStoreUri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TrackRepo", "Find MediaStore error: ${e.message}")
                    }
                }
                
                android.util.Log.d("TrackRepo", "MediaStore URI: $mediaStoreUri")
                
                // 1. Удаляем файл напрямую
                var fileDeleted = false
                if (filePath != null) {
                    val file = java.io.File(filePath!!)
                    if (file.exists()) {
                        fileDeleted = file.delete()
                        android.util.Log.d("TrackRepo", "File.delete(): $fileDeleted")
                    }
                }
                
                // 2. Удаляем из MediaStore по URI
                if (mediaStoreUri != null) {
                    try {
                        val deleted = context.contentResolver.delete(mediaStoreUri!!, null, null)
                        android.util.Log.d("TrackRepo", "MediaStore delete by URI: $deleted")
                    } catch (e: Exception) {
                        android.util.Log.w("TrackRepo", "Delete by URI error: ${e.message}")
                    }
                }
                
                // 3. Удаляем из MediaStore по пути
                if (filePath != null) {
                    try {
                        val deleted = context.contentResolver.delete(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            "${MediaStore.Audio.Media.DATA} = ?",
                            arrayOf(filePath)
                        )
                        android.util.Log.d("TrackRepo", "MediaStore delete by path: $deleted")
                    } catch (e: Exception) {
                        android.util.Log.w("TrackRepo", "Delete by path error: ${e.message}")
                    }
                }
                
                // 4. Добавляем в список удалённых
                if (filePath != null) {
                    deletedFilePaths.add(filePath!!)
                }
                
                // 5. Уведомляем MediaScanner
                if (filePath != null) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
                }
            }
            
            // 6. Удаляем из БД
            trackDao.deleteTrackById(trackId)
            android.util.Log.d("TrackRepo", "Deleted from DB")
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
            uri.startsWith("content://media/") -> {
                // Для content:// URI пробуем получить путь через ContentResolver
                try {
                    context.contentResolver.query(
                        android.net.Uri.parse(uri),
                        arrayOf(MediaStore.Audio.Media.DATA),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
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
