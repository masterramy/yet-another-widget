package com.tommasoberlose.anotherwidget.helpers

import android.Manifest
import android.app.Activity
import android.content.Context
import com.chibatching.kotpref.Kotpref
import com.google.android.gms.location.LocationServices
import com.tommasoberlose.anotherwidget.R
import com.tommasoberlose.anotherwidget.global.Constants
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.network.WeatherNetworkApi
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import com.tommasoberlose.anotherwidget.utils.checkGrantedPermission
import com.tommasoberlose.anotherwidget.utils.isDarkTheme
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


/**
 * Created by tommaso on 08/10/17.
 */

object WeatherHelper {

    suspend fun updateWeather(context: Context) {
        Kotpref.init(context)
        val networkApi = WeatherNetworkApi(context)
        if (Preferences.customLocationAdd != "") {
            networkApi.updateWeather()
        } else if (context is Activity && context.checkGrantedPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // Device coordinates are refreshed only from a visible app surface. Scheduled
            // refreshes reuse the last saved coordinates and never request device location.
            val location = suspendCancellableCoroutine<android.location.Location?> { continuation ->
                LocationServices.getFusedLocationProviderClient(context)
                    .lastLocation
                    .addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        continuation.resume(if (task.isSuccessful) task.result else null)
                    }
            }
            location?.let {
                Preferences.customLocationLat = it.latitude.toString()
                Preferences.customLocationLon = it.longitude.toString()
            }
            if (Preferences.customLocationLat != "" && Preferences.customLocationLon != "") {
                networkApi.updateWeather()
            }
        } else if (Preferences.customLocationLat != "" && Preferences.customLocationLon != "") {
            networkApi.updateWeather()
        }
    }

    fun removeWeather(context: Context) {
        Preferences.remove(Preferences::weatherTemp)
        Preferences.remove(Preferences::weatherRealTempUnit)
        Preferences.remove(Preferences::weatherIcon)
        MainWidget.updateWidget(context)
    }

    fun getProviderName(
        context: Context,
        provider: Constants.WeatherProvider = Constants.WeatherProvider.fromInt(Preferences.weatherProvider)
    ): String = context.getString(when (provider) {
        Constants.WeatherProvider.WEATHER_API -> R.string.settings_weather_provider_weather_api
        Constants.WeatherProvider.WEATHER_GOV -> R.string.settings_weather_provider_weather_gov
    })

    fun getProviderInfoTitle(
        context: Context,
        provider: Constants.WeatherProvider = Constants.WeatherProvider.fromInt(Preferences.weatherProvider)
    ): String = context.getString(when (provider) {
        Constants.WeatherProvider.WEATHER_API -> R.string.weather_provider_info_weatherapi_title
        Constants.WeatherProvider.WEATHER_GOV -> R.string.weather_provider_info_weather_gov_title
    })

    fun getProviderInfoSubtitle(
        context: Context,
        provider: Constants.WeatherProvider = Constants.WeatherProvider.fromInt(Preferences.weatherProvider)
    ): String = context.getString(when (provider) {
        Constants.WeatherProvider.WEATHER_API -> R.string.weatherapi_compliance_notice
        Constants.WeatherProvider.WEATHER_GOV -> R.string.weather_provider_info_weather_gov_subtitle
    })

    fun getProviderLink(
        provider: Constants.WeatherProvider = Constants.WeatherProvider.fromInt(Preferences.weatherProvider)
    ): String = when (provider) {
        Constants.WeatherProvider.WEATHER_API -> "https://www.weatherapi.com/"
        Constants.WeatherProvider.WEATHER_GOV -> "https://www.weather.gov/"
    }

    fun isKeyRequired(
        provider: Constants.WeatherProvider = Constants.WeatherProvider.fromInt(Preferences.weatherProvider)
    ): Boolean = provider == Constants.WeatherProvider.WEATHER_API

    fun getWeatherIconResource(context: Context, icon: String, style: Int = Preferences.weatherIconPack): Int {
        return when (icon) {
            "01d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.clear_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.clear_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.clear_day_4
                    else -> if (context.isDarkTheme()) R.drawable.clear_day_5 else R.drawable.clear_day_5_light
                }
            }
            "02d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.partly_cloudy_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.partly_cloudy_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.partly_cloudy_4
                    else -> if (context.isDarkTheme()) R.drawable.partly_cloudy_5 else R.drawable.partly_cloudy_5_light
                }
            }
            "03d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.mostly_cloudy_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.mostly_cloudy_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.mostly_cloudy_4
                    else -> if (context.isDarkTheme()) R.drawable.mostly_cloudy_5 else R.drawable.mostly_cloudy_5_light
                }
            }
            "04d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.cloudy_weather_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.cloudy_weather_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.cloudy_weather_4
                    else -> if (context.isDarkTheme()) R.drawable.cloudy_weather_5 else R.drawable.cloudy_weather_5_light
                }
            }
            "09d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.storm_weather_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.storm_weather_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.storm_weather_day_4
                    else -> if (context.isDarkTheme()) R.drawable.storm_weather_day_5 else R.drawable.storm_weather_day_5_light
                }
            }
            "10d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.rainy_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.rainy_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.rainy_day_4
                    else -> if (context.isDarkTheme()) R.drawable.rainy_day_5 else R.drawable.rainy_day_5_light
                }
            }
            "11d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.thunder_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.thunder_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.thunder_day_4
                    else -> if (context.isDarkTheme()) R.drawable.thunder_day_5 else R.drawable.thunder_day_5_light
                }
            }
            "13d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.snow_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.snow_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.snow_day_4
                    else -> if (context.isDarkTheme()) R.drawable.snow_day_5 else R.drawable.snow_day_5_light
                }
            }
            "50d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.haze_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.haze_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.haze_day_4
                    else -> if (context.isDarkTheme()) R.drawable.haze_day_5 else R.drawable.haze_day_5_light
                }
            }
            "80d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.windy_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.windy_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.windy_day_4
                    else -> if (context.isDarkTheme()) R.drawable.windy_day_5 else R.drawable.windy_day_5_light
                }
            }
            "81d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.rain_snow_day_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.rain_snow_day_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.rain_snow_day_4
                    else -> if (context.isDarkTheme()) R.drawable.rain_snow_day_5 else R.drawable.rain_snow_day_5_light
                }
            }
            "82d" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.haze_weather_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.haze_weather_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.haze_weather_4
                    else -> if (context.isDarkTheme()) R.drawable.haze_weather_5 else R.drawable.haze_weather_5_light
                }
            }



            "01n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.clear_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.clear_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.clear_night_4
                    else -> if (context.isDarkTheme()) R.drawable.clear_night_5 else R.drawable.clear_night_5_light
                }
            }
            "02n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.partly_cloudy_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.partly_cloudy_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.partly_cloudy_night_4
                    else -> if (context.isDarkTheme()) R.drawable.partly_cloudy_night_5 else R.drawable.partly_cloudy_night_5_light
                }
            }
            "03n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.mostly_cloudy_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.mostly_cloudy_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.mostly_cloudy_night_4
                    else -> if (context.isDarkTheme()) R.drawable.mostly_cloudy_night_5 else R.drawable.mostly_cloudy_night_5_light
                }
            }
            "04n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.cloudy_weather_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.cloudy_weather_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.cloudy_weather_4
                    else -> if (context.isDarkTheme()) R.drawable.cloudy_weather_5 else R.drawable.cloudy_weather_5_light
                }
            }
            "09n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.storm_weather_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.storm_weather_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.storm_weather_night_4
                    else -> if (context.isDarkTheme()) R.drawable.storm_weather_night_5 else R.drawable.storm_weather_night_5_light
                }
            }
            "10n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.rainy_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.rainy_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.rainy_night_4
                    else -> if (context.isDarkTheme()) R.drawable.rainy_night_5 else R.drawable.rainy_night_5_light
                }
            }
            "11n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.thunder_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.thunder_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.thunder_night_4
                    else -> if (context.isDarkTheme()) R.drawable.thunder_night_5 else R.drawable.thunder_night_5_light
                }
            }
            "13n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.snow_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.snow_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.snow_night_4
                    else -> if (context.isDarkTheme()) R.drawable.snow_night_5 else R.drawable.snow_night_5_light
                }
            }
            "50n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.haze_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.haze_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.haze_night_4
                    else -> if (context.isDarkTheme()) R.drawable.haze_night_5 else R.drawable.haze_night_5_light
                }
            }
            "80n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.windy_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.windy_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.windy_night_4
                    else -> if (context.isDarkTheme()) R.drawable.windy_night_5 else R.drawable.windy_night_5_light
                }
            }
            "81n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.rain_snow_night_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.rain_snow_night_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.rain_snow_night_4
                    else -> if (context.isDarkTheme()) R.drawable.rain_snow_night_5 else R.drawable.rain_snow_night_5_light
                }
            }
            "82n" -> {
                when (style) {
                    Constants.WeatherIconPack.COOL.rawValue -> R.drawable.haze_weather_3
                    Constants.WeatherIconPack.MINIMAL.rawValue -> R.drawable.haze_weather_2
                    Constants.WeatherIconPack.GOOGLE_NEWS.rawValue -> R.drawable.haze_weather_4
                    else -> if (context.isDarkTheme()) R.drawable.haze_weather_5 else R.drawable.haze_weather_5_light
                }
            }
            else -> {
                return if (context.isDarkTheme()) R.drawable.unknown_dark else R.drawable.unknown_light
            }
        }
    }

    fun getWeatherGovIcon(iconString: String, isDaytime: Boolean): String = when {
        iconString.contains("skc") -> "01"
        iconString.contains("few") -> "02"
        iconString.contains("sct") -> "03"
        iconString.contains("bkn") -> "04"
        iconString.contains("ovc") -> "04"
        iconString.contains("wind_skc") -> "01"
        iconString.contains("wind_few") -> "02"
        iconString.contains("wind_sct") -> "03"
        iconString.contains("wind_bkn") -> "04"
        iconString.contains("wind_ovc") -> "04"
        iconString.contains("snow") -> "13"
        iconString.contains("rain_snow") -> "81"
        iconString.contains("rain_sleet") -> "81"
        iconString.contains("snow_sleet") -> "81"
        iconString.contains("fzra") -> "81"
        iconString.contains("rain_fzra") -> "81"
        iconString.contains("snow_fzra") -> "81"
        iconString.contains("sleet") -> "81"
        iconString.contains("rain") -> "10"
        iconString.contains("rain_showers") -> "10"
        iconString.contains("rain_showers_hi") -> "10"
        iconString.contains("tsra") -> "82"
        iconString.contains("tsra_sct") -> "82"
        iconString.contains("tsra_hi") -> "82"
        iconString.contains("tornado") -> "80"
        iconString.contains("hurricane") -> "80"
        iconString.contains("tropical_storm") -> "09"
        iconString.contains("dust") -> "Dust"
        iconString.contains("smoke") -> "Smoke"
        iconString.contains("haze") -> "50"
        iconString.contains("hot") -> "01"
        iconString.contains("cold") -> "13"
        iconString.contains("blizzard") -> "80"
        iconString.contains("fog") -> "82"
        else -> ""
    } + if (isDaytime) "d" else "n"

    fun getYRIcon(iconCode: String, isDaytime: Boolean): String = when {
        iconCode.contains("clearsky") -> "01"
        iconCode.contains("cloudy") -> "04"
        iconCode.contains("fair") -> "02"
        iconCode.contains("fog") -> "82"
        iconCode.contains("heavyrain") -> "10"
        iconCode.contains("heavyrainandthunder") -> "11"
        iconCode.contains("heavyrainshowers") -> "10"
        iconCode.contains("heavyrainshowersandthunder") -> "11"
        iconCode.contains("heavysleet") -> "10"
        iconCode.contains("heavysleetandthunder") -> "11"
        iconCode.contains("heavysleetshowers") -> "10"
        iconCode.contains("heavysleetshowersandthunder") -> "11"
        iconCode.contains("heavysnow") -> "13"
        iconCode.contains("heavysnowandthunder") -> "13"
        iconCode.contains("heavysnowshowers") -> "13"
        iconCode.contains("heavysnowshowersandthunder") -> "13"
        iconCode.contains("lightrain") -> "10"
        iconCode.contains("lightrainandthunder") -> "11"
        iconCode.contains("lightrainshowers") -> "10"
        iconCode.contains("lightrainshowersandthunder") -> "11"
        iconCode.contains("lightsleet") -> "10"
        iconCode.contains("lightsleetandthunder") -> "11"
        iconCode.contains("lightsleetshowers") -> "10"
        iconCode.contains("lightsnow") -> "13"
        iconCode.contains("lightsnowandthunder") -> "13"
        iconCode.contains("lightsnowshowers") -> "13"
        iconCode.contains("lightssleetshowersandthunder") -> "81"
        iconCode.contains("lightssnowshowersandthunder") -> "81"
        iconCode.contains("partlycloudy") -> "03"
        iconCode.contains("rain") -> "10"
        iconCode.contains("rainandthunder") -> "11"
        iconCode.contains("rainshowers") -> "10"
        iconCode.contains("rainshowersandthunder") -> "11"
        iconCode.contains("sleet") -> "10"
        iconCode.contains("sleetandthunder") -> "11"
        iconCode.contains("sleetshowers") -> "10"
        iconCode.contains("sleetshowersandthunder") -> "11"
        iconCode.contains("snow") -> "13"
        iconCode.contains("snowandthunder") -> "13"
        iconCode.contains("snowshowers") -> "13"
        iconCode.contains("snowshowersandthunder") -> "13"
        else -> ""
    } + if (isDaytime) "d" else "n"


    fun getWeatherApiIcon(icon: Int, isDaytime: Boolean): String = when(icon) {
        1000 -> "01"; 1003 -> "02"; 1006 -> "03"; 1009 -> "04"; 1030 -> "82"
        1063 -> "10"; 1066 -> "13"; 1069 -> "13"; 1072 -> "81"; 1087 -> "11"
        1114 -> "13"; 1117 -> "09"; 1135 -> "82"; 1147 -> "82"
        1150, 1153, 1168, 1171, 1180, 1183, 1186, 1189, 1192, 1195 -> "10"
        1198, 1201 -> "81"; 1204, 1207, 1210, 1213, 1216, 1219, 1222, 1225, 1237 -> "13"
        1240, 1243, 1246 -> "10"; 1249, 1252, 1255, 1258, 1261, 1264 -> "13"
        1273, 1276 -> "09"; 1279, 1282 -> "13"; else -> ""
    } + if (isDaytime) "d" else "n"

}