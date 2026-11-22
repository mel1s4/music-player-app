package dev.melisa.musicplayer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.appcompat.app.AppCompatActivity
import dev.melisa.musicplayer.data.rememberAppSettings
import dev.melisa.musicplayer.ui.MusicPlayerApp
import dev.melisa.musicplayer.ui.components.LocalMusicLibraries
import dev.melisa.musicplayer.ui.components.LocalShowAlbumArt
import dev.melisa.musicplayer.ui.theme.MusicPlayerTheme
import dev.melisa.musicplayer.ui.theme.SystemBars

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15+ draws edge-to-edge whether you ask or not; Scaffold applies the insets.
        enableEdgeToEdge()
        setContent {
            val settings = rememberAppSettings()
            // Re-apply the chosen launcher alias once the app is alive; the manifest
            // default is not necessarily what the user picked last time.
            LaunchedEffect(settings.appIcon) { settings.applyAppIcon() }
            val darkTheme = settings.isDark(isSystemInDarkTheme())
            MusicPlayerTheme(darkTheme = darkTheme) {
                SystemBars(darkTheme = darkTheme)
                // Cover lookups need the folder grants from anywhere in the UI.
                CompositionLocalProvider(
                    LocalMusicLibraries provides settings.libraries,
                    LocalShowAlbumArt provides settings.showAlbumArt,
                ) {
                    MusicPlayerApp(settings)
                }
            }
        }
    }
}
