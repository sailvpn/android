plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.illiad.troad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.illiad.troad"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00") // Use latest BOM version
    implementation(composeBom)
    androidTestImplementation(composeBom)
    // Your other Compose dependencies (no need to specify versions if using BOM for them)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3") // If using Material 3
    implementation("androidx.compose.material:material")   // If using Material 2 (M2 icons often used with M3 too)
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Material Icons (still need this explicitly if not covered by a very specific BOM setup)
    implementation("androidx.compose.material:material-icons-core:1.7.0-beta01")
    implementation("androidx.compose.material:material-icons-extended:1.7.0-beta01")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1") // For ViewModel
    // https://mvnrepository.com/artifact/io.netty/netty-all
    implementation("io.netty:netty-all:4.1.118.Final")
    // https://mvnrepository.com/artifact/org.pcap4j/pcap4j-core
    implementation("org.pcap4j:pcap4j-core:2.0.0-alpha.6")
    // https://mvnrepository.com/artifact/org.pcap4j/pcap4j-packetfactory-static
    implementation("org.pcap4j:pcap4j-packetfactory-static:2.0.0-alpha.6")

    // ... other dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}