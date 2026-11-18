package com.eyeguard.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EyeGuardMobileScreen(
                        hasOverlayPermission = {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
                        },
                        requestOverlayPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            }
                        },
                        hasBatteryExemption = { isIgnoringBatteryOptimizations() },
                        requestBatteryExemption = { requestIgnoreBatteryOptimizations() },
                        applyFilter = { modeId, kelvin, brightness ->
                            val intent = Intent(this, ScreenFilterService::class.java).apply {
                                action = ScreenFilterService.ACTION_UPDATE
                                putExtra(ScreenFilterService.EXTRA_MODE_ID, modeId)
                                putExtra(ScreenFilterService.EXTRA_KELVIN, kelvin)
                                putExtra(ScreenFilterService.EXTRA_BRIGHTNESS, brightness)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        },
                        toggleFocusBand = {
                            startService(
                                Intent(this, ScreenFilterService::class.java).apply {
                                    action = ScreenFilterService.ACTION_TOGGLE_FOCUS
                                }
                            )
                        },
                        triggerBreak = {
                            startService(
                                Intent(this, ScreenFilterService::class.java).apply {
                                    action = ScreenFilterService.ACTION_TRIGGER_BREAK
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EyeGuardMobileScreen(
    hasOverlayPermission: () -> Boolean,
    requestOverlayPermission: () -> Unit,
    hasBatteryExemption: () -> Boolean,
    requestBatteryExemption: () -> Unit,
    applyFilter: (String, Int, Int) -> Unit,
    toggleFocusBand: () -> Unit,
    triggerBreak: () -> Unit
) {
    var activeMode by remember { mutableStateOf(EyeGuardDefaults.INITIAL_MODE_ID) }
    var kelvin by remember { mutableStateOf(EyeGuardDefaults.INITIAL_KELVIN.toFloat()) }
    var brightness by remember { mutableStateOf(EyeGuardDefaults.INITIAL_BRIGHTNESS.toFloat()) }
    var overlayReady by remember { mutableStateOf(hasOverlayPermission()) }
    var batteryReady by remember { mutableStateOf(hasBatteryExemption()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("EyeGuard Mobile", style = MaterialTheme.typography.headlineSmall)

        if (!overlayReady) {
            Button(
                onClick = {
                    requestOverlayPermission()
                    overlayReady = hasOverlayPermission()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("1. Grant Display Over Apps Permission")
            }
        }

        if (EyeGuardDefaults.IGNORE_BATTERY_OPTIMIZATION && !batteryReady) {
            OutlinedButton(
                onClick = {
                    requestBatteryExemption()
                    batteryReady = hasBatteryExemption()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("2. Ignore Battery Optimization (Run in Background)")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(108.dp)
        ) {
            items(EyeGuardDefaults.PRESETS) { preset ->
                val selected = preset.id == activeMode
                Button(
                    onClick = {
                        activeMode = preset.id
                        kelvin = preset.kelvin.toFloat()
                        brightness = preset.brightness.toFloat()
                        applyFilter(preset.id, preset.kelvin, preset.brightness)
                    },
                    colors = if (selected) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(preset.name, maxLines = 1)
                }
            }
        }

        Text("Color Temperature: ${kelvin.toInt()} K")
        Slider(
            value = kelvin,
            onValueChange = {
                kelvin = it
                applyFilter(activeMode, it.toInt(), brightness.toInt())
            },
            valueRange = 1200f..6500f
        )

        Text("Extra Dim Brightness: ${brightness.toInt()}%")
        Slider(
            value = brightness,
            onValueChange = {
                brightness = it
                applyFilter(activeMode, kelvin.toInt(), it.toInt())
            },
            valueRange = 20f..100f
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = toggleFocusBand,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Focus Band")
            }
            Button(
                onClick = triggerBreak,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Rest Now")
            }
        }
    }
}
