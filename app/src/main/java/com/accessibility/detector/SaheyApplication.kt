package com.accessibility.detector

import android.app.Application
import android.util.Log

/**
 * Main application class for SAHEY AI Multimodal Accessibility Assistant.
 */
class SaheyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SAHEY Application initialized")
    }

    companion object {
        private const val TAG = "SaheyApplication"
    }
}
