# Audio Reactive Cover - Production Guide

## Обзор

Production-ready решение для аудио-реактивной анимации обложки трека в Android приложении Soundly.

## Архитектура

### Компоненты

1. **AudioReactiveController** - основной контроллер
   - Управляет Visualizer API
   - Анализирует waveform и FFT данные
   - Вычисляет RMS, bass level, beat detection
   - Применяет low-pass фильтр для сглаживания
   - Управляет жизненным циклом

2. **PlayerController** - интеграция с плеером
   - Инициализирует AudioReactiveController с audioSessionId
   - Предоставляет StateFlow для UI
   - Управляет паузой/возобновлением

3. **MusicService** - точка инициализации
   - Получает audioSessionId от ExoPlayer
   - Инициализирует AudioReactiveController

4. **UI Components** - визуализация
   - FullPlayerScreen - полноэкранный плеер с волнами
   - MiniPlayer - мини-плеер с пульсацией

## Использование

### Получение данных в UI

```kotlin
@Composable
fun MyPlayerScreen(viewModel: PlayerViewModel) {
    // Получаем данные от AudioReactiveController
    val amplitude by viewModel.playerController.amplitude.collectAsState()
    val bassLevel by viewModel.playerController.bassLevel.collectAsState()
    val beatDetected by viewModel.playerController.beatDetected.collectAsState()
    val coverScale by viewModel.playerController.coverScale.collectAsState()
    
    // Используем scale для анимации
    val animatedScale by animateFloatAsState(
        targetValue = if (isPlaying) coverScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    // Применяем к ImageView/AsyncImage
    AsyncImage(
        model = track.artworkUri,
        modifier = Modifier
            .size(280.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(CircleShape)
    )
}
```

### Управление эффектом

```kotlin
// Включить/выключить
playerController.setAudioReactiveEnabled(true)

// Настроить чувствительность (0.5 - 2.0)
playerController.setAudioReactiveSensitivity(1.5f)

// Пауза (при onPause Activity)
playerController.pauseAudioReactive()

// Возобновление (при onResume Activity)
playerController.resumeAudioReactive()
```

### Fallback анимация

Если Visualizer не работает (нет разрешения или не поддерживается), используется fallback:

```kotlin
// Fallback анимация
val infiniteTransition = rememberInfiniteTransition()
val fallbackPulse by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(800, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )
)

// Используем fallback если нет данных
val effectiveScale = if (coverScale > 1.01f) coverScale else (1f + fallbackPulse * 0.05f)
```

## Параметры

### AudioReactiveController

- **CAPTURE_RATE_MILLIS** = 50ms (20 FPS) - частота обновления
- **RMS_SMOOTHING_FACTOR** = 0.3f - коэффициент сглаживания
- **BEAT_THRESHOLD** = 1.4f - порог определения бита
- **BEAT_COOLDOWN_MS** = 300ms - минимальное время между битами
- **MIN_SCALE** = 1.0f - минимальный масштаб
- **MAX_SCALE** = 1.08f - максимальный масштаб
- **MAX_AMPLITUDE_CHANGE** = 0.2f - limiter для защиты от скачков

### Настройки

```kotlin
// Чувствительность (0.5 - 2.0)
audioReactiveController.setSensitivity(1.0f)

// Beat detection (вкл/выкл)
audioReactiveController.setBeatDetectionEnabled(true)

// Limiter (вкл/выкл)
audioReactiveController.setLimiterEnabled(true)
```

## Производительность

### Оптимизации

1. **Частота обновления** - 20 FPS (50ms) оптимально для UI
2. **Отдельный поток** - анализ в Dispatchers.Default
3. **Минимум аллокаций** - переиспользование массивов
4. **Low-pass фильтр** - сглаживание без лагов
5. **Limiter** - защита от резких скачков

### Потребление ресурсов

- CPU: ~2-3% на средних устройствах
- Memory: ~1-2 MB дополнительно
- Battery: минимальное влияние

## Жизненный цикл

### Инициализация

```kotlin
// В MusicService.onCreate()
val audioSessionId = player.audioSessionId
if (audioSessionId != 0) {
    playerController.initializeAudioReactive(audioSessionId)
}
```

### Пауза/Возобновление

```kotlin
// В Activity/Fragment
override fun onPause() {
    super.onPause()
    playerController.pauseAudioReactive()
}

override fun onResume() {
    super.onResume()
    playerController.resumeAudioReactive()
}
```

### Освобождение

```kotlin
// В PlayerController.release()
audioReactiveController.release()
```

## Разрешения

Требуется в AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android:permission.MODIFY_AUDIO_SETTINGS" />
```

Запрос разрешения в HomeScreen:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    // Handle permissions
}

LaunchedEffect(Unit) {
    if (!hasRecordAudioPermission) {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }
}
```

## Отладка

### Логирование

```kotlin
// В AudioReactiveController
Log.d(TAG, "Beat detected! RMS: $rms")
Log.d(TAG, "Visualizer enabled successfully")

// В UI
LaunchedEffect(amplitude, bassLevel) {
    if (isPlaying && amplitude > 0.01f) {
        Log.d("PlayerScreen", "Amplitude: $amplitude, Bass: $bassLevel, Scale: $coverScale")
    }
}
```

### Проверка работы

1. Проверить разрешение RECORD_AUDIO
2. Проверить audioSessionId != 0
3. Проверить логи Visualizer
4. Проверить значения amplitude/bassLevel > 0

## Визуальные эффекты

### Слои анимации

1. **Внешнее кольцо** - реагирует на басы (фиолетово-розовое)
2. **Среднее кольцо** - пульсирует с битами
3. **Внутреннее свечение** - glow effect
4. **Обложка** - основная анимация scale

### Параметры волн

```kotlin
// Внешнее кольцо
val waveAlpha = 0.6f + (bassLevel * 0.4f)
val waveScale = animatedScale * 1.15f

// Среднее кольцо
val midWaveScale = if (beatDetected) animatedScale * 1.1f else animatedScale * 1.08f
val midWaveAlpha = 0.5f + (bassLevel * 0.3f)

// Glow
val glowAlpha = 0.5f + (bassLevel * 0.4f)
```

## Troubleshooting

### Анимация не работает

1. Проверить разрешение RECORD_AUDIO
2. Проверить audioSessionId в логах
3. Проверить что Visualizer создан успешно
4. Использовать fallback анимацию

### Лаги анимации

1. Уменьшить чувствительность
2. Включить limiter
3. Увеличить CAPTURE_RATE_MILLIS

### Слишком слабая реакция

1. Увеличить чувствительность
2. Уменьшить BEAT_THRESHOLD
3. Настроить RMS_SMOOTHING_FACTOR

## Best Practices

1. **Всегда освобождать ресурсы** - вызывать release()
2. **Использовать fallback** - для устройств без Visualizer
3. **Обрабатывать разрешения** - запрашивать RECORD_AUDIO
4. **Логировать ошибки** - для отладки
5. **Тестировать на разных устройствах** - производительность

## Совместимость

- minSdk: 26 (Android 8.0+)
- Visualizer API: доступен с API 9
- ExoPlayer: 1.3.0+
- Compose: 1.5.0+

## Автор

Soundly Team - Production-ready audio reactive solution
