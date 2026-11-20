plugins {
    id("com.android.application")
    // Kotlin itself comes from AGP. Compose still needs its compiler plugin.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.melisa.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.melisa.musicplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 + resource shrinking are the difference between a 25 MB debug APK and
            // a phone-sized release build. jaudiotagger needs explicit keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    // Leading icons for the metadata form; only used vectors end up in a minified build.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")

    // Playback + Storage Access Framework tree walking.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    // Reads and writes ID3 / Vorbis / MP4 tags.
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Per-app language (AppCompatDelegate.setApplicationLocales) + AppCompat themes.
    implementation("androidx.appcompat:appcompat:1.7.1")
}
