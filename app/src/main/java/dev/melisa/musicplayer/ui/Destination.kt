package dev.melisa.musicplayer.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.melisa.musicplayer.R

/**
 * The four top-level screens. One enum drives both the bottom bar and the drawer,
 * so a new destination shows up in both places automatically.
 *
 * Labels are resource ids, not strings, so they follow the selected language.
 */
enum class Destination(@param:StringRes val labelRes: Int, val icon: ImageVector) {
    Dashboard(R.string.nav_dashboard, Icons.Filled.Home),
    Songs(R.string.nav_songs, Icons.Filled.PlayArrow),
    Search(R.string.nav_search, Icons.Filled.Search),
    Playlists(R.string.nav_playlists, Icons.AutoMirrored.Filled.List),
    Files(R.string.nav_files, Icons.Filled.Folder),
    Albums(R.string.nav_albums, Icons.Filled.Album),
    Artists(R.string.nav_artists, Icons.Filled.Person),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}
