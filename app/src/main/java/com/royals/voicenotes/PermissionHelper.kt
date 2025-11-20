package com.royals.voicenotes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * Check if app has storage permission
     * For Android 10 (Q) and above, scoped storage is used so no permission needed
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses scoped storage, no permission needed for app-specific directory
            true
        } else {
            // Below Android 10, check for WRITE_EXTERNAL_STORAGE permission
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if app has audio recording permission
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if app has all required permissions
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasAudioPermission(context) && hasStoragePermission(context)
    }

    /**
     * Get list of permissions that need to be requested
     */
    fun getPermissionsToRequest(context: Context): List<String> {
        val permissions = mutableListOf<String>()

        if (!hasAudioPermission(context)) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // Only request storage permission for Android 9 and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasStoragePermission(context)) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions
    }
}