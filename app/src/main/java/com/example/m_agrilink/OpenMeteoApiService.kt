package com.example.m_agrilink

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * STEP 1: RETROFIT SERVICE INTERFACE
 * Targets the key-less Open-Meteo API for real-time agricultural meteorological parameters.
 */
interface OpenMeteoApiService {

    @GET("v1/forecast")
    suspend fun getLiveForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,relativehumidity_2m",
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("timezone") timezone: String = "Africa/Nairobi"
    ): WeatherResponseLive
}
