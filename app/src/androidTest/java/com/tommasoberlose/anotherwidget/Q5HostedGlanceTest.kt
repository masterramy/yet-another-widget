package com.ramybaheeg.yetanotherwidget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.GlanceProviderHelper
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import com.ramybaheeg.yetanotherwidget.ui.widgets.StandardWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production view and provider state, displayed on actual Pixel Launcher separately. */
@RunWith(AndroidJUnit4::class)
class Q5HostedGlanceTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val witness = "H16 Hosted Custom Glance Witness"
    private fun quiet() {
        Kotpref.init(target)
        Preferences.showEvents = false
        Preferences.showMusic = false
        Preferences.showWeather = false
        Preferences.showNotifications = false
        Preferences.showNextAlarm = false
        Preferences.showGreetings = false
        Preferences.showBatteryCharging = false
        Preferences.showEventsAsGlanceProvider = false
        Preferences.showClock = false
        Preferences.widgetAlign = Constants.WidgetAlign.CENTER.rawValue
        GlanceProviderHelper.saveGlanceProviderOrder(listOf(Constants.GlanceProviderId.CUSTOM_INFO))
    }
    @Test fun stageNoGlance() {
        quiet()
        Preferences.customNotes = ""
        assertFalse("glance off before fixture",GlanceProviderHelper.showGlanceProviders(target))
        MainWidget.updateWidget(target)
        Thread.sleep(2000)
    }
    @Test fun stageCustomNote() {
        quiet()
        Preferences.customNotes = witness
        assertTrue("production provider active",GlanceProviderHelper.showGlanceProviders(target))
        val binding = StandardWidget(target).generateWidgetView()
        assertEquals("production bitmap text must contain the witness",
            witness,requireNotNull(binding).subLineText.text.toString())
        MainWidget.updateWidget(target)
        Thread.sleep(2500)
    }
    @Test fun stageLeftAligned() {
        quiet()
        Preferences.customNotes = witness
        Preferences.widgetAlign = Constants.WidgetAlign.LEFT.rawValue
        assertTrue(GlanceProviderHelper.showGlanceProviders(target))
        MainWidget.updateWidget(target)
        Thread.sleep(2500)
    }
    @Test fun stageRightAligned() {
        quiet()
        Preferences.customNotes = witness
        Preferences.widgetAlign = Constants.WidgetAlign.RIGHT.rawValue
        assertTrue(GlanceProviderHelper.showGlanceProviders(target))
        MainWidget.updateWidget(target)
        Thread.sleep(2500)
    }
    @Test fun stageHostedClock() {
        quiet()
        Preferences.customNotes = ""
        Preferences.showClock = true
        Preferences.widgetAlign = Constants.WidgetAlign.CENTER.rawValue
        MainWidget.updateWidget(target)
        Thread.sleep(2500)
    }
    @Test fun assertFixtureStillActive() {
        Kotpref.init(target)
        assertEquals(witness,Preferences.customNotes)
        assertTrue(GlanceProviderHelper.showGlanceProviders(target))
    }
}
