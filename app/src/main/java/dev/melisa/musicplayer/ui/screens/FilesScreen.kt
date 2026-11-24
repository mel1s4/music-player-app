package dev.melisa.musicplayer.ui.screens

import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.AlphabetRail
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.ScanningState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongEditAction
import dev.melisa.musicplayer.ui.components.SongGoToAction
import dev.melisa.musicplayer.ui.components.SongRow
import dev.melisa.musicplayer.ui.components.TrackSort
import dev.melisa.musicplayer.ui.components.TrackSortButton
import dev.melisa.musicplayer.ui.components.labelRes
import dev.melisa.musicplayer.ui.components.sectionLabelFor
import dev.melisa.musicplayer.ui.components.sortKey
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlinx.coroutines.launch

/**
 * Folder navigation over the scanned tracks.
 *
 * The tree is built from the paths already stored on each Track, so there is no extra
 * I/O: browsing folders is an in-memory operation over the same scan the Songs list uses.
 */
@Composable
fun FilesScreen(
    tracks: List<Track>?,
    currentTrackUri: String?,
    hasLibraries: Boolean,
    starredFolders: Set<String>,
    addedAt: Map<String, Long>,
    onToggleFolderStar: (String) -> Unit,
    onReorderFolder: (String, List<Track>) -> Unit,
    onMoveFolder: (source: String, destination: String, tracks: List<Track>, destinationTracks: List<Track>) -> Unit,
    onExplodeFolder: (path: String, tracks: List<Track>) -> Unit,
    onDeleteFolder: (path: String, tracks: List<Track>) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditSong: (Track, SongEditAction) -> Unit,
    onGoToSong: (Track, SongGoToAction) -> Unit,
    onCompress: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    path: String,
    onPathChange: (String) -> Unit,
) {

    when {
        !hasLibraries -> MessageState(
            title = stringResource(R.string.songs_no_library_title),
            body = stringResource(R.string.songs_no_library_body),
        )

        tracks == null -> ScanningState()

        tracks.isEmpty() -> MessageState(
            title = stringResource(R.string.songs_empty_title),
            body = stringResource(R.string.songs_empty_body),
        )

        else -> {
            val root = remember(tracks) { buildTree(tracks) }
            val resolvedNode = remember(root, path) { root.find(path) }
            // A reorder can move the last song out of the folder the user is standing
            // in. Walk back to the nearest ancestor that still exists instead of
            // showing a breadcrumb for a folder that is gone from the tree.
            LaunchedEffect(root, path) {
                if (resolvedNode == null && path.isNotEmpty()) {
                    onPathChange(path.substringBeforeLast('/', ""))
                }
            }
            val node = resolvedNode ?: root
            var sort by rememberSaveable { mutableStateOf(TrackSort.Title) }
            val folderComparator = remember(sort, addedAt) {
                when (sort) {
                    TrackSort.DateAdded -> compareByDescending<FolderNode> { folder ->
                        folder.allTracks().maxOfOrNull {
                            addedAt[it.uri.toString()] ?: 0L
                        } ?: 0L
                    }.thenBy { it.name.lowercase() }

                    TrackSort.Title, TrackSort.Artist -> compareBy { it.name.lowercase() }

                    TrackSort.Duration -> compareBy<FolderNode> { folder ->
                        folder.allTracks().sumOf { it.durationMs }
                    }.thenBy { it.name.lowercase() }
                }
            }
            val folders = remember(node, sort, addedAt) {
                node.children.values.sortedWith(folderComparator)
            }
            val folderTracks = remember(node.tracks, sort, addedAt) {
                sortTracks(node.tracks, sort, addedAt)
            }
            val playQueue = remember(node, sort, addedAt) {
                sortTracks(node.allTracks(), sort, addedAt)
            }
            val atRoot = path.isEmpty()

            // Stars are paths, so they are resolved against the current tree at draw
            // time. A folder that moved or disappeared simply drops out of the section.
            val starredNodes = remember(root, starredFolders, sort, addedAt) {
                starredFolders
                    .mapNotNull { starredPath -> root.find(starredPath)?.let { starredPath to it } }
                    .sortedWith(compareBy(folderComparator) { (_, node) -> node })
            }
            // At the root a starred folder moves into the pinned section; deeper down
            // the normal listing stays untouched so nothing appears twice.
            val listedFolders = if (atRoot) {
                folders.filterNot { it.path in starredFolders }
            } else {
                folders
            }

            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val alphabetical = sort == TrackSort.Title || sort == TrackSort.Artist
            val showStarred = atRoot && starredNodes.isNotEmpty()
            val showFoldersHeader = showStarred && listedFolders.isNotEmpty()
            val starredRowStart = if (showStarred) 1 else 0
            val folderRowStart = starredRowStart +
                (if (showStarred) starredNodes.size else 0) +
                (if (showFoldersHeader) 1 else 0)
            val trackRowStart = folderRowStart + listedFolders.size

            val letterStarts = remember(
                alphabetical,
                sort,
                starredNodes,
                listedFolders,
                folderTracks,
                starredRowStart,
                folderRowStart,
                trackRowStart,
            ) {
                if (!alphabetical) {
                    emptyMap()
                } else {
                    val starts = mutableMapOf<String, Int>()
                    fun add(label: String, index: Int) {
                        starts[label] = minOf(starts[label] ?: index, index)
                    }
                    if (showStarred) {
                        starredNodes.forEachIndexed { index, (_, folder) ->
                            add(sectionLabelFor(folder.name), starredRowStart + index)
                        }
                    }
                    listedFolders.forEachIndexed { index, folder ->
                        add(sectionLabelFor(folder.name), folderRowStart + index)
                    }
                    folderTracks.forEachIndexed { index, track ->
                        val key = if (sort == TrackSort.Artist) {
                            track.artist.orEmpty().ifBlank { track.title }
                        } else {
                            track.title
                        }
                        add(sectionLabelFor(key), trackRowStart + index)
                    }
                    starts.toMap()
                }
            }
            val letters = remember(letterStarts) {
                letterStarts.keys.sortedWith(compareBy({ it == "#" }, { it }))
            }
            val currentLetter by remember(letterStarts, letters) {
                derivedStateOf {
                    val first = listState.firstVisibleItemIndex
                    letters.lastOrNull { letter ->
                        (letterStarts[letter] ?: Int.MAX_VALUE) <= first
                    }
                }
            }
            val showIndex = alphabetical && letters.size > 2
            LaunchedEffect(sort) { listState.scrollToItem(0) }

            var moveSource by remember { mutableStateOf<FolderNode?>(null) }
            var explodeSource by remember { mutableStateOf<FolderNode?>(null) }
            var deleteSource by remember { mutableStateOf<FolderNode?>(null) }
            // Only offer folders in the same granted tree, never a descendant of the
            // folder being moved, and never the parent it already lives in.
            val moveCandidates = remember(root, moveSource) {
                val source = moveSource ?: return@remember emptyList()
                val sourceTree = source.treeId()
                val parentPath = source.path.substringBeforeLast('/', "")
                if (sourceTree == null) {
                    emptyList()
                } else {
                    root.allFolders()
                        .filter { candidate ->
                            candidate.path != source.path &&
                                !candidate.path.startsWith("${source.path}/", ignoreCase = true) &&
                                candidate.path != parentPath &&
                                candidate.treeId() == sourceTree
                        }
                        .sortedBy { it.path.lowercase() }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                PathBar(
                    path = path,
                    starred = path.isNotEmpty() && path in starredFolders,
                    onToggleStar = { onToggleFolderStar(path) },
                    onNavigate = onPathChange,
                )

                if (folders.isEmpty() && folderTracks.isEmpty()) {
                    MessageState(
                        title = stringResource(R.string.files_empty),
                        body = stringResource(R.string.files_all),
                        icon = Icons.Filled.Folder,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionKicker(text = stringResource(sort.labelRes()))
                        Spacer(Modifier.width(8.dp))
                        CyberBadge(text = (starredNodes.size + listedFolders.size + folderTracks.size).toString())
                        Spacer(Modifier.weight(1f))
                        TrackSortButton(sort = sort, onSort = { sort = it })
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = if (showIndex) 24.dp else 0.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        if (atRoot && starredNodes.isNotEmpty()) {
                            item(key = "starred-header") {
                                FolderSectionHeader(
                                    label = stringResource(R.string.files_starred),
                                    count = starredNodes.size,
                                    accent = Cyber.Amber,
                                )
                            }
                            items(starredNodes, key = { "star:${it.first}" }) { (starredPath, starredNode) ->
                                FolderRow(
                                    name = starredNode.name,
                                    count = starredNode.allTracks().size,
                                    starred = true,
                                    // Only nested folders need the full path to tell two
                                    // same-named folders apart; top-level ones keep the
                                    // song count instead of repeating their own name.
                                    subtitle = starredPath.takeIf { it.contains('/') },
                                    onClick = { onPathChange(starredNode.path) },
                                    onToggleStar = { onToggleFolderStar(starredPath) },
                                    onReorder = {
                                        onReorderFolder(starredNode.path, starredNode.allTracks())
                                    },
                                    onMove = { moveSource = starredNode },
                                    onExplode = { explodeSource = starredNode },
                                    onDelete = { deleteSource = starredNode },
                                )
                            }
                            if (listedFolders.isNotEmpty()) {
                                item(key = "folders-header") {
                                    FolderSectionHeader(
                                        label = stringResource(R.string.files_folders),
                                        count = listedFolders.size,
                                    )
                                }
                            }
                        }
                        items(listedFolders, key = { "folder:${it.path}" }) { folder ->
                            FolderRow(
                                name = folder.name,
                                count = folder.allTracks().size,
                                starred = folder.path in starredFolders,
                                onClick = { onPathChange(folder.path) },
                                onToggleStar = { onToggleFolderStar(folder.path) },
                                onReorder = {
                                    onReorderFolder(folder.path, folder.allTracks())
                                },
                                onMove = { moveSource = folder },
                                onExplode = { explodeSource = folder },
                                onDelete = { deleteSource = folder },
                            )
                        }
                        items(folderTracks, key = { it.uri.toString() }) { track ->
                            SongRow(
                                track = track,
                                selected = track.uri.toString() == currentTrackUri,
                                // Playing from a folder queues that folder and everything under it.
                                onClick = { onPlay(track, playQueue) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onEdit = { action -> onEditSong(track, action) },
                                onGoTo = { action -> onGoToSong(track, action) },
                                onCompress = { onCompress(track) },
                                onLike = { onLike(track) },
                                onDislike = { onDislike(track) },
                                onDelete = { onDelete(track) },
                            )
                        }
                    }

                    if (showIndex) {
                        AlphabetRail(
                            letters = letters,
                            current = currentLetter,
                            onPick = { letter ->
                                letterStarts[letter]?.let { index ->
                                    scope.launch { listState.scrollToItem(index) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp),
                        )
                    }
                    }
                }
            }

            moveSource?.let { source ->
                MoveFolderDialog(
                    sourceName = source.name,
                    candidates = moveCandidates,
                    onDismiss = { moveSource = null },
                    onPick = { destination ->
                        moveSource = null
                        onMoveFolder(
                            source.path,
                            destination.path,
                            source.allTracks(),
                            destination.allTracks(),
                        )
                    },
                )
            }

            explodeSource?.let { source ->
                AlertDialog(
                    onDismissRequest = { explodeSource = null },
                    title = { Text(stringResource(R.string.folder_explode_title)) },
                    text = {
                        Text(stringResource(R.string.folder_explode_confirm, source.name))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                explodeSource = null
                                onExplodeFolder(source.path, source.allTracks())
                            },
                        ) {
                            Text(stringResource(R.string.folder_explode))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { explodeSource = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }

            deleteSource?.let { source ->
                val count = source.allTracks().size
                AlertDialog(
                    onDismissRequest = { deleteSource = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    title = { Text(stringResource(R.string.folder_delete_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.folder_delete_confirm, source.name))
                            // Spelling out how many songs disappear makes the scope of a
                            // folder delete concrete; the folder's own name alone hides it.
                            Text(
                                text = pluralStringResource(
                                    R.plurals.folder_delete_songs,
                                    count,
                                    count,
                                ),
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleteSource = null
                                onDeleteFolder(source.path, source.allTracks())
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteSource = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}

/**
 * Breadcrumb navigation for the folder tree.
 *
 * The strip scrolls horizontally and always parks at its end, so the folder you are
 * actually inside is the one that stays visible when the path is longer than the screen.
 * Every ancestor is a link back to that level; the current crumb is not clickable.
 */
@Composable
private fun PathBar(
    path: String,
    starred: Boolean,
    onToggleStar: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    // maxValue only becomes non-zero after the first measure, so key on it as well.
    LaunchedEffect(path, scroll.maxValue) {
        scroll.scrollTo(scroll.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (path.isNotEmpty()) {
            IconButton(onClick = { onNavigate(path.substringBeforeLast('/', "")) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.files_up),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val parts = path.split('/').filter { it.isNotBlank() }

                Breadcrumb(
                    label = stringResource(R.string.files_all),
                    current = path.isEmpty(),
                    separator = parts.isNotEmpty(),
                    onClick = { onNavigate("") },
                )

                var accumulated = ""
                parts.forEachIndexed { index, part ->
                    accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
                    val target = accumulated
                    Breadcrumb(
                        label = part,
                        current = index == parts.lastIndex,
                        separator = index != parts.lastIndex,
                        onClick = { onNavigate(target) },
                    )
                }
            }

            // Soft edges tell the eye the strip continues in that direction, and hide
            // the hard cut of a crumb that is only half on screen.
            val background = MaterialTheme.colorScheme.background
            if (scroll.value > 0) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(
                                Brush.horizontalGradient(listOf(background, Color.Transparent)),
                            ),
                    )
                }
            }
            if (scroll.value < scroll.maxValue) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Color.Transparent, background)),
                            ),
                    )
                }
            }
        }

        // The folder you are standing in has no row of its own, so it gets its own
        // star target here. The root itself is not a folder that can be pinned.
        if (path.isNotEmpty()) {
            IconButton(onClick = onToggleStar, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(
                        if (starred) R.string.files_unstar else R.string.files_star,
                    ),
                    tint = if (starred) Cyber.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Section label + count used by the pinned Starred block and the root folder list. */
@Composable
private fun FolderSectionHeader(
    label: String,
    count: Int,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionKicker(text = label.uppercase(), accent = accent)
        Spacer(Modifier.width(8.dp))
        CyberBadge(text = count.toString(), color = accent)
    }
}

@Composable
private fun Breadcrumb(
    label: String,
    current: Boolean,
    separator: Boolean,
    onClick: () -> Unit,
) {
    val color = if (current) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (current) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
        // The slash belongs to the crumb it follows: when an ancestor scrolls out of
        // view it takes its own separator with it, so no orphan "/" is left behind.
        if (separator) {
            Text(
                text = "/",
                modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    count: Int,
    starred: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onReorder: () -> Unit,
    onMove: () -> Unit,
    onExplode: () -> Unit,
    onDelete: () -> Unit,
    subtitle: String? = null,
) {
    val accent = if (starred) Cyber.Amber else MaterialTheme.colorScheme.secondary
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .cyberClick(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = accent,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle ?: pluralStringResource(R.plurals.song_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleStar, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(
                        if (starred) R.string.files_unstar else R.string.files_star,
                    ),
                    tint = if (starred) Cyber.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.files_folder_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reorder_contents)) },
                onClick = {
                    menuOpen = false
                    onReorder()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_move)) },
                onClick = {
                    menuOpen = false
                    onMove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_explode)) },
                onClick = {
                    menuOpen = false
                    onExplode()
                },
            )
            // Destructive, so it sits below a divider and in the error colour: it can
            // never be mistaken for the folder-level organising actions above it.
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.folder_delete),
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
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

private fun sortTracks(
    tracks: List<Track>,
    sort: TrackSort,
    addedAt: Map<String, Long>,
): List<Track> = when (sort) {
    TrackSort.DateAdded -> tracks.sortedWith(
        compareByDescending<Track> { addedAt[it.uri.toString()] ?: 0L }
            .thenBy { sortKey(it.title) },
    )
    TrackSort.Title -> tracks.sortedBy { sortKey(it.title) }
    TrackSort.Artist -> tracks.sortedWith(
        compareBy({ sortKey(it.artist.orEmpty()) }, { sortKey(it.title) }),
    )
    TrackSort.Duration -> tracks.sortedWith(
        compareBy<Track> { it.durationMs }.thenBy { sortKey(it.title) },
    )
}

@Composable
private fun MoveFolderDialog(
    sourceName: String,
    candidates: List<FolderNode>,
    onDismiss: () -> Unit,
    onPick: (FolderNode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_move_title, sourceName)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.folder_move_none))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.path }) { candidate ->
                        TextButton(
                            onClick = { onPick(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = candidate.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = candidate.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun FolderNode.allFolders(): List<FolderNode> = buildList {
    children.values.forEach { child ->
        add(child)
        addAll(child.allFolders())
    }
}

private fun FolderNode.treeId(): String? =
    allTracks().firstOrNull()?.uri?.let { uri ->
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    }

private class FolderNode(val name: String, val path: String) {
    val children = linkedMapOf<String, FolderNode>()
    val tracks = mutableListOf<Track>()

    fun allTracks(): List<Track> {
        val collected = mutableListOf<Track>()
        collect(collected)
        return collected
    }

    private fun collect(into: MutableList<Track>) {
        into += tracks
        children.values.forEach { it.collect(into) }
    }
}

private fun buildTree(tracks: List<Track>): FolderNode {
    val root = FolderNode(name = "", path = "")
    tracks.forEach { track ->
        var node = root
        track.folder.trim('/').split('/')
            .filter { it.isNotBlank() }
            .forEach { part ->
                val childPath = if (node.path.isEmpty()) part else "${node.path}/$part"
                node = node.children.getOrPut(part) { FolderNode(name = part, path = childPath) }
            }
        node.tracks += track
    }
    return root
}

private fun FolderNode.find(path: String): FolderNode? {
    if (path.isEmpty()) return this
    var node: FolderNode = this
    path.split('/').filter { it.isNotBlank() }.forEach { part ->
        node = node.children[part] ?: return null
    }
    return node
}
