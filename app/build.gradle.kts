import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.geminimultimodalliveapi"
    compileSdk = 35

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val inputStream = FileInputStream(localPropertiesFile)
        localProperties.load(inputStream)
        inputStream.close()
    }
    val geminiApiKey = localProperties.getProperty("gemini.api.key") ?: ""

    defaultConfig {
        applicationId = "com.example.geminimultimodalliveapi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.security.crypto)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Google Authentication & Google Drive API
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    implementation("org.json:json:20231013") // JSON parsing
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}