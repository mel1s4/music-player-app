package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.ui.theme.neonGlow

/**
 * The library summary: size, track and album counts, and the two upkeep actions.
 *
 * It used to top the Dashboard; it now tops Songs, where the user is already looking
 * at the tracks it describes.
 */
@Composable
fun LibraryHero(
    songs: Int,
    albums: Int,
    sizeBytes: Long,
    hours: Double,
    onRescan: () -> Unit,
    onFreeSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeonPanel(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        corner = 26.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionKicker(text = stringResource(R.string.dashboard_library))
                Spacer(Modifier.weight(1f))
                LiveDot(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "LOCAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = songs.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = stringResource(R.string.stat_songs).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatLibrarySize(sizeBytes, albums) +
                            " · " + "%.1f".format(hours) + " " +
                            stringResource(R.string.stat_hours).lowercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Two CTAs side by side at normal type sizes; stacked once the user
            // enlarges text, because "Free up space" must never be cut off.
            val stackedActions = LocalDensity.current.fontScale > 1.15f
            if (stackedActions) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RescanButton(onRescan, Modifier.fillMaxWidth())
                    FreeUpSpaceButton(onFreeSpace, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RescanButton(onRescan, Modifier.weight(1f))
                    FreeUpSpaceButton(onFreeSpace, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Storage used by the library, rounded the way people read it: one decimal for GB,
 * no decimals once the number is large enough that they stop mattering. Falls back to
 * the album count when the storage provider does not report file sizes.
 */
@Composable
private fun formatLibrarySize(bytes: Long, albums: Int): String {
    if (bytes <= 0L) return "$albums " + stringResource(R.string.stat_albums).lowercase()
    val mb = bytes / 1_048_576.0
    return when {
        mb >= 1024.0 -> "%.1f GB".format(mb / 1024.0)
        mb >= 100.0 -> "%.0f MB".format(mb)
        else -> "%.1f MB".format(mb)
    }
}

@Composable
private fun RescanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .neonGlow(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                radius = 10.dp,
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.dashboard_rescan),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun FreeUpSpaceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_free_space),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
