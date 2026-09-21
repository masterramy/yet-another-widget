package com.ramybaheeg.yetanotherwidget.ui.viewmodels.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.chibatching.kotpref.livedata.asLiveData
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences

class WeatherProviderViewModel(application: Application) : AndroidViewModel(application) {

    val weatherProvider = Preferences.asLiveData(Preferences::weatherProvider)
    val weatherProviderError = Preferences.asLiveData(Preferences::weatherProviderError)
    val weatherProviderLocationError = Preferences.asLiveData(Preferences::weatherProviderLocationError)


}