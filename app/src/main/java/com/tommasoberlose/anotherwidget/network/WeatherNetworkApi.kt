package com.tommasoberlose.anotherwidget.network

import android.content.Context
import com.chibatching.kotpref.Kotpref
import com.haroldadmin.cnradapter.NetworkResponse
import com.haroldadmin.cnradapter.executeWithRetry
import com.tommasoberlose.anotherwidget.R
import com.tommasoberlose.anotherwidget.global.Constants
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.helpers.WeatherHelper
import com.tommasoberlose.anotherwidget.network.repository.WeatherGovRepository
import com.tommasoberlose.anotherwidget.network.repository.YrRepository
import com.tommasoberlose.anotherwidget.ui.fragments.MainFragment
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import org.greenrobot.eventbus.EventBus
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Calendar

class WeatherNetworkApi(val context: Context) {
    suspend fun updateWeather() {
        Kotpref.init(context)
        Preferences.weatherProviderError = "-"
        Preferences.weatherProviderLocationError = ""

        if (Preferences.showWeather && Preferences.customLocationLat != "" && Preferences.customLocationLon != "") {
            when (Constants.WeatherProvider.fromInt(Preferences.weatherProvider)) {
                Constants.WeatherProvider.WEATHER_GOV -> useWeatherGov(context)
                Constants.WeatherProvider.YR -> useYrProvider(context)
            }
        } else {
            WeatherHelper.removeWeather(context)
            EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
        }
    }

    private suspend fun useWeatherGov(context: Context) {
        val repository = WeatherGovRepository()
        val pointsResponse = executeWithRetry(times = 5) {
            repository.getGridPoints(
                Preferences.customLocationLat,
                Preferences.customLocationLon
            )
        }

        when (pointsResponse) {
            is NetworkResponse.Success -> {
                try {
                    val pp = pointsResponse.body["properties"] as Map<*, *>
                    val gridId = pp["gridId"] as String
                    val gridX = pp["gridX"] as Double
                    val gridY = pp["gridY"] as Double

                    when (val weatherResponse = repository.getWeather(
                        gridId,
                        gridX,
                        gridY,
                        if (Preferences.weatherTempUnit == "F") "us" else "si"
                    )) {
                        is NetworkResponse.Success -> {
                            try {
                                val props =
                                    weatherResponse.body["properties"] as Map<*, *>
                                val periods = props["periods"] as List<*>
                                val now = periods[0] as Map<*, *>

                                val temp = now["temperature"] as Double
                                val fullIcon = now["icon"] as String
                                val isDaytime = now["isDaytime"] as Boolean

                                Preferences.weatherTemp = temp.toFloat()
                                Preferences.weatherIcon = WeatherHelper.getWeatherGovIcon(fullIcon, isDaytime)
                                Preferences.weatherRealTempUnit = Preferences.weatherTempUnit

                                Preferences.weatherProviderError = ""
                                Preferences.weatherProviderLocationError = ""

                                MainWidget.updateWidget(context)
                            } catch (ex: Exception) {
                                Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_generic)
                                Preferences.weatherProviderLocationError = ""
                            } finally {
                                EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                            }
                        }
                        else -> {
                            Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_connection)
                            Preferences.weatherProviderLocationError = ""
                        }
                    }
                } catch(ex: Exception) {
                    Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_generic)
                    Preferences.weatherProviderLocationError = ""
                } finally {
                    EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                }
            }
            is NetworkResponse.ServerError -> {
                if (pointsResponse.body?.containsKey("status") == true && (pointsResponse.body?.get("status") as Double).toInt() == 404) {
                    Preferences.weatherProviderError = ""
                    Preferences.weatherProviderLocationError = context.getString(R.string.weather_provider_error_wrong_location)
                } else {
                    Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_generic)
                    Preferences.weatherProviderLocationError = ""
                }

                WeatherHelper.removeWeather(
                    context
                )
                EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
            }
            else -> {
                Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_connection)
                Preferences.weatherProviderLocationError = ""
                EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
            }
        }
    }

    private suspend fun useYrProvider(context: Context) {
        val repository = YrRepository()

        when (val response = repository.getWeather()) {
            is NetworkResponse.Success -> {
                try {
                    val pp = response.body["properties"] as Map<*, *>
                    val data = pp["timeseries"] as List<Map<String, Any>>?
                    data?.let {
                        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        for (item in data) {
                            val time = Calendar.getInstance().apply { time = format.parse(item["time"] as String)!! }
                            val now = Calendar.getInstance()
                            if (time.timeInMillis >= now.timeInMillis) {
                                val dd = item["data"] as Map<*, *>
                                val instant = dd["instant"] as Map<*, *>
                                val next = dd["next_1_hours"] as Map<*, *>

                                val details = instant["details"] as Map<*, *>
                                val temp = details["air_temperature"] as Double

                                val summary = next["summary"] as Map<*, *>
                                val iconCode = summary["symbol_code"] as String

                                Preferences.weatherTemp =
                                    if (Preferences.weatherTempUnit == "F") {
                                        (temp * 9.0 / 5.0 + 32.0).toFloat()
                                    } else {
                                        temp.toFloat()
                                    }
                                Preferences.weatherIcon = WeatherHelper.getYRIcon(iconCode, now.get(Calendar.HOUR_OF_DAY) >= 22 || now.get(Calendar.HOUR_OF_DAY) <= 8)
                                Preferences.weatherRealTempUnit = Preferences.weatherTempUnit
                                MainWidget.updateWidget(context)

                                Preferences.weatherProviderError = ""
                                Preferences.weatherProviderLocationError = ""
                                break
                            }
                        }
                    }



                } catch(ex: Exception) {
                    ex.printStackTrace()
                    Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_generic)
                    Preferences.weatherProviderLocationError = ""
                } finally {
                    EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                }
            }
            is NetworkResponse.ServerError -> {
                Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_generic)
                Preferences.weatherProviderLocationError = ""
                WeatherHelper.removeWeather(
                    context
                )
                EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
            }
            else -> {
                Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_connection)
                Preferences.weatherProviderLocationError = ""
                EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
            }
        }
    }
}
