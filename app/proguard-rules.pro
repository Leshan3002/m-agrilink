# M-AgriLink release keep-rules (R8 full mode).
# Room, CameraX, LiteRT and lifecycle ship their own consumer rules;
# the entries below cover our reflection-based models and third-party UI libs.

# Retrofit/Gson payload models (parsed via reflection).
-keep class com.example.m_agrilink.network.** { *; }
-keep class com.example.m_agrilink.data.** { *; }
-keep class com.example.m_agrilink.WeatherResponse* { *; }
-keep class com.example.m_agrilink.OpenMeteoApiService* { *; }

# Room entities/DAOs referenced by generated code.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Charts + maps (reflection-heavy renderers).
-keep class com.github.mikephil.charting.** { *; }
-keep class org.osmdroid.** { *; }

# CameraX / LiteRT natives are loaded via System.loadLibrary; keep their facades.
-keep class androidx.camera.** { *; }
-keep class org.tensorflow.lite.** { *; }
