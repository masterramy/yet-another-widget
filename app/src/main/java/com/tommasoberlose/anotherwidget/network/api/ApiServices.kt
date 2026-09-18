package com.tommasoberlose.anotherwidget.network.api

import com.haroldadmin.cnradapter.NetworkResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

object ApiServices {
    interface WeatherGovApiService {
        @Headers("User-Agent: YetAnotherWidget/2.3.3 (https://github.com/masterramy/yet-another-widget)")
        @GET("points/{latitude},{longitude}")
        suspend fun getGridPoints(@Path("latitude") latitude: String, @Path("longitude") longitude: String): NetworkResponse<HashMap<String, Any>, HashMap<String, Any>>

        @Headers("User-Agent: YetAnotherWidget/2.3.3 (https://github.com/masterramy/yet-another-widget)")
        @GET("gridpoints/{gridId}/{gridX},{gridY}/forecast")
        suspend fun getWeather(@Path("gridId") gridId: String, @Path("gridX") gridX: Int, @Path("gridY") gridY: Int, @Query("units") unit: String): NetworkResponse<HashMap<String, Any>, HashMap<String, Any>>
    }

    interface WeatherApiService {
        @Headers("Accept: application/json")
        @GET("current.json")
        suspend fun getWeather(@Query("key") key: String, @Query("q") location: String): NetworkResponse<HashMap<String, Any>, HashMap<String, Any>>
    }
}
