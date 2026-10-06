package com.ramybaheeg.yetanotherwidget

import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.db.EventRepository
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.models.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5CalendarRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repo: EventRepository

    @Before fun prepare() {
        Kotpref.init(context)
        repo = EventRepository(context)
        repo.clearEvents()
        repo.resetNextEventData()
        Preferences.showUntil = 5
        Preferences.calendarAllDay = true
        Preferences.showDeclinedEvents = true
        Preferences.showAcceptedEvents = true
        Preferences.showInvitedEvents = true
        Preferences.showOnlyBusyEvents = false
    }

    private fun e(
        id: Long,
        minutes: Long,
        allDay: Boolean = false,
        attendee: Int = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
        availability: Int = CalendarContract.EventsEntity.AVAILABILITY_BUSY
    ) = Event(
        id = id,
        eventID = 1000 + id,
        title = "Q5 $id",
        startDate = System.currentTimeMillis() + minutes * 60_000L,
        endDate = System.currentTimeMillis() + (minutes + 20) * 60_000L,
        calendarID = 1,
        allDay = allDay,
        selfAttendeeStatus = attendee,
        availability = availability
    )

    @Test fun filtersAndHorizonApply() {
        val timed = e(1, 10)
        repo.saveEvents(listOf(
            timed,
            e(2, 20, allDay = true),
            e(3, 30, availability = CalendarContract.EventsEntity.AVAILABILITY_FREE),
            e(4, 40, attendee = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
            e(5, 50, attendee = CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
        ))
        assertEquals(5, repo.getEventsCount())
        Preferences.calendarAllDay = false
        Preferences.showOnlyBusyEvents = true
        Preferences.showDeclinedEvents = false
        Preferences.showInvitedEvents = false
        assertEquals(1, repo.getEventsCount())
        assertEquals(timed.eventID, repo.getNextEvent()?.eventID)

        repo.saveEvents(listOf(e(10, 20), e(11, 45), e(12, 120)))
        repo.resetNextEventData()
        Preferences.calendarAllDay = true
        Preferences.showOnlyBusyEvents = false
        Preferences.showDeclinedEvents = true
        Preferences.showInvitedEvents = true
        Preferences.showUntil = 6
        assertEquals(1, repo.getEventsCount())
        Preferences.showUntil = 7
        repo.resetNextEventData()
        assertEquals(2, repo.getEventsCount())
        Preferences.showUntil = 0
        repo.resetNextEventData()
        assertEquals(3, repo.getEventsCount())
    }

    @Test fun nextPreviousCyclingWraps() {
        val a=e(20,10); val b=e(21,20); val c=e(22,30)
        repo.saveEvents(listOf(a,b,c))
        assertEquals(a.eventID, repo.getNextEvent()?.eventID)
        repo.goToNextEvent(); assertEquals(b.eventID, repo.getNextEvent()?.eventID)
        repo.goToNextEvent(); assertEquals(c.eventID, repo.getNextEvent()?.eventID)
        repo.goToNextEvent(); assertEquals(a.eventID, repo.getNextEvent()?.eventID)
        repo.goToPreviousEvent(); assertEquals(c.eventID, repo.getNextEvent()?.eventID)
    }

    @Test fun emptyRepositoryHasNoVisibleEventState() {
        repo.clearEvents()
        repo.resetNextEventData()
        assertEquals(0, repo.getEventsCount())
        assertNull(repo.getNextEvent())
    }

}
