// SPDX-License-Identifier: GPL-3.0-or-later
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace    = "com.ujwal.installblock"
    compileSdk   = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ujwal.installblock"
        minSdk        = libs.versions.minSdk.get().toInt()
        targetSdk     = libs.versions.targetSdk.get().toInt()
        versionCode   = 1
        versionName   = "1.0.0"
    }

    val keystorePropertiesFile = rootProject.file("key.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    val hasKeyProperties = keystorePropertiesFile.exists() &&
            keystoreProperties.containsKey("storeFile") &&
            keystoreProperties.containsKey("storePassword") &&
            keystoreProperties.containsKey("keyAlias") &&
            keystoreProperties.containsKey("keyPassword")

    val envKeystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }
        ?: rootProject.file("release.jks").takeIf { it.exists() }
        ?: file("release.jks").takeIf { it.exists() }

    val hasEnvSigning = envKeystoreFile != null && envKeystoreFile.exists() &&
            !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank() &&
            !System.getenv("KEY_ALIAS").isNullOrBlank() &&
            !System.getenv("KEY_PASSWORD").isNullOrBlank()

    val hasReleaseSigning = hasKeyProperties || hasEnvSigning

    signingConfigs {
        create("release") {
            if (hasKeyProperties) {
                val storePath = keystoreProperties.getProperty("storeFile")
                val storeF = File(storePath)
                storeFile = if (storeF.isAbsolute) storeF else (rootProject.file(storePath).takeIf { it.exists() } ?: project.file(storePath))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else if (hasEnvSigning) {
                storeFile = envKeystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // AGP 9 handles Kotlin toolchains directly; Java 21 is required for the app code.
    kotlin {
        jvmToolchain(21)
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Assets: drop assets for configs not matching the running device.
    // Font assets must be committed manually. See README for the Inter download task.
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!*~"
    }

    packaging {
        resources {
            // apksig pulls in some JVM-only META-INF declarations we don't need on Android.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

dependencies {
    // Compose BOM: all compose-* versions come from here.
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.prev)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)

    // Kotlin
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    // Networking & images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.android)

    // Glass
    implementation(libs.haze)
    implementation(libs.haze.glass)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // APK signing: used at runtime to sign placeholder APKs on-device.
    // apksig depends only on standard JCE APIs, all of which are in Android's classpath.
    implementation(libs.apksig)

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.coroutines.test)
}
