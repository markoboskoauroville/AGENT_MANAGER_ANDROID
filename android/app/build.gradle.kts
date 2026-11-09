plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = (project.findProperty("appVersion") as String? ?: "1").trim().toInt()

android {
    namespace = "community.auroville.agentmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "community.auroville.agentmanager"
        minSdk = 34
        targetSdk = 35
        versionCode = appVersion
        versionName = appVersion.toString()
    }

    // the GitHub build signs with the saved key (secret ANDROID_DEBUG_KEYSTORE_B64 → $DEBUG_KEYSTORE), so every
    // version installs over the last one (MANTRA_MANIFEST workflows/README.md)
    System.getenv("DEBUG_KEYSTORE")?.let { ks ->
        signingConfigs.getByName("debug") {
            storeFile = file(ks); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "$appVersion-agent-manager-ceo-v$appVersion.apk"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
