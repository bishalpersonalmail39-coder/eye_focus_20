package com.optirest.eyecare.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.optirest.eyecare.data.UsageRepository
import com.optirest.eyecare.service.EyeCareForegroundService
import com.optirest.eyecare.service.ScreenFilterService

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTART_SERVICE = "com.optirest.eyecare.ACTION_RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBootOrRestart = action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_REBOOT ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == ACTION_RESTART_SERVICE

        if (!isBootOrRestart) return

        val repo = UsageRepository(context)
        val config = repo.loadConfig()
        if (!config.autoStartOnBoot) return

        val serviceIntent = Intent(context, EyeCareForegroundService::class.java).apply {
            this.action = EyeCareForegroundService.ACTION_START_OR_SYNC
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
            scheduleDelayedRetry(context)
        }

        val scheduledEvening = config.autoEveningSchedule &&
            repo.isWithinTimeWindow(config.sunsetStart, config.morningEnd)
        val shouldStartFilter = (config.filterEnabled || scheduledEvening) &&
            (config.filterExtraDimPercent > 0 || config.filterBlueCutPercent > 0) &&
            Settings.canDrawOverlays(context)

        if (shouldStartFilter) {
            val filterIntent = Intent(context, ScreenFilterService::class.java).apply {
                this.action = ScreenFilterService.ACTION_APPLY_FILTER
            }
            try {
                ContextCompat.startForegroundService(context, filterIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun scheduleDelayedRetry(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val retryIntent = Intent(context, BootCompletedReceiver::class.java).apply {
                action = ACTION_RESTART_SERVICE
            }
            val pi = PendingIntent.getBroadcast(
                context,
                701,
                retryIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 8_000L,
                pi
            )
        } catch (_: Exception) {
        }
    }
}
