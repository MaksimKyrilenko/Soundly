package com.example.soundly.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.soundly.domain.model.PlayerState
import com.example.soundly.domain.model.PlaybackMode
import com.example.soundly.domain.model.RepeatMode
import com.example.soundly.domain.model.Track
import com.example.soundly.player.audio.PlaybackEffect
import com.example.soundly.player.audio.PlaybackEffectManager
import com.example.soundly.player.audio.PlaybackParams
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackEffectManager: PlaybackEffectManager
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var pendingPlayRequest: Pair<Track, List<Track>>? = null
    private var isInitializing = false
    private var isInitialized = false
    
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            updatePositionAndDuration()
            mainHandler.postDelayed(this, 500L)
        }
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()
    
    // Expose effect manager state
    val currentEffect: StateFlow<PlaybackEffect> = playbackEffectManager.currentEffect
    val currentParams: StateFlow<PlaybackParams> = playbackEffectManager.currentParams

    init {
        // Подписываемся на изменения параметров воспроизведения
        scope.launch {
            playbackEffectManager.currentParams.collect { params ->
                updatePlayerState { 
                    it.copy(
                        playbackSpeed = params.speed, 
                        pitch = params.pitch,
                        playbackMode = mapEffectToMode(playbackEffectManager.currentEffect.value)
                    ) 
                }
            }
        }
    }
    
    private fun mapEffectToMode(effect: PlaybackEffect): PlaybackMode {
        return when (effect) {
            PlaybackEffect.NORMAL -> PlaybackMode.NORMAL
            PlaybackEffect.NIGHTCORE -> PlaybackMode.NIGHTCORE
            PlaybackEffect.DAYCORE -> PlaybackMode.DAYCORE
            PlaybackEffect.DOUBLE_TIME, PlaybackEffect.HALF_TIME, PlaybackEffect.CUSTOM -> PlaybackMode.SPEED_ONLY
        }
    }

    fun initialize() {
        if (isInitialized || isInitializing) return
        isInitializing = true
        
        try {
            val serviceIntent = Intent(context, MusicService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerController", "Failed to start service", e)
            }
            
            mainHandler.postDelayed({
                connectToService()
            }, 100)
        } catch (e: Exception) {
            isInitializing = false
            android.util.Log.e("PlayerController", "Failed to initialize", e)
        }
    }
    
    private fun connectToService() {
        try {
            val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    isInitialized = true
                    isInitializing = false
                    android.util.Log.d("PlayerController", "MediaController initialized successfully")
                    mainHandler.post { 
                        setupPlayerListener()
                        startPositionUpdates()
                        pendingPlayRequest?.let { (track, queue) ->
                            android.util.Log.d("PlayerController", "Processing pending play request: ${track.title}")
                            playTrackInternal(track, queue)
                            pendingPlayRequest = null
                        }
                    }
                } catch (e: Exception) {
                    isInitializing = false
                    android.util.Log.e("PlayerController", "Failed to initialize MediaController", e)
                }
            }, executor)
        } catch (e: Exception) {
            isInitializing = false
            android.util.Log.e("PlayerController", "Failed to connect to service", e)
        }
    }
    
    private fun startPositionUpdates() {
        mainHandler.removeCallbacks(positionUpdateRunnable)
        mainHandler.post(positionUpdateRunnable)
    }
    
    private fun updatePositionAndDuration() {
        mediaController?.let { controller ->
            val position = controller.currentPosition
            val duration = controller.duration.takeIf { it > 0 } ?: 0L
            val isPlaying = controller.isPlaying
            updatePlayerState { 
                it.copy(
                    currentPosition = position,
                    duration = duration,
                    isPlaying = isPlaying
                )
            }
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePositionAndDuration()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = mediaController?.currentMediaItemIndex ?: 0
                val track = _currentQueue.value.getOrNull(index)
                updatePlayerState { it.copy(currentTrack = track, currentIndex = index) }
                updatePositionAndDuration()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mode = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                updatePlayerState { it.copy(repeatMode = mode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePlayerState { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }
            
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                updatePlayerState { it.copy(playbackSpeed = playbackParameters.speed, pitch = playbackParameters.pitch) }
            }
        })
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        _currentQueue.value = queue
        updatePlayerState { it.copy(currentTrack = track, queue = queue, currentIndex = queue.indexOf(track).coerceAtLeast(0)) }
        
        if (mediaController == null) {
            pendingPlayRequest = Pair(track, queue)
            initialize()
            return
        }
        playTrackInternal(track, queue)
    }
    
    private fun playTrackInternal(track: Track, queue: List<Track>) {
        _currentQueue.value = queue
        val index = queue.indexOf(track).coerceAtLeast(0)
        
        val mediaItems = queue.map { it.toMediaItem() }
        
        mediaController?.apply {
            setMediaItems(mediaItems, index, 0)
            prepare()
            play()
        }
        updatePlayerState { it.copy(currentTrack = track, queue = queue, currentIndex = index) }
    }

    fun play() = mediaController?.play()
    fun pause() = mediaController?.pause()
    fun playPause() = mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    fun next() = mediaController?.seekToNextMediaItem()
    fun previous() = mediaController?.seekToPreviousMediaItem()
    fun seekTo(position: Long) = mediaController?.seekTo(position)

    fun toggleShuffle() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        mediaController?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ==================== PLAYBACK EFFECTS (osu! style) ====================
    
    /**
     * Установить эффект воспроизведения (как в osu!)
     */
    fun setEffect(effect: PlaybackEffect) {
        playbackEffectManager.setEffect(effect)
    }
    
    /**
     * Double Time - ускорение на 50% с сохранением pitch (time-stretch)
     */
    fun setDoubleTime() {
        playbackEffectManager.setEffect(PlaybackEffect.DOUBLE_TIME)
    }
    
    /**
     * Half Time - замедление на 25% с сохранением pitch (time-stretch)
     */
    fun setHalfTime() {
        playbackEffectManager.setEffect(PlaybackEffect.HALF_TIME)
    }
    
    /**
     * Nightcore - ускорение + повышение pitch (как в osu!)
     */
    fun setNightcore() {
        playbackEffectManager.setEffect(PlaybackEffect.NIGHTCORE)
    }
    
    /**
     * Daycore - замедление + понижение pitch
     */
    fun setDaycore() {
        playbackEffectManager.setEffect(PlaybackEffect.DAYCORE)
    }
    
    /**
     * Только изменение скорости без изменения pitch (time-stretch)
     */
    fun setSpeedOnly(speed: Float) {
        playbackEffectManager.setSpeedOnly(speed)
    }
    
    /**
     * Естественное изменение скорости (pitch меняется пропорционально)
     */
    fun setNaturalSpeed(speed: Float) {
        playbackEffectManager.setNaturalSpeed(speed)
    }
    
    /**
     * Кастомные параметры speed и pitch
     */
    fun setPlaybackParameters(speed: Float, pitch: Float) {
        playbackEffectManager.setCustomParams(speed, pitch, preservePitch = false)
    }
    
    /**
     * Сброс к нормальному воспроизведению
     */
    fun resetPlaybackParameters() {
        playbackEffectManager.reset()
    }
    
    // Legacy methods for compatibility
    fun setPlaybackSpeed(speed: Float) {
        playbackEffectManager.setSpeedOnly(speed)
    }

    fun getPlaybackSpeed(): Float = _playerState.value.playbackSpeed
    fun getPitch(): Float = _playerState.value.pitch
    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L
    fun getDuration(): Long = mediaController?.duration ?: 0L

    private fun updatePlayerState(update: (PlayerState) -> PlayerState) {
        _playerState.value = update(_playerState.value)
    }
    
    /**
     * Обновляет состояние избранного для текущего трека
     */
    fun updateCurrentTrackFavorite(isFavorite: Boolean) {
        updatePlayerState { state ->
            state.currentTrack?.let { track ->
                state.copy(currentTrack = track.copy(isFavorite = isFavorite))
            } ?: state
        }
    }

    fun release() {
        mainHandler.removeCallbacks(positionUpdateRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun Track.toMediaItem(): MediaItem {
        val mediaUri = when {
            uri.startsWith("content://") -> Uri.parse(uri)
            uri.startsWith("file:///") -> Uri.parse(uri)
            uri.startsWith("file:/") -> Uri.parse("file://" + uri.removePrefix("file:"))
            uri.startsWith("/") -> Uri.fromFile(java.io.File(uri))
            else -> Uri.parse(uri)
        }
        
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }
}
