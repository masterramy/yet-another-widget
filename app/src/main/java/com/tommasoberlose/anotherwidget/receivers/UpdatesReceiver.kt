package com.tommasoberlose.anotherwidget.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tommasoberlose.anotherwidget.db.EventRepository
import com.tommasoberlose.anotherwidget.global.Actions
import com.tommasoberlose.anotherwidget.global.Constants
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.helpers.*
import com.tommasoberlose.anotherwidget.models.Event
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.Period
import java.util.*

class UpdatesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Actions.ACTION_CALENDAR_UPDATE -> {
                ActiveNotificationsHelper.clearLastNotification(context)
                MediaPlayerHelper.updatePlayingMediaInfo(context)
                CalendarHelper.updateEventList(context)
            }

            "com.sec.android.widgetapp.APPWIDGET_RESIZE",
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            Actions.ACTION_ALARM_UPDATE,
            Actions.ACTION_TIME_UPDATE -> {
                MainWidget.updateWidget(context)
                if (intent.hasExtra(EVENT_ID)) setUpdates(context, intent.getLongExtra(EVENT_ID, -1))
            }

            Actions.ACTION_CLEAR_NOTIFICATION -> {
                ActiveNotificationsHelper.clearLastNotification(context)
                MainWidget.updateWidget(context)
            }
            Actions.ACTION_UPDATE_GREETINGS -> MainWidget.updateWidget(context)

            Actions.ACTION_REFRESH -> {
                GlobalScope.launch(Dispatchers.IO) {
                    CalendarHelper.updateEventList(context)
                    MediaPlayerHelper.updatePlayingMediaInfo(context)
                    WeatherHelper.updateWeather(context)
                }
            }
        }
    }

    companion object {
        const val EVENT_ID = "EVENT_ID"

        private fun AlarmManager.scheduleBestEffort(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canScheduleExactAlarms() -> setExact(type, triggerAtMillis, operation)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> setAndAllowWhileIdle(type, triggerAtMillis, operation)
                else -> set(type, triggerAtMillis, operation)
            }
        }

        fun setUpdates(context: Context, eventId: Long? = null) {
            val eventRepository = EventRepository(context)
            if (eventId == null) {
                removeUpdates(context)
                eventRepository.getFutureEvents().forEach { event -> setEventUpdate(context, event) }
            } else {
                eventRepository.getEventByEventId(eventId)?.let { setEventUpdate(context, it) }
            }
            eventRepository.close()
        }

        private fun setEventUpdate(context: Context, event: Event) {
            with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
                val now = Calendar.getInstance().apply {
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diff = Period(now.timeInMillis, event.startDate)
                val limit = when (Preferences.showUntil) {
                    0 -> 1000 * 60 * 60 * 3
                    1 -> 1000 * 60 * 60 * 6
                    2 -> 1000 * 60 * 60 * 12
                    3 -> 1000 * 60 * 60 * 24
                    4 -> 1000 * 60 * 60 * 24 * 3
                    5 -> 1000 * 60 * 60 * 24 * 7
                    6 -> 1000 * 60 * 30
                    7 -> 1000 * 60 * 60
                    else -> 1000 * 60 * 60 * 6
                }
                if (event.startDate <= limit) {
                    if (event.startDate > now.timeInMillis) {
                        if (diff.hours == 0) {
                            var minutes = 0
                            when (Preferences.widgetUpdateFrequency) {
                                Constants.WidgetUpdateFrequency.DEFAULT.rawValue -> {
                                    minutes = when {
                                        diff.minutes > 50 -> 50
                                        diff.minutes > 30 -> 30
                                        diff.minutes > 15 -> 15
                                        else -> 0
                                    }
                                }
                                Constants.WidgetUpdateFrequency.HIGH.rawValue -> minutes = diff.minutes - (diff.minutes % 5)
                            }
                            scheduleBestEffort(
                                AlarmManager.RTC,
                                if (event.startDate - minutes * 1000 * 60 > (now.timeInMillis + 120 * 1000)) event.startDate - 60 * 1000 * minutes else now.timeInMillis + 120000,
                                PendingIntent.getBroadcast(
                                    context,
                                    event.eventID.toInt(),
                                    Intent(context, UpdatesReceiver::class.java).apply {
                                        action = Actions.ACTION_TIME_UPDATE
                                        putExtra(EVENT_ID, event.eventID)
                                    },
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        } else {
                            scheduleBestEffort(
                                AlarmManager.RTC,
                                event.startDate - diff.hours * 1000 * 60 * 60 + if (diff.minutes > 30) (-30) else (+30),
                                PendingIntent.getBroadcast(
                                    context,
                                    event.eventID.toInt(),
                                    Intent(context, UpdatesReceiver::class.java).apply {
                                        action = Actions.ACTION_TIME_UPDATE
                                        putExtra(EVENT_ID, event.eventID)
                                    },
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                    } else {
                        val fireTime = if (event.endDate > now.timeInMillis + 120 * 1000) event.endDate else now.timeInMillis + 120000
                        scheduleBestEffort(
                            AlarmManager.RTC,
                            fireTime,
                            PendingIntent.getBroadcast(
                                context,
                                event.eventID.toInt(),
                                Intent(context, UpdatesReceiver::class.java).apply { action = Actions.ACTION_TIME_UPDATE },
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    }
                } else {
                    scheduleBestEffort(
                        AlarmManager.RTC,
                        if (event.startDate - limit > now.timeInMillis + 120 * 1000) event.startDate - limit else now.timeInMillis + 120000,
                        PendingIntent.getBroadcast(
                            context,
                            event.eventID.toInt(),
                            Intent(context, UpdatesReceiver::class.java).apply {
                                action = Actions.ACTION_TIME_UPDATE
                                putExtra(EVENT_ID, event.eventID)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
        }

        fun removeUpdates(context: Context) {
            with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
                val eventRepository = EventRepository(context)
                eventRepository.getFutureEvents().forEach {
                    cancel(
                        PendingIntent.getBroadcast(
                            context,
                            it.eventID.toInt(),
                            Intent(context, UpdatesReceiver::class.java),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                eventRepository.close()
            }
        }
    }
}
