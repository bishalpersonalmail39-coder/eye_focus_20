package com.eyeguard.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ScreenFilterService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.eyeguard.android.ACTION_UPDATE"
        const val ACTION_TOGGLE_PAUSE = "com.eyeguard.android.ACTION_TOGGLE_PAUSE"
        const val ACTION_TOGGLE_FOCUS = "com.eyeguard.android.ACTION_TOGGLE_FOCUS"
        const val ACTION_TRIGGER_BREAK = "com.eyeguard.android.ACTION_TRIGGER_BREAK"
        const val EXTRA_KELVIN = "kelvin"
        const val EXTRA_BRIGHTNESS = "brightness"
        const val EXTRA_MODE_ID = "mode_id"

        private const val CHANNEL_ID = "eyeguard_filter_channel"
        private const val NOTIF_ID = 7001

        @Volatile var isRunning = false
        @Volatile var isPaused = false
        @Volatile var isScreenOn = true
        @Volatile var focusEnabled = false
        @Volatile var currentModeId = EyeGuardDefaults.INITIAL_MODE_ID
        @Volatile var currentKelvin = EyeGuardDefaults.INITIAL_KELVIN
        @Volatile var currentBrightness = EyeGuardDefaults.INITIAL_BRIGHTNESS
        @Volatile var workSecondsRemaining = EyeGuardDefaults.WORK_MINUTES * 60
    }

    private lateinit var windowManager: WindowManager
    private var filterOverlayView: View? = null
    private var focusTopView: View? = null
    private var focusBottomView: View? = null
    private var breakOverlayView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var screenOffAtElapsedMs: Long = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenOffAtElapsedMs = SystemClock.elapsedRealtime()
                    // Stop ticking immediately while phone screen is turned off
                    handler.removeCallbacks(tickRunnable)
                    updateNotification()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val wasOff = !isScreenOn
                    isScreenOn = true
                    if (wasOff && screenOffAtElapsedMs > 0L) {
                        val offDurationSec = (SystemClock.elapsedRealtime() - screenOffAtElapsedMs) / 1000L
                        // If screen stayed off for at least the eye-rest duration, reset the work cycle
                        if (offDurationSec >= EyeGuardDefaults.REST_SECONDS) {
                            workSecondsRemaining = EyeGuardDefaults.WORK_MINUTES * 60
                        }
                    }
                    handler.removeCallbacks(tickRunnable)
                    handler.postDelayed(tickRunnable, 1000L)
                    updateNotification()
                }
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            isScreenOn = pm.isInteractive

            // Count break timer ONLY when the mobile screen is actively ON
            val shouldCount = !EyeGuardDefaults.COUNT_TIMER_ONLY_WHEN_SCREEN_ON || isScreenOn
            if (EyeGuardDefaults.BREAK_ENABLED && shouldCount && breakOverlayView == null) {
                workSecondsRemaining--
                if (workSecondsRemaining <= 0) {
                    showBreakOverlay()
                }
            }

            if (isScreenOn) {
                checkForegroundAppRule()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, screenFilter)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        attachFilterOverlay()

        if (isScreenOn) {
            handler.postDelayed(tickRunnable, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                isPaused = !isPaused
                updateFilterAppearance()
            }
            ACTION_TOGGLE_FOCUS -> {
                focusEnabled = !focusEnabled
                updateFocusBands()
            }
            ACTION_TRIGGER_BREAK -> showBreakOverlay()
            ACTION_UPDATE -> {
                currentKelvin = intent.getIntExtra(EXTRA_KELVIN, currentKelvin).coerceIn(1200, 6500)
                currentBrightness = intent.getIntExtra(EXTRA_BRIGHTNESS, currentBrightness).coerceIn(20, 100)
                intent.getStringExtra(EXTRA_MODE_ID)?.let { currentModeId = it }
                isPaused = false
                attachFilterOverlay()
                updateFilterAppearance()
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Ensure service restarts immediately if user swipes the app from Recents
        val restartIntent = Intent(applicationContext, ScreenFilterService::class.java)
        val pending = PendingIntent.getService(
            applicationContext,
            2,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, pending)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tickRunnable)
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
        detachView(filterOverlayView)
        detachView(focusTopView)
        detachView(focusBottomView)
        detachView(breakOverlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayLayoutParams(touchable: Boolean = false): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }
        val flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun attachFilterOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        if (filterOverlayView == null) {
            filterOverlayView = View(this)
            windowManager.addView(filterOverlayView, overlayLayoutParams())
        }
        updateFilterAppearance()
    }

    private fun updateFilterAppearance() {
        filterOverlayView?.setBackgroundColor(
            ColorMath.computeOverlayArgb(currentKelvin, currentBrightness, isPaused)
        )
    }

    private fun updateFocusBands() {
        if (!focusEnabled) {
            detachView(focusTopView); focusTopView = null
            detachView(focusBottomView); focusBottomView = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        val metrics = resources.displayMetrics
        val bandPx = (EyeGuardDefaults.FOCUS_BAND_HEIGHT_DP * metrics.density).toInt()
        val topH = (metrics.heightPixels - bandPx) / 2
        val dimColor = Color.argb((EyeGuardDefaults.FOCUS_SURROUND_DIM * 255).toInt(), 2, 6, 23)

        if (focusTopView == null) {
            focusTopView = View(this).apply { setBackgroundColor(dimColor) }
            val lpTop = overlayLayoutParams().apply {
                height = topH
                gravity = Gravity.TOP
            }
            windowManager.addView(focusTopView, lpTop)
        }
        if (focusBottomView == null) {
            focusBottomView = View(this).apply { setBackgroundColor(dimColor) }
            val lpBottom = overlayLayoutParams().apply {
                height = topH
                gravity = Gravity.BOTTOM
            }
            windowManager.addView(focusBottomView, lpBottom)
        }
    }

    private fun showBreakOverlay() {
        if (breakOverlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(238, 15, 23, 42))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Time to Rest Your Eyes"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Look 20 feet away for ${EyeGuardDefaults.REST_SECONDS} seconds."
            textSize = 15f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 36)
        }
        val btnDismiss = Button(this).apply {
            text = if (EyeGuardDefaults.ALLOW_SKIP_BREAK) "Skip Break" else "Delay 5 Min"
            setOnClickListener {
                detachView(breakOverlayView)
                breakOverlayView = null
                workSecondsRemaining = if (EyeGuardDefaults.ALLOW_SKIP_BREAK) {
                    EyeGuardDefaults.WORK_MINUTES * 60
                } else {
                    300
                }
            }
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(btnDismiss)

        breakOverlayView = container
        windowManager.addView(container, overlayLayoutParams(touchable = true))
    }

    private fun checkForegroundAppRule() {
        if (EyeGuardDefaults.APP_RULES.isEmpty()) return
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000L, now)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestPkg = event.packageName
                }
            }
            val matchedMode = latestPkg?.lowercase()?.let { EyeGuardDefaults.APP_RULES[it] } ?: return
            val preset = EyeGuardDefaults.PRESETS.firstOrNull { it.id == matchedMode } ?: return
            if (currentModeId != preset.id) {
                currentModeId = preset.id
                currentKelvin = preset.kelvin
                currentBrightness = preset.brightness
                updateFilterAppearance()
            }
        } catch (_: SecurityException) {
            // Usage access not granted; skip per-app rule check gracefully
        }
    }

    private fun detachView(view: View?) {
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EyeGuard Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenFilterService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_IMMUTABLE
        )
        val mins = (workSecondsRemaining / 60).coerceAtLeast(0)
        val secs = (workSecondsRemaining % 60).coerceAtLeast(0)
        val timerText = if (isScreenOn) {
            String.format("Break in %02d:%02d", mins, secs)
        } else {
            "Screen off (timer paused)"
        }
        val text = if (isPaused) {
            "Filter paused · $timerText"
        } else {
            "${currentKelvin}K · ${currentBrightness}% · $timerText"
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("EyeGuard Mobile")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pauseIntent
            )
            .setOngoing(true)
            .build()
    }
}
