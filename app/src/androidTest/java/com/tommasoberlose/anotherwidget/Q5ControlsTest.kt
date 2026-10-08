package com.ramybaheeg.yetanotherwidget

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ramybaheeg.yetanotherwidget.ui.activities.MainActivity
import org.hamcrest.Matchers.anyOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5ControlsTest {

    private fun openRow(id: Int) {
        onView(withId(id)).perform(scrollTo(), click())
    }

    private fun assertChild(rowId: Int, title: String) {
        openRow(rowId)
        onView(withText(title)).check(matches(isDisplayed()))
        onView(withId(R.id.action_back)).perform(click())
        onView(withId(R.id.action_typography)).check(matches(isDisplayed()))
    }

    private fun revealText(
        text: String,
        scrollContainerId: Int,
        direction: ViewAction,
        maxAttempts: Int = 12
    ) {
        var lastFailure: Throwable? = null
        repeat(maxAttempts) {
            try {
                onView(withText(text)).check(matches(isDisplayed()))
                return
            } catch (failure: Throwable) {
                lastFailure = failure
            }
            onView(withId(scrollContainerId)).perform(direction)
        }
        throw AssertionError("Could not reveal visible text: $text", lastFailure)
    }

    private fun openGlanceProvider(title: String) {
        revealText(title, R.id.scrollView, swipeUp())
        onView(withText(title)).perform(click())
    }

    private fun exitSearchActivity() {
        onView(withId(R.id.action_back)).perform(click())
    }

    @Test
    fun allMainChildrenNavigateAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertChild(R.id.action_typography, "Typography")
            assertChild(R.id.action_general_settings, "Layout")
            assertChild(R.id.action_show_clock, "Clock")
            assertChild(R.id.action_show_events, "Calendar")
            assertChild(R.id.action_show_weather, "Weather")
            assertChild(R.id.action_show_glance, "At a glance")
            assertChild(R.id.action_tab_default_app, "Gestures")
        }
    }

    @Test
    fun typographyMenusExposeSupportedBoundaries() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_typography)

            openRow(R.id.action_main_text_size)
            revealText("40sp", R.id.menu, swipeDown())
            revealText("10sp", R.id.menu, swipeUp(), maxAttempts = 20)
            pressBack()

            openRow(R.id.action_second_text_size)
            revealText("40sp", R.id.menu, swipeDown())
            revealText("10sp", R.id.menu, swipeUp(), maxAttempts = 20)
            pressBack()

            openRow(R.id.action_font_color)
            onView(withText("Text color")).check(matches(isDisplayed()))
            onView(withId(R.id.alpha_selector_container)).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_secondary_font_color)
            onView(withText("Text color")).check(matches(isDisplayed()))
            onView(withId(R.id.alpha_selector_container)).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_text_shadow)
            onView(withText("Text shadow")).check(matches(isDisplayed()))
            onView(withText("None")).check(matches(isDisplayed()))
            onView(withText("High")).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_custom_font)
            onView(withText("Widget font")).check(matches(isDisplayed()))
            onView(withText("Device font")).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_date_format)
            onView(withText("Date format")).check(matches(isDisplayed()))
            onView(withText("Custom date format")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun layoutControlsExposeSupportedRangesAndToggleRestores() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_general_settings)

            openRow(R.id.action_widget_align)
            onView(withText("Left")).check(matches(isDisplayed()))
            onView(withText("Center")).check(matches(isDisplayed()))
            onView(withText("Right")).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_second_row_top_margin_size)
            onView(withText("Rows spacing")).check(matches(isDisplayed()))
            onView(withText("None")).check(matches(isDisplayed()))
            onView(withText("Large")).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_clock_bottom_margin_size)
            onView(withText("Clock bottom margin")).check(matches(isDisplayed()))
            onView(withText("None")).check(matches(isDisplayed()))
            onView(withText("Large")).check(matches(isDisplayed()))
            pressBack()

            openRow(R.id.action_background_color)
            onView(anyOf(withText("Background color"), withText("Background"))).check(matches(isDisplayed()))
            pressBack()

            val divider = onView(withId(R.id.show_dividers_toggle))
            val initiallyChecked = try {
                divider.check(matches(isChecked()))
                true
            } catch (_: Throwable) {
                divider.check(matches(isNotChecked()))
                false
            }
            openRow(R.id.action_show_dividers)
            divider.check(matches(if (initiallyChecked) isNotChecked() else isChecked()))
            openRow(R.id.action_show_dividers)
            divider.check(matches(if (initiallyChecked) isChecked() else isNotChecked()))
        }
    }

    @Test
    fun clockCalendarWeatherAndGesturesCoreControlsOpen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_clock)
            onView(withText("Clock")).check(matches(isDisplayed()))
            openRow(R.id.action_clock_text_size)
            onView(withText("Text size")).check(matches(isDisplayed()))
            pressBack()
            openRow(R.id.action_alt_timezone_clock)
            onView(withText("Time Zones")).check(matches(isDisplayed()))
            exitSearchActivity()
            onView(withId(R.id.action_back)).perform(click())

            openRow(R.id.action_show_events)
            onView(withText("Calendar")).check(matches(isDisplayed()))
            openRow(R.id.action_change_attendee_filter)
            onView(withText("Attendee status")).check(matches(isDisplayed()))
            pressBack()
            openRow(R.id.action_second_row_info)
            onView(withText("Event info")).check(matches(isDisplayed()))
            pressBack()
            openRow(R.id.action_show_until)
            onView(withText("Show events at least")).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.action_back)).perform(click())

            openRow(R.id.action_show_weather)
            onView(withText("Weather")).check(matches(isDisplayed()))
            openRow(R.id.action_change_unit)
            onView(withText(R.string.settings_unit_title)).check(matches(isDisplayed()))
            pressBack()
            openRow(R.id.action_weather_refresh_period)
            onView(withText("Refresh frequency")).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.action_back)).perform(click())

            openRow(R.id.action_tab_default_app)
            onView(withText("Gestures")).check(matches(isDisplayed()))
            openRow(R.id.action_open_event_details)
            onView(anyOf(withText("Default event app"), withText("Default calendar app"))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun weatherSecondarySurfacesOpen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_weather)
            onView(withText("Weather")).check(matches(isDisplayed()))

            openRow(R.id.action_weather_provider)
            onView(withText("Weather provider")).check(matches(isDisplayed()))
            exitSearchActivity()

            openRow(R.id.action_custom_location)
            onView(withText("Location")).check(matches(isDisplayed()))
            exitSearchActivity()

            openRow(R.id.action_weather_icon_pack)
            onView(withText("Icon pack")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun customDateCapitalizationSurfaceOpensAndCycles() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_typography)
            openRow(R.id.action_date_format)
            onView(withText("Custom date format")).perform(click())
            onView(withId(R.id.date_format)).check(matches(isDisplayed()))
            onView(withId(R.id.action_capitalize)).check(matches(isDisplayed()))
            onView(withId(R.id.action_capitalize)).perform(click(), click(), click())
        }
    }

    @Test
    fun defaultApplicationChoosersOpenFromGestures() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_tab_default_app)
            onView(withText("Gestures")).check(matches(isDisplayed()))

            openRow(R.id.action_calendar_app)
            onView(withText("Choose application")).check(matches(isDisplayed()))
            exitSearchActivity()

            openRow(R.id.action_clock_app)
            onView(withText("Choose application")).check(matches(isDisplayed()))
            exitSearchActivity()

            openRow(R.id.action_weather_app)
            onView(withText("Choose application")).check(matches(isDisplayed()))
        }
    }


    @Test
    fun glanceNotificationAndMediaConfigurationSurfacesOpen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Latest notifications")
            onView(withId(R.id.action_filter_notifications_app)).perform(click())
            onView(withText("Applications")).check(matches(isDisplayed()))
            exitSearchActivity()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Latest notifications")
            onView(withId(R.id.action_change_notification_timer)).perform(click())
            onView(withText("Hide the notification after")).check(matches(isDisplayed()))
            pressBack()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Current playing song")
            onView(withId(R.id.action_filter_music_players)).perform(click())
            onView(withText("Music Players")).check(matches(isDisplayed()))
            exitSearchActivity()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Current playing song")
            onView(withId(R.id.action_change_media_info_format)).perform(click())
            onView(withId(R.id.media_info_format_input)).check(matches(isDisplayed()))
            exitSearchActivity()
        }
    }


    @Test
    fun customNotesCreateEditAndClear() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Custom notes")
            onView(withId(R.id.notes)).perform(replaceText("Q5 note"), closeSoftKeyboard())
            onView(withId(R.id.action_positive)).perform(click())
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Custom notes")
            onView(withId(R.id.notes)).check(matches(withText("Q5 note")))
            onView(withId(R.id.notes)).perform(replaceText("Q5 edited"), closeSoftKeyboard())
            onView(withId(R.id.action_positive)).perform(click())
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            openRow(R.id.action_show_glance)
            openGlanceProvider("Custom notes")
            onView(withId(R.id.notes)).check(matches(withText("Q5 edited")))
            onView(withId(R.id.notes)).perform(replaceText(""), closeSoftKeyboard())
            onView(withId(R.id.action_positive)).perform(click())
        }
    }

}
