# 🎵 Soundly

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" alt="Soundly Logo"/>
</p>

<p align="center">
  <b>Современный музыкальный плеер для Android</b><br>
  <i>Kotlin • Jetpack Compose • Material 3</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-14+-green?style=flat-square&logo=android" alt="Android 14+"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9+-purple?style=flat-square&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-blue?style=flat-square" alt="Compose"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License"/>
</p>

---

## ✨ Возможности

### 🎧 Воспроизведение
- Поддержка локальных аудиофайлов (MP3, M4A, FLAC, WAV и др.)
- Фоновое воспроизведение с уведомлениями
- Управление с экрана блокировки и наушников
- Очередь воспроизведения с drag & drop

### 🎛️ Эквалайзер
- **10-полосный эквалайзер** с пресетами
- **Режимы воспроизведения** в стиле osu!:
  - 🌙 **Nightcore** — ускорение + повышение тона
  - 🌅 **Daycore** — замедление + понижение тона  
  - ⏩ **Double Time** — 1.5x скорость
  - ⏪ **Half Time** — 0.75x скорость
- **Bass Boost** с настройкой частоты
- **Stereo Width** и моно режим
- **Экспорт треков** с применёнными эффектами

### 📥 Загрузка музыки
- Скачивание с YouTube через yt-dlp
- Автоматическое извлечение метаданных
- Сохранение обложек альбомов

### 📚 Библиотека
- Плейлисты с обложками
- Избранные треки
- История прослушивания
- Статистика прослушивания

### ☁️ Синхронизация
- Облачная синхронизация через Supabase
- Авторизация по email
- Синхронизация плейлистов и избранного

---

## 🛠️ Технологии

| Категория | Технологии |
|-----------|------------|
| **UI** | Jetpack Compose, Material 3, Coil |
| **Архитектура** | MVVM, Clean Architecture, Hilt DI |
| **Аудио** | ExoPlayer (Media3), MediaCodec |
| **База данных** | Room, DataStore |
| **Сеть** | Retrofit, OkHttp, Kotlinx Serialization |
| **Бэкенд** | Supabase (Auth, Database, Storage) |
| **Загрузка** | yt-dlp (youtubedl-android) |

---

## 📱 Скриншоты

<p align="center">
  <i>Скриншоты будут добавлены позже</i>
</p>

---

## 🚀 Установка

### Требования
- Android 8.0 (API 26) или выше
- ~100 MB свободного места

### Сборка из исходников

```bash
# Клонирование репозитория
git clone https://github.com/MaksimKyrilenko/Soundly.git
cd Soundly

# Сборка debug APK
./gradlew assembleDebug

# APK будет в app/build/outputs/apk/debug/
```

---

## 📁 Структура проекта

```
app/src/main/java/com/example/soundly/
├── data/
│   ├── export/          # Экспорт аудио с эффектами
│   ├── local/           # Room DB, DataStore
│   ├── remote/          # Supabase, YouTube API
│   └── repository/      # Реализации репозиториев
├── di/                  # Hilt модули
├── domain/
│   ├── model/           # Доменные модели
│   └── repository/      # Интерфейсы репозиториев
├── player/
│   ├── audio/           # Аудио процессоры, эффекты
│   └── ...              # ExoPlayer, MusicService
└── presentation/
    ├── components/      # UI компоненты
    ├── navigation/      # Навигация
    ├── screens/         # Экраны приложения
    └── theme/           # Material 3 тема
```

---

## 🎵 Экспорт с эффектами

Soundly позволяет экспортировать треки с "запечёнными" эффектами эквалайзера:

1. Откройте эквалайзер во время воспроизведения
2. Настройте эффекты (Nightcore, Daycore, скорость и т.д.)
3. Нажмите кнопку "Экспорт"
4. Новый трек появится в библиотеке

Экспортированный файл будет звучать с эффектами даже в других плеерах!

---

## 📄 Лицензия

```
MIT License

Copyright (c) 2024-2026 Maksim Kyrilenko

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<p align="center">
  Сделано с ❤️ и 🎵
</p>
