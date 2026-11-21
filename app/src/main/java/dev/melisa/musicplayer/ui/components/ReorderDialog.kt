package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R

/** Progress and result of a folder reorganisation, shown while it runs and afterwards. */
data class ReorderUiState(
    val folderName: String = "",
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentTitle: String = "",
    val moved: Int = 0,
    val alreadySorted: Int = 0,
    val missingArtist: Int = 0,
    val failed: Int = 0,
    val error: Boolean = false,
)

@Composable
fun ReorderDialog(
    state: ReorderUiState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (state.running) R.string.reorder_running_title else R.string.reorder_done_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.folderName.isNotBlank()) {
                    Text(
                        text = state.folderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.running) {
                    Text(stringResource(R.string.reorder_running_body))
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (state.completed.toFloat() / state.total).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        if (state.currentTitle.isNotBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.reorder_progress,
                                    (state.completed + 1).coerceAtMost(state.total),
                                    state.total,
                                    state.currentTitle,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else if (state.error) {
                    Text(stringResource(R.string.reorder_error))
                } else if (
                    state.moved == 0 && state.alreadySorted == 0 &&
                    state.missingArtist == 0 && state.failed == 0
                ) {
                    Text(stringResource(R.string.reorder_empty))
                } else {
                    Text(
                        pluralStringResource(
                            R.plurals.reorder_moved,
                            state.moved,
                            state.moved,
                        ),
                    )
                    if (state.alreadySorted > 0) {
                        Text(stringResource(R.string.reorder_already, state.alreadySorted))
                    }
                    if (state.missingArtist > 0) {
                        Text(stringResource(R.string.reorder_missing_artist, state.missingArtist))
                    }
                    if (state.failed > 0) {
                        Text(stringResource(R.string.reorder_failed, state.failed))
                    }
                }
            }
        },
        confirmButton = {
            if (!state.running) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_done))
                }
            }
        },
    )
}
