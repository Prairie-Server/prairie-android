package com.continuum.app.android.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

const val LEGACY_PUBLIC_DOWNLOAD_PERMISSION: String = Manifest.permission.WRITE_EXTERNAL_STORAGE

fun requiresLegacyPublicDownloadPermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt < Build.VERSION_CODES.Q

fun hasLegacyPublicDownloadPermission(context: Context): Boolean =
    !requiresLegacyPublicDownloadPermission() ||
        ContextCompat.checkSelfPermission(
            context,
            LEGACY_PUBLIC_DOWNLOAD_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
