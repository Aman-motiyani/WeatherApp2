package com.example.powerhouseassgn

import com.example.powerhouseassgn.model.WeatherData
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {

    @GET("data/2.5/weather?&units=metric&appid=5d7b702e382e8bcdd650db6eebf78fc1")
    suspend fun getCityData(
        @Query("q") cityName: String
    ) : Response<WeatherData>

    @GET("data/2.5/weather?&units=metric&appid=5d7b702e382e8bcdd650db6eebf78fc1")
    suspend fun getCurrentWeatherData(
        @Query("lat") lat: String?,
        @Query("lon") lon: String?,
    ) : Response<WeatherData>
}