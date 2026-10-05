package com.example.m_agrilink

import com.google.gson.annotations.SerializedName

/**
 * STEP 2: METEOROLOGICAL DATA MODEL PAYLOADS
 * Maps the Open-Meteo JSON response structure for hourly forecast metrics.
 * hourly.relative_humidity_2m is canonical; relativehumidity_2m kept as
 * legacy alias via alternate. daily + current are optional (null when
 * server omits them) so old cached payloads still parse.
 */
data class WeatherResponseLive(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val hourly: HourlyData,
    val daily: DailyData? = null,
    val current: CurrentBlock? = null,
    val current_weather: CurrentWeather?
)

data class HourlyData(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val precipitation_probability: List<Int> = emptyList(),
    @SerializedName(value = "relative_humidity_2m", alternate = ["relativehumidity_2m"])
    val relativehumidity_2m: List<Int> = emptyList()
)

data class DailyData(
    val time: List<String> = emptyList(),
    val temperature_2m_max: List<Double> = emptyList(),
    val temperature_2m_min: List<Double> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
    val precipitation_probability_max: List<Int> = emptyList(),
    @SerializedName(value = "weathercode", alternate = ["weather_code", "weatherCode"])
    val weathercode: List<Int> = emptyList()
)

data class CurrentBlock(
    val temperature_2m: Double? = null,
    val relative_humidity_2m: Int? = null,
    val weather_code: Int? = null,
    val wind_speed_10m: Double? = null,
    val time: String? = null
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val winddirection: Double,
    val weathercode: Int,
    val time: String
)
