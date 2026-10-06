package com.ramybaheeg.yetanotherwidget

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.db.EventRepository
import com.ramybaheeg.yetanotherwidget.global.Actions
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.models.Event
import com.ramybaheeg.yetanotherwidget.receivers.UpdatesReceiver
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5SchedulingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val eventId = 99123L

    @Before
    fun prepare() {
        Kotpref.init(context)
        Preferences.showUntil = 0
        Preferences.widgetUpdateFrequency = Constants.WidgetUpdateFrequency.DEFAULT.rawValue
        exactPendingIntent(PendingIntent.FLAG_NO_CREATE)?.cancel()
        EventRepository(context).apply {
            clearEvents()
            resetNextEventData()
            saveEvents(
                listOf(
                    Event(
                        id = 99123,
                        eventID = eventId,
                        title = "Q5 scheduled event",
                        startDate = System.currentTimeMillis() + 15 * 60_000L,
                        endDate = System.currentTimeMillis() + 45 * 60_000L,
                        calendarID = 1,
                        allDay = false,
                        selfAttendeeStatus = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
                        availability = android.provider.CalendarContract.EventsEntity.AVAILABILITY_BUSY
                    )
                )
            )
        }
    }

    @After
    fun cleanUp() {
        exactPendingIntent(PendingIntent.FLAG_NO_CREATE)?.cancel()
        EventRepository(context).apply {
            clearEvents()
            resetNextEventData()
            close()
        }
    }

    private fun exactPendingIntent(extraFlags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            Intent(context, UpdatesReceiver::class.java).apply {
                action = Actions.ACTION_TIME_UPDATE
            },
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )

    @Test
    fun removeUpdatesCancelsTheExactScheduledCalendarPendingIntent() {
        UpdatesReceiver.setUpdates(context)

        assertNotNull(
            "setUpdates must create the ACTION_TIME_UPDATE PendingIntent for the event",
            exactPendingIntent(PendingIntent.FLAG_NO_CREATE)
        )

        UpdatesReceiver.removeUpdates(context)

        assertNull(
            "removeUpdates must cancel the same ACTION_TIME_UPDATE PendingIntent identity",
            exactPendingIntent(PendingIntent.FLAG_NO_CREATE)
        )
    }
}
