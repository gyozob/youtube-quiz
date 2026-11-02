package com.personal.youtubequiz.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.personal.youtubequiz.service.YouTubeWatchService

/**
 * The app is useless without four manual grants, none of which can be
 * requested with a normal runtime dialog except the microphone. This collects
 * the checks and the Settings deep links in one place so the setup screen can
 * show honest status.
 */
object Permissions {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Reads the system list of enabled accessibility services. More reliable
     * than asking whether our service object exists, because the system may
     * not have bound it yet.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, YouTubeWatchService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    fun hasMicrophone(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun isYouTubeInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(YouTubeWatchService.YOUTUBE_PACKAGE, 0)
        }.isSuccess

    fun overlaySettings(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * App info page. On Android 13+ a sideloaded app may need
     * "Allow restricted settings" from the overflow menu here before the
     * accessibility toggle can be switched on at all.
     */
    fun appDetails(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri(),
    )
}
