package com.ramybaheeg.yetanotherwidget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.IntentHelper
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Q5 H9: observable state only, no synthetic tap or direct receiver dispatch. */
@RunWith(AndroidJUnit4::class)
class Q5HostedTapTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sentinel = "Q5_H9_REAL_LAUNCHER_TAP_NOT_YET_RECEIVED"
    @Test fun stageCalendarRefresh() {
        Kotpref.init(target)
        Preferences.showWeather = false
        Preferences.showEvents = false
        Preferences.showClock = false
        Preferences.showMusic = false
        Preferences.showNotifications = false
        Preferences.showNextAlarm = false
        Preferences.showGreetings = false
        Preferences.showBatteryCharging = false
        Preferences.showEventsAsGlanceProvider = false
        Preferences.customNotes = ""
        Preferences.calendarAppPackage = IntentHelper.REFRESH_WIDGET_OPTION
        Preferences.calendarAppName = IntentHelper.REFRESH_WIDGET_OPTION
        MainWidget.updateWidget(target)
        Thread.sleep(3000)
        Preferences.mediaPlayerTitle = sentinel
        assertEquals(sentinel, Preferences.mediaPlayerTitle)
    }
    @Test fun verifyUntouchedBeforeRealTap() {
        Kotpref.init(target)
        assertEquals("refresh sentinel cleared without tap, invalid test", sentinel, Preferences.mediaPlayerTitle)
    }
    @Test fun verifyRealTapDispatchedRefresh() {
        Kotpref.init(target)
        // ACTION_REFRESH updates media state and removes the sentinel.
        // The only supported trigger between these separately invoked tests
        // is a real adb input tap on configured Pixel Launcher widget.
        val deadline = System.currentTimeMillis() + 7000
        while (System.currentTimeMillis() < deadline && Preferences.mediaPlayerTitle == sentinel)
            Thread.sleep(200)
        assertEquals("real Launcher widget tap did not invoke ACTION_REFRESH", "", Preferences.mediaPlayerTitle)
    }
}
