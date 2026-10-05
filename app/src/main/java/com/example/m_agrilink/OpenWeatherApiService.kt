package com.example.m_agrilink

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap — user-selected primary source (replaces HingaSmart ask).
 * HingaSmart publishes no public weather endpoint (AICCRA/KMD/TomorrowNow
 * stack), so we wire the closest public equivalent: OpenWeather current +
 * 5-day /3-hour forecast. Needs OPENWEATHER_API_KEY in local.properties.
 * Open-Meteo remains as keyless fallback in WeatherTerminalScreen.
 */
interface OpenWeatherApiService {

    @GET("data/2.5/weather")
    suspend fun getCurrent(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appId: String,
        @Query("units") units: String = "metric"
    ): OwmCurrentResponse

    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appId: String,
        @Query("units") units: String = "metric"
    ): OwmForecastResponse
}

data class OwmCurrentResponse(
    val main: OwmMain? = null,
    val weather: List<OwmWeather> = emptyList(),
    val wind: OwmWind? = null,
    val sys: OwmSys? = null,
    val dt: Long = 0,
    val name: String? = null
)

data class OwmForecastResponse(
    val list: List<OwmItem> = emptyList()
)

data class OwmItem(
    val dt: Long = 0,
    val dt_txt: String? = null,
    val main: OwmMain? = null,
    val weather: List<OwmWeather> = emptyList(),
    val pop: Double? = null
)

data class OwmMain(
    val temp: Double? = null,
    val temp_min: Double? = null,
    val temp_max: Double? = null,
    val humidity: Int? = null
)

data class OwmWeather(
    val id: Int? = null,
    val main: String? = null,
    val description: String? = null
)

data class OwmWind(val speed: Double? = null)
data class OwmSys(val sunrise: Long? = null, val sunset: Long? = null)
