package com.ramybaheeg.yetanotherwidget.services

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
import com.ramybaheeg.yetanotherwidget.db.EventRepository
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.CalendarHelper
import com.ramybaheeg.yetanotherwidget.helpers.CalendarHelper.applyFilters
import com.ramybaheeg.yetanotherwidget.helpers.CalendarHelper.sortEvents
import com.ramybaheeg.yetanotherwidget.models.Event
import com.ramybaheeg.yetanotherwidget.receivers.UpdatesReceiver
import com.ramybaheeg.yetanotherwidget.ui.fragments.MainFragment
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import com.ramybaheeg.yetanotherwidget.utils.checkGrantedPermission
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
                val begin = now.timeInMillis + now.timeZone.getOffset(now.timeInMillis).coerceAtMost(0)
                val end = limit.timeInMillis + limit.timeZone.getOffset(limit.timeInMillis).coerceAtLeast(0)
                val filteredCalendars = CalendarHelper.getFilteredCalendarIdList()
                val events = mutableListOf<Event>()
                val projection = arrayOf(
                    CalendarContract.Instances._ID,
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                    CalendarContract.Instances.AVAILABILITY
                )

                CalendarContract.Instances.query(
                    context.contentResolver,
                    projection,
                    begin,
                    end
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val calendarId = cursor.getLong(5)
                        if (filteredCalendars.contains(calendarId)) continue

                        var instanceBegin = cursor.getLong(3)
                        var instanceEnd = cursor.getLong(4)
                        val allDay = cursor.getInt(6) != 0
                        if (allDay) {
                            val start = Calendar.getInstance().apply { timeInMillis = instanceBegin }
                            val finish = Calendar.getInstance().apply { timeInMillis = instanceEnd }
                            instanceBegin -= start.timeZone.getOffset(start.timeInMillis)
                            instanceEnd -= finish.timeZone.getOffset(finish.timeInMillis)
                        }

                        if (instanceBegin <= limit.timeInMillis && now.timeInMillis < instanceEnd) {
                            events += Event(
                                id = cursor.getLong(0),
                                eventID = cursor.getLong(1),
                                title = cursor.getString(2) ?: "",
                                startDate = instanceBegin,
                                endDate = instanceEnd,
                                calendarID = calendarId.toInt(),
                                allDay = allDay,
                                address = cursor.getString(7) ?: "",
                                selfAttendeeStatus = cursor.getInt(8),
                                availability = cursor.getInt(9)
                            )
                        }
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
