package com.optirest.eyecare.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.optirest.eyecare.service.EyeCareForegroundService

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val serviceIntent = Intent(context, EyeCareForegroundService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
