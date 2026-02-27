import com.android.build.api.dsl.Packaging

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
    packaging {

        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"

            // Option 1: Pick the first one encountered (most common for licenses)
            pickFirsts.add("META-INF/license/LICENSE.webbit.txt")
            pickFirsts.add("META-INF/license/LICENSE.jbzip2.txt")
            pickFirsts.add("META-INF/license/LICENSE.snappy.txt")
            pickFirsts.add("META-INF/license/LICENSE.xz.txt")
            pickFirsts.add("META-INF/license/LICENSE.protobuf.txt")
            pickFirsts.add("META-INF/license/LICENSE.nghttp2-hpack.txt")
            pickFirsts.add("META-INF/license/LICENSE.base64.txt")
            pickFirsts.add("META-INF/license/LICENSE.commons-lang.txt")
            pickFirsts.add("META-INF/license/LICENSE.jzlib.txt")
            pickFirsts.add("META-INF/license/LICENSE.jctools.txt")
            pickFirsts.add("META-INF/license/LICENSE.log4j.txt")
            pickFirsts.add("META-INF/license/LICENSE.libdivsufsort.txt")
            pickFirsts.add("META-INF/license/LICENSE.commons-logging.txt")
            pickFirsts.add("META-INF/license/LICENSE.hyper-hpack.txt")
            pickFirsts.add("META-INF/native-image/io.netty/netty-codec-native-quic/resource-config.json")
            pickFirsts.add("META-INF/license/LICENSE.bouncycastle.txt")
            pickFirsts.add("META-INF/license/LICENSE.lzma-java.txt")
            pickFirsts.add("META-INF/native-image/io.netty/netty-codec-native-quic/reflect-config.json")
            pickFirsts.add("META-INF/license/LICENSE.caliper.txt")
            pickFirsts.add("META-INF/license/LICENSE.lz4.txt")
            pickFirsts.add("META-INF/license/LICENSE.boringssl.txt")
            pickFirsts.add("META-INF/license/LICENSE.jsr166y.txt")
            pickFirsts.add("META-INF/license/NOTICE.harmony.txt")
            pickFirsts.add("META-INF/license/LICENSE.quiche.txt")
            pickFirsts.add("META-INF/license/LICENSE.mvn-wrapper.txt")
            pickFirsts.add("META-INF/license/LICENSE.slf4j.txt")
            pickFirsts.add("META-INF/license/LICENSE.aalto-xml.txt")
            pickFirsts.add("META-INF/license/LICENSE.dnsinfo.txt")
            pickFirsts.add("META-INF/license/LICENSE.zstd-jni.txt")
            pickFirsts.add("META-INF/native-image/io.netty/netty-codec-native-quic/native-image.properties")
            pickFirsts.add("META-INF/license/LICENSE.compress-lzf.txt")
            pickFirsts.add("META-INF/license/LICENSE.jboss-marshalling.txt")
            pickFirsts.add("META-INF/license/LICENSE.hpack.txt")
            pickFirsts.add("META-INF/native-image/io.netty/netty-codec-native-quic/jni-config.json")
            pickFirsts.add("META-INF/license/LICENSE.harmony.txt")
            pickFirsts.add("META-INF/license/LICENSE.jfastlz.txt")
            pickFirsts.add("META-INF/license/LICENSE.brotli4j.txt")

            // Option 2: Exclude the file (use with caution, understand implications)
            // excludes.add("META-INF/license/LICENSE.webbit.txt")

            // You might encounter similar issues with other META-INF files,
            // so you might need to add more pickFirst or exclude rules:
            // pickFirsts.add("META-INF/LICENSE.txt")
            // pickFirsts.add("META-INF/NOTICE.txt")
            // pickFirsts.add("META-INF/DEPENDENCIES")
            // ... and so on for any other conflicts reported by Gradle.
        }

    }
    lint {
        // Disables the specific detector causing the crash
        disable.add("LocalContextConfigurationRead")
        disable.add("ConfigurationScreenWidthHeight")

        // Optional: prevents lint from stopping the entire build if other errors occur
        abortOnError = false
    }
}

dependencies {

    implementation(libs.androidx.compose.material3)
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
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1") // For ViewModel
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // https://mvnrepository.com/artifact/io.netty/netty-all
    implementation("io.netty:netty-all:4.2.2.Final")
    implementation("io.projectreactor.netty:reactor-netty:1.1.0")
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    // JWT support
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    // https://mvnrepository.com/artifact/org.pcap4j/pcap4j-core
    implementation("org.pcap4j:pcap4j-core:2.0.0-alpha.6")
    // https://mvnrepository.com/artifact/org.pcap4j/pcap4j-packetfactory-static
    implementation("org.pcap4j:pcap4j-packetfactory-static:2.0.0-alpha.6")
    // Bridges Project Reactor (Mono/Flux) with Kotlin Coroutines (suspend)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

    // bouncy castle for DTLS support, android had
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bctls-jdk18on:1.78.1") // Required for BCJSSE/DTLS


    // For ViewModel support with Hilt (specifically for @HiltViewModel and ViewModel lifecycle)
    // This is implicitly included with hilt-android, but sometimes explicit inclusion
    // or ensuring compatibility with androidx.hilt:hilt-navigation-compose might be needed
    // depending on your setup. Usually, the above two are sufficient for @HiltViewModel.
    // ... other dependencies
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core.android)
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
