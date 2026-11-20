package com.optirest.eyecare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optirest.eyecare.data.AppConfig
import com.optirest.eyecare.data.DailyUsageRecord
import com.optirest.eyecare.data.EyeGuardDefaults
import com.optirest.eyecare.service.EyeCareUiState
import com.optirest.eyecare.service.ScreenFilterState
import com.optirest.eyecare.ui.theme.AmberGold
import com.optirest.eyecare.ui.theme.DarkNavyBg
import com.optirest.eyecare.ui.theme.ElevatedCardSurface
import com.optirest.eyecare.ui.theme.EmeraldBright
import com.optirest.eyecare.ui.theme.EmeraldTeal
import com.optirest.eyecare.ui.theme.InsetCardSurface
import com.optirest.eyecare.ui.theme.RoseAlert
import com.optirest.eyecare.ui.theme.SkyModerate
import com.optirest.eyecare.ui.theme.SubtleBorder
import com.optirest.eyecare.ui.theme.TextPrimary
import com.optirest.eyecare.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptiRestAppScreen(
    uiState: EyeCareUiState,
    filterState: ScreenFilterState,
    isIgnoringBatteryOpt: Boolean,
    canDrawOverlay: Boolean,
    hasUsageAccess: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onTogglePause: () -> Unit,
    onStartBreak: () -> Unit,
    onCompleteBreak: () -> Unit,
    onSnoozeBreak: () -> Unit,
    onSkipBreak: () -> Unit,
    onDismissPrompt: () -> Unit,
    onResetCycle: () -> Unit,
    onToggleSampleHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onUpdateConfig: (AppConfig) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = DarkNavyBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OptiRest",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                actions = {
                    Button(
                        onClick = onStartBreak,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldTeal,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Take Break", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavyBg)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ElevatedCardSurface,
                tonalElevation = 0.dp
            ) {
                val items = listOf(
                    "Overview" to Icons.Outlined.Visibility,
                    "Eye Filter" to Icons.Outlined.BrightnessMedium,
                    "Reports" to Icons.Outlined.BarChart,
                    "Alerts" to Icons.Outlined.Notifications
                )
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldBright,
                            selectedTextColor = EmeraldBright,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x2416A388)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkNavyBg)
        ) {
            when (selectedTab) {
                0 -> OverviewScreen(
                    uiState = uiState,
                    filterState = filterState,
                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                    canDrawOverlay = canDrawOverlay,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onTogglePause = onTogglePause,
                    onStartBreak = onStartBreak,
                    onResetCycle = onResetCycle,
                    onQuickToggleFilter = {
                        onUpdateConfig(uiState.config.copy(filterEnabled = !uiState.config.filterEnabled))
                    },
                    onOpenAlertsTab = { selectedTab = 3 }
                )

                1 -> EyeFilterScreen(
                    config = uiState.config,
                    filterState = filterState,
                    canDrawOverlay = canDrawOverlay,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onUpdateConfig = onUpdateConfig
                )

                2 -> ReportsScreen(
                    uiState = uiState,
                    onToggleSampleHistory = onToggleSampleHistory,
                    onClearHistory = onClearHistory
                )

                3 -> AlertsScreen(
                    config = uiState.config,
                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                    canDrawOverlay = canDrawOverlay,
                    hasUsageAccess = hasUsageAccess,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestUsageAccess = onRequestUsageAccess,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAutoStartSettings = onOpenAutoStartSettings,
                    onUpdateConfig = onUpdateConfig
                )
            }
        }
    }

    if ((uiState.breakDueModalOpen || uiState.isOnBreak) && !uiState.isBreakOverlayVisible) {
        val sec = if (uiState.isOnBreak) uiState.breakRemainingSec else uiState.config.breakSeconds
        val mmSs = "%02d:%02d".format((sec / 60).coerceAtLeast(0), (sec % 60).coerceAtLeast(0))
        AlertDialog(
            onDismissRequest = { if (!uiState.isOnBreak) onDismissPrompt() },
            containerColor = Color(0xF0111822),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Text(
                    text = "Time to Rest Your Eyes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "20-20-20 Rule: Look at an object at least 20 feet away and blink slowly.",
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = mmSs,
                        style = tabularStyle(48.sp, FontWeight.Bold, Color(0xFF00D4FF))
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onSkipBreak,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B8F4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Skip Break", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onSnoozeBreak,
                    border = BorderStroke(1.dp, Color(0xFFB8C7D9)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Delay 5 Min", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun OverviewScreen(
    uiState: EyeCareUiState,
    filterState: ScreenFilterState,
    isIgnoringBatteryOpt: Boolean,
    canDrawOverlay: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onTogglePause: () -> Unit,
    onStartBreak: () -> Unit,
    onResetCycle: () -> Unit,
    onQuickToggleFilter: () -> Unit,
    onOpenAlertsTab: () -> Unit
) {
    val workMs = (uiState.config.workMinutes * 60_000L).coerceAtLeast(60_000L)
    val remainingMs = (workMs - uiState.cycleElapsedMs).coerceAtLeast(0L)
    val progress = (uiState.cycleElapsedMs.toFloat() / workMs.toFloat()).coerceIn(0f, 1f)
    val today = uiState.todayRecord
    val breakdown = uiState.strainBreakdown
    val strainScore = breakdown.totalScore
    val effectiveTodayMs = maxOf(today?.activeScreenMs ?: 0L, uiState.systemUsageStatsMs)

    val totalBreaks = (today?.breaksCompleted ?: 0) + (today?.breaksSkipped ?: 0)
    val compliancePct = if (totalBreaks > 0) ((today?.breaksCompleted ?: 0) * 100) / totalBreaks else 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isIgnoringBatteryOpt || !canDrawOverlay || !hasNotificationPermission) {
            SurfaceCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = AmberGold)
                    Text(
                        text = "Overlay, Boot & Background Setup",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Grant Display Over Apps so the 20s countdown lays directly over other apps, and ignore battery optimization so OptiRest starts after power off/on.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!canDrawOverlay) {
                            Button(
                                onClick = onRequestOverlayPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldTeal, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Allow Overlay", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!isIgnoringBatteryOpt) {
                            Button(
                                onClick = onRequestBatteryWhitelist,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Ignore Battery Opt", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!hasNotificationPermission) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onRequestNotificationPermission,
                                border = BorderStroke(1.dp, SubtleBorder),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enable Alerts", fontSize = 12.sp, color = EmeraldBright)
                            }
                            OutlinedButton(
                                onClick = onOpenNotificationSettings,
                                border = BorderStroke(1.dp, SubtleBorder),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("App Settings", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Hero 20-20-20 Countdown Ring Card
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            uiState.isOnBreak -> "Resting · 20-20-20 Break Active"
                            uiState.isManualPaused -> "Paused · Manual Hold"
                            uiState.isScreenOnAndUnlocked -> "Counting · Display On & Engaged"
                            else -> "Paused · Screen Off"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.config.schedulePresetId == "20-20-20") {
                            "20-20-20 Eye Protection"
                        } else {
                            "${uiState.config.workMinutes}m / ${uiState.config.breakSeconds}s Focus Cycle"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = onQuickToggleFilter,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.config.filterEnabled) AmberGold else SubtleBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uiState.config.filterEnabled) Color(0x1FF59E0B) else Color.Transparent
                    )
                ) {
                    Text(
                        text = if (uiState.config.filterEnabled) "${uiState.config.filterWarmthKelvin}K On" else "Filter Off",
                        fontSize = 11.sp,
                        color = if (uiState.config.filterEnabled) AmberGold else TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(188.dp)) {
                    drawArc(
                        color = SubtleBorder,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = if (uiState.isManualPaused) AmberGold else EmeraldTeal,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Next break in", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = formatCountdown(remainingMs),
                        style = tabularStyle(36.sp, FontWeight.Bold, TextPrimary)
                    )
                    Text(
                        text = "${formatCompact(uiState.cycleElapsedMs)} elapsed",
                        style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartBreak,
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldTeal, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start ${uiState.config.breakSeconds}s Break", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onTogglePause,
                    modifier = Modifier.height(46.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isManualPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = if (uiState.isManualPaused) "Resume" else "Pause",
                        tint = TextPrimary
                    )
                }
                OutlinedButton(
                    onClick = onResetCycle,
                    modifier = Modifier.height(46.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reset cycle", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SubtleBorder)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rule: ${uiState.config.workMinutes} min active · ${uiState.config.breakSeconds} sec gaze",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Edit Rules",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldBright,
                    modifier = Modifier.clickable { onOpenAlertsTab() }
                )
            }
        }

        // Eye Strain Monitor Card
        SurfaceCard {
            val strainColor = when {
                strainScore >= 65 -> RoseAlert
                strainScore >= 42 -> AmberGold
                strainScore >= 22 -> SkyModerate
                else -> EmeraldBright
            }
            val strainLabel = when {
                strainScore >= 65 -> "High Strain"
                strainScore >= 42 -> "Elevated Strain"
                strainScore >= 22 -> "Moderate Load"
                else -> "Optimal Comfort"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Eye Strain Monitor", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                Text(strainLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = strainColor)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$strainScore", style = tabularStyle(32.sp, FontWeight.Bold, TextPrimary))
                Text("/ 100 strain index", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (strainScore / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = strainColor,
                trackColor = InsetCardSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SubtleBorder)
            Spacer(modifier = Modifier.height(10.dp))
            FactorRow("Continuous stretch load", "${breakdown.continuousStretchPoints} / 40", TextPrimary)
            FactorRow("Daily screen time load", "${breakdown.dailyLoadPoints} / 30", TextPrimary)
            FactorRow("Skipped break penalty", "${breakdown.compliancePenaltyPoints} / 20", TextPrimary)
            FactorRow("Evening / blue-light penalty", "${breakdown.blueLightPenaltyPoints} / 10", TextPrimary)
        }

        // 2x2 Today KPI Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Active Screen Today", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(formatCompact(effectiveTodayMs), style = tabularStyle(20.sp, FontWeight.Bold, TextPrimary))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Goal ${uiState.config.dailyLimitMinutes / 60}h ${uiState.config.dailyLimitMinutes % 60}m",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Break Compliance", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("$compliancePct%", style = tabularStyle(20.sp, FontWeight.Bold, EmeraldBright))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${today?.breaksCompleted ?: 0} done · ${today?.breaksSkipped ?: 0} skipped",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Longest Stretch", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(formatCompact(today?.maxContinuousMs ?: 0L), style = tabularStyle(20.sp, FontWeight.Bold, TextPrimary))
                Spacer(modifier = Modifier.height(2.dp))
                Text("Max continuous gaze", fontSize = 11.sp, color = TextSecondary)
            }
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Eye Comfort Filter", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (filterState.isOverlayActive || uiState.config.filterEnabled) "${uiState.config.filterWarmthKelvin}K" else "Off",
                    style = tabularStyle(20.sp, FontWeight.Bold, TextPrimary)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (uiState.config.filterEnabled) {
                        "${uiState.config.filterExtraDimPercent}% dim · ${uiState.config.filterBlueCutPercent}% cut"
                    } else "Tap Eye Filter to tune",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Boot & Display-On Guard Card
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = EmeraldBright)
                    Text("Display-On & Boot Status", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                if (isIgnoringBatteryOpt) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EmeraldBright)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FactorRow("Auto-start on boot", if (uiState.config.autoStartOnBoot) "Enabled" else "Off", TextPrimary)
            FactorRow(
                "Battery optimization",
                if (isIgnoringBatteryOpt) "Unrestricted (Ignored)" else "Optimized",
                if (isIgnoringBatteryOpt) EmeraldBright else AmberGold
            )
            FactorRow("Off-screen music/audio", "Excluded automatically", TextPrimary)
        }
    }
}

