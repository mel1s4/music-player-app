import java.util.Properties

plugins {
    id("com.android.application")
    // Kotlin itself comes from AGP. Compose still needs its compiler plugin.
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is optional and local-only. `keystore.properties` is git-ignored;
// the keystore and its password live outside the repository. Without the file the
// release build remains unsigned, which is what CI uses.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun localSecret(valueKey: String, fileKey: String): String? =
    keystoreProperties.getProperty(valueKey)
        ?: keystoreProperties.getProperty(fileKey)
            ?.let { rootProject.file(it) }
            ?.takeIf { it.isFile }
            ?.readText()
            ?.trim()

val releaseStoreFile = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }
val releaseStorePassword = localSecret("storePassword", "storePasswordFile")
val releaseKeyAlias = keystoreProperties.getProperty("keyAlias")?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeyPassword = localSecret("keyPassword", "keyPasswordFile")

android {
    namespace = "dev.melisa.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.melisa.musicplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.7.2"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val releaseSigning = if (
        releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        signingConfigs.create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
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
            releaseSigning?.let { signingConfig = it }
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
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Per-app language (AppCompatDelegate.setApplicationLocales) + AppCompat themes.
    implementation("androidx.appcompat:appcompat:1.8.0")
}
