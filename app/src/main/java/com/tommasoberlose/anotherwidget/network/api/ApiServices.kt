package com.tommasoberlose.anotherwidget.network.api

import com.haroldadmin.cnradapter.NetworkResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

object ApiServices {
    interface WeatherGovApiService {
        @GET("points/{latitude},{longitude}")
        suspend fun getGridPoints(
            @Header("User-Agent") userAgent: String,
            @Path("latitude") latitude: String,
            @Path("longitude") longitude: String
        ): NetworkResponse<HashMap<String, Any>, HashMap<String, Any>>

        @GET("gridpoints/{gridId}/{gridX},{gridY}/forecast")
        suspend fun getWeather(
            @Header("User-Agent") userAgent: String,
            @Path("gridId") gridId: String,
            @Path("gridX") gridX: Int,
            @Path("gridY") gridY: Int,
            @Query("units") unit: String
        ): NetworkResponse<HashMap<String, Any>, HashMap<String, Any>>
    }

    interface WeatherApiService {
        @Headers("Accept: application/json")
        @GET("current.json")
        suspend fun getWeather(@Query("key") key: String, @Query("q") location: String): NetworkResponse<HashMap<String, Any>, HashMap<String, Any>>
    }
}
