package com.optirest.eyecare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.optirest.eyecare.MainActivity
import com.optirest.eyecare.R
import com.optirest.eyecare.data.EyeGuardDefaults
import com.optirest.eyecare.data.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenFilterState(
    val isOverlayActive: Boolean = false,
    val warmthKelvin: Int = EyeGuardDefaults.DEFAULT_WARMTH_KELVIN,
    val extraDimPercent: Int = EyeGuardDefaults.DEFAULT_EXTRA_DIM_PERCENT,
    val blueCutPercent: Int = EyeGuardDefaults.DEFAULT_BLUE_CUT_PERCENT
)

object ScreenFilterStateHolder {
    private val _state = MutableStateFlow(ScreenFilterState())
    val state: StateFlow<ScreenFilterState> = _state.asStateFlow()

    fun update(state: ScreenFilterState) {
        _state.value = state
    }
}

class ScreenFilterService : Service() {

    companion object {
        const val ACTION_APPLY_FILTER = "com.optirest.eyecare.ACTION_APPLY_FILTER"
        const val ACTION_DISABLE_FILTER = "com.optirest.eyecare.ACTION_DISABLE_FILTER"
        const val ACTION_USER_TURN_OFF_FILTER = "com.optirest.eyecare.ACTION_USER_TURN_OFF_FILTER"
        private const val CHANNEL_ID = "optirest_filter_channel"
        private const val NOTIF_ID = 1003

        fun computeFilterArgb(warmthKelvin: Int, extraDimPercent: Int, blueCutPercent: Int): Int {
            val kelvinT = ((warmthKelvin - 1800).toFloat() / (6500 - 1800)).coerceIn(0f, 1f)
            val blueCut = (blueCutPercent.coerceIn(0, 100) / 100f)
            val dimAlpha = (extraDimPercent.coerceIn(0, 65) / 100f)

            val r = (255f - kelvinT * 24f).toInt().coerceIn(0, 255)
            val g = ((130f + kelvinT * 105f) * (1f - blueCut * 0.22f)).toInt().coerceIn(0, 255)
            val b = ((36f + kelvinT * 194f) * (1f - blueCut * 0.65f)).toInt().coerceIn(0, 255)

            val spectralAlpha = (blueCut * 0.35f).coerceAtMost(0.45f)
            val combinedAlpha = maxOf(dimAlpha, spectralAlpha * 0.6f + dimAlpha * 0.7f).coerceIn(0f, 0.75f)
            val a = (combinedAlpha * 255f).toInt().coerceIn(0, 192)
            return Color.argb(a, r, g, b)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var repository: UsageRepository
    private lateinit var notificationManager: NotificationManager
    private var overlayView: View? = null
    private var isScreenInteractive: Boolean = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    detachOverlay()
                }

                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    syncFilterFromConfig()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = UsageRepository(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = pm?.isInteractive ?: true

        createChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_USER_TURN_OFF_FILTER -> {
                val updated = repository.loadConfig().copy(
                    filterEnabled = false,
                    autoEveningSchedule = false
                )
                repository.saveConfig(updated)
                val syncIntent = Intent(this, EyeCareForegroundService::class.java).apply {
                    action = EyeCareForegroundService.ACTION_SYNC_CONFIG
                }
                try {
                    startService(syncIntent)
                } catch (_: Exception) {
                }
                detachOverlay()
                ScreenFilterStateHolder.update(
                    ScreenFilterState(
                        isOverlayActive = false,
                        warmthKelvin = updated.filterWarmthKelvin,
                        extraDimPercent = updated.filterExtraDimPercent,
                        blueCutPercent = updated.filterBlueCutPercent
                    )
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_DISABLE_FILTER -> {
                val cfg = repository.loadConfig()
                detachOverlay()
                ScreenFilterStateHolder.update(
                    ScreenFilterState(
                        isOverlayActive = false,
                        warmthKelvin = cfg.filterWarmthKelvin,
                        extraDimPercent = cfg.filterExtraDimPercent,
                        blueCutPercent = cfg.filterBlueCutPercent
                    )
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val active = syncFilterFromConfig()
                if (!active) {
                    detachOverlay()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                val notification = buildFilterNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIF_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIF_ID, notification)
                }
                notificationManager.notify(NOTIF_ID, notification)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        detachOverlay()
        val cfg = repository.loadConfig()
        ScreenFilterStateHolder.update(
            ScreenFilterState(
                isOverlayActive = false,
                warmthKelvin = cfg.filterWarmthKelvin,
                extraDimPercent = cfg.filterExtraDimPercent,
                blueCutPercent = cfg.filterBlueCutPercent
            )
        )
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncFilterFromConfig(): Boolean {
        val config = repository.loadConfig()
        val scheduledNow = config.autoEveningSchedule &&
            repository.isWithinTimeWindow(config.sunsetStart, config.morningEnd)
        val shouldEnable = (config.filterEnabled || scheduledNow) &&
            (config.filterExtraDimPercent > 0 || config.filterBlueCutPercent > 0)

        if (!shouldEnable) {
            detachOverlay()
            ScreenFilterStateHolder.update(
                ScreenFilterState(
                    isOverlayActive = false,
                    warmthKelvin = config.filterWarmthKelvin,
                    extraDimPercent = config.filterExtraDimPercent,
                    blueCutPercent = config.filterBlueCutPercent
                )
            )
            return false
        }

        val canOverlay = isScreenInteractive && Settings.canDrawOverlays(this)
        if (canOverlay) {
            val argb = computeFilterArgb(
                warmthKelvin = config.filterWarmthKelvin,
                extraDimPercent = config.filterExtraDimPercent,
                blueCutPercent = config.filterBlueCutPercent
            )
            attachOrUpdateOverlay(argb)
        } else {
            detachOverlay()
        }

        ScreenFilterStateHolder.update(
            ScreenFilterState(
                isOverlayActive = overlayView != null,
                warmthKelvin = config.filterWarmthKelvin,
                extraDimPercent = config.filterExtraDimPercent,
                blueCutPercent = config.filterBlueCutPercent
            )
        )
        return true
    }

    private fun attachOrUpdateOverlay(argbColor: Int) {
        if (overlayView == null) {
            val view = View(this).apply {
                setBackgroundColor(argbColor)
            }
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(view, params)
                overlayView = view
            } catch (_: Exception) {
                overlayView = null
            }
        } else {
            overlayView?.setBackgroundColor(argbColor)
        }
    }

    private fun detachOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Blue-Light Screen Filter",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the system-wide Warmth, Extra Dim, and Blue Cut overlay active"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildFilterNotification(): Notification {
        val config = repository.loadConfig()
        val openIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disableIntent = PendingIntent.getService(
            this,
            21,
            Intent(this, ScreenFilterService::class.java).apply { action = ACTION_USER_TURN_OFF_FILTER },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_notification)
            .setContentTitle("OptiRest Comfort Filter Active")
            .setContentText(
                "${config.filterWarmthKelvin}K Warmth · ${config.filterExtraDimPercent}% Dim · ${config.filterBlueCutPercent}% Blue Cut"
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Turn Off", disableIntent)
            .build()
    }
}
