package com.tommasoberlose.anotherwidget.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Q1 compatibility shim. Remote crash telemetry is intentionally removed. */
class CrashlyticsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit

    companion object {
        fun sendCrash(context: Context, exception: Exception) {
            Log.e("AnotherWidget", "Caught widget/runtime exception", exception)
        }
    }
}
