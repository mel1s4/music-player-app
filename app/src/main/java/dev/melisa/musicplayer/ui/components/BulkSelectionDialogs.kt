package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R

/** One text value applied to a track (title, artist or album). */
@Composable
fun BulkTextFieldDialog(
    title: String,
    label: String,
    supporting: String,
    initial: String,
    suggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var expanded by remember { mutableStateOf(false) }

    // Same ranking as the editor's autocomplete: exact, then prefix, then the alphabet.
    // Existing values are a source, not a closed list, so free text stays valid.
    val options = remember(value, suggestions) {
        val needle = value.trim()
        val matches = if (needle.isEmpty()) {
            suggestions
        } else {
            suggestions.filter { it.contains(needle, ignoreCase = true) }
        }
        matches
            .sortedWith(
                compareBy<String> { !it.equals(needle, ignoreCase = true) }
                    .thenBy { !it.startsWith(needle, ignoreCase = true) }
                    .thenBy { it.lowercase() },
            )
            .take(60)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box {
                OutlinedTextField(
                    value = value,
                    onValueChange = { typed ->
                        value = typed
                        if (suggestions.isNotEmpty()) {
                            expanded = typed.isBlank() ||
                                suggestions.any { it.contains(typed.trim(), ignoreCase = true) }
                        }
                    },
                    singleLine = true,
                    label = { Text(label) },
                    supportingText = { Text(supporting) },
                    trailingIcon = if (suggestions.isNotEmpty()) {
                        {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = stringResource(
                                        R.string.metadata_choose_existing,
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    if (options.isEmpty()) {
                        Text(
                            text = stringResource(R.string.metadata_no_matches),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = {
                                    value = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Tags are multi-valued, so the bulk editor works with the tags shared by every
 * selected track: removing one removes it from all, and a new tag is added to all.
 * Changes apply immediately, matching the single-track tag editor.
 */
@Composable
fun BulkTagsDialog(
    title: String,
    tags: List<String>,
    note: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bulk_tags_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(tags, key = { it.lowercase() }) { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                IconButton(onClick = { onRemove(tag) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_close),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

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
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}
