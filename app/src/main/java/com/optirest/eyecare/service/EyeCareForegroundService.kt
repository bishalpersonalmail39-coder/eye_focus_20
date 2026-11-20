package com.optirest.eyecare.service

import android.app.AlarmManager
import android.app.KeyguardManager
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
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.optirest.eyecare.MainActivity
import com.optirest.eyecare.R
import com.optirest.eyecare.data.AppConfig
import com.optirest.eyecare.data.DailyUsageRecord
import com.optirest.eyecare.data.StrainBreakdown
import com.optirest.eyecare.data.UsageRepository
import com.optirest.eyecare.receiver.BootCompletedReceiver
import com.optirest.eyecare.receiver.NotificationActionReceiver
import com.optirest.eyecare.util.BatteryOptimizationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EyeCareUiState(
    val isServiceRunning: Boolean = false,
    val isScreenOnAndUnlocked: Boolean = true,
    val isManualPaused: Boolean = false,
    val isOnBreak: Boolean = false,
    val breakDueModalOpen: Boolean = false,
    val isBreakOverlayVisible: Boolean = false,
    val breakRemainingSec: Int = 20,
    val cycleElapsedMs: Long = 0L,
    val todayRecord: DailyUsageRecord? = null,
    val systemUsageStatsMs: Long = 0L,
    val strainBreakdown: StrainBreakdown = StrainBreakdown(0, 0, 0, 0, 0),
    val last14Days: List<DailyUsageRecord> = emptyList(),
    val hasSampleData: Boolean = false,
    val hasPastData: Boolean = false,
    val config: AppConfig = AppConfig()
)

object ServiceStateHolder {
    private val _uiState = MutableStateFlow(EyeCareUiState())
    val uiState: StateFlow<EyeCareUiState> = _uiState.asStateFlow()

    fun update(transform: (EyeCareUiState) -> EyeCareUiState) {
        _uiState.value = transform(_uiState.value)
    }
}

class EyeCareForegroundService : Service() {

    companion object {
        const val ACTION_START_OR_SYNC = "com.optirest.eyecare.ACTION_START_OR_SYNC"
        const val ACTION_TOGGLE_PAUSE = "com.optirest.eyecare.ACTION_TOGGLE_PAUSE"
        const val ACTION_START_BREAK = "com.optirest.eyecare.ACTION_START_BREAK"
        const val ACTION_COMPLETE_BREAK = "com.optirest.eyecare.ACTION_COMPLETE_BREAK"
        const val ACTION_SNOOZE_BREAK = "com.optirest.eyecare.ACTION_SNOOZE_BREAK"
        const val ACTION_SKIP_BREAK = "com.optirest.eyecare.ACTION_SKIP_BREAK"
        const val ACTION_RESET_CYCLE = "com.optirest.eyecare.ACTION_RESET_CYCLE"
        const val ACTION_SYNC_CONFIG = "com.optirest.eyecare.ACTION_SYNC_CONFIG"
        const val ACTION_DISMISS_PROMPT = "com.optirest.eyecare.ACTION_DISMISS_PROMPT"
        const val ACTION_TOGGLE_SAMPLE_HISTORY = "com.optirest.eyecare.ACTION_TOGGLE_SAMPLE_HISTORY"

        private const val MONITOR_CHANNEL_ID = "optirest_monitor_channel"
        private const val BREAK_CHANNEL_ID = "optirest_break_alert_channel"
        private const val MONITOR_NOTIF_ID = 1001
        private const val BREAK_NOTIF_ID = 1002
    }

