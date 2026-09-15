package com.tommasoberlose.anotherwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Q1-only harness: asks the real Launcher host to pin MainWidget via the platform API. */
@RunWith(AndroidJUnit4::class)
class PinWidgetRequestTest {
    @Test
    fun requestRealLauncherPin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = AppWidgetManager.getInstance(context)
        assertTrue("Launcher does not support requestPinAppWidget", manager.isRequestPinAppWidgetSupported)
        val provider = ComponentName(context.packageName, "com.tommasoberlose.anotherwidget.ui.widgets.MainWidget")
        assertTrue("Launcher rejected MainWidget pin request", manager.requestPinAppWidget(provider, null, null))
    }
}
