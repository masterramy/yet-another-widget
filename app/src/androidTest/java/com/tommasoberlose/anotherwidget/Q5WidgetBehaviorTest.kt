package com.ramybaheeg.yetanotherwidget

import android.content.res.Configuration
import android.app.LocaleManager
import android.os.LocaleList
import androidx.test.core.app.ActivityScenario
import com.ramybaheeg.yetanotherwidget.ui.activities.MainActivity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import java.util.Locale
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

    @Test
    fun refreshWidgetTapDispatchUsesBroadcastForCalendarClockAndEvent() {
        val oldCalendar = Preferences.calendarAppPackage
        val oldClock = Preferences.clockAppPackage
        val oldEventDetails = Preferences.openEventDetails
        try {
            Preferences.calendarAppPackage = IntentHelper.REFRESH_WIDGET_OPTION
            Preferences.clockAppPackage = IntentHelper.REFRESH_WIDGET_OPTION
            Preferences.openEventDetails = true

            val calendarIntent = IntentHelper.getCalendarIntent(target)
            assertEquals(Actions.ACTION_REFRESH, calendarIntent.action)
            val calendarTap = IntentHelper.getWidgetTapPendingIntent(target, 93101, calendarIntent)
            assertTrue("date REFRESH tap must use getBroadcast, never getActivity", calendarTap.isBroadcast)
            calendarTap.cancel()

            val clockIntent = IntentHelper.getClockIntent(target)
            assertEquals(Actions.ACTION_REFRESH, clockIntent.action)
            val clockTap = IntentHelper.getWidgetTapPendingIntent(target, 93102, clockIntent)
            assertTrue("clock REFRESH tap must use getBroadcast", clockTap.isBroadcast)
            clockTap.cancel()

            val event = com.ramybaheeg.yetanotherwidget.models.Event(
                id = 93103, eventID = 93103, title = "Q5 tap routing",
                startDate = System.currentTimeMillis() + 60_000L,
                endDate = System.currentTimeMillis() + 120_000L,
                calendarID = 1, allDay = false,
                selfAttendeeStatus = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
                availability = android.provider.CalendarContract.EventsEntity.AVAILABILITY_BUSY
            )
            val eventIntent = IntentHelper.getEventIntent(target, event)
            assertEquals("configured Refresh widget should also handle event taps", Actions.ACTION_REFRESH, eventIntent.action)
            val eventTap = IntentHelper.getWidgetTapPendingIntent(target, 93103, eventIntent)
            assertTrue("event REFRESH tap must use getBroadcast", eventTap.isBroadcast)
            eventTap.cancel()

            // Ordinary app-opening taps must not be converted to broadcasts.
            Preferences.calendarAppPackage = IntentHelper.DEFAULT_OPTION
            Preferences.clockAppPackage = IntentHelper.DEFAULT_OPTION
            val normalDate = IntentHelper.getWidgetTapPendingIntent(target, 93104, IntentHelper.getCalendarIntent(target))
            val normalClock = IntentHelper.getWidgetTapPendingIntent(target, 93105, IntentHelper.getClockIntent(target))
            assertTrue("default date tap must launch an activity", normalDate.isActivity)
            assertTrue("default clock tap must launch an activity", normalClock.isActivity)
            normalDate.cancel()
            normalClock.cancel()
        } finally {
            Preferences.calendarAppPackage = oldCalendar
            Preferences.clockAppPackage = oldClock
            Preferences.openEventDetails = oldEventDetails
        }
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

    @Test
    fun rtlActualActivityPreviewRetainsVisibleDate() {
        val locales = target.getSystemService(LocaleManager::class.java)
        val old = locales.applicationLocales
        val oldAlign = Preferences.widgetAlign
        val oldPreview = Preferences.showPreview
        try {
            Preferences.widgetAlign = Constants.WidgetAlign.CENTER.rawValue
            Preferences.showPreview = true
            locales.applicationLocales = LocaleList.forLanguageTags("ar-EG")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var visible = false
                var diagnostic = "activity date view absent"
                repeat(16) {
                    scenario.onActivity { activity ->
                        val date = activity.findViewById<android.widget.TextView>(R.id.date)
                        val row = activity.findViewById<View>(R.id.date_layout)
                        val rect = android.graphics.Rect()
                        val onScreen = date?.getGlobalVisibleRect(rect) ?: false
                        val root = activity.findViewById<View>(R.id.widget)
                        visible = date?.text?.isNotBlank() == true &&
                            date.isShown && onScreen && rect.width() > 0 &&
                            (root?.alpha ?: 0f) > 0f
                        val chain = mutableListOf<String>()
                        var ancestor: View? = date
                        while (ancestor != null && chain.size < 10) {
                            val point = IntArray(2)
                            ancestor.getLocationOnScreen(point)
                            chain.add("${ancestor.javaClass.simpleName}#${ancestor.id}:" +
                                "x=${point[0]},left=${ancestor.left},w=${ancestor.width}," +
                                "dir=${ancestor.layoutDirection},alpha=${ancestor.alpha}")
                            ancestor = ancestor.parent as? View
                        }
                        diagnostic = "date='${date?.text}' row=${row?.width} " +
                            "shown=${date?.isShown} rootAlpha=${root?.alpha} rect=$rect; " +
                            chain.joinToString(" | ")
                    }
                    if (visible) return@use
                    Thread.sleep(600)
                }
                assertTrue("Arabic Activity preview invalid: $diagnostic", visible)
            }
        } finally {
            locales.applicationLocales = old
            Preferences.widgetAlign = oldAlign
            Preferences.showPreview = oldPreview
        }
    }

    @Test
    fun rtlDatePreviewLayoutPreservesVisibleContent() {
        Preferences.widgetAlign = Constants.WidgetAlign.CENTER.rawValue
        Preferences.showClock = false
        val config = Configuration(target.resources.configuration).apply {
            setLocale(Locale("ar", "EG"))
            setLayoutDirection(Locale("ar", "EG"))
        }
        val rtlContext = target.createConfigurationContext(config)
        var dateText = ""
        var dateWidth = -1
        var dateVisibility = -1
        var rtlDateGeometry = "not measured"
        instrumentation.runOnMainSync {
            val widget = requireNotNull(MainWidget.getWidgetView(rtlContext, null)?.root)
            val host = LinearLayout(rtlContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(widget, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            host.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.AT_MOST)
            )
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
            val date = requireNotNull(widget.findViewById<TextView>(R.id.date))
            dateText = date.text.toString()
            dateWidth = date.width
            dateVisibility = date.visibility
            val row = requireNotNull(widget.findViewById<View>(R.id.date_layout))
            rtlDateGeometry = "date=${date.left}..${date.right} rowWidth=${row.width}"
            assertTrue("RTL date lies outside row: $rtlDateGeometry",
                date.left >= 0 && date.right <= row.width)
        }
        assertTrue("RTL date text is empty", dateText.isNotBlank())
        assertEquals("RTL date view is hidden", View.VISIBLE, dateVisibility)
        assertTrue("RTL date view collapsed to zero width: ${dateWidth}px", dateWidth > 0)
    }

}
