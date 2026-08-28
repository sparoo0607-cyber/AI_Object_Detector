package com.accessibility.detector.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Permission utility for checking and handling Camera and Audio permissions.
 */
object PermissionManager {

    val VISION_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    val SOUND_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    fun hasVisionPermissions(context: Context): Boolean {
        return VISION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasSoundPermissions(context: Context): Boolean {
        return SOUND_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
