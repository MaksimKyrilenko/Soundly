package com.example.soundly.domain.model

/**
 * 10-полосный эквалайзер v2.0
 */

val EQ_FREQUENCY_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

enum class EqualizerMode { SIMPLE, PRO }
enum class BassEnhancerMode { SOFT, HARD }
enum class HeadphoneType { TWS, WIRED, OVER_EAR }

/**
 * Настройки реверберации
 */
data class ReverbSettings(
    val enabled: Boolean = false,
    val roomSize: Float = 0.5f,      // 0-1: маленькая - большая комната
    val decay: Float = 0.5f,          // 0-1: короткий - длинный хвост
    val wetDryMix: Float = 0.3f,      // 0-1: сухой - мокрый сигнал
    val preDelay: Float = 0.02f       // 0-0.1: задержка перед реверберацией
)

/**
 * Настройки компрессора
 */
data class CompressorSettings(
    val enabled: Boolean = false,
    val threshold: Float = -20f,      // -60 to 0 dB
    val ratio: Float = 4f,            // 1:1 to 20:1
    val attack: Float = 10f,          // 0.1-100 ms
    val release: Float = 100f,        // 10-1000 ms
    val makeupGain: Float = 0f        // 0-24 dB
)

/**
 * Настройки Noise Gate
 */
data class NoiseGateSettings(
    val enabled: Boolean = false,
    val threshold: Float = -40f,      // -80 to 0 dB
    val attack: Float = 1f,           // 0.1-50 ms
    val release: Float = 50f,         // 10-500 ms
    val range: Float = -80f           // Глубина подавления
)

/**
 * Настройки De-Esser
 */
data class DeEsserSettings(
    val enabled: Boolean = false,
    val frequency: Float = 6000f,     // 4000-10000 Hz
    val threshold: Float = -20f,      // -40 to 0 dB
    val reduction: Float = 6f         // 0-12 dB
)

/**
 * Настройки Bass Boost с сабгармониками
 */
data class SubBassSettings(
    val enabled: Boolean = false,
    val amount: Float = 50f,          // 0-100%
    val frequency: Int = 80,          // 40-120 Hz
    val subHarmonics: Boolean = true, // Генерация октавы ниже
    val subAmount: Float = 30f        // 0-100% уровень сабгармоник
)

data class EqualizerPresetV2(
    val id: String,
    val name: String,
    val description: String = "",
    val bands: List<Float>,
    val bassEnhancer: Float = 0f,
    val bassEnhancerFrequency: Int = 80,
    val bassEnhancerMode: BassEnhancerMode = BassEnhancerMode.SOFT,
    val stereoWidth: Float = 100f,
    val loudnessEnabled: Boolean = false,
    // Новые параметры
    val reverb: ReverbSettings = ReverbSettings(),
    val compressor: CompressorSettings = CompressorSettings(),
    val noiseGate: NoiseGateSettings = NoiseGateSettings(),
    val deEsser: DeEsserSettings = DeEsserSettings(),
    val subBass: SubBassSettings = SubBassSettings()
)

/**
 * Пользовательский пресет с привязкой
 */
data class UserPreset(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val preset: EqualizerPresetV2,
    val linkedTrackId: String? = null,      // Привязка к треку
    val linkedPlaylistId: String? = null    // Привязка к плейлисту
)

val builtInPresetsV2 = listOf(
    EqualizerPresetV2("flat", "Flat", "Без изменений", List(10) { 0f }),
    EqualizerPresetV2("bass_boost", "Bass Boost", "Усиленные низкие", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), bassEnhancer = 30f),
    EqualizerPresetV2("phonk", "Phonk", "Глубокий бас", listOf(8f, 6f, 3f, 0f, -2f, -1f, 2f, 4f, 3f, 2f), bassEnhancer = 50f, bassEnhancerMode = BassEnhancerMode.HARD),
    EqualizerPresetV2("edm", "EDM", "Мощный бас", listOf(6f, 5f, 2f, 0f, 1f, 2f, 3f, 4f, 3f, 2f), bassEnhancer = 40f),
    EqualizerPresetV2("rock", "Rock", "Драйвовые гитары", listOf(4f, 3f, 1f, 0f, -1f, 1f, 3f, 4f, 3f, 2f)),
    EqualizerPresetV2("vocal", "Vocal Clarity", "Чистый вокал", listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 2f, 1f, 0f), deEsser = DeEsserSettings(enabled = true)),
    EqualizerPresetV2("night", "Night Mode", "Мягкий звук", listOf(-2f, -1f, 0f, 1f, 2f, 2f, 1f, 0f, -1f, -2f), loudnessEnabled = true, compressor = CompressorSettings(enabled = true, threshold = -15f, ratio = 3f)),
    EqualizerPresetV2("tws", "TWS Fix", "Для беспроводных", listOf(4f, 3f, 2f, 1f, 0f, 1f, 2f, 1f, -1f, -2f), bassEnhancer = 25f, subBass = SubBassSettings(enabled = true, amount = 40f)),
    EqualizerPresetV2("studio", "Studio", "Чистый мониторинг", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), compressor = CompressorSettings(enabled = true, threshold = -10f, ratio = 2f), noiseGate = NoiseGateSettings(enabled = true)),
    EqualizerPresetV2("live", "Live Concert", "Концертный звук", listOf(3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 3f, 2f), reverb = ReverbSettings(enabled = true, roomSize = 0.7f, decay = 0.6f, wetDryMix = 0.25f))
)

fun generateCalibration(headphoneType: HeadphoneType, bassLevel: Int, highsLevel: Int, volumeLevel: Int): Triple<List<Float>, Float, Int> {
    val bands = MutableList(10) { 0f }
    var bassEnhancer = 0f
    var bassFreq = 80
    
    when (bassLevel) {
        -1 -> { bands[0] = 5f; bands[1] = 4f; bands[2] = 3f; bassEnhancer = 40f }
        1 -> { bands[0] = -3f; bands[1] = -2f; bands[2] = -1f }
    }
    when (highsLevel) {
        -1 -> { bands[7] = -2f; bands[8] = -3f; bands[9] = -4f }
        1 -> { bands[7] = 3f; bands[8] = 4f; bands[9] = 3f }
    }
    when (headphoneType) {
        HeadphoneType.TWS -> { bassEnhancer += 15f; bassFreq = 80 }
        HeadphoneType.WIRED -> bassFreq = 60
        HeadphoneType.OVER_EAR -> { bassFreq = 60; bands[0] -= 1f }
    }
    
    return Triple(bands.map { it.coerceIn(-12f, 12f) }, bassEnhancer.coerceIn(0f, 100f), bassFreq)
}
