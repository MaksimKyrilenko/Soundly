package com.example.soundly

import android.app.Application
import android.os.Build
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@HiltAndroidApp
class SoundlyApp : Application() {
    
    companion object {
        private const val TAG = "SoundlyApp"
        
        @Volatile
        var isYoutubeDLReady = false
            private set
        
        var initError: String? = null
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.w(TAG, "=== APP STARTED ===")
        
        // Инициализируем YoutubeDL в фоне
        CoroutineScope(Dispatchers.IO).launch {
            initializeYoutubeDL()
        }
    }
    
    private suspend fun initializeYoutubeDL() {
        Log.w(TAG, "=== YoutubeDL Init Start ===")
        Log.w(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.w(TAG, "Android: ${Build.VERSION.SDK_INT}")
        Log.w(TAG, "ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        
        val freeSpace = filesDir.freeSpace / (1024 * 1024)
        Log.w(TAG, "Free space: ${freeSpace}MB")
        
        // Попытка 1: обычная инициализация
        try {
            Log.w(TAG, "Attempt 1: Normal init...")
            YoutubeDL.getInstance().init(this@SoundlyApp)
            
            // Обновляем yt-dlp до последней версии
            try {
                Log.w(TAG, "Updating yt-dlp...")
                YoutubeDL.getInstance().updateYoutubeDL(this@SoundlyApp)
                Log.w(TAG, "yt-dlp updated successfully")
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed (not critical): ${e.message}")
            }
            
            isYoutubeDLReady = true
            initError = null
            Log.w(TAG, "=== SUCCESS! YoutubeDL Ready ===")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 1 failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        
        // Попытка 2: очистка и повтор
        try {
            Log.w(TAG, "Attempt 2: Clean and retry...")
            delay(1000)
            
            val ytdlDir = File(filesDir, "youtubedl-android")
            if (ytdlDir.exists()) {
                val deleted = ytdlDir.deleteRecursively()
                Log.w(TAG, "Deleted old files: $deleted")
            }
            
            delay(500)
            YoutubeDL.getInstance().init(this@SoundlyApp)
            
            // Обновляем yt-dlp
            try {
                Log.w(TAG, "Updating yt-dlp...")
                YoutubeDL.getInstance().updateYoutubeDL(this@SoundlyApp)
                Log.w(TAG, "yt-dlp updated successfully")
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed (not critical): ${e.message}")
            }
            
            isYoutubeDLReady = true
            initError = null
            Log.w(TAG, "=== SUCCESS after clean! ===")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 2 failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        
        // Попытка 3: ещё раз с задержкой
        try {
            Log.w(TAG, "Attempt 3: Final try...")
            delay(2000)
            YoutubeDL.getInstance().init(this@SoundlyApp)
            isYoutubeDLReady = true
            initError = null
            Log.w(TAG, "=== SUCCESS on attempt 3! ===")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 3 failed: ${e.javaClass.simpleName}: ${e.message}")
            initError = "Загрузчик недоступен: ${e.message}"
        }
        
        Log.e(TAG, "=== ALL ATTEMPTS FAILED ===")
    }
}
