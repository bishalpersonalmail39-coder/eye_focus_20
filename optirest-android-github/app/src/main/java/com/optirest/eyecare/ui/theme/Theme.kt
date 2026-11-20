package com.optirest.eyecare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.optirest.eyecare.data.EyeGuardDefaults

val DarkNavyBg = Color(EyeGuardDefaults.DARK_BG_HEX)
val ElevatedCardSurface = Color(EyeGuardDefaults.DARK_SURFACE_HEX)
val InsetCardSurface = Color(EyeGuardDefaults.DARK_INSET_HEX)
val ControlSurface = Color(EyeGuardDefaults.DARK_CONTROL_HEX)
val SubtleBorder = Color(EyeGuardDefaults.DARK_BORDER_HEX)

val EmeraldTeal = Color(EyeGuardDefaults.EMERALD_PRIMARY_HEX)
val EmeraldBright = Color(EyeGuardDefaults.EMERALD_BRIGHT_HEX)
val AmberGold = Color(EyeGuardDefaults.AMBER_WARNING_HEX)
val SkyModerate = Color(EyeGuardDefaults.SKY_MODERATE_HEX)
val RoseAlert = Color(EyeGuardDefaults.ROSE_ALERT_HEX)

val TextPrimary = Color(EyeGuardDefaults.DARK_TEXT_PRIMARY_HEX)
val TextSecondary = Color(EyeGuardDefaults.DARK_TEXT_SECONDARY_HEX)

private val OptiRestDarkScheme = darkColorScheme(
    primary = EmeraldTeal,
    onPrimary = Color.White,
    secondary = AmberGold,
    onSecondary = Color.Black,
    tertiary = EmeraldBright,
    background = DarkNavyBg,
    onBackground = TextPrimary,
    surface = ElevatedCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = InsetCardSurface,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder
)

@Composable
fun OptiRestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OptiRestDarkScheme,
        content = content
    )
}
