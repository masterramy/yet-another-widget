package com.ramybaheeg.yetanotherwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5SchedulingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val eventId = 99123L

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
            .bufferedReader()
            .use { it.readText() }
    }

    private fun scheduledTimeUpdateCount(): Int {
        val dump = shell("dumpsys alarm")
        val marker = "Pending alarm batches:"
        val start = dump.indexOf(marker)
        if (start < 0) return 0

        val tail = dump.substring(start)
        val end = listOf(
            "Pending user blocked background alarms:",
            "Idle mode state:",
            "Next wake from idle:",
            "Past-due non-wakeup alarms:"
        )
            .map { tail.indexOf(it) }
            .filter { it > 0 }
            .minOrNull() ?: tail.length

        return tail.substring(0, end)
            .lineSequence()
            .count { it.contains("tag=*alarm*:${Actions.ACTION_TIME_UPDATE}") }
    }

    private fun eventUpdatePendingIntent(action: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            Intent(context, UpdatesReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelExactTestAlarm() {
        val operation = eventUpdatePendingIntent(Actions.ACTION_TIME_UPDATE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(operation)
        operation.cancel()
    }

    @Before
    fun prepare() {
        Kotpref.init(context)
        cancelExactTestAlarm()
        Preferences.showUntil = 0
        Preferences.widgetUpdateFrequency = Constants.WidgetUpdateFrequency.DEFAULT.rawValue
        Preferences.showAcceptedEvents = true
        Preferences.showInvitedEvents = true
        Preferences.showDeclinedEvents = true
        Preferences.calendarAllDay = true
        Preferences.showOnlyBusyEvents = false
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
        assertEquals("test must start without a pending YAW time-update alarm", 0, scheduledTimeUpdateCount())
    }

    @After
    fun cleanUp() {
        cancelExactTestAlarm()
        EventRepository(context).apply {
            clearEvents()
            resetNextEventData()
            close()
        }
    }

    @Test
    fun removeUpdatesCancelsTheSameScheduledCalendarAlarmIdentity() {
        UpdatesReceiver.setUpdates(context)
        val pendingAfterSet = scheduledTimeUpdateCount()
        assertTrue(
            "setUpdates must schedule an ACTION_TIME_UPDATE alarm for the event; activeCount=$pendingAfterSet; dumpsys alarm=\n" +
                shell("dumpsys alarm").take(18000),
            pendingAfterSet > 0
        )

        // Regression control: this is the historical actionless cancellation
        // identity. It must not cancel the ACTION_TIME_UPDATE alarm.
        val wrongIdentity = eventUpdatePendingIntent(null)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(wrongIdentity)
        wrongIdentity.cancel()
        assertTrue(
            "historical actionless cancellation must leave the scheduled ACTION_TIME_UPDATE alarm pending",
            scheduledTimeUpdateCount() > 0
        )

        UpdatesReceiver.removeUpdates(context)
        assertEquals(
            "removeUpdates must clear the pending ACTION_TIME_UPDATE alarm",
            0,
            scheduledTimeUpdateCount()
        )
    }
}
