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
        val pendingCount = Regex("""(?m)^\s*(\d+) pending alarms:\s*$""").find(dump)
        if (pendingCount != null) {
            val activeAlarmCount = pendingCount.groupValues[1].toInt()
            if (activeAlarmCount == 0) return 0
            val header = Regex("""^\s*(?:RTC_WAKEUP|RTC|ELAPSED_WAKEUP|ELAPSED) #\d+: Alarm\{""")
            var seenAlarms = 0
            var awaitingTag = false
            var matchingAlarms = 0
            for (line in dump.substring(pendingCount.range.last + 1).lineSequence()) {
                if (header.containsMatchIn(line)) {
                    seenAlarms++
                    if (seenAlarms > activeAlarmCount) break
                    awaitingTag = true
                } else if (awaitingTag && line.trimStart().startsWith("tag=")) {
                    if (line.trim() == "tag=*alarm*:${Actions.ACTION_TIME_UPDATE}") matchingAlarms++
                    awaitingTag = false
                    if (seenAlarms == activeAlarmCount) break
                }
            }
            assertEquals("must parse every active Android 16 alarm entry", activeAlarmCount, seenAlarms)
            return matchingAlarms
        }
        // Legacy Android dumps group active alarms into pending batches.
        val marker = "Pending alarm batches:"
        val start = dump.indexOf(marker)
        if (start < 0) return 0
        val tail = dump.substring(start)
        val end = listOf(
            "Pending user blocked background alarms:",
            "Idle mode state:",
            "Next wake from idle:",
            "Past-due non-wakeup alarms:"
        ).map { tail.indexOf(it) }.filter { it > 0 }.minOrNull() ?: tail.length
        return tail.substring(0, end).lineSequence()
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
    fun datePresentationCrossesMidnightLeapDayAndDstTransitions() {
        // E7/E8 bounded proof: exercise production DateHelper formatting at exact
        // civil-time boundaries. This does NOT prove a hosted launcher receives
        // and re-renders the system's midnight/time-zone-change broadcast.
        val savedZone = java.util.TimeZone.getDefault()
        val savedLocale = java.util.Locale.getDefault()
        val savedFormat = Preferences.dateFormat
        val savedUppercase = Preferences.isDateUppercase
        val savedCapitalize = Preferences.isDateCapitalize
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            java.util.Locale.setDefault(java.util.Locale.US)
            Preferences.dateFormat = "yyyy-MM-dd HH:mm z"
            Preferences.isDateUppercase = false
            Preferences.isDateCapitalize = false

            fun show(utc: String): String {
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = java.time.Instant.parse(utc).toEpochMilli()
                }
                return com.ramybaheeg.yetanotherwidget.helpers.DateHelper.getDateText(context, calendar)
            }
            // A local New Year midnight and leap-day rollover.
            assertEquals("2025-12-31 23:59 PST", show("2026-01-01T07:59:00Z"))
            assertEquals("2026-01-01 00:01 PST", show("2026-01-01T08:01:00Z"))
            assertEquals("2024-02-28 23:59 PST", show("2024-02-29T07:59:00Z"))
            assertEquals("2024-02-29 00:01 PST", show("2024-02-29T08:01:00Z"))
            assertEquals("2024-03-01 00:01 PST", show("2024-03-01T08:01:00Z"))
            // Spring-forward skips the 02:00 local hour; fall-back repeats 01:30.
            assertEquals("2026-03-08 01:59 PST", show("2026-03-08T09:59:00Z"))
            assertEquals("2026-03-08 03:01 PDT", show("2026-03-08T10:01:00Z"))
            assertEquals("2026-11-01 01:30 PDT", show("2026-11-01T08:30:00Z"))
            assertEquals("2026-11-01 01:30 PST", show("2026-11-01T09:30:00Z"))
        } finally {
            Preferences.dateFormat = savedFormat
            Preferences.isDateUppercase = savedUppercase
            Preferences.isDateCapitalize = savedCapitalize
            java.util.Locale.setDefault(savedLocale)
            java.util.TimeZone.setDefault(savedZone)
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
