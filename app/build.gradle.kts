import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// SAHAY optional online enhancement (Gemini) — the key lives only in
// local.properties (git-ignored, see .gitignore), never in a tracked
// source file. Blank/missing key = the feature is compiled in but
// stays off; GeminiEnhancer checks for a blank key before ever making
// a network call.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val geminiApiKey: String = localProps.getProperty("gemini.api.key", "")

android {
    namespace = "com.accessibility.detector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.accessibility.detector"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = File(projectDir, "release.keystore")
            storePassword = "accessibility123"
            keyAlias = "accessibility"
            keyPassword = "accessibility123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        noCompress("tflite")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // TensorFlow Lite Task Vision & Support
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // SAHAY SEE — ML Kit on-device Text Recognition (Latin + Devanagari/Hindi).
    // Telugu has no on-device ML Kit script model as of this build — see
    // Admin > AI Models for the disclosed limitation.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")

    // SAHAY SEE — real trained TFLite currency classifier (Interpreter API,
    // same runtime already pulled in by tensorflow-lite-support above).
    implementation("org.tensorflow:tensorflow-lite:2.13.0")

    // SAHAY LISTEN — real acoustic event classification via Google's
    // MediaPipe Audio Classifier Task, running the pretrained YAMNet model
    // bundled in assets/yamnet.tflite (521 AudioSet classes — real horn,
    // siren, alarm, doorbell detection, not an amplitude heuristic).
    implementation("com.google.mediapipe:tasks-audio:0.10.14")
}
