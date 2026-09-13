package com.tommasoberlose.anotherwidget

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.chibatching.kotpref.Kotpref
import com.tommasoberlose.anotherwidget.global.Preferences

class AWApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        AppCompatDelegate.setDefaultNightMode(Preferences.darkThemePreference)
        calibrateVersions()
    }

    private fun calibrateVersions() {
        if (Preferences.clockTextSize > 50f) Preferences.clockTextSize = 32f
        if (Preferences.textMainSize > 36f) Preferences.textMainSize = 32f
        if (Preferences.textSecondSize > 28f) Preferences.textSecondSize = 24f
    }
}
