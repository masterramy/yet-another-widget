package com.tommasoberlose.anotherwidget.services

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tommasoberlose.anotherwidget.db.EventRepository
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.helpers.CalendarHelper
import com.tommasoberlose.anotherwidget.helpers.CalendarHelper.applyFilters
import com.tommasoberlose.anotherwidget.helpers.CalendarHelper.sortEvents
import com.tommasoberlose.anotherwidget.models.Event
import com.tommasoberlose.anotherwidget.receivers.UpdatesReceiver
import com.tommasoberlose.anotherwidget.ui.fragments.MainFragment
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import com.tommasoberlose.anotherwidget.utils.checkGrantedPermission
import me.everything.providers.android.calendar.CalendarProvider
import org.greenrobot.eventbus.EventBus
import java.util.Calendar

class UpdateCalendarWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val repo = EventRepository(context)
        try {
            UpdatesReceiver.removeUpdates(context)
            if (!Preferences.showEvents) {
                repo.resetNextEventData()
                repo.clearEvents()
            } else if (!context.checkGrantedPermission(Manifest.permission.READ_CALENDAR)) {
                repo.resetNextEventData()
                repo.clearEvents()
            } else {
                val now = Calendar.getInstance()
                val limit = Calendar.getInstance().apply {
                    set(Calendar.MILLISECOND, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.HOUR_OF_DAY, 0)
                    add(Calendar.DATE, 1)
                    when (Preferences.showUntil) {
                        0 -> add(Calendar.HOUR, 3)
                        1 -> add(Calendar.HOUR, 6)
                        2 -> add(Calendar.HOUR, 12)
                        3 -> add(Calendar.DAY_OF_MONTH, 1)
                        4 -> add(Calendar.DAY_OF_MONTH, 3)
                        5 -> add(Calendar.DAY_OF_MONTH, 7)
                        6 -> add(Calendar.MINUTE, 30)
                        7 -> add(Calendar.HOUR, 1)
                        else -> add(Calendar.HOUR, 6)
                    }
                }
                val filteredCalendars = CalendarHelper.getFilteredCalendarIdList()
                val events = mutableListOf<Event>()
                val provider = CalendarProvider(context)
                provider.getInstances(
                    now.timeInMillis + now.timeZone.getOffset(now.timeInMillis).coerceAtMost(0),
                    limit.timeInMillis + limit.timeZone.getOffset(limit.timeInMillis).coerceAtLeast(0)
                )?.list?.forEach { instance ->
                    try {
                        val event = provider.getEvent(instance.eventId) ?: return@forEach
                        if (event.deleted || filteredCalendars.contains(event.calendarId)) return@forEach
                        if (event.allDay) {
                            val start = Calendar.getInstance().apply { timeInMillis = instance.begin }
                            val end = Calendar.getInstance().apply { timeInMillis = instance.end }
                            instance.begin -= start.timeZone.getOffset(start.timeInMillis)
                            instance.end -= end.timeZone.getOffset(end.timeInMillis)
                        }
                        if (instance.begin <= limit.timeInMillis && now.timeInMillis < instance.end) {
                            events += Event(
                                id = instance.id,
                                eventID = event.id,
                                title = event.title ?: "",
                                startDate = instance.begin,
                                endDate = instance.end,
                                calendarID = event.calendarId.toInt(),
                                allDay = event.allDay,
                                address = event.eventLocation ?: "",
                                selfAttendeeStatus = event.selfAttendeeStatus.toInt(),
                                availability = event.availability
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
                val filtered = events.sortEvents().applyFilters()
                if (filtered.isEmpty()) {
                    repo.resetNextEventData()
                    repo.clearEvents()
                } else {
                    repo.saveEvents(filtered)
                    repo.saveNextEventData(filtered.first())
                }
            }
            UpdatesReceiver.setUpdates(context)
            MainWidget.updateWidget(context)
            EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
            if (Preferences.showEvents) enqueueTrigger(context)
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        } finally {
            repo.close()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "updateEventList",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCalendarWorker>().build()
            )
        }

        fun enqueueTrigger(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                "updateEventListTrigger",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<Trigger>()
                    .setConstraints(
                        Constraints.Builder()
                            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                            .build()
                    )
                    .build()
            )
        }

        fun cancelTrigger(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            WorkManager.getInstance(context).cancelUniqueWork("updateEventListTrigger")
        }
    }

    class Trigger(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            if (Preferences.showEvents && !isStopped) enqueue(applicationContext)
            return Result.success()
        }
    }
}
