package com.eyeguard.android

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class EyeGuardTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val active = ScreenFilterService.isRunning && !ScreenFilterService.isPaused
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "EyeGuard"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (active) "${ScreenFilterService.currentKelvin}K" else "Paused"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ScreenFilterService::class.java).apply {
            action = if (ScreenFilterService.isRunning) {
                ScreenFilterService.ACTION_TOGGLE_PAUSE
            } else {
                ScreenFilterService.ACTION_UPDATE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        onStartListening()
    }
}
