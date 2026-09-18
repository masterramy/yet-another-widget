package com.tommasoberlose.anotherwidget.network

import android.content.Context
import com.chibatching.kotpref.Kotpref
import com.haroldadmin.cnradapter.NetworkResponse
import com.haroldadmin.cnradapter.executeWithRetry
import com.tommasoberlose.anotherwidget.R
import com.tommasoberlose.anotherwidget.global.Constants
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.helpers.WeatherHelper
import com.tommasoberlose.anotherwidget.network.repository.WeatherApiRepository
import com.tommasoberlose.anotherwidget.network.repository.WeatherGovRepository
import com.tommasoberlose.anotherwidget.ui.fragments.MainFragment
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import org.greenrobot.eventbus.EventBus
import java.lang.Exception

class WeatherNetworkApi(val context: Context) {
    suspend fun updateWeather() {
        Kotpref.init(context)
        Preferences.weatherProviderError = "-"
        Preferences.weatherProviderLocationError = ""
        if (Preferences.showWeather && Preferences.customLocationLat != "" && Preferences.customLocationLon != "") {
            when (Constants.WeatherProvider.fromInt(Preferences.weatherProvider)) {
                Constants.WeatherProvider.WEATHER_API -> useWeatherApiProvider(context)
                Constants.WeatherProvider.WEATHER_GOV -> useWeatherGov(context)
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
                                Preferences.weatherUpdatedAt = System.currentTimeMillis()

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

    private suspend fun useWeatherApiProvider(context: Context) {
        if (Preferences.weatherProviderApiWeatherApi != "") {
            val repository = WeatherApiRepository()
            when (val response = repository.getWeather()) {
                is NetworkResponse.Success -> {
                    try {
                        val current = response.body["current"] as Map<String, Any>?
                        current?.let {
                            val tempC = current["temp_c"] as Double
                            val tempF = current["temp_f"] as Double
                            val isDay = current["is_day"] as Double
                            val condition = current["condition"] as Map<String, Any>
                            val iconCode = condition["code"] as Double
                            Preferences.weatherTemp = if (Preferences.weatherTempUnit == "F") tempF.toFloat() else tempC.toFloat()
                            Preferences.weatherIcon = WeatherHelper.getWeatherApiIcon(iconCode.toInt(), isDay.toInt() == 1)
                            Preferences.weatherRealTempUnit = Preferences.weatherTempUnit
                            Preferences.weatherUpdatedAt = System.currentTimeMillis()
                            MainWidget.updateWidget(context)
                            Preferences.weatherProviderError = ""
                            Preferences.weatherProviderLocationError = ""
                        }
                    } catch (_: Exception) {
                        Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_generic)
                        Preferences.weatherProviderLocationError = ""
                    } finally {
                        EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                    }
                }
                is NetworkResponse.ServerError -> {
                    Preferences.weatherProviderError = when (response.code) {
                        401 -> context.getString(R.string.weather_provider_error_invalid_key)
                        403 -> context.getString(R.string.weather_provider_error_expired_key)
                        else -> context.getString(R.string.weather_provider_error_generic)
                    }
                    Preferences.weatherProviderLocationError = ""
                    WeatherHelper.removeWeather(context)
                    EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                }
                else -> {
                    Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_connection)
                    Preferences.weatherProviderLocationError = ""
                    EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
                }
            }
        } else {
            Preferences.weatherProviderError = context.getString(R.string.weather_provider_error_missing_key)
            Preferences.weatherProviderLocationError = ""
            WeatherHelper.removeWeather(context)
            EventBus.getDefault().post(MainFragment.UpdateUiMessageEvent())
        }
    }
}
