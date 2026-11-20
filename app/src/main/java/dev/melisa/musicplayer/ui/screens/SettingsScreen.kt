package dev.melisa.musicplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.melisa.musicplayer.BuildConfig
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.AppIcon
import dev.melisa.musicplayer.data.AppSettings
import dev.melisa.musicplayer.data.HoldingHand
import dev.melisa.musicplayer.data.Languages
import dev.melisa.musicplayer.data.libraryDisplayName
import dev.melisa.musicplayer.data.ThemeMode
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.theme.cyberClick

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onFreeSpace: () -> Unit,
) {
    // Controls sit on the hand's side; the labels take the remaining space.
    val onEnd = settings.hand == HoldingHand.Right

    // Opens the system folder picker. The Activity result is the tree URI we persist.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) settings.addLibrary(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_appearance))

        ChoiceSection(
            title = stringResource(R.string.settings_theme),
            subtitle = null,
            controlsOnEnd = onEnd,
        ) {
            Segmented(
                options = listOf(
                    ThemeMode.Light to stringResource(R.string.theme_light),
                    ThemeMode.Dark to stringResource(R.string.theme_dark),
                ),
                selected = settings.themeMode,
                onSelect = { settings.updateThemeMode(it) },
            )
        }

        LanguageSection(controlsOnEnd = onEnd)

        ChoiceSection(
            title = stringResource(R.string.settings_holding_hand),
            subtitle = stringResource(R.string.settings_holding_hand_hint),
            controlsOnEnd = onEnd,
        ) {
            Segmented(
                options = listOf(
                    HoldingHand.Left to stringResource(R.string.hand_left),
                    HoldingHand.Right to stringResource(R.string.hand_right),
                ),
                selected = settings.hand,
                onSelect = { settings.updateHand(it) },
            )
        }

        SectionHeader(stringResource(R.string.settings_app_icon))
        AppIconPicker(settings)

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        SectionHeader(stringResource(R.string.settings_libraries))

        Text(
            text = stringResource(R.string.libraries_hint),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        if (settings.libraries.isEmpty()) {
            Text(
                text = stringResource(R.string.libraries_empty),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            settings.libraries.forEach { libraryUri ->
                LibraryRow(
                    name = libraryDisplayName(libraryUri),
                    onRemove = { settings.removeLibrary(libraryUri) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = if (onEnd) Arrangement.End else Arrangement.Start,
        ) {
            OutlinedButton(onClick = { folderPicker.launch(null) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.libraries_add))
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        SectionHeader(stringResource(R.string.settings_playback))

        ToggleRow(
            title = stringResource(R.string.settings_scan_on_launch),
            subtitle = stringResource(R.string.settings_scan_on_launch_desc),
            checked = settings.scanOnLaunch,
            onCheckedChange = { settings.updateScanOnLaunch(it) },
            controlsOnEnd = onEnd,
        )
        ToggleRow(
            title = stringResource(R.string.settings_album_art),
            subtitle = stringResource(R.string.settings_album_art_desc),
            checked = settings.showAlbumArt,
            onCheckedChange = { settings.updateShowAlbumArt(it) },
            controlsOnEnd = onEnd,
        )
        ToggleRow(
            title = stringResource(R.string.settings_sync_files),
            subtitle = stringResource(R.string.settings_sync_files_desc),
            checked = settings.syncToFiles,
            onCheckedChange = { settings.updateSyncToFiles(it) },
            controlsOnEnd = onEnd,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        SectionHeader(stringResource(R.string.settings_storage))
        ActionRow(
            title = stringResource(R.string.settings_free_space),
            subtitle = stringResource(R.string.settings_free_space_desc),
            onClick = onFreeSpace,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        SectionHeader(stringResource(R.string.settings_about))
        InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
        InfoRow(stringResource(R.string.about_package), BuildConfig.APPLICATION_ID)
        InfoRow(stringResource(R.string.about_min_android), stringResource(R.string.min_android_value))
    }
}

@Composable
private fun AppIconPicker(settings: AppSettings) {
    var pendingIcon by remember { mutableStateOf<AppIcon?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_app_icon_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        // Three columns instead of five: translated labels such as "Auriculares" need
        // the width, and a mid-word break is worse than a second row.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppIcon.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { icon ->
                        AppIconTile(
                            icon = icon,
                            selected = settings.appIcon == icon,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (icon != settings.appIcon) pendingIcon = icon
                            },
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

    }

    val toChange = pendingIcon
    if (toChange != null) {
        AlertDialog(
            onDismissRequest = { pendingIcon = null },
            title = { Text(stringResource(R.string.app_icon_change_title)) },
            text = { Text(stringResource(R.string.app_icon_change_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings.updateAppIcon(toChange)
                        pendingIcon = null
                    },
                ) {
                    Text(stringResource(R.string.app_icon_change_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingIcon = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AppIconTile(
    icon: AppIcon,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(colorResource(icon.background))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon.foreground),
                // The visible label names the choice; the image itself is decorative.
                contentDescription = null,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(icon.label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    SectionKicker(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
    )
}

/**
 * Label block with the control *below* it, aligned to the thumb side.
 *
 * Stacking instead of putting the control on the same row is deliberate: translations
 * change text length ("Left" -> "Izquierda"), and a same-row layout overflows off-screen
 * once the label and a two-option control compete for 360dp.
 */
@Composable
private fun ChoiceSection(
    title: String,
    subtitle: String?,
    controlsOnEnd: Boolean,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (controlsOnEnd) Arrangement.End else Arrangement.Start,
        ) {
            control()
        }
    }
}

@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(MaterialTheme.colorScheme.background)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LanguageSection(controlsOnEnd: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val currentTag = AppSettings.currentLanguageTag()
    val current = Languages.firstOrNull { it.tag == currentTag } ?: Languages.first()

    ChoiceSection(
        title = stringResource(R.string.settings_language),
        subtitle = current.label,
        controlsOnEnd = controlsOnEnd,
    ) {
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(current.label)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label) },
                        onClick = {
                            expanded = false
                            // Recreates the activity with the new resources applied.
                            AppSettings.setLanguage(language.tag)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(name: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.libraries_remove),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    controlsOnEnd: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label: @Composable ColumnScope.() -> Unit = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A Switch is narrow, so this row is safe side-by-side in every language.
        if (controlsOnEnd) {
            Column(modifier = Modifier.weight(1f), content = label)
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), content = label)
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cyberClick(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Compress,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
