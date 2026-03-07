
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Add the serialization plugin to support JSON without Jackson
    kotlin("plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.illiad.troad"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.illiad.troad"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // Specifies the ABIs Gradle should package from your gomobile AAR
            // Common filters: 'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Recommended for smaller APKs
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    lint {
        disable.add("LocalContextConfigurationRead")
        disable.add("ConfigurationScreenWidthHeight")
        abortOnError = false
    }
}

dependencies {
    // --- KOTLIN NATIVE REPLACEMENTS ---
    // Networking (Replaces Reactor/Netty)
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // Serialization (Replaces Jackson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Time (Replaces java.time for Native compatibility)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // --- COMPOSE & UI ---
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:compose:1.9.2")

    // --- DATA & CORE ---
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // native archives
    implementation(files("libs/engine.aar"))
}
