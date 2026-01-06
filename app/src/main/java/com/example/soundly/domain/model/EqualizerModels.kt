package com.example.soundly.domain.model

/**
 * 10-полосный эквалайзер v2.0
 */

val EQ_FREQUENCY_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

enum class EqualizerMode { SIMPLE, PRO }
enum class BassEnhancerMode { SOFT, HARD }
enum class HeadphoneType { TWS, WIRED, OVER_EAR }

data class EqualizerPresetV2(
    val id: String,
    val name: String,
    val description: String = "",
    val bands: List<Float>,
    val bassEnhancer: Float = 0f,
    val bassEnhancerFrequency: Int = 80,
    val bassEnhancerMode: BassEnhancerMode = BassEnhancerMode.SOFT,
    val stereoWidth: Float = 100f,
    val loudnessEnabled: Boolean = false
)

val builtInPresetsV2 = listOf(
    EqualizerPresetV2("flat", "Flat", "Без изменений", List(10) { 0f }),
    EqualizerPresetV2("bass_boost", "Bass Boost", "Усиленные низкие", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), bassEnhancer = 30f),
    EqualizerPresetV2("phonk", "Phonk", "Глубокий бас", listOf(8f, 6f, 3f, 0f, -2f, -1f, 2f, 4f, 3f, 2f), bassEnhancer = 50f, bassEnhancerMode = BassEnhancerMode.HARD),
    EqualizerPresetV2("edm", "EDM", "Мощный бас", listOf(6f, 5f, 2f, 0f, 1f, 2f, 3f, 4f, 3f, 2f), bassEnhancer = 40f),
    EqualizerPresetV2("rock", "Rock", "Драйвовые гитары", listOf(4f, 3f, 1f, 0f, -1f, 1f, 3f, 4f, 3f, 2f)),
    EqualizerPresetV2("vocal", "Vocal Clarity", "Чистый вокал", listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 2f, 1f, 0f)),
    EqualizerPresetV2("night", "Night Mode", "Мягкий звук", listOf(-2f, -1f, 0f, 1f, 2f, 2f, 1f, 0f, -1f, -2f), loudnessEnabled = true),
    EqualizerPresetV2("tws", "TWS Fix", "Для беспроводных", listOf(4f, 3f, 2f, 1f, 0f, 1f, 2f, 1f, -1f, -2f), bassEnhancer = 25f)
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
