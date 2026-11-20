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
import android.hardware.display.DisplayManager
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
import android.view.Display
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

/**
 * High-precision, zero-drift screen-on eye care foreground service.
 *
 * Accuracy architecture:
 * 1. Anchors active screen-on time to hardware monotonic SystemClock.elapsedRealtime()
 *    with NO destructive per-tick delta clipping, so zero milliseconds are ever dropped
 *    even under heavy foreground apps or temporary OS CPU throttling.
 * 2. Schedules an AlarmManager wake backup for the exact remaining work cycle duration
 *    so the 20-minute break fires precisely on time even if Handler callbacks are delayed.
 * 3. Runs the 20-second break countdown against a monotonic deadline (breakEndElapsedRealtime)
 *    sampled at 200ms so the on-screen MM:SS timer ticks cleanly without skipping or drifting.
 * 4. Caches daily records and UsageStats queries in memory (persisting every 5s and on every
 *    lifecycle boundary) to keep main-thread execution silky smooth with zero jank.
 */
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
        const val ACTION_BREAK_ALARM_TRIGGER = "com.optirest.eyecare.ACTION_BREAK_ALARM_TRIGGER"

        private const val MONITOR_CHANNEL_ID = "optirest_monitor_channel"
        private const val BREAK_CHANNEL_ID = "optirest_break_alert_channel"
        private const val MONITOR_NOTIF_ID = 1001
        private const val BREAK_NOTIF_ID = 1002
        private const val BREAK_ALARM_REQ_CODE = 705

        private const val PERSIST_INTERVAL_MS = 5_000L
        private const val USAGE_STATS_POLL_INTERVAL_MS = 30_000L
    }

    private lateinit var repository: UsageRepository
    private lateinit var overlayManager: BlueLightOverlayManager
    private lateinit var breakOverlayManager: BreakCountdownOverlayManager
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    private var config: AppConfig = AppConfig()

    // Exact monotonic clock tracking for active screen-on cycle (zero drift, zero lost time)
    private var committedCycleMs: Long = 0L
    private var activeAnchorElapsedRealtime: Long = 0L
    private var lastCommittedElapsedRealtime: Long = 0L
    private var lastPersistElapsedRealtime: Long = 0L

    // Cached records to avoid main-thread SharedPreferences/IPC churn every second
    private var cachedTodayKey: String = ""
    private var cachedTodayRecord: DailyUsageRecord? = null
    private var cachedLast14Days: List<DailyUsageRecord> = emptyList()
    private var cachedHasSampleData: Boolean = false
    private var cachedHasPastData: Boolean = false
    private var cachedSystemUsageStatsMs: Long = 0L
    private var lastUsageStatsQueryRealtime: Long = 0L
    private var lastNotifStatusText: String = ""

    private var isScreenInteractive: Boolean = true
    private var isDeviceUnlocked: Boolean = true
    private var isManualPaused: Boolean = false

    // Exact monotonic deadline for 20-second break countdown
    private var isOnBreak: Boolean = false
    private var breakRemainingSec: Int = 20
    private var breakEndElapsedRealtime: Long = 0L
    private var snoozeUntilElapsedRealtime: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    commitActiveElapsed(forcePersist = true)
                    isScreenInteractive = false
                    isDeviceUnlocked = false
                    activeAnchorElapsedRealtime = 0L
                    stopTickLoop()
                    cancelBreakTriggerAlarm()
                    if (isOnBreak) {
                        breakOverlayManager.dismiss()
                    }
                    startUnlockWatchdogIfWaiting()
                    emitState()
                    updateForegroundNotification(force = true)
                }

                Intent.ACTION_SCREEN_ON -> {
                    reconcileHardwareDisplayState(forceNotify = true)
                }

                Intent.ACTION_USER_PRESENT, Intent.ACTION_USER_UNLOCKED -> {
                    isScreenInteractive = true
                    isDeviceUnlocked = true
                    stopUnlockWatchdog()
                    onScreenBecameActiveAndUnlocked()
                }
            }
        }
    }

    /**
     * Self-healing hardware state watchdog for OEM ROMs (Honor MagicOS 8/9, Huawei EMUI, Xiaomi)
     * that occasionally suppress ACTION_USER_PRESENT after biometric/face unlock or cold boot.
     * Polls hardware PowerManager + DisplayManager + KeyguardManager only while waiting to unlock,
     * and stops immediately once active counting begins.
     */
    private val unlockWatchdogRunnable = object : Runnable {
        override fun run() {
            if (isManualPaused || isOnBreak || isCurrentlyCounting()) return
            reconcileHardwareDisplayState(forceNotify = false)
            if (!isCurrentlyCounting() && !isManualPaused && !isOnBreak) {
                val nextDelay = if (isScreenInteractive) 1_500L else 5_000L
                handler.postDelayed(this, nextDelay)
            }
        }
    }

    private val activeTickRunnable = object : Runnable {
        override fun run() {
            if (!isCurrentlyCounting()) return

            // Double-check live hardware display/keyguard state so off-screen time is never counted
            val (liveScreenOn, liveUnlocked) = readLiveHardwareDisplayState()
            if (!liveScreenOn || !liveUnlocked) {
                commitActiveElapsed(forcePersist = true)
                isScreenInteractive = liveScreenOn
                isDeviceUnlocked = liveUnlocked
                activeAnchorElapsedRealtime = 0L
                cancelBreakTriggerAlarm()
                startUnlockWatchdogIfWaiting()
                emitState()
                updateForegroundNotification(force = true)
                return
            }

            val now = SystemClock.elapsedRealtime()
            val shouldPersist = (now - lastPersistElapsedRealtime) >= PERSIST_INTERVAL_MS
            commitActiveElapsed(forcePersist = shouldPersist)

            if (checkBreakTrigger()) {
                return
            }

            syncOverlayService(force = false)
            refreshUsageStatsIfDue(now)
            emitState()
            updateForegroundNotification(force = false)

            // Align next tick cleanly to the next second boundary for smooth 1Hz display
            val delayMs = if (config.batterySaverMode) {
                val remMs = (config.workMinutes * 60_000L - currentCycleElapsedMs()).coerceAtLeast(250L)
                minOf(5_000L, remMs)
            } else {
                val remMs = (config.workMinutes * 60_000L - currentCycleElapsedMs()).coerceAtLeast(100L)
                minOf(1_000L, remMs)
            }
            handler.postDelayed(this, delayMs)
        }
    }

    private val breakCountdownRunnable = object : Runnable {
        override fun run() {
            if (!isOnBreak) return

            val now = SystemClock.elapsedRealtime()
            val remainingMs = (breakEndElapsedRealtime - now).coerceAtLeast(0L)
            if (remainingMs <= 0L) {
                completeBreak()
                return
            }

            val nextSec = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
            if (nextSec != breakRemainingSec) {
                breakRemainingSec = nextSec
                if (isScreenInteractive && isDeviceUnlocked && config.showBreakModal) {
                    if (!breakOverlayManager.isShowing) {
                        breakOverlayManager.show(breakRemainingSec)
                    } else {
                        breakOverlayManager.updateCountdown(breakRemainingSec)
                    }
                }
                emitState()
                updateForegroundNotification(force = true)
                if (!breakOverlayManager.isShowing && config.notificationsEnabled) {
                    dispatchBreakAlertNotification(silentUpdate = true)
                }
            }

            val nextDelay = minOf(200L, remainingMs)
            handler.postDelayed(this, nextDelay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = UsageRepository(this)
        overlayManager = BlueLightOverlayManager(this)
        breakOverlayManager = BreakCountdownOverlayManager(
            context = this,
            onSnooze = { snoozeBreak() },
            onSkip = { skipBreak() }
        )
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()

        config = repository.loadConfig()
        repository.resetCycleIfDeviceRebooted(this)
        committedCycleMs = repository.loadCycleElapsedMs()
        reloadRepositoryCaches()

        val (liveScreenOn, liveUnlocked) = readLiveHardwareDisplayState()
        isScreenInteractive = liveScreenOn
        isDeviceUnlocked = liveUnlocked

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        // RECEIVER_EXPORTED is required on Android 14+ / Honor MagicOS so system-sent
        // ACTION_USER_PRESENT and ACTION_SCREEN_ON broadcasts are reliably delivered.
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        promoteToForeground()

        if (isCurrentlyCounting()) {
            beginActiveSegment()
            syncOverlayService(force = true)
            startTickLoopIfActive()
            scheduleBreakTriggerAlarm()
        } else {
            startUnlockWatchdogIfWaiting()
        }
        emitState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        when (intent?.action) {
            ACTION_BREAK_ALARM_TRIGGER -> {
                reconcileHardwareDisplayState(forceNotify = false)
                if (isCurrentlyCounting()) {
                    commitActiveElapsed(forcePersist = true)
                    if (!checkBreakTrigger()) {
                        scheduleBreakTriggerAlarm()
                        startTickLoopIfActive()
                        emitState()
                    }
                }
            }

            ACTION_SYNC_CONFIG -> {
                commitActiveElapsed(forcePersist = true)
                config = repository.loadConfig()
                reloadRepositoryCaches()
                syncOverlayService(force = true)
                if (isCurrentlyCounting()) {
                    if (!checkBreakTrigger()) {
                        scheduleBreakTriggerAlarm()
                        startTickLoopIfActive()
                    }
                }
                emitState()
                updateForegroundNotification(force = true)
            }

            ACTION_TOGGLE_PAUSE -> {
                if (!isManualPaused) {
                    commitActiveElapsed(forcePersist = true)
                    isManualPaused = true
                    activeAnchorElapsedRealtime = 0L
                    stopTickLoop()
                    stopUnlockWatchdog()
                    cancelBreakTriggerAlarm()
                } else {
                    isManualPaused = false
                    reconcileHardwareDisplayState(forceNotify = false)
                    if (isCurrentlyCounting()) {
                        if (activeAnchorElapsedRealtime == 0L) {
                            beginActiveSegment()
                        }
                        startTickLoopIfActive()
                        scheduleBreakTriggerAlarm()
                    } else {
                        startUnlockWatchdogIfWaiting()
                    }
                }
                emitState()
                updateForegroundNotification(force = true)
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
                reloadRepositoryCaches()
                emitState()
            }

            ACTION_RESET_CYCLE -> {
                commitActiveElapsed(forcePersist = true)
                committedCycleMs = 0L
                snoozeUntilElapsedRealtime = 0L
                repository.saveCycleElapsedMs(0L)
                if (isCurrentlyCounting()) {
                    beginActiveSegment()
                    scheduleBreakTriggerAlarm()
                    startTickLoopIfActive()
                }
                emitState()
                updateForegroundNotification(force = true)
            }

            else -> {
                config = repository.loadConfig()
                reloadRepositoryCaches()
                reconcileHardwareDisplayState(forceNotify = true)
                syncOverlayService(force = true)
                if (isCurrentlyCounting()) {
                    if (activeAnchorElapsedRealtime == 0L) {
                        beginActiveSegment()
                    }
                    startTickLoopIfActive()
                    scheduleBreakTriggerAlarm()
                } else {
                    startUnlockWatchdogIfWaiting()
                }
                emitState()
                updateForegroundNotification(force = true)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        commitActiveElapsed(forcePersist = true)
        if (config.autoStartOnBoot) {
            scheduleSelfRestart()
        }
    }

    override fun onDestroy() {
        commitActiveElapsed(forcePersist = true)
        stopTickLoop()
        stopUnlockWatchdog()
        cancelBreakTriggerAlarm()
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

    private fun readLiveHardwareDisplayState(): Pair<Boolean, Boolean> {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val defaultDisplayState = try {
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN
        } catch (_: Exception) {
            Display.STATE_UNKNOWN
        }

        val screenOn = (pm?.isInteractive == true) || (defaultDisplayState == Display.STATE_ON)
        val unlocked = km?.isKeyguardLocked == false
        return Pair(screenOn, unlocked)
    }

    private fun reconcileHardwareDisplayState(forceNotify: Boolean) {
        val wasCounting = isCurrentlyCounting()
        val (liveScreenOn, liveUnlocked) = readLiveHardwareDisplayState()
        val stateChanged = (liveScreenOn != isScreenInteractive) || (liveUnlocked != isDeviceUnlocked)

        if (!stateChanged && !forceNotify) return

        if (wasCounting && (!liveScreenOn || !liveUnlocked)) {
            commitActiveElapsed(forcePersist = true)
            isScreenInteractive = liveScreenOn
            isDeviceUnlocked = liveUnlocked
            activeAnchorElapsedRealtime = 0L
            stopTickLoop()
            cancelBreakTriggerAlarm()
            startUnlockWatchdogIfWaiting()
            emitState()
            updateForegroundNotification(force = true)
            return
        }

        isScreenInteractive = liveScreenOn
        isDeviceUnlocked = liveUnlocked

        if (isScreenInteractive && isDeviceUnlocked) {
            stopUnlockWatchdog()
            if (!wasCounting) {
                onScreenBecameActiveAndUnlocked()
            } else if (forceNotify) {
                emitState()
                updateForegroundNotification(force = true)
            }
        } else {
            startUnlockWatchdogIfWaiting()
            if (stateChanged || forceNotify) {
                emitState()
                updateForegroundNotification(force = true)
            }
        }
    }

    private fun startUnlockWatchdogIfWaiting() {
        handler.removeCallbacks(unlockWatchdogRunnable)
        if (!isManualPaused && !isOnBreak && !isCurrentlyCounting()) {
            val delay = if (isScreenInteractive) 1_500L else 5_000L
            handler.postDelayed(unlockWatchdogRunnable, delay)
        }
    }

    private fun stopUnlockWatchdog() {
        handler.removeCallbacks(unlockWatchdogRunnable)
    }

    private fun onScreenBecameActiveAndUnlocked() {
        stopUnlockWatchdog()
        syncOverlayService(force = true)
        if (isOnBreak) {
            val remainingMs = (breakEndElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (remainingMs <= 0L) {
                completeBreak()
            } else {
                breakRemainingSec = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                if (config.showBreakModal) {
                    breakOverlayManager.show(breakRemainingSec)
                }
                handler.removeCallbacks(breakCountdownRunnable)
                handler.postDelayed(breakCountdownRunnable, minOf(200L, remainingMs))
                emitState()
                updateForegroundNotification(force = true)
            }
        } else if (isCurrentlyCounting()) {
            beginActiveSegment()
            if (!checkBreakTrigger()) {
                startTickLoopIfActive()
                scheduleBreakTriggerAlarm()
                emitState()
                updateForegroundNotification(force = true)
            }
        } else {
            emitState()
            updateForegroundNotification(force = true)
        }
    }

    private fun beginActiveSegment() {
        val now = SystemClock.elapsedRealtime()
        activeAnchorElapsedRealtime = now
        lastCommittedElapsedRealtime = now
        lastPersistElapsedRealtime = now
    }

    private fun currentCycleElapsedMs(): Long {
        if (!isCurrentlyCounting() || activeAnchorElapsedRealtime == 0L) {
            return committedCycleMs
        }
        val extra = (SystemClock.elapsedRealtime() - lastCommittedElapsedRealtime).coerceAtLeast(0L)
        return committedCycleMs + extra
    }

    /**
     * Commits exact elapsed monotonic time since lastCommittedElapsedRealtime with ZERO clipping
     * so no active screen-on time is ever lost. Persists to SharedPreferences on schedule or
     * lifecycle boundaries so main-thread execution stays lag-free.
     */
    private fun commitActiveElapsed(forcePersist: Boolean) {
        if (!isCurrentlyCounting() || activeAnchorElapsedRealtime == 0L) return

        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastCommittedElapsedRealtime).coerceAtLeast(0L)
        if (delta <= 0L && (!forcePersist || pendingUnpersistedMs <= 0L)) return

        // Handle midnight date rollover cleanly without losing time
        val currentDateKey = repository.todayKey()
        if (cachedTodayKey != currentDateKey) {
            flushPendingUnpersisted()
            cachedTodayKey = currentDateKey
            cachedTodayRecord = repository.loadDailyRecord(currentDateKey)
            cachedLast14Days = repository.loadLastNDays(14)
        }

        if (delta > 0L) {
            committedCycleMs += delta
            pendingUnpersistedMs += delta
            lastCommittedElapsedRealtime = now
        }

        if (forcePersist || pendingUnpersistedMs >= PERSIST_INTERVAL_MS) {
            flushPendingUnpersisted()
        } else if (delta > 0L) {
            // Update in-memory snapshot for smooth 1Hz UI display with zero disk I/O
            val base = cachedTodayRecord ?: repository.loadDailyRecord(currentDateKey)
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            val nextHourly = base.hourlyMs.copyOf()
            nextHourly[hour] = nextHourly[hour] + delta
            val updated = base.copy(
                activeScreenMs = base.activeScreenMs + delta,
                maxContinuousMs = maxOf(base.maxContinuousMs, committedCycleMs),
                hourlyMs = nextHourly,
                isSample = false
            )
            val score = repository.computeStrainBreakdown(updated, committedCycleMs, config).totalScore
            cachedTodayRecord = updated.copy(avgStrainScore = score)
        }
    }

    private var pendingUnpersistedMs: Long = 0L

    private fun flushPendingUnpersisted() {
        val toFlush = pendingUnpersistedMs
        pendingUnpersistedMs = 0L
        if (toFlush > 0L) {
            cachedTodayRecord = repository.addActiveScreenDelta(toFlush, committedCycleMs, config)
            cachedLast14Days = repository.loadLastNDays(14)
            lastPersistElapsedRealtime = SystemClock.elapsedRealtime()
        } else {
            repository.saveCycleElapsedMs(committedCycleMs)
        }
    }

    private fun reloadRepositoryCaches() {
        flushPendingUnpersisted()
        cachedTodayKey = repository.todayKey()
        cachedTodayRecord = repository.loadDailyRecord(cachedTodayKey)
        cachedLast14Days = repository.loadLastNDays(14)
        cachedHasSampleData = repository.hasSampleHistory()
        cachedHasPastData = repository.hasPastNonTodayData()
        cachedSystemUsageStatsMs = BatteryOptimizationHelper.queryTodayDeviceForegroundMs(this)
        lastUsageStatsQueryRealtime = SystemClock.elapsedRealtime()
    }

    private fun refreshUsageStatsIfDue(nowRealtime: Long) {
        if ((nowRealtime - lastUsageStatsQueryRealtime) >= USAGE_STATS_POLL_INTERVAL_MS) {
            cachedSystemUsageStatsMs = BatteryOptimizationHelper.queryTodayDeviceForegroundMs(this)
            lastUsageStatsQueryRealtime = nowRealtime
        }
    }

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

    private fun scheduleBreakTriggerAlarm() {
        cancelBreakTriggerAlarm()
        if (!isCurrentlyCounting()) return
        val workMs = (config.workMinutes * 60_000L).coerceAtLeast(60_000L)
        val now = SystemClock.elapsedRealtime()
        val remainingWorkMs = (workMs - currentCycleElapsedMs()).coerceAtLeast(1_000L)
        val snoozeRemainingMs = (snoozeUntilElapsedRealtime - now).coerceAtLeast(0L)
        val triggerDelayMs = maxOf(remainingWorkMs, snoozeRemainingMs)
        val triggerAt = now + triggerDelayMs

        try {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, EyeCareForegroundService::class.java).apply {
                action = ACTION_BREAK_ALARM_TRIGGER
            }
            val pi = PendingIntent.getService(
                this,
                BREAK_ALARM_REQ_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (_: Exception) {
        }
    }

    private fun cancelBreakTriggerAlarm() {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, EyeCareForegroundService::class.java).apply {
                action = ACTION_BREAK_ALARM_TRIGGER
            }
            val pi = PendingIntent.getService(
                this,
                BREAK_ALARM_REQ_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
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

    private fun syncOverlayService(force: Boolean) {
        val scheduledEvening = config.autoEveningSchedule &&
            repository.isWithinTimeWindow(config.sunsetStart, config.morningEnd)
        overlayManager.syncWithService(config, scheduledEvening, force = force)
    }

    private fun startTickLoopIfActive() {
        handler.removeCallbacks(activeTickRunnable)
        if (isCurrentlyCounting()) {
            val delayMs = if (config.batterySaverMode) 5_000L else 1_000L
            handler.postDelayed(activeTickRunnable, delayMs)
        }
    }

    private fun stopTickLoop() {
        handler.removeCallbacks(activeTickRunnable)
    }

    private fun checkBreakTrigger(): Boolean {
        val workMs = (config.workMinutes * 60_000L).coerceAtLeast(60_000L)
        val now = SystemClock.elapsedRealtime()
        if (currentCycleElapsedMs() >= workMs && !isOnBreak && now >= snoozeUntilElapsedRealtime) {
            startBreak(triggeredAutomatically = true)
            return true
        }
        return false
    }

    private fun startBreak(triggeredAutomatically: Boolean) {
        commitActiveElapsed(forcePersist = true)
        flushPendingUnpersisted()
        stopTickLoop()
        cancelBreakTriggerAlarm()
        activeAnchorElapsedRealtime = 0L

        val totalSec = config.breakSeconds.coerceAtLeast(5)
        isOnBreak = true
        breakRemainingSec = totalSec
        breakEndElapsedRealtime = SystemClock.elapsedRealtime() + (totalSec * 1_000L)

        val inQuietHours = config.quietHoursEnabled &&
            repository.isWithinTimeWindow(config.quietStart, config.quietEnd)

        val overlayShown = if (config.showBreakModal && isScreenInteractive && isDeviceUnlocked) {
            breakOverlayManager.show(breakRemainingSec)
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
        handler.postDelayed(breakCountdownRunnable, 200L)
        updateForegroundNotification(force = true)
    }

    private fun completeBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        breakOverlayManager.dismiss()
        isOnBreak = false
        breakEndElapsedRealtime = 0L
        committedCycleMs = 0L
        pendingUnpersistedMs = 0L
        snoozeUntilElapsedRealtime = 0L

        cachedTodayRecord = repository.recordBreakCompleted(config)
        cachedLast14Days = repository.loadLastNDays(14)

        val inQuietHours = config.quietHoursEnabled &&
            repository.isWithinTimeWindow(config.quietStart, config.quietEnd)
        if (!inQuietHours && config.vibrateEnabled) {
            triggerHapticTap()
        }

        ServiceStateHolder.update {
            it.copy(
                isOnBreak = false,
                breakDueModalOpen = false,
                isBreakOverlayVisible = false,
                breakRemainingSec = config.breakSeconds
            )
        }
        if (isCurrentlyCounting()) {
            beginActiveSegment()
            startTickLoopIfActive()
            scheduleBreakTriggerAlarm()
        }
        emitState()
        updateForegroundNotification(force = true)
    }

    private fun snoozeBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        breakOverlayManager.dismiss()
        isOnBreak = false
        breakEndElapsedRealtime = 0L
        snoozeUntilElapsedRealtime = SystemClock.elapsedRealtime() + 5 * 60_000L

        ServiceStateHolder.update {
            it.copy(
                isOnBreak = false,
                breakDueModalOpen = false,
                isBreakOverlayVisible = false
            )
        }
        if (isCurrentlyCounting()) {
            beginActiveSegment()
            startTickLoopIfActive()
            scheduleBreakTriggerAlarm()
        }
        emitState()
        updateForegroundNotification(force = true)
    }

    private fun skipBreak() {
        handler.removeCallbacks(breakCountdownRunnable)
        notificationManager.cancel(BREAK_NOTIF_ID)
        breakOverlayManager.dismiss()
        isOnBreak = false
        breakEndElapsedRealtime = 0L
        committedCycleMs = 0L
        pendingUnpersistedMs = 0L
        snoozeUntilElapsedRealtime = 0L

        cachedTodayRecord = repository.recordBreakSkipped(config)
        cachedLast14Days = repository.loadLastNDays(14)

        ServiceStateHolder.update {
            it.copy(
                isOnBreak = false,
                breakDueModalOpen = false,
                isBreakOverlayVisible = false
            )
        }
        if (isCurrentlyCounting()) {
            beginActiveSegment()
            startTickLoopIfActive()
            scheduleBreakTriggerAlarm()
        }
        emitState()
        updateForegroundNotification(force = true)
    }

    private fun emitState() {
        val today = cachedTodayRecord ?: repository.loadDailyRecord()
        val liveCycleMs = currentCycleElapsedMs()
        val breakdown = repository.computeStrainBreakdown(today, liveCycleMs, config)
        val overlayVisible = breakOverlayManager.isShowing
        ServiceStateHolder.update { prev ->
            prev.copy(
                isServiceRunning = true,
                isScreenOnAndUnlocked = isScreenInteractive && isDeviceUnlocked,
                isManualPaused = isManualPaused,
                isOnBreak = isOnBreak,
                isBreakOverlayVisible = overlayVisible,
                breakRemainingSec = breakRemainingSec,
                cycleElapsedMs = liveCycleMs,
                todayRecord = today,
                systemUsageStatsMs = cachedSystemUsageStatsMs,
                strainBreakdown = breakdown,
                last14Days = cachedLast14Days,
                hasSampleData = cachedHasSampleData,
                hasPastData = cachedHasPastData,
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

    private fun computeMonitorStatusText(): String {
        val workMs = (config.workMinutes * 60_000L).coerceAtLeast(60_000L)
        val remainingMs = (workMs - currentCycleElapsedMs()).coerceAtLeast(0L)
        val totalRemSec = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val remMin = totalRemSec / 60L
        val remSec = totalRemSec % 60L
        val mmSs = "%02d:%02d".format(remMin, remSec)
        return when {
            isOnBreak -> "Time to Rest Your Eyes · %02d:%02d left".format(breakRemainingSec / 60, breakRemainingSec % 60)
            isManualPaused -> "Tracking paused manually"
            !isScreenInteractive || !isDeviceUnlocked -> "Screen locked — timer paused"
            else -> "Display On · Next 20-20-20 break in $mmSs"
        }
    }

    private fun buildMonitorNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = computeMonitorStatusText()
        lastNotifStatusText = statusText

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
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, "Take Break", takeBreakIntent)
            .build()
    }

    private fun updateForegroundNotification(force: Boolean) {
        val nextText = computeMonitorStatusText()
        if (!force && nextText == lastNotifStatusText) return
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

        val delayPi = PendingIntent.getBroadcast(
            this,
            3,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_SNOOZE_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipPi = PendingIntent.getBroadcast(
            this,
            4,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = ACTION_SKIP_BREAK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mmSs = "%02d:%02d".format(breakRemainingSec / 60, breakRemainingSec % 60)
        val builder = NotificationCompat.Builder(this, BREAK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eye_notification)
            .setContentTitle("Time to Rest Your Eyes · $mmSs")
            .setContentText("20-20-20 Rule: Look at an object at least 20 feet away and blink slowly.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(isOnBreak)
            .setAutoCancel(!isOnBreak)
            .setContentIntent(openIntent)
            .addAction(0, "Delay 5 Min", delayPi)
            .addAction(0, "Skip Break", skipPi)

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