@Composable
private fun EyeFilterScreen(
    config: AppConfig,
    filterState: ScreenFilterState,
    canDrawOverlay: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onUpdateConfig: (AppConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Blue-Light & Glare Comfort Filter",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (filterState.isOverlayActive) {
                            "System-wide overlay active across all apps"
                        } else {
                            "8 one-tap presets + Warmth, Extra Dim & Blue Cut sliders"
                        },
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = config.filterEnabled,
                    onCheckedChange = { onUpdateConfig(config.copy(filterEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AmberGold)
                )
            }

            if (!canDrawOverlay) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestOverlayPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Display Over Apps Permission", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("8 Optical Presets", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            val rows = EyeGuardDefaults.FILTER_PRESETS.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { preset ->
                            val isSelected = (preset.id == "off" && !config.filterEnabled) ||
                                (config.filterEnabled && config.filterPresetId == preset.id)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateConfig(
                                            config.copy(
                                                filterEnabled = preset.enabled,
                                                filterPresetId = preset.id,
                                                filterWarmthKelvin = preset.warmthKelvin,
                                                filterExtraDimPercent = preset.extraDimPercent,
                                                filterBlueCutPercent = preset.blueCutPercent
                                            )
                                        )
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0x1FF59E0B) else InsetCardSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AmberGold else SubtleBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${preset.warmthKelvin}K",
                                            style = tabularStyle(11.sp, FontWeight.Medium, TextSecondary)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.description,
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Color Temperature (Warmth) Card
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color Temperature (Warmth)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Text("${config.filterWarmthKelvin}K", style = tabularStyle(15.sp, FontWeight.Bold, AmberGold))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Shifts display white point toward warm amber.", fontSize = 12.sp, color = TextSecondary)
            Slider(
                value = config.filterWarmthKelvin.toFloat(),
                onValueChange = {
                    onUpdateConfig(
                        config.copy(
                            filterPresetId = "custom",
                            filterWarmthKelvin = it.toInt(),
                            filterEnabled = true
                        )
                    )
                },
                valueRange = 1800f..6500f,
                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1800K", style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary))
                Text("6500K", style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary))
            }
        }

        // Extra Dim (%) Card
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Extra Dim", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Text("${config.filterExtraDimPercent}%", style = tabularStyle(15.sp, FontWeight.Bold, EmeraldBright))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Dims luminance below hardware minimum for dark rooms.", fontSize = 12.sp, color = TextSecondary)
            Slider(
                value = config.filterExtraDimPercent.toFloat(),
                onValueChange = {
                    val v = it.toInt()
                    onUpdateConfig(
                        config.copy(
                            filterPresetId = "custom",
                            filterExtraDimPercent = v,
                            filterEnabled = v > 0 || config.filterBlueCutPercent > 0
                        )
                    )
                },
                valueRange = 0f..65f,
                colors = SliderDefaults.colors(thumbColor = EmeraldTeal, activeTrackColor = EmeraldTeal)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0%", style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary))
                Text("65%", style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary))
            }
        }

        // Blue Cut (%) Card
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                    Text("Blue Cut", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                }
                Text("${config.filterBlueCutPercent}%", style = tabularStyle(15.sp, FontWeight.Bold, AmberGold))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Attenuates high-energy 440–480nm short-wavelength blue light.", fontSize = 12.sp, color = TextSecondary)
            Slider(
                value = config.filterBlueCutPercent.toFloat(),
                onValueChange = {
                    val v = it.toInt()
                    onUpdateConfig(
                        config.copy(
                            filterPresetId = "custom",
                            filterBlueCutPercent = v,
                            filterEnabled = v > 0 || config.filterExtraDimPercent > 0
                        )
                    )
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0%", style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary))
                Text("100%", style = tabularStyle(11.sp, FontWeight.Normal, TextSecondary))
            }
        }
    }
}

