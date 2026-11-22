package dev.melisa.musicplayer.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class ThemeMode { System, Light, Dark }

enum class HoldingHand { Left, Right }

/** The languages the app ships strings for. Names are endonyms, so they are not
 *  translated — "Español" reads the same from every locale. */
data class Language(val tag: String, val label: String)

val Languages = listOf(
    Language("en", "English"),
    Language("es", "Español"),
    Language("fr", "Français"),
    Language("pt", "Português"),
)

/**
 * Small, dependency-free settings store.
 *
 * SharedPreferences (not DataStore) on purpose: reads are synchronous, so the first
 * frame already has the right theme — no flash of the wrong colours. Swap in DataStore
 * if you later need async or multi-process access.
 */
class AppSettings(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(readThemeMode())
        private set

    var hand by mutableStateOf(
        if (prefs.getBoolean(KEY_RIGHT_HAND, true)) HoldingHand.Right else HoldingHand.Left,
    )
        private set

    var scanOnLaunch by mutableStateOf(prefs.getBoolean(KEY_SCAN_ON_LAUNCH, true))
        private set

    var showAlbumArt by mutableStateOf(prefs.getBoolean(KEY_ALBUM_ART, true))
        private set

    /** Mirror ratings, favourites and app tags into the audio files themselves. */
    var syncToFiles by mutableStateOf(prefs.getBoolean(KEY_SYNC_FILES, true))
        private set

    /** True once the one-time Android 13+ notification prompt has been shown. */
    var notificationPromptShown by mutableStateOf(
        prefs.getBoolean(KEY_NOTIFICATION_PROMPT, false),
    )
        private set

    /** Tree URIs the user has granted, newest last. Stored as strings. */
    var libraries by mutableStateOf(readLibraries())
        private set

    /** Launcher icon chosen in Settings; the manifest enables the matching alias. */
    var appIcon by mutableStateOf(AppIcon.fromId(prefs.getString(KEY_APP_ICON, null)))
        private set

    /** Resolves the saved choice against the device setting for [ThemeMode.System]. */
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    /**
     * The saved mode, falling back to the legacy dark boolean for installs that
     * predate the three-way selector.
     */
    private fun readThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME, null)
        if (stored != null) {
            ThemeMode.entries.firstOrNull { it.name == stored }?.let { return it }
        }
        return if (prefs.getBoolean(KEY_DARK, true)) ThemeMode.Dark else ThemeMode.Light
    }

    fun updateHand(value: HoldingHand) {
        hand = value
        prefs.edit().putBoolean(KEY_RIGHT_HAND, value == HoldingHand.Right).apply()
    }

    fun updateScanOnLaunch(value: Boolean) {
        scanOnLaunch = value
        prefs.edit().putBoolean(KEY_SCAN_ON_LAUNCH, value).apply()
    }

    fun updateShowAlbumArt(value: Boolean) {
        showAlbumArt = value
        prefs.edit().putBoolean(KEY_ALBUM_ART, value).apply()
    }

    fun updateSyncToFiles(value: Boolean) {
        syncToFiles = value
        prefs.edit().putBoolean(KEY_SYNC_FILES, value).apply()
    }

    fun markNotificationPromptShown() {
        notificationPromptShown = true
        prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPT, true).apply()
    }

    fun updateAppIcon(icon: AppIcon) {
        if (icon == appIcon) return
        appIcon = icon
        // Commit synchronously: disabling the old alias can close the process, and the
        // next launch must read the new choice before reconciling the aliases.
        prefs.edit().putString(KEY_APP_ICON, icon.id).commit()
        AppIconManager.apply(appContext, icon)
    }

    /**
     * Reconciles the manifest alias with the stored choice. Needed after a fresh install
     * (manifest resets to the default alias) or if the component state was changed while
     * the app data survived.
     */
    fun applyAppIcon() {
        AppIconManager.apply(appContext, appIcon)
    }

    /**
     * Saves a folder the user picked through the Storage Access Framework.
     *
     * takePersistableUriPermission is the important call: without it the grant dies the
     * moment the activity does and the folder is unreadable after a restart.
     */
    fun addLibrary(uri: Uri) {
        val key = uri.toString()
        if (libraries.contains(key)) return

        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // A few providers only grant access for this session. Keep the entry anyway;
            // it will simply report as unreadable next launch.
        }

        libraries = libraries + key
        prefs.edit().putString(KEY_LIBRARIES, libraries.joinToString(SEPARATOR)).apply()
    }

    fun removeLibrary(uriString: String) {
        try {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Already revoked (or never persisted) — nothing to release.
        }

        libraries = libraries - uriString
        prefs.edit().putString(KEY_LIBRARIES, libraries.joinToString(SEPARATOR)).apply()
    }

    private fun readLibraries(): List<String> =
        prefs.getString(KEY_LIBRARIES, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DARK = "theme_dark"
        private const val KEY_RIGHT_HAND = "right_hand"
        private const val KEY_SCAN_ON_LAUNCH = "scan_on_launch"
        private const val KEY_ALBUM_ART = "album_art"
        private const val KEY_SYNC_FILES = "sync_files"
        private const val KEY_NOTIFICATION_PROMPT = "notification_prompt_shown"
        private const val KEY_APP_ICON = "app_icon"
        private const val KEY_LIBRARIES = "libraries"

        // URIs cannot contain newlines, so this is a safe separator.
        private const val SEPARATOR = "\n"

        /**
         * Language is owned by AppCompat, not by our prefs: on Android 13+ the system
         * persists it, and below that the autoStoreLocales service in the manifest does.
         */
        fun currentLanguageTag(): String {
            val override = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (override.isNotEmpty()) {
                return Languages.firstOrNull { override.startsWith(it.tag) }?.tag ?: override
            }
            // No explicit choice yet: follow the device if we speak it.
            val system = Locale.getDefault().language
            return Languages.firstOrNull { it.tag == system }?.tag ?: "en"
        }

        fun setLanguage(tag: String) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}

/**
 * Turns a tree URI into something readable, without touching the disk.
 *
 * "content://com.android.externalstorage.documents/tree/primary%3AMusic" -> "Music"
 */
fun libraryDisplayName(uriString: String): String {
    val uri = Uri.parse(uriString)
    return try {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val path = documentId.substringAfter(':', documentId)
        path.substringAfterLast('/').ifBlank { documentId }
    } catch (_: Exception) {
        uri.lastPathSegment ?: uriString
    }
}

@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    return remember { AppSettings(context) }
}
