package com.example.powerhouseassgn

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiUtilities {

    private const val base_Url = "https://api.openweathermap.org/"

    fun getInstance() : Retrofit{
        return Retrofit.Builder()
            .baseUrl(base_Url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}