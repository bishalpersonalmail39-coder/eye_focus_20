package com.eyeguard.android

data class EyePreset(
    val id: String,
    val name: String,
    val kelvin: Int,
    val brightness: Int
)

object EyeGuardDefaults {
    const val INITIAL_MODE_ID = "health"
    const val INITIAL_KELVIN = 4200
    const val INITIAL_BRIGHTNESS = 90
    const val AUTO_START_ON_BOOT = true
    const val IGNORE_BATTERY_OPTIMIZATION = true
    const val COUNT_TIMER_ONLY_WHEN_SCREEN_ON = true
    const val AMOLED_EXTRA_DIM = true

    const val SCHEDULE_ENABLED = true
    const val SUNRISE_TIME = "07:00"
    const val SUNSET_TIME = "19:30"
    const val DAY_MODE_ID = "office"
    const val NIGHT_MODE_ID = "night"

    const val BREAK_ENABLED = true
    const val WORK_MINUTES = 20
    const val REST_SECONDS = 20
    const val ALLOW_SKIP_BREAK = true

    const val FOCUS_BAND_HEIGHT_DP = 140
    const val FOCUS_SURROUND_DIM = 0.58f

    val PRESETS: List<EyePreset> = listOf(
        EyePreset("normal", "Normal", 6500, 100),
        EyePreset("health", "Health", 4200, 90),
        EyePreset("movie", "Movie", 5000, 88),
        EyePreset("game", "Game", 4800, 95),
        EyePreset("office", "Office", 4500, 85),
        EyePreset("reading", "Reading", 3600, 76),
        EyePreset("edit", "Edit", 5600, 96),
        EyePreset("night", "Night", 2500, 55)
    )

    val APP_RULES: Map<String, String> = mapOf(
        "com.amazon.kindle" to "reading",
        "com.google.android.youtube" to "movie",
        "org.videolan.vlc" to "movie",
        "com.adobe.lrmobile" to "edit"
    )
}
