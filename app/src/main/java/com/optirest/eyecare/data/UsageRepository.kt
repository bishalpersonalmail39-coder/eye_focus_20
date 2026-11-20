package com.optirest.eyecare.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyUsageRecord(
    val dateKey: String,
    val activeScreenMs: Long,
    val breaksCompleted: Int,
    val breaksSkipped: Int,
    val maxContinuousMs: Long,
    val hourlyMs: LongArray,
    val avgStrainScore: Int,
    val isSample: Boolean = false
)

data class StrainBreakdown(
    val totalScore: Int,
    val continuousStretchPoints: Int,
    val dailyLoadPoints: Int,
    val compliancePenaltyPoints: Int,
    val blueLightPenaltyPoints: Int
)

data class AppConfig(
    val schedulePresetId: String = EyeGuardDefaults.DEFAULT_SCHEDULE_PRESET_ID,
    val workMinutes: Int = EyeGuardDefaults.DEFAULT_WORK_MINUTES,
    val breakSeconds: Int = EyeGuardDefaults.DEFAULT_BREAK_SECONDS,
    val dailyLimitMinutes: Int = EyeGuardDefaults.DEFAULT_DAILY_LIMIT_MINUTES,
    val autoStartOnBoot: Boolean = true,
    val batterySaverMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val showBreakModal: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStart: String = EyeGuardDefaults.DEFAULT_QUIET_START,
    val quietEnd: String = EyeGuardDefaults.DEFAULT_QUIET_END,
    val filterEnabled: Boolean = false,
    val filterPresetId: String = EyeGuardDefaults.DEFAULT_FILTER_PRESET_ID,
    val filterWarmthKelvin: Int = EyeGuardDefaults.DEFAULT_WARMTH_KELVIN,
    val filterExtraDimPercent: Int = EyeGuardDefaults.DEFAULT_EXTRA_DIM_PERCENT,
    val filterBlueCutPercent: Int = EyeGuardDefaults.DEFAULT_BLUE_CUT_PERCENT,
    val autoEveningSchedule: Boolean = false,
    val sunsetStart: String = EyeGuardDefaults.DEFAULT_SUNSET_START,
    val morningEnd: String = EyeGuardDefaults.DEFAULT_MORNING_END
)

