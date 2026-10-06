package com.ramybaheeg.yetanotherwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.db.EventRepository
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.models.Event
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Q5StoreMediaSetupTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before
    fun prepare() {
        Kotpref.init(context)
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_CALENDAR
        )
    }

    @Test
    fun seedControlledDemoStateForExactCandidateScreenshots() {
        val now = System.currentTimeMillis()
        val start = now + TimeUnit.MINUTES.toMillis(75)
        val end = start + TimeUnit.HOURS.toMillis(1)

        Preferences.showEvents = true
        Preferences.calendarAllDay = true
        Preferences.showOnlyBusyEvents = false
        Preferences.showDeclinedEvents = true
        Preferences.showInvitedEvents = true
        Preferences.showAcceptedEvents = true
        Preferences.showDiffTime = true
        Preferences.showNextEvent = true
        Preferences.showNextEventOnMultipleLines = false
        Preferences.secondRowInformation = 0
        Preferences.showUntil = 3

        EventRepository(context).apply {
            clearEvents()
            resetNextEventData()
            saveEvents(
                listOf(
                    Event(
                        id = 9001,
                        eventID = 99001,
                        title = "Dinner with friends",
                        startDate = start,
                        endDate = end,
                        calendarID = 1,
                        allDay = false,
                        selfAttendeeStatus = 1,
                        availability = 0,
                        address = "Ontario, CA"
                    )
                )
            )
        }

        Preferences.showWeather = true
        Preferences.weatherProvider = Constants.WeatherProvider.WEATHER_GOV.rawValue
        Preferences.customLocationAdd = "Washington, DC"
        Preferences.customLocationLat = "38.8894"
        Preferences.customLocationLon = "-77.0352"
        Preferences.weatherProviderError = ""
        Preferences.weatherProviderLocationError = ""
        Preferences.weatherIcon = "01d"
        Preferences.weatherTemp = 72f
        Preferences.weatherTempUnit = "F"
        Preferences.weatherRealTempUnit = "F"
        Preferences.weatherUpdatedAt = now

        Preferences.showClock = false
        Preferences.widgetAlign = Constants.WidgetAlign.CENTER.rawValue
        Preferences.showNextAlarm = false
        Preferences.showBatteryCharging = false
        Preferences.showGreetings = false
        Preferences.showNotifications = false
        Preferences.showMusic = false
        Preferences.showEventsAsGlanceProvider = false
        Preferences.customNotes = ""

        val widgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, MainWidget::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        assertTrue("store-media setup requires a real hosted YAW widget", widgetIds.isNotEmpty())
        widgetIds.forEach { widgetId ->
            MainWidget.updateAppWidget(context, widgetManager, widgetId)
        }

        assertTrue(Preferences.showEvents)
        assertTrue(Preferences.showWeather)
        assertTrue(Preferences.weatherIcon.isNotEmpty())
        assertTrue(EventRepository(context).getEventsCount() > 0)
    }
}
