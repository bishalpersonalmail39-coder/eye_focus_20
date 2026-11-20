package com.optirest.eyecare.data

data class FilterPreset(
    val id: String,
    val name: String,
    val description: String,
    val warmthKelvin: Int,
    val extraDimPercent: Int,
    val blueCutPercent: Int,
    val enabled: Boolean
)

data class ScheduleRulePreset(
    val id: String,
    val name: String,
    val description: String,
    val workMinutes: Int,
    val breakSeconds: Int
)

object EyeGuardDefaults {
    // Exact OptiRest Dark Navy-Slate Theme Palette
    const val DARK_BG_HEX = 0xFF0B1015
    const val DARK_SURFACE_HEX = 0xFF131A22
    const val DARK_INSET_HEX = 0xFF18212C
    const val DARK_CONTROL_HEX = 0xFF1B2532
    const val DARK_BORDER_HEX = 0xFF1F2A36
    const val DARK_TEXT_PRIMARY_HEX = 0xFFF1F5F9
    const val DARK_TEXT_SECONDARY_HEX = 0xFF94A3B8

    // Primary & Semantic Accents
    const val EMERALD_PRIMARY_HEX = 0xFF16A388
    const val EMERALD_BRIGHT_HEX = 0xFF10B981
    const val AMBER_WARNING_HEX = 0xFFF59E0B
    const val SKY_MODERATE_HEX = 0xFF38BDF8
    const val ROSE_ALERT_HEX = 0xFFF43F5E

    // Default 20-20-20 & Schedule Values
    const val DEFAULT_SCHEDULE_PRESET_ID = "20-20-20"
    const val DEFAULT_WORK_MINUTES = 20
    const val DEFAULT_BREAK_SECONDS = 20
    const val DEFAULT_DAILY_LIMIT_MINUTES = 180

    const val DEFAULT_FILTER_PRESET_ID = "reading"
    const val DEFAULT_WARMTH_KELVIN = 3600
    const val DEFAULT_EXTRA_DIM_PERCENT = 18
    const val DEFAULT_BLUE_CUT_PERCENT = 45

    const val DEFAULT_SUNSET_START = "20:00"
    const val DEFAULT_MORNING_END = "06:30"
    const val DEFAULT_QUIET_START = "22:30"
    const val DEFAULT_QUIET_END = "07:00"

    // All 8 Calibrated Optical Filter Presets
    val FILTER_PRESETS: List<FilterPreset> = listOf(
        FilterPreset(
            id = "off",
            name = "Daylight Off",
            description = "Native display white balance",
            warmthKelvin = 6500,
            extraDimPercent = 0,
            blueCutPercent = 0,
            enabled = false
        ),
        FilterPreset(
            id = "office",
            name = "Office Focus",
            description = "Crisp 4800K anti-glare tint",
            warmthKelvin = 4800,
            extraDimPercent = 10,
            blueCutPercent = 25,
            enabled = true
        ),
        FilterPreset(
            id = "reading",
            name = "Reading Paper",
            description = "Soft 3600K amber paper tone",
            warmthKelvin = 3600,
            extraDimPercent = 18,
            blueCutPercent = 45,
            enabled = true
        ),
        FilterPreset(
            id = "dusk",
            name = "Warm Dusk",
            description = "Balanced 2900K sunset filter",
            warmthKelvin = 2900,
            extraDimPercent = 26,
            blueCutPercent = 62,
            enabled = true
        ),
        FilterPreset(
            id = "night",
            name = "Night Owl",
            description = "Deep 2200K evening protection",
            warmthKelvin = 2200,
            extraDimPercent = 35,
            blueCutPercent = 80,
            enabled = true
        ),
        FilterPreset(
            id = "candle",
            name = "Candlelight",
            description = "Warm 1900K melatonin-safe hue",
            warmthKelvin = 1900,
            extraDimPercent = 42,
            blueCutPercent = 90,
            enabled = true
        ),
        FilterPreset(
            id = "cinema",
            name = "Cinema Dim",
            description = "Low-luminance 3200K theater veil",
            warmthKelvin = 3200,
            extraDimPercent = 48,
            blueCutPercent = 52,
            enabled = true
        ),
        FilterPreset(
            id = "bedtime",
            name = "Ultra Dark Bed",
            description = "Max 1800K dark-room shield",
            warmthKelvin = 1800,
            extraDimPercent = 60,
            blueCutPercent = 96,
            enabled = true
        )
    )

    // Schedule & Break Rule Presets
    val SCHEDULE_RULES: List<ScheduleRulePreset> = listOf(
        ScheduleRulePreset(
            id = "20-20-20",
            name = "20-20-20 Rule",
            description = "Every 20 min · rest 20 sec looking 20 ft away",
            workMinutes = 20,
            breakSeconds = 20
        ),
        ScheduleRulePreset(
            id = "pomodoro",
            name = "Pomodoro Eye Care",
            description = "25 min active focus · 5 min visual recovery",
            workMinutes = 25,
            breakSeconds = 300
        ),
        ScheduleRulePreset(
            id = "reading",
            name = "Long Reading",
            description = "35 min reading · 30 sec blink & far-gaze",
            workMinutes = 35,
            breakSeconds = 30
        ),
        ScheduleRulePreset(
            id = "custom",
            name = "Custom Interval",
            description = "Tailor work minutes and break seconds",
            workMinutes = 20,
            breakSeconds = 20
        )
    )
}
