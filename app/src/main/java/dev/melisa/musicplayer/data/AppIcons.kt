package dev.melisa.musicplayer.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.melisa.musicplayer.R

/**
 * The five launcher icons. Each one is a manifest activity-alias with its own adaptive
 * icon; switching means enabling the chosen alias and disabling the other four, which is
 * the only way Android lets an app change its icon without a separate APK.
 */
enum class AppIcon(
    val id: String,
    val aliasSuffix: String,
    @param:DrawableRes val foreground: Int,
    @param:ColorRes val background: Int,
    @param:StringRes val label: Int,
) {
    Equalizer(
        id = "equalizer",
        aliasSuffix = "LauncherEqualizer",
        foreground = R.drawable.ic_icon_eq,
        background = R.color.icon_bg_eq,
        label = R.string.app_icon_equalizer,
    ),
    Vinyl(
        id = "vinyl",
        aliasSuffix = "LauncherVinyl",
        foreground = R.drawable.ic_icon_vinyl,
        background = R.color.icon_bg_vinyl,
        label = R.string.app_icon_vinyl,
    ),
    Note(
        id = "note",
        aliasSuffix = "LauncherNote",
        foreground = R.drawable.ic_icon_note,
        background = R.color.icon_bg_note,
        label = R.string.app_icon_note,
    ),
    Wave(
        id = "wave",
        aliasSuffix = "LauncherWave",
        foreground = R.drawable.ic_icon_wave,
        background = R.color.icon_bg_wave,
        label = R.string.app_icon_wave,
    ),
    Headphones(
        id = "headphones",
        aliasSuffix = "LauncherPhones",
        foreground = R.drawable.ic_icon_phones,
        background = R.color.icon_bg_phones,
        label = R.string.app_icon_headphones,
    ),
    ;

    companion object {
        /** The equalizer icon ships enabled in the manifest. */
        val DEFAULT = Equalizer

        fun fromId(id: String?): AppIcon = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

object AppIconManager {

    /**
     * Makes [icon] the only enabled launcher alias.
     *
     * The selected alias is enabled before the others are disabled, so there is never a
     * moment where the app has no launcher entry. DONT_KILL_APP keeps the current screen
     * alive while the launcher refreshes its shortcut.
     */
    fun apply(context: Context, icon: AppIcon) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        fun alias(target: AppIcon) = ComponentName(packageName, "$packageName.${target.aliasSuffix}")

        runCatching {
            val selected = alias(icon)
            if (packageManager.getComponentEnabledSetting(selected) !=
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) {
                packageManager.setComponentEnabledSetting(
                    selected,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }

            AppIcon.entries.forEach { other ->
                if (other == icon) return@forEach
                val component = alias(other)
                if (packageManager.getComponentEnabledSetting(component) !=
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                ) {
                    packageManager.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }
}
