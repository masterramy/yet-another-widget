package com.tommasoberlose.anotherwidget.models

import android.provider.CalendarContract
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "events")
data class Event(
    @PrimaryKey
    var id: Long = 0,
    var eventID: Long = 0,
    var title: String = "",
    var startDate: Long = 0,
    var endDate: Long = 0,
    var calendarID: Int = 0,
    var allDay: Boolean = false,
    var address: String = "",
    var selfAttendeeStatus: Int = CalendarContract.Attendees.ATTENDEE_STATUS_NONE,
    var availability: Int = CalendarContract.EventsEntity.AVAILABILITY_BUSY
) {
    override fun toString(): String =
        "Event:\nEVENT ID: $eventID\nTITLE: $title\nSTART DATE: ${Date(startDate)}\nEND DATE: ${Date(endDate)}\nCAL ID: $calendarID\nADDRESS: $address"
}
