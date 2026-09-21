package com.ramybaheeg.yetanotherwidget.ui.viewmodels.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.TimeZone

class TimeZoneSelectorViewModel(application: Application) : AndroidViewModel(application) {

    private val allTimeZones: List<String> = TimeZone.getAvailableIDs()
        .distinct()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

    val timeZones: MutableLiveData<List<String>> = MutableLiveData(allTimeZones)
    val locationInput: MutableLiveData<String> = MutableLiveData("")

    fun filterTimeZones(query: String) {
        val normalized = query.trim()
        timeZones.value = if (normalized.isEmpty()) {
            allTimeZones
        } else {
            allTimeZones.filter { zoneId ->
                zoneId.contains(normalized, ignoreCase = true) ||
                    zoneId.replace('_', ' ').contains(normalized, ignoreCase = true)
            }
        }
    }
}
