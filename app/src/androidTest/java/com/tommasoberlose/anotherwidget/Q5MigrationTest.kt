package com.ramybaheeg.yetanotherwidget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5MigrationTest {
    private val app: AWApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AWApplication

    @Before
    fun prepare() {
        Kotpref.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun invokePrivate(name: String) {
        AWApplication::class.java.getDeclaredMethod(name).apply {
            isAccessible = true
            invoke(app)
        }
    }

    @Test
    fun retiredWeatherProviderNormalizesAndRetiredKeysAreScrubbed() {
        Preferences.weatherProvider = 99
        Preferences.weatherProviderApiWeatherApi = "preserve-current-key"
        Preferences.weatherProviderApiOpen = "retired-open"
        Preferences.weatherProviderApiHere = "retired-here"
        Preferences.weatherProviderApiWeatherBit = "retired-weatherbit"
        Preferences.weatherProviderApiAccuweather = "retired-accuweather"

        invokePrivate("migrateLegacyPreferences")

        assertEquals(Constants.WeatherProvider.WEATHER_API.rawValue, Preferences.weatherProvider)
        assertEquals("preserve-current-key", Preferences.weatherProviderApiWeatherApi)
        assertEquals("", Preferences.weatherProviderApiOpen)
        assertEquals("", Preferences.weatherProviderApiHere)
        assertEquals("", Preferences.weatherProviderApiWeatherBit)
        assertEquals("", Preferences.weatherProviderApiAccuweather)
    }

    @Test
    fun retiredRemoteFontModeNormalizesToBundledDefault() {
        Preferences.customFont = 1
        invokePrivate("migrateLegacyPreferences")
        assertEquals(Constants.CUSTOM_FONT_DEFAULT, Preferences.customFont)
    }

    @Test
    fun downloadedFontModeAndMetadataArePreserved() {
        Preferences.customFont = Constants.CUSTOM_FONT_DOWNLOADED
        Preferences.customFontFile = "/legacy/downloaded/font.ttf"
        Preferences.customFontName = "Legacy Downloaded Font"
        Preferences.customFontVariant = "regular"

        invokePrivate("migrateLegacyPreferences")

        assertEquals(Constants.CUSTOM_FONT_DOWNLOADED, Preferences.customFont)
        assertEquals("/legacy/downloaded/font.ttf", Preferences.customFontFile)
        assertEquals("Legacy Downloaded Font", Preferences.customFontName)
        assertEquals("regular", Preferences.customFontVariant)
    }

    @Test
    fun oversizedHistoricalTextValuesCalibrateToSafeDefaults() {
        Preferences.clockTextSize = 90f
        Preferences.textMainSize = 50f
        Preferences.textSecondSize = 40f

        invokePrivate("calibrateVersions")

        assertEquals(32f, Preferences.clockTextSize)
        assertEquals(32f, Preferences.textMainSize)
        assertEquals(24f, Preferences.textSecondSize)
    }
}
