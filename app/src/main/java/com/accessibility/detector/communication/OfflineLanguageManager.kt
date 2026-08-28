package com.accessibility.detector.communication

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.concurrent.ConcurrentHashMap

enum class ModelStatus {
    DOWNLOADED,
    DOWNLOADING,
    NOT_DOWNLOADED,
    ERROR
}

data class LanguagePackItem(
    val language: SupportedLanguage,
    var status: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    var statusText: String = "↓ Download",
    var approxSizeMb: String = "~30 MB"
)

/**
 * On-Device Translation Language Pack Manager using Google ML Kit.
 * Handles checking, downloading, deleting, and status reporting of offline language models.
 */
class OfflineLanguageManager(private val context: Context) {

    private val modelManager = RemoteModelManager.getInstance()
    private val downloadingSet = ConcurrentHashMap.newKeySet<String>()

    /**
     * Maps SupportedLanguage enum to Google ML Kit TranslateLanguage code.
     */
    fun getMlKitLanguageCode(language: SupportedLanguage): String? {
        if (language == SupportedLanguage.AUTO) return null
        return TranslateLanguage.fromLanguageTag(language.code) ?: language.code
    }

    /**
     * Checks whether an offline language model is downloaded.
     */
    fun checkModelDownloaded(language: SupportedLanguage, onResult: (Boolean) -> Unit) {
        val mlKitCode = getMlKitLanguageCode(language)
        if (mlKitCode == null) {
            onResult(false)
            return
        }

        val model = TranslateRemoteModel.Builder(mlKitCode).build()
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                onResult(isDownloaded)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error checking model for ${language.name}: ${e.message}")
                onResult(false)
            }
    }

    /**
     * Checks if both source and target models are downloaded for a language pair.
     */
    fun checkLanguagePairReady(
        source: SupportedLanguage,
        target: SupportedLanguage,
        onResult: (Boolean) -> Unit
    ) {
        checkModelDownloaded(source) { sourceReady ->
            if (!sourceReady) {
                onResult(false)
                return@checkModelDownloaded
            }
            checkModelDownloaded(target) { targetReady ->
                onResult(targetReady)
            }
        }
    }

    /**
     * Downloads an offline language model from Google ML Kit with safety validation.
     */
    fun downloadModel(
        language: SupportedLanguage,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val mlKitCode = getMlKitLanguageCode(language)
        if (mlKitCode == null) {
            onError("Language is not supported for offline translation.")
            return
        }

        if (downloadingSet.contains(mlKitCode)) {
            onProgress("Download already in progress...")
            return
        }

        if (!isInternetAvailable(context)) {
            onError("No internet connection available to download ${language.displayName}.")
            return
        }

        downloadingSet.add(mlKitCode)
        onProgress("Downloading ${language.displayName} language pack...")

        val model = TranslateRemoteModel.Builder(mlKitCode).build()
        val conditions = DownloadConditions.Builder()
            .build() // Downloads over Wi-Fi or mobile data when user explicitly requests

        modelManager.download(model, conditions)
            .addOnSuccessListener {
                downloadingSet.remove(mlKitCode)
                Log.d(TAG, "Language pack downloaded successfully: ${language.name}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                downloadingSet.remove(mlKitCode)
                Log.e(TAG, "Failed to download model for ${language.name}: ${e.message}", e)
                onError("Unable to download language pack. Please check your internet connection.")
            }
    }

    /**
     * Deletes a downloaded offline language pack to free storage.
     */
    fun deleteModel(
        language: SupportedLanguage,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val mlKitCode = getMlKitLanguageCode(language)
        if (mlKitCode == null) {
            onError("Unsupported language")
            return
        }

        val model = TranslateRemoteModel.Builder(mlKitCode).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener {
                Log.d(TAG, "Deleted offline language model: ${language.name}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete model for ${language.name}: ${e.message}", e)
                onError("Error deleting model: ${e.localizedMessage}")
            }
    }

    /**
     * Returns list of offline language packs configurable by the user.
     */
    fun getAvailableLanguagePacks(): List<SupportedLanguage> {
        return listOf(
            SupportedLanguage.ENGLISH,
            SupportedLanguage.TELUGU,
            SupportedLanguage.HINDI,
            SupportedLanguage.TAMIL,
            SupportedLanguage.KANNADA,
            SupportedLanguage.MALAYALAM,
            SupportedLanguage.SPANISH
        )
    }

    /**
     * Checks if the device has an active internet connection.
     */
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "OfflineLanguageManager"
    }
}
