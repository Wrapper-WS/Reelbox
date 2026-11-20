package com.example.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class MediaPermissionState {
    CHECKING,
    DENIED,
    PERMANENTLY_DENIED,
    PARTIAL_ACCESS,
    FULL_ACCESS,
    ;

    val hasAnyAccess: Boolean
        get() = this == FULL_ACCESS || this == PARTIAL_ACCESS
}

object MediaPermissionHelper {

    fun requiredMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
            )

            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
        }
    }

    fun evaluatePermissionState(
        context: Context,
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): MediaPermissionState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasVideo = isPermissionGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
            val hasImages = isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
            val hasSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                isPermissionGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

            when {
                hasVideo && hasImages -> MediaPermissionState.FULL_ACCESS
                hasSelected || hasVideo || hasImages -> MediaPermissionState.PARTIAL_ACCESS
                hasRequestedBefore && !shouldShowRationale -> MediaPermissionState.PERMANENTLY_DENIED
                else -> MediaPermissionState.DENIED
            }
        } else {
            val hasStorage = isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            when {
                hasStorage -> MediaPermissionState.FULL_ACCESS
                hasRequestedBefore && !shouldShowRationale -> MediaPermissionState.PERMANENTLY_DENIED
                else -> MediaPermissionState.DENIED
            }
        }
    }

    fun shouldShowAnyRationale(activity: Activity?): Boolean {
        if (activity == null) return false
        val corePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return corePermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
