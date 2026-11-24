package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Playlist

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.playlists_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(playlist) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = playlist.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = playlist.trackUris.size.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateNew() }
                        .padding(vertical = 14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.playlist_new),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Quick tag editor. Tags are multi-valued, so they are shown as pills: a checked pill
 * is on the song (tap to remove it) and an unchecked "Frequently used" pill adds it.
 * A new tag can still be typed. Changes apply immediately, as in the full editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageTagsDialog(
    tags: List<String>,
    suggestions: List<String> = emptyList(),
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (tags.isEmpty() && suggestions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.forEach { tag ->
                                TagPill(
                                    tag = tag,
                                    selected = true,
                                    onClick = { onRemove(tag) },
                                )
                            }
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.tags_frequent),
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            suggestions.forEach { tag ->
                                TagPill(
                                    tag = tag,
                                    selected = false,
                                    onClick = { onAdd(tag) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.tags_hint)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onAdd(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank(),
                    ) { Text(stringResource(R.string.action_add)) }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.tags_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

/** One selectable tag pill: checked means the tag is on the song. */
@Composable
private fun TagPill(tag: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp),
        label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** The entries under the kebab menu's Edit submenu. */
enum class SongEditAction { Page, Title, Artist, Album, Tags }

@Composable
fun SongActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEdit: (SongEditAction) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onCompress: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onStartBulkSelection: (() -> Unit)? = null,
) {
    // The menu has two pages: the per-song actions and the Edit submenu. Keying the
    // state on [expanded] means every reopen starts on the first page.
    var editPage by remember(expanded) { mutableStateOf(false) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (editPage) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                },
                onClick = { editPage = false },
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            EditMenuItem(R.string.action_edit_page, Icons.Filled.Edit) {
                onDismiss()
                onEdit(SongEditAction.Page)
            }
            EditMenuItem(R.string.action_change_title, Icons.Filled.Title) {
                onDismiss()
                onEdit(SongEditAction.Title)
            }
            EditMenuItem(R.string.bulk_set_artist, Icons.Filled.Person) {
                onDismiss()
                onEdit(SongEditAction.Artist)
            }
            EditMenuItem(R.string.bulk_set_album, Icons.Filled.Album) {
                onDismiss()
                onEdit(SongEditAction.Album)
            }
            EditMenuItem(R.string.action_edit_tags, Icons.Filled.Label) {
                onDismiss()
                onEdit(SongEditAction.Tags)
            }
        } else {
            // Selection mode is a mode, not an edit of this song, so it opens the menu
            // above the per-song actions and carries its own divider.
            if (onStartBulkSelection != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.bulk_start)) },
                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onStartBulkSelection()
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_like)) },
                leadingIcon = { Icon(Icons.Filled.ThumbUp, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onLike()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_dislike)) },
                leadingIcon = { Icon(Icons.Filled.ThumbDown, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onDislike()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_to_playlist)) },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                },
            )
            // The arrow marks Edit as a second page instead of a terminal action; the
            // submenu holds the four ways of editing that song.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                onClick = { editPage = true },
            )
            if (onCompress != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_compress)) },
                    leadingIcon = { Icon(Icons.Filled.Compress, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onCompress()
                    },
                )
            }
            // Destructive actions sit at the bottom, separated and in the error colour,
            // so they can never be confused with "edit" or "add to playlist".
            if (onDelete != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete_file),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onDismiss()
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun EditMenuItem(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
