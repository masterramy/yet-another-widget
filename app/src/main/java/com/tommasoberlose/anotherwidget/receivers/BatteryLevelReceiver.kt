package com.ramybaheeg.yetanotherwidget.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import com.ramybaheeg.yetanotherwidget.utils.toast

class BatteryLevelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action) {
            Intent.ACTION_BATTERY_LOW -> Preferences.isBatteryLevelLow = true
            Intent.ACTION_BATTERY_OKAY -> Preferences.isBatteryLevelLow = false
            Intent.ACTION_POWER_CONNECTED -> Preferences.isCharging = true
            Intent.ACTION_POWER_DISCONNECTED -> Preferences.isCharging = false
        }
        MainWidget.updateWidget(context)
    }

}