class UsageRepository(context: Context) {
    private val prefs: SharedPreferences = resolveDeviceProtectedPrefs(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val PREFS_NAME = "optirest_native_prefs"

        private fun resolveDeviceProtectedPrefs(context: Context): SharedPreferences {
            val appCtx = context.applicationContext ?: context
            val dpCtx = appCtx.createDeviceProtectedStorageContext()
            val userManager = appCtx.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager?.isUserUnlocked != false) {
                try {
                    val dpPrefs = dpCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    if (!dpPrefs.getBoolean("migrated_from_ce", false)) {
                        dpCtx.moveSharedPreferencesFrom(appCtx, PREFS_NAME)
                        dpCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("migrated_from_ce", true)
                            .apply()
                    }
                } catch (_: Exception) {
                }
            }
            return dpCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun todayKey(): String = dateFormat.format(Date())

    fun loadConfig(): AppConfig {
        return AppConfig(
            schedulePresetId = prefs.getString("schedule_preset_id", EyeGuardDefaults.DEFAULT_SCHEDULE_PRESET_ID)
                ?: EyeGuardDefaults.DEFAULT_SCHEDULE_PRESET_ID,
            workMinutes = prefs.getInt("work_minutes", EyeGuardDefaults.DEFAULT_WORK_MINUTES).coerceIn(5, 90),
            breakSeconds = prefs.getInt("break_seconds", EyeGuardDefaults.DEFAULT_BREAK_SECONDS).coerceIn(10, 300),
            dailyLimitMinutes = prefs.getInt("daily_limit_minutes", EyeGuardDefaults.DEFAULT_DAILY_LIMIT_MINUTES).coerceIn(60, 720),
            autoStartOnBoot = prefs.getBoolean("auto_start_boot", true),
            batterySaverMode = prefs.getBoolean("battery_saver_mode", false),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            vibrateEnabled = prefs.getBoolean("vibrate_enabled", true),
            showBreakModal = prefs.getBoolean("show_break_modal", true),
            quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", false),
            quietStart = prefs.getString("quiet_start", EyeGuardDefaults.DEFAULT_QUIET_START)
                ?: EyeGuardDefaults.DEFAULT_QUIET_START,
            quietEnd = prefs.getString("quiet_end", EyeGuardDefaults.DEFAULT_QUIET_END)
                ?: EyeGuardDefaults.DEFAULT_QUIET_END,
            filterEnabled = prefs.getBoolean("filter_enabled", false),
            filterPresetId = prefs.getString("filter_preset_id", EyeGuardDefaults.DEFAULT_FILTER_PRESET_ID)
                ?: EyeGuardDefaults.DEFAULT_FILTER_PRESET_ID,
            filterWarmthKelvin = prefs.getInt("filter_warmth_kelvin", EyeGuardDefaults.DEFAULT_WARMTH_KELVIN).coerceIn(1800, 6500),
            filterExtraDimPercent = prefs.getInt("filter_extra_dim_percent", EyeGuardDefaults.DEFAULT_EXTRA_DIM_PERCENT).coerceIn(0, 65),
            filterBlueCutPercent = prefs.getInt("filter_blue_cut_percent", EyeGuardDefaults.DEFAULT_BLUE_CUT_PERCENT).coerceIn(0, 100),
            autoEveningSchedule = prefs.getBoolean("auto_evening_schedule", false),
            sunsetStart = prefs.getString("sunset_start", EyeGuardDefaults.DEFAULT_SUNSET_START)
                ?: EyeGuardDefaults.DEFAULT_SUNSET_START,
            morningEnd = prefs.getString("morning_end", EyeGuardDefaults.DEFAULT_MORNING_END)
                ?: EyeGuardDefaults.DEFAULT_MORNING_END
        )
    }

    fun saveConfig(config: AppConfig) {
        prefs.edit()
            .putString("schedule_preset_id", config.schedulePresetId)
            .putInt("work_minutes", config.workMinutes)
            .putInt("break_seconds", config.breakSeconds)
            .putInt("daily_limit_minutes", config.dailyLimitMinutes)
            .putBoolean("auto_start_boot", config.autoStartOnBoot)
            .putBoolean("battery_saver_mode", config.batterySaverMode)
            .putBoolean("notifications_enabled", config.notificationsEnabled)
            .putBoolean("sound_enabled", config.soundEnabled)
            .putBoolean("vibrate_enabled", config.vibrateEnabled)
            .putBoolean("show_break_modal", config.showBreakModal)
            .putBoolean("quiet_hours_enabled", config.quietHoursEnabled)
            .putString("quiet_start", config.quietStart)
            .putString("quiet_end", config.quietEnd)
            .putBoolean("filter_enabled", config.filterEnabled)
            .putString("filter_preset_id", config.filterPresetId)
            .putInt("filter_warmth_kelvin", config.filterWarmthKelvin)
            .putInt("filter_extra_dim_percent", config.filterExtraDimPercent)
            .putInt("filter_blue_cut_percent", config.filterBlueCutPercent)
            .putBoolean("auto_evening_schedule", config.autoEveningSchedule)
            .putString("sunset_start", config.sunsetStart)
            .putString("morning_end", config.morningEnd)
            .apply()
    }

    fun loadCycleElapsedMs(): Long {
        val savedDate = prefs.getString("cycle_date", "")
        if (savedDate != todayKey()) return 0L
        return prefs.getLong("cycle_elapsed_ms", 0L).coerceAtLeast(0L)
    }

    fun saveCycleElapsedMs(ms: Long) {
        prefs.edit()
            .putString("cycle_date", todayKey())
            .putLong("cycle_elapsed_ms", ms.coerceAtLeast(0L))
            .apply()
    }

    /**
     * Detects an actual phone power-off/restart using Settings.Global.BOOT_COUNT.
     * Resets only the continuous 20-minute stretch once per reboot (preserving today's
     * total screen time and break history) so stale pre-reboot stretch time is never carried over.
     */
    fun resetCycleIfDeviceRebooted(context: Context): Boolean {
        return try {
            val currentBootCount = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1
            )
            if (currentBootCount < 0) return false
            val savedBootCount = prefs.getInt("last_seen_boot_count", -1)
            if (savedBootCount == -1) {
                prefs.edit().putInt("last_seen_boot_count", currentBootCount).apply()
                false
            } else if (savedBootCount != currentBootCount) {
                prefs.edit()
                    .putInt("last_seen_boot_count", currentBootCount)
                    .putString("cycle_date", todayKey())
                    .putLong("cycle_elapsed_ms", 0L)
                    .apply()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun loadDailyRecord(dateKey: String = todayKey()): DailyUsageRecord {
        val rawMap = loadAllRecordsJson()
        val obj = rawMap.optJSONObject(dateKey) ?: return emptyRecord(dateKey)
        val hoursJson = obj.optJSONArray("hourlyMs")
        val hourly = LongArray(24) { idx ->
            hoursJson?.optLong(idx, 0L) ?: 0L
        }
        return DailyUsageRecord(
            dateKey = dateKey,
            activeScreenMs = obj.optLong("activeScreenMs", 0L),
            breaksCompleted = obj.optInt("breaksCompleted", 0),
            breaksSkipped = obj.optInt("breaksSkipped", 0),
            maxContinuousMs = obj.optLong("maxContinuousMs", 0L),
            hourlyMs = hourly,
            avgStrainScore = obj.optInt("avgStrainScore", 0),
            isSample = obj.optBoolean("isSample", false)
        )
    }

    fun loadLastNDays(days: Int): List<DailyUsageRecord> {
        val cal = Calendar.getInstance()
        val list = mutableListOf<DailyUsageRecord>()
        for (i in (days - 1) downTo 0) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = dateFormat.format(cal.time)
            list.add(loadDailyRecord(key))
        }
        return list
    }

    fun hasSampleHistory(): Boolean {
        val map = loadAllRecordsJson()
        val keys = map.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (map.optJSONObject(k)?.optBoolean("isSample", false) == true) {
                return true
            }
        }
        return false
    }

    fun hasPastNonTodayData(): Boolean {
        val today = todayKey()
        val map = loadAllRecordsJson()
        val keys = map.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k != today && (map.optJSONObject(k)?.optLong("activeScreenMs", 0L) ?: 0L) > 0L) {
                return true
            }
        }
        return false
    }

    @Synchronized
    fun toggle14DaySampleHistory(): Boolean {
        val map = loadAllRecordsJson()
        if (hasSampleHistory()) {
            val keysToRemove = mutableListOf<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (map.optJSONObject(k)?.optBoolean("isSample", false) == true) {
                    keysToRemove.add(k)
                }
            }
            keysToRemove.forEach { map.remove(it) }
            prefs.edit().putString("daily_records_json", map.toString()).apply()
            return false
        }

        val today = todayKey()
        val patternMinutes = intArrayOf(96, 128, 84, 152, 114, 72, 138, 162, 108, 174, 92, 122, 146, 88)
        val patternDone = intArrayOf(5, 6, 4, 7, 5, 4, 6, 7, 5, 7, 5, 6, 6, 4)
        val patternSkipped = intArrayOf(0, 1, 0, 2, 1, 0, 1, 2, 0, 2, 0, 1, 1, 0)
        val patternStrain = intArrayOf(15, 21, 12, 34, 18, 11, 24, 38, 17, 42, 14, 19, 28, 13)

        val cal = Calendar.getInstance()
        for (i in 1..14) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = dateFormat.format(cal.time)
            if (key == today) continue

            val existing = map.optJSONObject(key)
            if (existing != null && !existing.optBoolean("isSample", false) && existing.optLong("activeScreenMs", 0L) > 0L) {
                continue
            }

            val idx = (i - 1) % patternMinutes.size
            val totalMs = patternMinutes[idx] * 60_000L
            val hourly = LongArray(24) { hr ->
                if (hr in 8..22) totalMs / 15L else 0L
            }
            val rec = DailyUsageRecord(
                dateKey = key,
                activeScreenMs = totalMs,
                breaksCompleted = patternDone[idx],
                breaksSkipped = patternSkipped[idx],
                maxContinuousMs = (18 + patternSkipped[idx] * 5) * 60_000L,
                hourlyMs = hourly,
                avgStrainScore = patternStrain[idx],
                isSample = true
            )
            saveDailyRecordIntoMap(map, rec)
        }
        prefs.edit().putString("daily_records_json", map.toString()).apply()
        return true
    }

    @Synchronized
    fun addActiveScreenDelta(deltaMs: Long, newCycleMs: Long, config: AppConfig): DailyUsageRecord {
        val key = todayKey()
        val current = loadDailyRecord(key)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        val nextHourly = current.hourlyMs.copyOf()
        nextHourly[hour] = nextHourly[hour] + deltaMs

        val nextActive = current.activeScreenMs + deltaMs
        val nextMaxContinuous = maxOf(current.maxContinuousMs, newCycleMs)

        val updated = current.copy(
            activeScreenMs = nextActive,
            maxContinuousMs = nextMaxContinuous,
            hourlyMs = nextHourly,
            isSample = false
        )
        val strain = computeStrainBreakdown(updated, newCycleMs, config).totalScore
        val withStrain = updated.copy(avgStrainScore = strain)
        saveDailyRecord(withStrain)
        saveCycleElapsedMs(newCycleMs)
        return withStrain
    }

    @Synchronized
    fun recordBreakCompleted(config: AppConfig): DailyUsageRecord {
        val current = loadDailyRecord(todayKey())
        val updated = current.copy(breaksCompleted = current.breaksCompleted + 1, isSample = false)
        val withStrain = updated.copy(avgStrainScore = computeStrainBreakdown(updated, 0L, config).totalScore)
        saveDailyRecord(withStrain)
        saveCycleElapsedMs(0L)
        return withStrain
    }

    @Synchronized
    fun recordBreakSkipped(config: AppConfig): DailyUsageRecord {
        val current = loadDailyRecord(todayKey())
        val updated = current.copy(breaksSkipped = current.breaksSkipped + 1, isSample = false)
        val withStrain = updated.copy(avgStrainScore = computeStrainBreakdown(updated, 0L, config).totalScore)
        saveDailyRecord(withStrain)
        saveCycleElapsedMs(0L)
        return withStrain
    }

    @Synchronized
    fun clearAllHistory() {
        prefs.edit()
            .remove("daily_records_json")
            .remove("cycle_elapsed_ms")
            .apply()
    }

    fun computeStrainBreakdown(
        today: DailyUsageRecord,
        cycleElapsedMs: Long,
        config: AppConfig
    ): StrainBreakdown {
        val workMs = (config.workMinutes * 60_000L).coerceAtLeast(60_000L)
        val stretchRatio = cycleElapsedMs.toDouble() / workMs.toDouble()
        val stretchPoints = (stretchRatio * 34.0).toInt().coerceIn(0, 40)

        val todayMinutes = today.activeScreenMs / 60_000.0
        val limit = config.dailyLimitMinutes.coerceAtLeast(60).toDouble()
        val loadPoints = ((todayMinutes / limit) * 28.0).toInt().coerceIn(0, 30)

        val totalBreaks = today.breaksCompleted + today.breaksSkipped
        val skipPenalty = if (totalBreaks > 0) {
            ((today.breaksSkipped.toDouble() / totalBreaks.toDouble()) * 20.0).toInt().coerceIn(0, 20)
        } else 0

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isEvening = hour >= 19 || hour < 6
        val filterActive = config.filterEnabled || (config.autoEveningSchedule && isWithinTimeWindow(config.sunsetStart, config.morningEnd))
        val blueLightPenalty = when {
            isEvening && !filterActive -> 10
            !filterActive && todayMinutes > 120.0 -> 5
            filterActive && config.filterExtraDimPercent < 15 && isEvening -> 3
            else -> 0
        }

        val rawScore = (stretchPoints + loadPoints + skipPenalty + blueLightPenalty).coerceIn(0, 100)
        val total = if (stretchRatio >= 1.0) maxOf(rawScore, 72) else rawScore

        return StrainBreakdown(
            totalScore = total,
            continuousStretchPoints = stretchPoints,
            dailyLoadPoints = loadPoints,
            compliancePenaltyPoints = skipPenalty,
            blueLightPenaltyPoints = blueLightPenalty
        )
    }

    fun isWithinTimeWindow(startHHMM: String, endHHMM: String): Boolean {
        val parseMinutes = { str: String ->
            val parts = str.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            (h.coerceIn(0, 23) * 60) + m.coerceIn(0, 59)
        }
        val cal = Calendar.getInstance()
        val cur = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = parseMinutes(startHHMM)
        val end = parseMinutes(endHHMM)
        if (start == end) return false
        return if (start < end) {
            cur in start until end
        } else {
            cur >= start || cur < end
        }
    }

    private fun saveDailyRecord(record: DailyUsageRecord) {
        val map = loadAllRecordsJson()
        saveDailyRecordIntoMap(map, record)
        prefs.edit().putString("daily_records_json", map.toString()).apply()
    }

    private fun saveDailyRecordIntoMap(map: JSONObject, record: DailyUsageRecord) {
        val obj = JSONObject().apply {
            put("activeScreenMs", record.activeScreenMs)
            put("breaksCompleted", record.breaksCompleted)
            put("breaksSkipped", record.breaksSkipped)
            put("maxContinuousMs", record.maxContinuousMs)
            put("avgStrainScore", record.avgStrainScore)
            put("isSample", record.isSample)
            put("hourlyMs", JSONArray().apply {
                record.hourlyMs.forEach { put(it) }
            })
        }
        map.put(record.dateKey, obj)
    }

    private fun loadAllRecordsJson(): JSONObject {
        val raw = prefs.getString("daily_records_json", null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun emptyRecord(dateKey: String): DailyUsageRecord {
        return DailyUsageRecord(
            dateKey = dateKey,
            activeScreenMs = 0L,
            breaksCompleted = 0,
            breaksSkipped = 0,
            maxContinuousMs = 0L,
            hourlyMs = LongArray(24) { 0L },
            avgStrainScore = 0,
            isSample = false
        )
    }
}
