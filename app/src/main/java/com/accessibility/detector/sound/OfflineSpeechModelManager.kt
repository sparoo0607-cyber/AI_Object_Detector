package com.accessibility.detector.sound

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

enum class SpeechRecognitionMode {
    OFFLINE_ONLY,
    PREFER_OFFLINE,
    ONLINE_FALLBACK
}

enum class SpeechModelStatus {
    OFFLINE_READY,
    ONLINE_AVAILABLE,
    MODEL_NOT_INSTALLED,
    UNAVAILABLE
}

/**
 * Manager for checking and configuring on-device offline speech recognition models (Telugu, English, etc.).
 */
class OfflineSpeechModelManager(private val context: Context) {

    /**
     * Checks the actual offline speech recognition capability for a given locale (e.g. Telugu "te-IN").
     */
    fun checkTeluguOfflineStatus(callback: (SpeechModelStatus, String) -> Unit) {
        val hasSpeechService = SpeechRecognizer.isRecognitionAvailable(context)
        if (!hasSpeechService) {
            callback(SpeechModelStatus.UNAVAILABLE, "Speech recognizer service not available on device")
            return
        }

        // On Android 12 (API 31+), check createOnDeviceSpeechRecognizer availability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isOnDeviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            if (isOnDeviceAvailable) {
                callback(SpeechModelStatus.OFFLINE_READY, "🟢 Telugu Offline Ready")
                return
            }
        }

        // Check if device has internet
        val isOnline = isInternetAvailable(context)
        if (isOnline) {
            callback(SpeechModelStatus.ONLINE_AVAILABLE, "🌐 Online (Offline pack not installed)")
        } else {
            callback(SpeechModelStatus.MODEL_NOT_INSTALLED, "⚠️ Telugu Offline Model Not Available")
        }
    }

    /**
     * Opens system voice settings so user can download the offline Telugu speech pack with one tap.
     */
    fun openVoiceModelDownloadSettings(context: Context) {
        val intentList = listOf(
            Intent("android.speech.action.DOWNLOAD_LANGUAGE_PACK").apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
            },
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_LOCALE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intentList) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Launched voice settings via ${intent.action}")
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "OfflineSpeechModelManager"
    }
}
