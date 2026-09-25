import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

// 1. Read the secret API key safely at the top-level scope of the Kotlin DSL file
val props = Properties()
val propFile = rootProject.file("local.properties")
if (propFile.exists()) {
    propFile.inputStream().use { props.load(it) }
}
val apiKey = props.getProperty("GEMINI_API_KEY") ?: "\"\""

android {
    namespace = "com.example.m_agrilink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.m_agrilink"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 2. Inject the dynamic API key token into your build variables matrix safely
        buildConfigField("String", "GEMINI_API_KEY", apiKey)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }

        // Proper nested location for your Jetpack Compose build features flags
        buildFeatures {
            viewBinding = true
            buildConfig = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Jetpack Compose UI Platform dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Jetpack Room Components
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Core Icon and Networking modules
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    "kapt"(libs.room.compiler)

    // Native CameraX field scanner (camera viewport + lifecycle binding)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Test Configurations
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // LiteRT (official TensorFlow Lite successor): unique manifest namespace,
    // so the merger passes on AGP 9. Note: litert-support is intentionally
    // omitted — our pipeline does manual ByteBuffer preprocessing and the
    // support/support-api pair shares one namespace, which breaks the merge.
    implementation("com.google.ai.edge.litert:litert:1.4.2")
}
