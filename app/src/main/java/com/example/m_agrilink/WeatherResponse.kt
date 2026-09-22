package com.example.m_agrilink

/**
 * STEP 2: METEOROLOGICAL DATA MODEL PAYLOADS
 * Maps the Open-Meteo JSON response structure for hourly forecast metrics.
 */
data class WeatherResponseLive(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val hourly: HourlyData,
    val current_weather: CurrentWeather?
)

data class HourlyData(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val precipitation_probability: List<Int>,
    val relativehumidity_2m: List<Int>
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val winddirection: Double,
    val weathercode: Int,
    val time: String
)
