package com.ramybaheeg.yetanotherwidget.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.global.Actions
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.IntentHelper
import com.ramybaheeg.yetanotherwidget.utils.toast


class WidgetClickListenerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Actions.ACTION_OPEN_WEATHER_INTENT) {
            try {
                if (Preferences.weatherAppPackage == IntentHelper.REFRESH_WIDGET_OPTION) {
                    context.sendBroadcast(IntentHelper.getWeatherIntent(context))
                } else {
                    context.startActivity(IntentHelper.getWeatherIntent(context))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val uri = Uri.parse("https://www.google.com/search?q=weather")
                val i = Intent(Intent.ACTION_VIEW, uri)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(i)
                } catch (ignored: Exception) {
                    context.toast(context.getString(R.string.error_opening_app))
                }
            }
        }
    }
}
