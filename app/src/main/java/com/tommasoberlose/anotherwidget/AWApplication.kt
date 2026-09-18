package com.tommasoberlose.anotherwidget

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.chibatching.kotpref.Kotpref
import com.tommasoberlose.anotherwidget.global.Constants
import com.tommasoberlose.anotherwidget.global.Preferences

class AWApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        migrateLegacyPreferences()
        AppCompatDelegate.setDefaultNightMode(Preferences.darkThemePreference)
        calibrateVersions()
    }

    private fun migrateLegacyPreferences() {
        if (
            Preferences.weatherProvider == Constants.WeatherProvider.HERE.rawValue ||
            Preferences.weatherProvider == Constants.WeatherProvider.ACCUWEATHER.rawValue
        ) {
            Preferences.weatherProvider = Constants.WeatherProvider.YR.rawValue
        }

        // Historical Google Sans mode was value 1. It is retired; normalize it to
        // the default bundled typeface while preserving downloaded-font mode (2).
        if (Preferences.customFont == 1) {
            Preferences.customFont = Constants.CUSTOM_FONT_DEFAULT
        }
    }

    private fun calibrateVersions() {
        if (Preferences.clockTextSize > 50f) Preferences.clockTextSize = 32f
        if (Preferences.textMainSize > 36f) Preferences.textMainSize = 32f
        if (Preferences.textSecondSize > 28f) Preferences.textSecondSize = 24f
    }
}
