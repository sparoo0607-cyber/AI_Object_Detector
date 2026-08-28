import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Release signing is read from (in order of precedence):
 *   1. keystore.properties in the project root  (storeFile / storePassword / keyAlias / keyPassword)
 *   2. environment variables SAHEY_STORE_FILE / SAHEY_STORE_PASSWORD / SAHEY_KEY_ALIAS / SAHEY_KEY_PASSWORD
 *   3. the bundled debug-only app/release.keystore (prototype fallback ONLY — do not ship)
 *
 * `assembleDebug` never needs any of this. `assembleRelease` falls back to the bundled
 * keystore so the prototype still produces an installable APK out of the box.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String, default: String): String =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey) ?: default)

android {
    namespace = "com.accessibility.detector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.accessibility.detector"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "3.0.0-prototype"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    signingConfigs {
        create("release") {
            storeFile = File(
                rootProject.projectDir,
                signingValue("storeFile", "SAHEY_STORE_FILE", "app/release.keystore")
            )
            storePassword = signingValue("storePassword", "SAHEY_STORE_PASSWORD", "accessibility123")
            keyAlias = signingValue("keyAlias", "SAHEY_KEY_ALIAS", "accessibility")
            keyPassword = signingValue("keyPassword", "SAHEY_KEY_PASSWORD", "accessibility123")
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
        // MediaPipe Tasks uses java.time APIs; desugaring keeps minSdk 24 working.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        noCompress("tflite")
        noCompress("task")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
        // MediaPipe Tasks and the TFLite Task libraries each bundle their own copy of the
        // TFLite native runtime. Without this, AGP fails with
        // "More than one file was found with OS independent path lib/<abi>/libtensorflowlite_jni.so".
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += setOf(
                "**/libtensorflowlite_jni.so",
                "**/libtensorflowlite_gpu_jni.so",
                "**/libtensorflowlite_jni_gms_client.so",
                "**/libmediapipe_tasks_vision_jni.so",
                "**/libmediapipe_tasks_audio_jni.so",
                "**/libimage_processing_util_jni.so",
                "**/libc++_shared.so"
            )
        }
    }

    lint {
        // Prototype: keep lint from failing the build; still runs and reports.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // HTTP client for Gemini Multimodal REST API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // TensorFlow Lite Task Vision & Support (SSD object detection + raw Interpreter for sign models)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // TensorFlow Lite Task Audio (YAMNet environmental sound classification)
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")

    // MediaPipe Tasks Vision (real hand-landmark extraction for sign language)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Google ML Kit On-Device Text Recognition (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Google ML Kit On-Device Translation & Language ID
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
}
