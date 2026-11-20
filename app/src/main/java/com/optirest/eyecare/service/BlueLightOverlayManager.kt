package com.optirest.eyecare.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.optirest.eyecare.data.AppConfig

class BlueLightOverlayManager(private val context: Context) {

    private var lastAppliedSignature: String? = null

    fun syncWithService(config: AppConfig, isScheduledActive: Boolean, force: Boolean = false) {
        val shouldRun = (config.filterEnabled || isScheduledActive) &&
            (config.filterExtraDimPercent > 0 || config.filterBlueCutPercent > 0) &&
            Settings.canDrawOverlays(context)

        val signature = if (shouldRun) {
            "ON:${config.filterWarmthKelvin}:${config.filterExtraDimPercent}:${config.filterBlueCutPercent}"
        } else {
            "OFF"
        }

        if (!force && signature == lastAppliedSignature) {
            return
        }
        lastAppliedSignature = signature

        val intent = Intent(context, ScreenFilterService::class.java).apply {
            action = if (shouldRun) {
                ScreenFilterService.ACTION_APPLY_FILTER
            } else {
                ScreenFilterService.ACTION_DISABLE_FILTER
            }
        }

        if (shouldRun) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        } else {
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service not running
            }
        }
    }

    fun removeOverlay() {
        lastAppliedSignature = "OFF"
        val intent = Intent(context, ScreenFilterService::class.java).apply {
            action = ScreenFilterService.ACTION_DISABLE_FILTER
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}
