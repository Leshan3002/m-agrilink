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
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,relative_humidity_2m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,weathercode",
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("timezone") timezone: String = "Africa/Nairobi",
        @Query("forecast_days") forecastDays: Int = 7
    ): WeatherResponseLive
}
