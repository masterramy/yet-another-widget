package com.ramybaheeg.yetanotherwidget.network.repository

import com.haroldadmin.cnradapter.NetworkResponseAdapterFactory
import com.ramybaheeg.yetanotherwidget.BuildConfig
import com.ramybaheeg.yetanotherwidget.network.api.ApiServices
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherGovRepository {

    /* WEATHER GOV*/
    private val apiServiceGov: ApiServices.WeatherGovApiService = getRetrofit().create(ApiServices.WeatherGovApiService::class.java)
    private val userAgent =
        "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (https://github.com/masterramy/yet-another-widget)"

    suspend fun getGridPoints(latitude: String, longitude: String) =
        apiServiceGov.getGridPoints(userAgent, latitude, longitude)

    suspend fun getWeather(gridId: String, gridX: Double, gridY: Double, unit: String) =
        apiServiceGov.getWeather(userAgent, gridId, gridX.toInt(), gridY.toInt(), unit)

    companion object {
        private const val BASE_URL_GOV = "https://api.weather.gov/"

        private fun getRetrofit(): Retrofit {
            return Retrofit.Builder()
                .baseUrl(BASE_URL_GOV)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(NetworkResponseAdapterFactory())
                .build()
        }
    }
}