@Composable
private fun ReportsScreen(
    uiState: EyeCareUiState,
    onToggleSampleHistory: () -> Unit,
    onClearHistory: () -> Unit
) {
    var rangeMode by remember { mutableIntStateOf(2) } // 0 = Today, 1 = 7 Days, 2 = 14 Days
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val all14 = uiState.last14Days
    val series: List<DailyUsageRecord> = when (rangeMode) {
        1 -> all14.takeLast(7)
        else -> all14
    }
    val today = uiState.todayRecord
    val effectiveTodayMs = maxOf(today?.activeScreenMs ?: 0L, uiState.systemUsageStatsMs)

    val displayedRecords = if (rangeMode == 0) {
        emptyList()
    } else {
        series.mapIndexed { i, rec ->
            if (i == series.lastIndex) {
                rec.copy(
                    activeScreenMs = maxOf(rec.activeScreenMs, effectiveTodayMs),
                    avgStrainScore = maxOf(rec.avgStrainScore, uiState.strainBreakdown.totalScore)
                )
            } else rec
        }
    }

    val barMinutes: List<Float> = if (rangeMode == 0) {
        (0 until 24).map { hr -> (today?.hourlyMs?.getOrNull(hr) ?: 0L) / 60_000f }
    } else {
        displayedRecords.map { it.activeScreenMs / 60_000f }
    }

    val strainValues: List<Int> = if (rangeMode == 0) {
        (0 until 24).map { hr ->
            val mins = ((today?.hourlyMs?.getOrNull(hr) ?: 0L) / 60_000L).toInt()
            when {
                mins >= 45 -> 62
                mins >= 20 -> 34
                mins > 0 -> 18
                else -> 0
            }
        }
    } else {
        displayedRecords.map { it.avgStrainScore }
    }

    val totalMinutes = if (rangeMode == 0) {
        (effectiveTodayMs / 60_000L).toInt()
    } else {
        displayedRecords.sumOf { (it.activeScreenMs / 60_000L).toInt() }
    }
    val activeDaysCount = if (rangeMode == 0) 1 else displayedRecords.count { it.activeScreenMs > 0L }.coerceAtLeast(1)
    val dailyAvgMinutes = totalMinutes / activeDaysCount

    val breaksDone = if (rangeMode == 0) (today?.breaksCompleted ?: 0) else displayedRecords.sumOf { it.breaksCompleted }
    val breaksSkip = if (rangeMode == 0) (today?.breaksSkipped ?: 0) else displayedRecords.sumOf { it.breaksSkipped }
    val totalBreaks = breaksDone + breaksSkip
    val compliancePct = if (totalBreaks > 0) (breaksDone * 100) / totalBreaks else 100

    val activeStrainRecords = if (rangeMode == 0) listOf(uiState.strainBreakdown.totalScore) else displayedRecords.filter { it.activeScreenMs > 0L }.map { it.avgStrainScore }
    val avgStrainIndex = if (activeStrainRecords.isNotEmpty()) activeStrainRecords.sum() / activeStrainRecords.size else uiState.strainBreakdown.totalScore

    val peakBarMin = barMinutes.maxOrNull() ?: 0f
    val yAxisTopMin = if (peakBarMin > 180f) (((peakBarMin.toInt() + 44) / 45) * 45) else 180

    val inspectedIndex = (selectedIdx ?: (barMinutes.lastIndex)).coerceIn(0, (barMinutes.lastIndex).coerceAtLeast(0))
    val inspectedLabel = if (rangeMode == 0) {
        "${"%02d".format(inspectedIndex)}:00"
    } else {
        displayedRecords.getOrNull(inspectedIndex)?.dateKey?.takeLast(5) ?: "--"
    }
    val inspectedMins = barMinutes.getOrNull(inspectedIndex)?.toInt() ?: 0
    val inspectedStrain = strainValues.getOrNull(inspectedIndex) ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner Card matching Web Preview
        SurfaceCard {
            Text(
                text = if (uiState.hasSampleData) {
                    "Showing 14-day sample history alongside today's live activity"
                } else {
                    "Only today's live device activity is recorded so far"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (uiState.hasSampleData) {
                    "Tap any bar or data point below to inspect daily metrics, or switch back to live-only data."
                } else {
                    "Past days fill in automatically as you use OptiRest. Load 14-day sample history to inspect multi-day trends immediately."
                },
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    onToggleSampleHistory()
                    rangeMode = 2
                    selectedIdx = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.hasSampleData) InsetCardSurface else EmeraldTeal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (uiState.hasSampleData) "Clear 14-Day Sample History" else "Load 14-Day Sample History",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Range Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Today" to 0, "7 Days" to 1, "14 Days" to 2).forEach { (label, mode) ->
                val selected = rangeMode == mode
                OutlinedButton(
                    onClick = {
                        rangeMode = mode
                        selectedIdx = null
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (selected) EmeraldTeal else SubtleBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) Color(0x1F16A388) else ElevatedCardSurface
                    )
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) EmeraldBright else TextSecondary
                    )
                }
            }
        }

        // 2x2 Metric Cards Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Total Active Screen", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(formatMinutes(totalMinutes), style = tabularStyle(20.sp, FontWeight.Bold, TextPrimary))
            }
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Daily Average", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(formatMinutes(dailyAvgMinutes), style = tabularStyle(20.sp, FontWeight.Bold, TextPrimary))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Break Compliance", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("$compliancePct%", style = tabularStyle(20.sp, FontWeight.Bold, EmeraldBright))
            }
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Avg Strain Index", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("$avgStrainIndex / 100", style = tabularStyle(20.sp, FontWeight.Bold, AmberGold))
            }
        }

        // Chart 1: Daily Active Screen Trend (Bar Chart with 45m, 90m, 135m, 180m Y-axis + Tap-to-Inspect)
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (rangeMode == 0) "Hourly Active Screen" else "Daily Active Screen Trend",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text("Tap any bar to inspect day details", fontSize = 11.sp, color = TextSecondary)
                }
                Text(
                    text = "$inspectedLabel · ${formatMinutes(inspectedMins)}",
                    style = tabularStyle(12.sp, FontWeight.SemiBold, EmeraldBright)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().height(176.dp)) {
                // Y-axis ticks (180m, 135m, 90m, 45m, 0m)
                val q1 = yAxisTopMin / 4
                val q2 = (yAxisTopMin * 2) / 4
                val q3 = (yAxisTopMin * 3) / 4
                Column(
                    modifier = Modifier.fillMaxHeight().width(38.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf("${yAxisTopMin}m", "${q3}m", "${q2}m", "${q1}m", "0m").forEach { tick ->
                        Text(tick, style = tabularStyle(10.sp, FontWeight.Normal, TextSecondary))
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(barMinutes.size) {
                            detectTapGestures { offset ->
                                val count = barMinutes.size.coerceAtLeast(1)
                                val idx = ((offset.x / size.width) * count).toInt().coerceIn(0, count - 1)
                                selectedIdx = idx
                            }
                        }
                ) {
                    val count = barMinutes.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.60f).coerceIn(4f, 36f)

                    // Horizontal grid lines at 25%, 50%, 75%, 100%
                    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                        val y = size.height - frac * size.height
                        drawLine(
                            color = SubtleBorder,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f
                        )
                    }

                    barMinutes.forEachIndexed { idx, mins ->
                        val ratio = (mins / yAxisTopMin.toFloat()).coerceIn(0f, 1f)
                        val barH = (size.height * ratio).coerceAtLeast(if (mins > 0f) 6f else 2f)
                        val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                        val y = size.height - barH
                        val isSel = idx == inspectedIndex
                        val barColor = when {
                            isSel -> EmeraldBright
                            mins > uiState.config.dailyLimitMinutes -> AmberGold
                            else -> EmeraldTeal
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 46.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (rangeMode == 0) {
                    listOf("0h", "6h", "12h", "18h", "23h").forEach {
                        Text(it, style = tabularStyle(10.sp, FontWeight.Normal, TextSecondary))
                    }
                } else {
                    val step = if (displayedRecords.size > 7) 3 else 1
                    displayedRecords.forEachIndexed { idx, rec ->
                        if (idx % step == 0 || idx == displayedRecords.lastIndex) {
                            Text(rec.dateKey.takeLast(5), style = tabularStyle(10.sp, FontWeight.Normal, TextSecondary))
                        }
                    }
                }
            }
        }

        // Chart 2: Eye Strain Trend (Amber Line Chart with Y-axis 0, 50, 100)
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Eye Strain Trend", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Calculated from continuous stretch & blue-light filter", fontSize = 11.sp, color = TextSecondary)
                }
                Text(
                    text = "$inspectedStrain / 100",
                    style = tabularStyle(12.sp, FontWeight.SemiBold, AmberGold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().height(132.dp)) {
                Column(
                    modifier = Modifier.fillMaxHeight().width(32.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf("100", "50", "0").forEach { tick ->
                        Text(tick, style = tabularStyle(10.sp, FontWeight.Normal, TextSecondary))
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(strainValues.size) {
                            detectTapGestures { offset ->
                                val count = strainValues.size.coerceAtLeast(1)
                                val idx = ((offset.x / size.width) * count).toInt().coerceIn(0, count - 1)
                                selectedIdx = idx
                            }
                        }
                ) {
                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = size.height - frac * size.height
                        drawLine(
                            color = SubtleBorder,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f
                        )
                    }

                    if (strainValues.size > 1) {
                        val path = Path()
                        strainValues.forEachIndexed { idx, score ->
                            val x = (idx.toFloat() / (strainValues.size - 1).coerceAtLeast(1)) * size.width
                            val y = size.height - (score.coerceIn(0, 100) / 100f) * size.height
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = AmberGold,
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                        )
                    }

                    strainValues.forEachIndexed { idx, score ->
                        val x = (idx.toFloat() / (strainValues.size - 1).coerceAtLeast(1)) * size.width
                        val y = size.height - (score.coerceIn(0, 100) / 100f) * size.height
                        val isSel = idx == inspectedIndex
                        drawCircle(
                            color = AmberGold,
                            radius = if (isSel) 9f else 5.5f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }

        // Local History Reset Card
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clear Recorded Data", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (confirmClear) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onClearHistory()
                                confirmClear = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseAlert),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Confirm", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { confirmClear = false }, shape = RoundedCornerShape(10.dp)) {
                            Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        border = BorderStroke(1.dp, RoseAlert),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset History", fontSize = 12.sp, color = RoseAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsScreen(
    config: AppConfig,
    isIgnoringBatteryOpt: Boolean,
    canDrawOverlay: Boolean,
    hasUsageAccess: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onUpdateConfig: (AppConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 20-20-20 Break Interval Rules
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = EmeraldBright)
                Text("20-20-20 Break Interval Rules", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))

            val ruleRows = EyeGuardDefaults.SCHEDULE_RULES.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ruleRows.forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { rule ->
                            val selected = config.schedulePresetId == rule.id
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateConfig(
                                            config.copy(
                                                schedulePresetId = rule.id,
                                                workMinutes = if (rule.id == "custom") config.workMinutes else rule.workMinutes,
                                                breakSeconds = if (rule.id == "custom") config.breakSeconds else rule.breakSeconds
                                            )
                                        )
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) Color(0x1F16A388) else InsetCardSurface
                                ),
                                border = BorderStroke(1.dp, if (selected) EmeraldTeal else SubtleBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(rule.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(rule.description, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Active Screen Interval: ${config.workMinutes} min", color = TextPrimary, fontSize = 13.sp)
            Slider(
                value = config.workMinutes.toFloat(),
                onValueChange = {
                    onUpdateConfig(config.copy(schedulePresetId = "custom", workMinutes = it.toInt()))
                },
                valueRange = 5f..60f,
                colors = SliderDefaults.colors(thumbColor = EmeraldTeal, activeTrackColor = EmeraldTeal)
            )

            Text("Break Rest Duration: ${config.breakSeconds} sec", color = TextPrimary, fontSize = 13.sp)
            Slider(
                value = config.breakSeconds.toFloat(),
                onValueChange = {
                    onUpdateConfig(config.copy(schedulePresetId = "custom", breakSeconds = it.toInt()))
                },
                valueRange = 10f..300f,
                colors = SliderDefaults.colors(thumbColor = EmeraldTeal, activeTrackColor = EmeraldTeal)
            )

            Text(
                "Daily Healthy Screen Goal: ${config.dailyLimitMinutes / 60}h ${config.dailyLimitMinutes % 60}m",
                color = TextPrimary,
                fontSize = 13.sp
            )
            Slider(
                value = config.dailyLimitMinutes.toFloat(),
                onValueChange = { onUpdateConfig(config.copy(dailyLimitMinutes = it.toInt())) },
                valueRange = 60f..600f,
                colors = SliderDefaults.colors(thumbColor = EmeraldTeal, activeTrackColor = EmeraldTeal)
            )
        }

        // Sunrise / Sunset Auto-Schedule & Quiet Hours
        SurfaceCard {
            Text("Sunrise / Sunset Auto-Schedule", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            RuleSwitchRow(
                title = "Auto-enable Eye Filter at Sunset",
                subtitle = "Applies comfort overlay from ${config.sunsetStart} to ${config.morningEnd}",
                checked = config.autoEveningSchedule,
                onCheckedChange = { onUpdateConfig(config.copy(autoEveningSchedule = it)) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = config.sunsetStart,
                    onValueChange = { onUpdateConfig(config.copy(sunsetStart = it)) },
                    label = { Text("Sunset Start", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.morningEnd,
                    onValueChange = { onUpdateConfig(config.copy(morningEnd = it)) },
                    label = { Text("Sunrise End", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SubtleBorder)
            Spacer(modifier = Modifier.height(10.dp))

            RuleSwitchRow(
                title = "Quiet Hours (Do Not Disturb)",
                subtitle = "Silences break alerts from ${config.quietStart} to ${config.quietEnd}",
                checked = config.quietHoursEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(quietHoursEnabled = it)) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = config.quietStart,
                    onValueChange = { onUpdateConfig(config.copy(quietStart = it)) },
                    label = { Text("Quiet Start", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.quietEnd,
                    onValueChange = { onUpdateConfig(config.copy(quietEnd = it)) },
                    label = { Text("Quiet End", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Boot & Notification Alert Settings
        SurfaceCard {
            Text("Notification Alerts & Boot Behavior", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            RuleSwitchRow(
                title = "Start automatically on phone boot",
                subtitle = "Launches foreground monitor via BOOT_COMPLETED",
                checked = config.autoStartOnBoot,
                onCheckedChange = { onUpdateConfig(config.copy(autoStartOnBoot = it)) }
            )
            RuleSwitchRow(
                title = "Battery Saver tick cadence (5s)",
                subtitle = "Cuts background timer wakeups by 80%",
                checked = config.batterySaverMode,
                onCheckedChange = { onUpdateConfig(config.copy(batterySaverMode = it)) }
            )
            RuleSwitchRow(
                title = "Break reminder notifications",
                subtitle = "OS banner alerts with Start & Snooze actions",
                checked = config.notificationsEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(notificationsEnabled = it)) }
            )
            RuleSwitchRow(
                title = "Notification sound chime",
                subtitle = "Plays gentle tone when break is due",
                checked = config.soundEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(soundEnabled = it)) }
            )
            RuleSwitchRow(
                title = "Haptic vibration alert",
                subtitle = "Double pulse vibration on break reminder",
                checked = config.vibrateEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(vibrateEnabled = it)) }
            )
            RuleSwitchRow(
                title = "Auto-countdown break overlay over apps",
                subtitle = "Automatically starts 20s countdown on screen over other apps when 20m ends",
                checked = config.showBreakModal,
                onCheckedChange = { onUpdateConfig(config.copy(showBreakModal = it)) }
            )
        }

        // Android System Permissions Status Cards (Direct Settings Intent Buttons)
        PermissionStatusCard(
            title = "Display Over Other Apps (Required for On-Screen Countdown)",
            description = "Lets the automatic 20-second break countdown and Eye Comfort Filter display directly over other apps.",
            isGranted = canDrawOverlay,
            grantedLabel = "Overlay Permitted",
            actionLabel = "Grant Overlay Access",
            onAction = onRequestOverlayPermission
        )

        PermissionStatusCard(
            title = "Battery Optimization Exemption",
            description = "Prevents Android Doze from killing the 20-20-20 screen-on service.",
            isGranted = isIgnoringBatteryOpt,
            grantedLabel = "Unrestricted (Ignored)",
            actionLabel = "Open Battery Settings",
            onAction = onRequestBatteryWhitelist
        )

        PermissionStatusCard(
            title = "Auto-Start After Power Off / Reboot (OEM)",
            description = "OptiRest registers LOCKED_BOOT_COMPLETED & BOOT_COMPLETED. On Xiaomi, Oppo, Vivo, OnePlus, or Samsung devices, also enable Autostart / Background launch in phone settings.",
            isGranted = config.autoStartOnBoot,
            grantedLabel = "Boot Receiver Enabled",
            actionLabel = "Open Auto-Start Settings",
            onAction = onOpenAutoStartSettings,
            secondaryActionLabel = "Open Phone Auto-Start / App Info",
            onSecondaryAction = onOpenAutoStartSettings
        )

        PermissionStatusCard(
            title = "Usage Access (UsageStatsManager)",
            description = "Optional Android system screen-time enrichment for today's foreground usage.",
            isGranted = hasUsageAccess,
            grantedLabel = "Usage Access Granted",
            actionLabel = "Open Usage Access",
            onAction = onRequestUsageAccess
        )

        PermissionStatusCard(
            title = "Post Notifications",
            description = "Allows high-priority 20-20-20 break alert banners. If Android blocks tapping 'Allow' on the popup (due to another screen dimmer/floating overlay), use Notification Settings below.",
            isGranted = hasNotificationPermission,
            grantedLabel = "Notifications Allowed",
            actionLabel = "Allow Notifications",
            onAction = onRequestNotificationPermission,
            secondaryActionLabel = if (!hasNotificationPermission) "Open Notification Settings" else null,
            onSecondaryAction = if (!hasNotificationPermission) onOpenNotificationSettings else null
        )
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(
                text = if (isGranted) grantedLabel else "Action Needed",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGranted) EmeraldBright else AmberGold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(description, fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAction,
            border = BorderStroke(1.dp, if (isGranted) SubtleBorder else AmberGold),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isGranted) "Review in Android Settings" else actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGranted) TextSecondary else AmberGold
            )
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSecondaryAction,
                border = BorderStroke(1.dp, SubtleBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = secondaryActionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldBright
                )
            }
        }
    }
}

@Composable
private fun RuleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldTeal)
        )
    }
}

@Composable
private fun FactorRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, style = tabularStyle(12.sp, FontWeight.SemiBold, valueColor))
    }
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCardSurface),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

private fun tabularStyle(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, color: Color): TextStyle {
    return TextStyle(
        fontSize = size,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        color = color
    )
}

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}

private fun formatCompact(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return when {
        hrs > 0 -> "${hrs}h ${"%02d".format(mins)}m"
        mins > 0 -> "${mins}m ${"%02d".format(secs)}s"
        else -> "${secs}s"
    }
}

private fun formatMinutes(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val hrs = m / 60
    val rem = m % 60
    return if (hrs > 0) "${hrs}h ${"%02d".format(rem)}m" else "${rem}m"
}
