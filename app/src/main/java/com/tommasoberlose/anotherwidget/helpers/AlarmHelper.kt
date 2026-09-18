package com.tommasoberlose.anotherwidget.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import com.tommasoberlose.anotherwidget.global.Actions
import com.tommasoberlose.anotherwidget.receivers.UpdatesReceiver
import java.text.SimpleDateFormat
import java.util.*

object AlarmHelper {
    fun getNextAlarm(context: Context): String = with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
        val alarm = nextAlarmClock
        return if (
            alarm != null
            && alarm.triggerTime - Calendar.getInstance().timeInMillis > 5 * 60 * 1000
        ) {
            setTimeout(context, alarm.triggerTime)
            "%s %s".format(
                SimpleDateFormat("EEE", Locale.getDefault()).format(alarm.triggerTime),
                DateFormat.getTimeFormat(context).format(Date(alarm.triggerTime))
            )
        } else {
            ""
        }
    }

    fun isAlarmProbablyWrong(context: Context): Boolean {
        with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
            val alarm = nextAlarmClock
            return (
                alarm != null
                && alarm.triggerTime - Calendar.getInstance().timeInMillis < 5 * 60 * 1000
            )
        }
    }

    private fun setTimeout(context: Context, trigger: Long) {
        with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
            val intent = Intent(context, UpdatesReceiver::class.java).apply {
                action = Actions.ACTION_ALARM_UPDATE
            }
            val operation = PendingIntent.getBroadcast(
                context,
                ALARM_UPDATE_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            cancel(operation)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canScheduleExactAlarms() ->
                    setExact(AlarmManager.RTC, trigger, operation)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    setAndAllowWhileIdle(AlarmManager.RTC, trigger, operation)
                else -> set(AlarmManager.RTC, trigger, operation)
            }
        }
    }

    private const val ALARM_UPDATE_ID = 24953
}