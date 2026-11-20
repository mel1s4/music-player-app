package dev.melisa.musicplayer.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.AudioFileDetails
import dev.melisa.musicplayer.data.AudioFileInfo
import dev.melisa.musicplayer.data.LibraryExtras
import dev.melisa.musicplayer.data.SongFileInfo
import dev.melisa.musicplayer.data.songFileInfo
import dev.melisa.musicplayer.data.SongMetadata
import dev.melisa.musicplayer.data.TagEditor
import dev.melisa.musicplayer.data.TagWriteResult
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.NewPlaylistDialog
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.formatTime
import dev.melisa.musicplayer.data.Track
import kotlinx.coroutines.launch

/**
 * Edits the real tag frames inside the file, not an app-side override — so the change
 * is visible in every other player on the device.
 */
@Composable
fun EditMetadataScreen(
    track: Track,
    extras: LibraryExtras,
    knownArtists: List<String> = emptyList(),
    knownAlbums: List<String> = emptyList(),
    onClose: () -> Unit,
    onSaved: (Uri) -> Unit,
    onCompress: (Track) -> Unit = {},
    onHearDifference: (Track, AudioFileDetails?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var metadata by remember(track.uri) { mutableStateOf<SongMetadata?>(null) }
    var fileInfo by remember(track.uri) { mutableStateOf<SongFileInfo?>(null) }
    var fileName by remember(track.uri) { mutableStateOf("") }
    var saving by remember(track.uri) { mutableStateOf(false) }
    var errorRes by remember(track.uri) { mutableStateOf<Int?>(null) }
    var audioDetails by remember(track.uri) { mutableStateOf<AudioFileDetails?>(null) }

    LaunchedEffect(track.uri) {
        val info = songFileInfo(track.uri)
        fileInfo = info
        fileName = info.name
        metadata = TagEditor.read(context, track.uri)
        audioDetails = AudioFileInfo.read(context, track.uri, track.sizeBytes)
    }

    fun saveMetadata() {
        val current = metadata ?: return
        if (saving) return
        scope.launch {
            saving = true
            errorRes = null

            var target = track.uri
            val originalName = fileInfo?.name.orEmpty()
            if (fileName.isNotBlank() && fileName != originalName) {
                val renamed = TagEditor.rename(context, track.uri, fileName)
                if (renamed == null) {
                    errorRes = R.string.metadata_rename_failed
                    saving = false
                    return@launch
                }
                target = renamed
            }

            when (val result = TagEditor.write(context, target, current)) {
                is TagWriteResult.Success -> onSaved(target)
                is TagWriteResult.NoWritePermission ->
                    errorRes = R.string.metadata_no_write_permission
                is TagWriteResult.Failed ->
                    errorRes = R.string.metadata_write_failed
            }
            saving = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // The app draws edge-to-edge, so an open keyboard would otherwise sit
                // on top of the form and hide the suggestion list it just opened.
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.edit_metadata_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            errorRes?.let { res ->
                Text(
                    text = stringResource(res),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val current = metadata
            if (current == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.metadata_reading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Size and compression first: that is the context for every edit
                    // below it (a 3 MB MP3 and a 30 MB FLAC deserve different care).
                    FileSummary(
                        details = audioDetails,
                        fallbackSizeBytes = track.sizeBytes,
                        durationMs = track.durationMs,
                        onCompress = { onCompress(track) },
                        onHearDifference = { onHearDifference(track, audioDetails) },
                    )

                    // Artist and title come first: they identify the song, while the
                    // file name below is only the storage detail that implements it.
                    SectionKicker(
                        text = stringResource(R.string.metadata_section_details),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Field(
                        labelRes = R.string.field_artist,
                        icon = Icons.Filled.Person,
                        value = current.artist,
                        suggestions = knownArtists,
                        onValueChange = { metadata = current.copy(artist = it) },
                    )
                    Field(
                        labelRes = R.string.field_title,
                        icon = Icons.Filled.Title,
                        value = current.title,
                        onValueChange = { metadata = current.copy(title = it) },
                    )
                    Field(
                        labelRes = R.string.field_album,
                        icon = Icons.Filled.LibraryMusic,
                        value = current.album,
                        suggestions = knownAlbums,
                        onValueChange = { metadata = current.copy(album = it) },
                    )
                    Field(
                        labelRes = R.string.field_album_artist,
                        icon = Icons.Filled.Group,
                        value = current.albumArtist,
                        onValueChange = { metadata = current.copy(albumArtist = it) },
                    )

                    // Short numbers share one row: they wasted a full-width field each.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(
                            labelRes = R.string.field_year,
                            value = current.year,
                            numeric = true,
                            modifier = Modifier.weight(1.2f),
                            onValueChange = { metadata = current.copy(year = it) },
                        )
                        Field(
                            labelRes = R.string.field_track,
                            value = current.track,
                            numeric = true,
                            modifier = Modifier.weight(1f),
                            onValueChange = { metadata = current.copy(track = it) },
                        )
                        Field(
                            labelRes = R.string.field_disc,
                            value = current.disc,
                            numeric = true,
                            modifier = Modifier.weight(1f),
                            onValueChange = { metadata = current.copy(disc = it) },
                        )
                    }

                    Field(
                        labelRes = R.string.field_genre,
                        icon = Icons.Filled.LocalOffer,
                        value = current.genre,
                        onValueChange = { metadata = current.copy(genre = it) },
                    )
                    Field(
                        labelRes = R.string.field_comment,
                        value = current.comment,
                        singleLine = false,
                        minLines = 3,
                        onValueChange = { metadata = current.copy(comment = it) },
                    )

                    SectionKicker(
                        text = stringResource(R.string.metadata_section_file),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Field(
                        labelRes = R.string.field_file_name,
                        icon = Icons.Filled.Description,
                        value = fileName,
                        onValueChange = { fileName = it },
                    )
                    Field(
                        labelRes = R.string.field_folder,
                        icon = Icons.Filled.Folder,
                        value = fileInfo?.folder.orEmpty(),
                        readOnly = true,
                        onValueChange = {},
                    )

                    // These are app-level and apply instantly — unlike the fields above,
                    // which only reach the file when the user taps Save.
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    TagsSection(uri = track.uri.toString(), extras = extras)

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    PlaylistsSection(uri = track.uri.toString(), extras = extras)

                    Spacer(Modifier.height(24.dp))
                }
            }

            // Sticky above the keyboard: the primary action never scrolls out of reach.
            SaveBar(
                saving = saving,
                enabled = metadata != null && !saving,
                onSave = ::saveMetadata,
            )
        }
    }
}

@Composable
private fun SaveBar(
    saving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (saving) R.string.metadata_saving else R.string.action_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Field(
    labelRes: Int,
    value: String,
    icon: ImageVector? = null,
    numeric: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    suggestions: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    val leading: (@Composable () -> Unit)? = icon?.let { vector ->
        { Icon(vector, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    // Existing values are an autocomplete source, not a closed list: whatever is typed
    // stays, and matches are found case-insensitively anywhere in the name. Exact and
    // prefix matches come first, so "cold" puts "Coldplay" at the top.
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
            // The panel scrolls; this just stops an entire artist catalogue from being
            // composed at once when the field is empty.
            .take(60)
    }
    val hasSuggestions = suggestions.isNotEmpty()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "suggestions-arrow",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                onValueChange(typed)
                // Autocomplete opens as the user types, but only while something
                // actually matches — a wall of "no matches" under every keystroke
                // would be worse than no list at all.
                if (hasSuggestions) {
                    expanded = typed.isBlank() ||
                        suggestions.any { it.contains(typed.trim(), ignoreCase = true) }
                }
            },
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            // Decorative: the label already names the field, so a description would duplicate it.
            leadingIcon = leading,
            trailingIcon = if (hasSuggestions) {
                {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.metadata_choose_existing),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(arrowRotation),
                        )
                    }
                }
            } else {
                null
            },
            label = { Text(stringResource(labelRes)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    // Leaving the field must not leave a wall of names covering the form.
                    if (!focus.isFocused) expanded = false
                },
        )

        // Inline instead of a DropdownMenu: this lives inside the page's own scroll, so
        // the list can always be scrolled with the same gesture that scrolls the form.
        // The height cap fits a little over five rows; anything beyond scrolls.
        if (expanded && hasSuggestions) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .heightIn(max = 280.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onValueChange(option)
                                        expanded = false
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (option.equals(value.trim(), ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Size and compression at a glance. Users editing tags rarely know whether the file is
 * a 3 MB MP3 or a 30 MB FLAC, and that context matters before renaming or re-tagging.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileSummary(
    details: AudioFileDetails?,
    fallbackSizeBytes: Long,
    durationMs: Long,
    onCompress: () -> Unit,
    onHearDifference: () -> Unit,
) {
    val sizeBytes = details?.sizeBytes?.takeIf { it > 0L } ?: fallbackSizeBytes
    val sizeText = if (sizeBytes > 0L) "%.1f MB".format(sizeBytes / 1_048_576.0) else "—"
    val codec = details?.codec ?: stringResource(R.string.metadata_codec_unknown)
    val bitrate = details?.bitrateKbps?.takeIf { it > 0 }
    val compression = buildString {
        append(codec)
        if (bitrate != null) {
            append(" · ")
            append(stringResource(R.string.metadata_kbps, bitrate))
        }
    }

    NeonPanel(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        corner = 20.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionKicker(
                    text = stringResource(R.string.metadata_audio_file),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.weight(1f))
                details?.let {
                    CyberBadge(
                        text = stringResource(
                            if (it.lossless) R.string.metadata_lossless else R.string.metadata_lossy,
                        ),
                        color = if (it.lossless) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = compression,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Wrap instead of squeezing: at large font scales the two labels no
            // longer fit side by side, and losing either action would be worse than
            // the extra row.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = onHearDifference) {
                    Icon(
                        imageVector = Icons.Filled.Hearing,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.hear_difference))
                }
                TextButton(onClick = onCompress) {
                    Text(stringResource(R.string.action_compress))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.metadata_instant),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagsSection(uri: String, extras: LibraryExtras) {
    var draft by remember { mutableStateOf("") }
    val tags = extras.tagsFor(uri)

    SectionTitle(stringResource(R.string.tags_title))

    if (tags.isEmpty()) {
        Text(
            text = stringResource(R.string.tags_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        tags.forEach { tag ->
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
                IconButton(onClick = { extras.removeTag(uri, tag) }) {
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
                extras.addTag(uri, draft)
                draft = ""
            },
            enabled = draft.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_add))
        }
    }
}

@Composable
private fun PlaylistsSection(uri: String, extras: LibraryExtras) {
    var showNewPlaylist by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.playlists))

    if (extras.playlists.isEmpty()) {
        Text(
            text = stringResource(R.string.playlists_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        extras.playlists.forEach { playlist ->
            val member = playlist.trackUris.contains(uri)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Dynamic playlists are rule-based: membership is not editable here.
                    .clickable(enabled = !playlist.isDynamic) {
                        if (member) {
                            extras.removeFromPlaylist(playlist.id, uri)
                        } else {
                            extras.addToPlaylist(playlist.id, uri)
                        }
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (playlist.isDynamic) {
                        Text(
                            text = "${stringResource(R.string.playlist_dynamic)} #${playlist.tagRule}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!playlist.isDynamic) {
                    Icon(
                        imageVector = if (member) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (member) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    TextButton(onClick = { showNewPlaylist = true }) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.playlist_new))
    }

    if (showNewPlaylist) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylist = false },
            onCreate = { name ->
                val playlist = extras.createPlaylist(name)
                extras.addToPlaylist(playlist.id, uri)
                showNewPlaylist = false
            },
        )
    }
}
