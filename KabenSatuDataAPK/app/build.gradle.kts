plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.kabensatudata"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kabensatudata"
        minSdk = 26 // Android 8.0+ to support EncryptedSharedPreferences
        targetSdk = 35
        versionCode = 7
        versionName = "2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room schema export for migrations
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            storeFile = file("kabensatudata-release.jks")
            storePassword = "kabensatu2025"
            keyAlias = "kabensatudata"
            keyPassword = "kabensatu2025"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/versions/9/module-info.class",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room (Local Database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit + OkHttp (Networking)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Hilt (Dependency Injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager (Background Sync)
    implementation(libs.androidx.work.runtime.ktx)

    // Security (Encrypted Token Storage)
    implementation(libs.androidx.security.crypto)

    // MapLibre GL (Vector Map Engine — Offline + Dark Mode)
    implementation(libs.maplibre.android.sdk)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore (Preferences)
    implementation(libs.androidx.datastore.preferences)

    // Coil (Image Loading — animated WebP)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Google Fonts (Plus Jakarta Sans)
    implementation(libs.ui.text.google.fonts)

    // Firebase (Push Notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Nearby Connections (Bluetooth P2P)
    implementation(libs.play.services.nearby)

    // Location (GPS Tracking)
    implementation(libs.play.services.location)

    // CameraX (Camera Monitor)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit (QR Scanner)
    implementation(libs.mlkit.barcode)

    // NanoHTTPD (embedded MJPEG server)
    implementation(libs.nanohttpd)

    // SceneView AR (ARCore + 3D rendering)
    // AR temporarily disabled
    // implementation(libs.arsceneview)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