    private lateinit var repository: UsageRepository
    private lateinit var overlayManager: BlueLightOverlayManager
    private lateinit var breakOverlayManager: BreakCountdownOverlayManager
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    private var config: AppConfig = AppConfig()
    private var cycleElapsedMs: Long = 0L
    private var lastTickElapsedRealtime: Long = 0L
    private var isScreenInteractive: Boolean = true
    private var isDeviceUnlocked: Boolean = true
    private var isManualPaused: Boolean = false
    private var isOnBreak: Boolean = false
    private var breakRemainingSec: Int = 20
    private var breakTotalSec: Int = 20
    private var snoozeUntilElapsedRealtime: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    flushActiveDelta()
                    isScreenInteractive = false
                    isDeviceUnlocked = false
                    stopTickLoop()
                    if (isOnBreak) {
                        breakOverlayManager.dismiss()
                    }
                    emitState()
                    updateForegroundNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    isDeviceUnlocked = km?.isKeyguardLocked == false
                    lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                    if (isDeviceUnlocked) {
                        syncOverlayService()
                        if (isOnBreak && config.showBreakModal) {
                            breakOverlayManager.show(breakRemainingSec, breakTotalSec, config.workMinutes)
                        } else {
                            startTickLoopIfActive()
                        }
                    }
                    emitState()
                    updateForegroundNotification()
                }

                Intent.ACTION_USER_PRESENT, Intent.ACTION_USER_UNLOCKED -> {
                    isScreenInteractive = true
                    isDeviceUnlocked = true
                    lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                    syncOverlayService()
                    if (isOnBreak && config.showBreakModal) {
                        breakOverlayManager.show(breakRemainingSec, breakTotalSec, config.workMinutes)
                    } else {
                        startTickLoopIfActive()
                    }
                    emitState()
                    updateForegroundNotification()
                }
            }
        }
    }

    private val activeTickRunnable = object : Runnable {
        override fun run() {
            if (isCurrentlyCounting()) {
                flushActiveDelta()
                checkBreakTrigger()
                if (!isOnBreak) {
                    syncOverlayService()
                    emitState()
                    updateForegroundNotification()
                    val delayMs = if (config.batterySaverMode) 5000L else 1000L
                    handler.postDelayed(this, delayMs)
                }
            }
        }
    }

    private val breakCountdownRunnable = object : Runnable {
        override fun run() {
            if (!isOnBreak) return
            if (breakRemainingSec > 1) {
                breakRemainingSec -= 1
                if (isScreenInteractive && isDeviceUnlocked && config.showBreakModal) {
                    if (!breakOverlayManager.isShowing) {
                        breakOverlayManager.show(breakRemainingSec, breakTotalSec, config.workMinutes)
                    } else {
                        breakOverlayManager.updateCountdown(breakRemainingSec, breakTotalSec)
                    }
                }
                emitState()
                updateForegroundNotification()
                if (!breakOverlayManager.isShowing && config.notificationsEnabled) {
                    dispatchBreakAlertNotification(silentUpdate = true)
                }
                handler.postDelayed(this, 1000L)
            } else {
                completeBreak()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = UsageRepository(this)
        overlayManager = BlueLightOverlayManager(this)
        breakOverlayManager = BreakCountdownOverlayManager(
            context = this,
            onFinishEarly = { completeBreak() },
            onSnooze = { snoozeBreak() },
            onSkip = { skipBreak() }
        )
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()

        config = repository.loadConfig()
        cycleElapsedMs = repository.loadCycleElapsedMs()

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isScreenInteractive = pm?.isInteractive ?: true
        isDeviceUnlocked = km?.isKeyguardLocked == false
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        promoteToForeground()

        if (isScreenInteractive && isDeviceUnlocked) {
            syncOverlayService()
        }

        startTickLoopIfActive()
        emitState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        when (intent?.action) {
            ACTION_SYNC_CONFIG -> {
                config = repository.loadConfig()
                syncOverlayService()
                startTickLoopIfActive()
                emitState()
                updateForegroundNotification()
            }

            ACTION_TOGGLE_PAUSE -> {
                if (!isManualPaused) {
                    flushActiveDelta()
                }
                isManualPaused = !isManualPaused
                lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                if (isManualPaused) stopTickLoop() else startTickLoopIfActive()
                emitState()
                updateForegroundNotification()
            }

            ACTION_START_BREAK -> startBreak(triggeredAutomatically = false)
            ACTION_COMPLETE_BREAK -> completeBreak()
            ACTION_SNOOZE_BREAK -> snoozeBreak()
            ACTION_SKIP_BREAK -> skipBreak()
            ACTION_DISMISS_PROMPT -> {
                ServiceStateHolder.update { it.copy(breakDueModalOpen = false) }
            }

            ACTION_TOGGLE_SAMPLE_HISTORY -> {
                repository.toggle14DaySampleHistory()
                emitState()
            }

            ACTION_RESET_CYCLE -> {
                cycleElapsedMs = 0L
                snoozeUntilElapsedRealtime = 0L
                repository.saveCycleElapsedMs(0L)
                lastTickElapsedRealtime = SystemClock.elapsedRealtime()
                emitState()
                updateForegroundNotification()
            }

            else -> {
                config = repository.loadConfig()
                syncOverlayService()
                startTickLoopIfActive()
                emitState()
                updateForegroundNotification()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (config.autoStartOnBoot) {
            scheduleSelfRestart()
        }
    }

    override fun onDestroy() {
        flushActiveDelta()
        stopTickLoop()
        handler.removeCallbacks(breakCountdownRunnable)
        breakOverlayManager.dismiss()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        ServiceStateHolder.update {
            it.copy(
                isServiceRunning = false,
                isBreakOverlayVisible = false
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        try {
            val notif = buildMonitorNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    MONITOR_NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(MONITOR_NOTIF_ID, notif)
            }
        } catch (_: Exception) {
        }
    }

    private fun scheduleSelfRestart() {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val restartIntent = Intent(applicationContext, BootCompletedReceiver::class.java).apply {
                action = BootCompletedReceiver.ACTION_RESTART_SERVICE
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                702,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 3_000L,
                pi
            )
        } catch (_: Exception) {
        }
    }

    private fun isCurrentlyCounting(): Boolean {
        return isScreenInteractive && isDeviceUnlocked && !isManualPaused && !isOnBreak
    }

    private fun syncOverlayService() {
        val scheduledEvening = config.autoEveningSchedule &&
            repository.isWithinTimeWindow(config.sunsetStart, config.morningEnd)
        overlayManager.syncWithService(config, scheduledEvening)
    }

    private fun startTickLoopIfActive() {
        handler.removeCallbacks(activeTickRunnable)
        if (isCurrentlyCounting()) {
            lastTickElapsedRealtime = SystemClock.elapsedRealtime()
            val delayMs = if (config.batterySaverMode) 5000L else 1000L
            handler.postDelayed(activeTickRunnable, delayMs)
        }
    }

    private fun stopTickLoop() {
        handler.removeCallbacks(activeTickRunnable)
    }

    private fun flushActiveDelta() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickElapsedRealtime).coerceIn(0L, 15_000L)
        lastTickElapsedRealtime = now
        if (delta > 0L && isCurrentlyCounting()) {
            cycleElapsedMs += delta
            repository.addActiveScreenDelta(delta, cycleElapsedMs, config)
        }
    }

    private fun checkBreakTrigger() {
        val workMs = config.workMinutes * 60_000L
        val now = SystemClock.elapsedRealtime()
        if (cycleElapsedMs >= workMs && !isOnBreak && now >= snoozeUntilElapsedRealtime) {
            // Immediately start the 20-second countdown automatically and display it on screen over all apps
            startBreak(triggeredAutomatically = true)
        }
    }

    private fun startBreak(triggeredAutomatically: Boolean) {
        flushActiveDelta()
        stopTickLoop()
        isOnBreak = true
        breakTotalSec = config.breakSeconds.coerceAtLeast(5)
        breakRemainingSec = breakTotalSec

        val inQuietHours = config.quietHoursEnabled &&
            repository.isWithinTimeWindow(config.quietStart, config.quietEnd)

        val overlayShown = if (config.showBreakModal && isScreenInteractive && isDeviceUnlocked) {
            breakOverlayManager.show(breakRemainingSec, breakTotalSec, config.workMinutes)
        } else {
            false
        }

        if (triggeredAutomatically && !inQuietHours) {
            if (config.vibrateEnabled) {
                triggerHapticTap()
            }
            if (!overlayShown) {
                dispatchBreakAlertNotification(silentUpdate = false)
            } else if (config.soundEnabled) {
                playGentleBreakTone()
            }
        } else {
            notificationManager.cancel(BREAK_NOTIF_ID)
        }

        ServiceStateHolder.update {
            it.copy(
                isOnBreak = true,
                breakDueModalOpen = !overlayShown,
                isBreakOverlayVisible = overlayShown,
                breakRemainingSec = breakRemainingSec
            )
        }

        handler.removeCallbacks(breakCountdownRunnable)
        handler.postDelayed(breakCountdownRunnable, 1000L)
        updateForegroundNotification()
    }

    private fun completeBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        breakOverlayManager.dismiss()
        isOnBreak = false
        cycleElapsedMs = 0L
        snoozeUntilElapsedRealtime = 0L
        repository.recordBreakCompleted(config)
        val inQuietHours = config.quietHoursEnabled &&
            repository.isWithinTimeWindow(config.quietStart, config.quietEnd)
        if (!inQuietHours && config.vibrateEnabled) {
            triggerHapticTap()
        }
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        ServiceStateHolder.update {
            it.copy(
                isOnBreak = false,
                breakDueModalOpen = false,
                isBreakOverlayVisible = false,
                breakRemainingSec = config.breakSeconds
            )
        }
        startTickLoopIfActive()
        emitState()
        updateForegroundNotification()
    }

    private fun snoozeBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        breakOverlayManager.dismiss()
        isOnBreak = false
        snoozeUntilElapsedRealtime = SystemClock.elapsedRealtime() + 5 * 60_000L
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        ServiceStateHolder.update {
            it.copy(
                isOnBreak = false,
                breakDueModalOpen = false,
                isBreakOverlayVisible = false
            )
        }
        startTickLoopIfActive()
        emitState()
        updateForegroundNotification()
    }

    private fun skipBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        breakOverlayManager.dismiss()
        isOnBreak = false
        cycleElapsedMs = 0L
        snoozeUntilElapsedRealtime = 0L
        repository.recordBreakSkipped(config)
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        ServiceStateHolder.update {
            it.copy(
                isOnBreak = false,
                breakDueModalOpen = false,
                isBreakOverlayVisible = false
            )
        }
        startTickLoopIfActive()
        emitState()
        updateForegroundNotification()
    }

    private fun emitState() {
        val today = repository.loadDailyRecord()
        val last14 = repository.loadLastNDays(14)
        val breakdown = repository.computeStrainBreakdown(today, cycleElapsedMs, config)
        val usageStatsMs = BatteryOptimizationHelper.queryTodayDeviceForegroundMs(this)
        val overlayVisible = breakOverlayManager.isShowing
        ServiceStateHolder.update { prev ->
            prev.copy(
                isServiceRunning = true,
                isScreenOnAndUnlocked = isScreenInteractive && isDeviceUnlocked,
                isManualPaused = isManualPaused,
                isOnBreak = isOnBreak,
                isBreakOverlayVisible = overlayVisible,
                breakRemainingSec = breakRemainingSec,
                cycleElapsedMs = cycleElapsedMs,
                todayRecord = today,
                systemUsageStatsMs = usageStatsMs,
                strainBreakdown = breakdown,
                last14Days = last14,
                hasSampleData = repository.hasSampleHistory(),
                hasPastData = repository.hasPastNonTodayData(),
                config = config
            )
        }
    }

    private fun createNotificationChannels() {
        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Screen-On Eye Care Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status showing active screen-on tracking"
            setShowBadge(false)
        }

        val breakChannel = NotificationChannel(
            BREAK_CHANNEL_ID,
            "20-20-20 Break Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live countdown alerts when resting your eyes for 20 seconds"
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(monitorChannel)
        notificationManager.createNotificationChannel(breakChannel)
    }

    private fun buildMonitorNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val workMs = config.workMinutes * 60_000L
        val remainingMin = ((workMs - cycleElapsedMs).coerceAtLeast(0L) / 60_000L) + 1L

        val statusText = when {
            isOnBreak -> "20-20-20 Eye Rest counting down: ${breakRemainingSec}s left"
            isManualPaused -> "Tracking paused manually"
            !isScreenInteractive || !isDeviceUnlocked -> "Screen off — timer paused (audio excluded)"
            else -> "Display On · Next 20-20-20 break in ~${remainingMin}m"
        }

        val takeBreakIntent = PendingIntent.getBroadcast(
            this,
            10,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_START_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_notification)
            .setContentTitle("OptiRest Active")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Take Break", takeBreakIntent)
            .build()
    }

    private fun updateForegroundNotification() {
        notificationManager.notify(MONITOR_NOTIF_ID, buildMonitorNotification())
    }

    private fun dispatchBreakAlertNotification(silentUpdate: Boolean) {
        if (!config.notificationsEnabled) return

        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val donePi = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_COMPLETE_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozePi = PendingIntent.getBroadcast(
            this,
            3,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_SNOOZE_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, BREAK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_notification)
            .setContentTitle("20-20-20 Eye Rest · ${breakRemainingSec}s left")
            .setContentText("Look 20 feet away and blink slowly. Countdown running automatically.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(isOnBreak)
            .setAutoCancel(!isOnBreak)
            .setContentIntent(openIntent)
            .addAction(0, "Done", donePi)
            .addAction(0, "Snooze 5m", snoozePi)

        if (silentUpdate) {
            builder.setSilent(true)
        } else if (config.soundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        notificationManager.notify(BREAK_NOTIF_ID, builder.build())
    }

    private fun playGentleBreakTone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (_: Exception) {
        }
    }

    private fun triggerHapticTap() {
        try {
            val pattern = longArrayOf(0, 140, 90, 140)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {
        }
    }
}
