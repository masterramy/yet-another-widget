package com.tommasoberlose.anotherwidget.helpers

import android.Manifest
import android.content.Context
import android.provider.CalendarContract
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.models.Event
import com.tommasoberlose.anotherwidget.services.UpdateCalendarWorker
import com.tommasoberlose.anotherwidget.utils.checkGrantedPermission
import java.util.Calendar

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String
)

object CalendarHelper {
    fun updateEventList(context: Context) {
        UpdateCalendarWorker.enqueue(context)
    }

    fun getCalendarList(context: Context): List<CalendarInfo> {
        if (!context.checkGrantedPermission(Manifest.permission.READ_CALENDAR)) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val calendars = mutableListOf<CalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars += CalendarInfo(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1) ?: "",
                    accountName = cursor.getString(2) ?: ""
                )
            }
        }
        return calendars
    }

    fun getFilteredCalendarIdList(): List<Long> =
        Preferences.calendarFilter.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLong() }

    fun filterCalendar(list: List<Long>) {
        Preferences.calendarFilter = list.joinToString(separator = ",", prefix = " ")
    }

    fun setEventUpdatesAndroidN(context: Context) = UpdateCalendarWorker.enqueueTrigger(context)
    fun removeEventUpdatesAndroidN(context: Context) = UpdateCalendarWorker.cancelTrigger(context)

    fun List<Event>.applyFilters(): List<Event> = asSequence()
        .filter { Preferences.showDeclinedEvents || it.selfAttendeeStatus != CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED }
        .filter { Preferences.showAcceptedEvents || it.selfAttendeeStatus != CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED }
        .filter { Preferences.showInvitedEvents || it.selfAttendeeStatus != CalendarContract.Attendees.ATTENDEE_STATUS_INVITED }
        .filter { Preferences.calendarAllDay || !it.allDay }
        .filter { !Preferences.showOnlyBusyEvents || it.availability != CalendarContract.EventsEntity.AVAILABILITY_FREE }
        .toList()

    fun List<Event>.sortEvents(): List<Event> = sortedWith { event, event1 ->
        val date = Calendar.getInstance().apply { timeInMillis = event.startDate }
        val date1 = Calendar.getInstance().apply { timeInMillis = event1.startDate }
        if (date.get(Calendar.DAY_OF_YEAR) == date1.get(Calendar.DAY_OF_YEAR) && date.get(Calendar.YEAR) == date1.get(Calendar.YEAR)) {
            when {
                event.allDay && event1.allDay -> event.startDate.compareTo(event1.startDate)
                event.allDay -> 1
                event1.allDay -> -1
                else -> event.startDate.compareTo(event1.startDate)
            }
        } else {
            event.startDate.compareTo(event1.startDate)
        }
    }
}
