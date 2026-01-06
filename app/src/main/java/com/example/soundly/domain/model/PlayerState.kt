package com.example.soundly.domain.model

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val playbackMode: PlaybackMode = PlaybackMode.NORMAL
)

/**
 * Режим воспроизведения скорости/питча
 */
enum class PlaybackMode {
    NORMAL,      // 1.0x speed, 1.0x pitch
    NIGHTCORE,   // 1.5x speed, 1.5x pitch (как в osu!)
    DAYCORE,     // 0.75x speed, 0.75x pitch
    SPEED_ONLY,  // Только скорость без изменения питча (time-stretch)
    DOUBLE_TIME, // 1.5x speed, pitch сохранён (как DT в osu!)
    HALF_TIME    // 0.75x speed, pitch сохранён (как HT в osu!)
}

enum class RepeatMode {
    OFF, ONE, ALL
}

data class EqualizerPreset(
    val name: String,
    val bands: List<Float> // 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
)

val defaultEqualizerPresets = listOf(
    EqualizerPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset("Bass Boost", listOf(5f, 4f, 0f, 0f, 0f)),
    EqualizerPreset("Rock", listOf(4f, 2f, -1f, 2f, 4f)),
    EqualizerPreset("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
    EqualizerPreset("Jazz", listOf(3f, 1f, 0f, 1f, 3f)),
    EqualizerPreset("Classical", listOf(4f, 2f, 0f, 2f, 4f))
)
