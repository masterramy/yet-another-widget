package com.ramybaheeg.yetanotherwidget

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.global.Actions
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.IntentHelper
import com.ramybaheeg.yetanotherwidget.receivers.UpdatesReceiver
import com.ramybaheeg.yetanotherwidget.ui.widgets.AlignedWidget
import com.ramybaheeg.yetanotherwidget.ui.widgets.ClockWidget
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import com.ramybaheeg.yetanotherwidget.ui.widgets.StandardWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5WidgetBehaviorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext

    @Before
    fun prepare() {
        Kotpref.init(target)
        Preferences.showEvents = false
        Preferences.showWeather = false
        Preferences.showNextAlarm = false
        Preferences.showBatteryCharging = false
        Preferences.showGreetings = false
        Preferences.showNotifications = false
        Preferences.showMusic = false
        Preferences.showEventsAsGlanceProvider = false
        Preferences.customNotes = ""
        Preferences.altTimezoneId = ""
        Preferences.altTimezoneLabel = ""
    }

    private fun applyRemoteViews(remoteViews: RemoteViews): View {
        var root: View? = null
        instrumentation.runOnMainSync {
            root = remoteViews.apply(target, FrameLayout(target))
        }
        return requireNotNull(root)
    }

    @Test
    fun clockRemoteViewsHonorVisibilityMarginAndTimezonePreferences() {
        Preferences.showClock = false
        var remoteViews = ClockWidget(target).updateClockView(
            RemoteViews(target.packageName, R.layout.the_widget_sans),
            7001
        )
        var root = applyRemoteViews(remoteViews)
        assertEquals(View.GONE, root.findViewById<View>(R.id.time).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.time_am_pm).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.timezones_container).visibility)

        Preferences.showClock = true
        Preferences.showAMPMIndicator = false
        Preferences.clockBottomMargin = Constants.ClockBottomMargin.LARGE.rawValue
        Preferences.altTimezoneId = "America/New_York"
        Preferences.altTimezoneLabel = "New York"

        remoteViews = ClockWidget(target).updateClockView(
            RemoteViews(target.packageName, R.layout.the_widget_sans),
            7002
        )
        root = applyRemoteViews(remoteViews)

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.time).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.time_am_pm).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.clock_bottom_margin_none).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.clock_bottom_margin_small).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.clock_bottom_margin_medium).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.clock_bottom_margin_large).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.timezones_container).visibility)
        assertEquals(
            "New York",
            root.findViewById<android.widget.TextView>(R.id.alt_timezone_label).text.toString()
        )
    }

    @Test
    fun centerLeftAndRightPreviewPathsUseExpectedProductionLayouts() {
        Preferences.widgetAlign = Constants.WidgetAlign.CENTER.rawValue
        val center = MainWidget.getWidgetView(target, null)?.root
        assertNotNull(center)
        assertNull(center!!.findViewById<View?>(R.id.main_content))

        Preferences.widgetAlign = Constants.WidgetAlign.LEFT.rawValue
        val left = MainWidget.getWidgetView(target, null)?.root
        assertNotNull(left)
        val leftContent = left!!.findViewById<LinearLayout>(R.id.main_content)
        assertNotNull(leftContent)
        assertTrue(leftContent.gravity and Gravity.END != Gravity.END)

        Preferences.widgetAlign = Constants.WidgetAlign.RIGHT.rawValue
        val right = MainWidget.getWidgetView(target, null)?.root
        assertNotNull(right)
        val rightContent = right!!.findViewById<LinearLayout>(R.id.main_content)
        assertNotNull(rightContent)
        assertEquals(Gravity.END, rightContent.gravity and Gravity.END)
    }

    @Test
    fun invalidExternalTargetsFailClosedAndRefreshActionIsExplicit() {
        Preferences.weatherAppPackage = "q5.missing.weather"
        Preferences.calendarAppPackage = "q5.missing.calendar"
        Preferences.clockAppPackage = "q5.missing.clock"

        val weather = IntentHelper.getWeatherIntent(target)
        val calendar = IntentHelper.getCalendarIntent(target)
        val clock = IntentHelper.getClockIntent(target)

        assertNull(weather.component)
        assertNull(weather.action)
        assertNull(calendar.component)
        assertNull(calendar.action)
        assertNull(clock.component)
        assertNull(clock.action)

        Preferences.weatherAppPackage = IntentHelper.REFRESH_WIDGET_OPTION
        val refresh = IntentHelper.getWeatherIntent(target)
        assertEquals(Actions.ACTION_REFRESH, refresh.action)
        assertEquals(UpdatesReceiver::class.java.name, refresh.component?.className)
    }

    @Test
    fun repeatedProductionWidgetGenerationStaysStableAcrossCoreLayouts() {
        val alignments = listOf(
            Constants.WidgetAlign.CENTER.rawValue,
            Constants.WidgetAlign.LEFT.rawValue,
            Constants.WidgetAlign.RIGHT.rawValue
        )

        repeat(18) { cycle ->
            Preferences.widgetAlign = alignments[cycle % alignments.size]
            Preferences.showClock = cycle % 2 == 0

            val preview = MainWidget.getWidgetView(target, null)?.root
            assertNotNull(preview)

            val remoteViews = when (Preferences.widgetAlign) {
                Constants.WidgetAlign.LEFT.rawValue ->
                    AlignedWidget(target).generateWidget(8100 + cycle, 900, null)
                Constants.WidgetAlign.RIGHT.rawValue ->
                    AlignedWidget(target, rightAligned = true).generateWidget(8100 + cycle, 900, null)
                else ->
                    StandardWidget(target).generateWidget(8100 + cycle, 900, null)
            }
            assertNotNull(remoteViews)
            assertNotNull(applyRemoteViews(requireNotNull(remoteViews)))
        }
    }

}
