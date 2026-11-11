plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.nexus.panel"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.nexus.panel"
        minSdk = 27          // NSPanel Pro ships Android 8.1
        targetSdk = 27       // keep the 8.x permission model on the panel
        versionCode = 1
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // MQTT v3 (Pro-Tools-compatible spine)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    // JSON for discovery payloads / config
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Coroutines for the service loop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // AndroidX basics
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // HA WebSocket client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
