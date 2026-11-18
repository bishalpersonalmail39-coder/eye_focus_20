package com.eyeguard.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val validBootAction = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (!validBootAction) return
        if (!EyeGuardDefaults.AUTO_START_ON_BOOT) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return

        val serviceIntent = Intent(context, ScreenFilterService::class.java).apply {
            this.action = ScreenFilterService.ACTION_UPDATE
            putExtra(ScreenFilterService.EXTRA_MODE_ID, EyeGuardDefaults.INITIAL_MODE_ID)
            putExtra(ScreenFilterService.EXTRA_KELVIN, EyeGuardDefaults.INITIAL_KELVIN)
            putExtra(ScreenFilterService.EXTRA_BRIGHTNESS, EyeGuardDefaults.INITIAL_BRIGHTNESS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
