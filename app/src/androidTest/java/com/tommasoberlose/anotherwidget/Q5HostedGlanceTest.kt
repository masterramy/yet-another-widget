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
    @Test fun stageVisibleNotificationBeforeRevocation() {
        quiet()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val listener = target.packageName +
            "/com.ramybaheeg.yetanotherwidget.receivers.NotificationListener"
        fun shell(command: String): String {
            val pfd = ui.executeShellCommand(command)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                .bufferedReader().use { it.readText() }
        }
        shell("cmd notification allow_listener $listener")
        val deadline = System.currentTimeMillis() + 10_000
        while (!com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper
                .checkNotificationAccess(target) && System.currentTimeMillis()<deadline)
            Thread.sleep(150)
        assertTrue(com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper.checkNotificationAccess(target))
        Preferences.showNotifications = true
        Preferences.lastNotificationId = 72164
        Preferences.lastNotificationTitle = "F7 Secret Revocation Witness"
        Preferences.lastNotificationPackage = target.packageName
        Preferences.lastNotificationIcon = R.drawable.round_notifications_24
        assertTrue(com.ramybaheeg.yetanotherwidget.helpers.GlanceProviderHelper.showGlanceProviders(target))
        val widget = StandardWidget(target).generateWidgetView()
        assertEquals("F7 Secret Revocation Witness",
            requireNotNull(widget).subLineText.text.toString())
        MainWidget.updateWidget(target)
        Thread.sleep(2800)
    }
    @Test fun revokeNotificationAndRefreshHostedState() {
        Kotpref.init(target)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val listener = target.packageName +
            "/com.ramybaheeg.yetanotherwidget.receivers.NotificationListener"
        val pfd = ui.executeShellCommand("cmd notification disallow_listener $listener")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
            .bufferedReader().use { it.readText() }
        val deadline = System.currentTimeMillis() + 10_000
        while (com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper
                .checkNotificationAccess(target) && System.currentTimeMillis()<deadline)
            Thread.sleep(150)
        assertFalse(com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper.checkNotificationAccess(target))
        assertFalse(com.ramybaheeg.yetanotherwidget.helpers.GlanceProviderHelper.showGlanceProviders(target))
        assertFalse(com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper.showLastNotification())
        MainWidget.updateWidget(target)
        Thread.sleep(2800)
    }

    @Test fun assertFixtureStillActive() {
        Kotpref.init(target)
        assertEquals(witness,Preferences.customNotes)
        assertTrue(GlanceProviderHelper.showGlanceProviders(target))
    }
}
