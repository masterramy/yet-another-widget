package com.ramybaheeg.yetanotherwidget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.WeatherHelper
import com.ramybaheeg.yetanotherwidget.network.WeatherNetworkApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Q5WeatherTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun prepare() {
        Kotpref.init(context)
        Preferences.showWeather = true
        Preferences.customLocationAdd = "Q5 deterministic location"
        Preferences.customLocationLat = "38.8894"
        Preferences.customLocationLon = "-77.0352"
        Preferences.weatherProviderError = ""
        Preferences.weatherProviderLocationError = ""
        Preferences.weatherIcon = ""
        Preferences.weatherUpdatedAt = 0L
    }

    @Test
    fun weatherApiMissingKeyFailsSafely() = runBlocking {
        Preferences.weatherProvider = Constants.WeatherProvider.WEATHER_API.rawValue
        Preferences.weatherProviderApiWeatherApi = ""
        WeatherNetworkApi(context).updateWeather()

        assertEquals(
            context.getString(R.string.weather_provider_error_missing_key),
            Preferences.weatherProviderError
        )
        assertEquals("", Preferences.weatherProviderLocationError)
        assertEquals("", Preferences.weatherIcon)
        assertEquals(0L, Preferences.weatherUpdatedAt)
    }

    @Test
    fun staleWeatherApiCacheIsClearedAtOneHour() {
        val now = 2_000_000_000_000L
        Preferences.weatherProvider = Constants.WeatherProvider.WEATHER_API.rawValue
        Preferences.weatherIcon = "01d"
        Preferences.weatherTemp = 72f
        Preferences.weatherRealTempUnit = "F"
        Preferences.weatherUpdatedAt = now - 60L * 60L * 1000L

        assertFalse(WeatherHelper.hasDisplayableWeather(now))
        assertEquals("", Preferences.weatherIcon)
        assertEquals(0L, Preferences.weatherUpdatedAt)
    }

    @Test
    fun weatherGovLiveUsLocationReturnsDisplayableWeather() = runBlocking {
        Preferences.weatherProvider = Constants.WeatherProvider.WEATHER_GOV.rawValue
        Preferences.weatherTempUnit = "F"
        WeatherNetworkApi(context).updateWeather()

        assertEquals("", Preferences.weatherProviderError)
        assertEquals("", Preferences.weatherProviderLocationError)
        assertTrue(Preferences.weatherIcon.isNotEmpty())
        assertTrue(Preferences.weatherUpdatedAt > 0L)
        assertTrue(WeatherHelper.hasDisplayableWeather())
    }

    @Test
    fun providerFailureThenWeatherGovRecoveryRestoresDisplayableWeather() = runBlocking {
        Preferences.weatherProvider = Constants.WeatherProvider.WEATHER_API.rawValue
        Preferences.weatherProviderApiWeatherApi = ""
        WeatherNetworkApi(context).updateWeather()
        assertEquals(
            context.getString(R.string.weather_provider_error_missing_key),
            Preferences.weatherProviderError
        )
        assertEquals("", Preferences.weatherIcon)

        Preferences.weatherProvider = Constants.WeatherProvider.WEATHER_GOV.rawValue
        Preferences.weatherTempUnit = "F"
        WeatherNetworkApi(context).updateWeather()

        assertEquals("", Preferences.weatherProviderError)
        assertEquals("", Preferences.weatherProviderLocationError)
        assertTrue(Preferences.weatherIcon.isNotEmpty())
        assertTrue(Preferences.weatherUpdatedAt > 0L)
        assertTrue(WeatherHelper.hasDisplayableWeather())
    }

}
