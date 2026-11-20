package com.optirest.eyecare.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.optirest.eyecare.data.AppConfig

class BlueLightOverlayManager(private val context: Context) {

    fun syncWithService(config: AppConfig, isScheduledActive: Boolean) {
        val shouldRun = (config.filterEnabled || isScheduledActive) &&
            (config.filterExtraDimPercent > 0 || config.filterBlueCutPercent > 0) &&
            Settings.canDrawOverlays(context)

        val intent = Intent(context, ScreenFilterService::class.java).apply {
            action = if (shouldRun) {
                ScreenFilterService.ACTION_APPLY_FILTER
            } else {
                ScreenFilterService.ACTION_DISABLE_FILTER
            }
        }

        if (shouldRun) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service not running
            }
        }
    }

    fun removeOverlay() {
        val intent = Intent(context, ScreenFilterService::class.java).apply {
            action = ScreenFilterService.ACTION_DISABLE_FILTER
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}
