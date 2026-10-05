package com.ramybaheeg.yetanotherwidget

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.ui.activities.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5LegacyWallpaperTest {
    @Test
    fun wallpaperPreviewToggleWorksOnApi32AndEarlier() {
        assumeTrue(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            target.packageName,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        Preferences.showWallpaper = false
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.action_settings)).perform(click())
            onView(withId(R.id.action_show_wallpaper)).check(matches(isDisplayed()))
            onView(withId(R.id.show_wallpaper_toggle)).check(matches(isNotChecked()))

            onView(withId(R.id.action_show_wallpaper)).perform(click())
            onView(withId(R.id.show_wallpaper_toggle)).check(matches(isChecked()))
            assertTrue(Preferences.showWallpaper)

            onView(withId(R.id.action_show_wallpaper)).perform(click())
            onView(withId(R.id.show_wallpaper_toggle)).check(matches(isNotChecked()))
            assertFalse(Preferences.showWallpaper)
        }
    }
}
