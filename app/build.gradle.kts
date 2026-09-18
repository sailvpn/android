
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
            isMinifyEnabled = false // <-- Set this to false to turn off shrinking completely

            // You can also completely remove the line if you prefer,
            // as it defaults to false automatically.

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

    // Use the BOM defined in your TOML
    implementation(platform(libs.androidx.compose.bom))

    // Networking (Replaces Reactor/Netty)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Serialization (Replaces Jackson)
    implementation(libs.kotlinx.serialization.json)

    // Time (Replaces java.time for Native compatibility)
    implementation(libs.kotlinx.datetime)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // --- COMPOSE & UI ---
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // --- DATA & CORE ---
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)

    // native archives
    implementation(files("libs/troadengine.aar"))
    implementation(files("libs/troadengine-sources.jar"))
}
