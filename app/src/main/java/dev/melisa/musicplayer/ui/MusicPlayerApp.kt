package dev.melisa.musicplayer.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.AppSettings
import dev.melisa.musicplayer.data.AudioFileDetails
import dev.melisa.musicplayer.data.CompressAudio
import dev.melisa.musicplayer.data.CompressFailure
import dev.melisa.musicplayer.data.CompressOutcome
import dev.melisa.musicplayer.data.FolderReorder
import dev.melisa.musicplayer.data.HoldingHand
import dev.melisa.musicplayer.data.LibraryExtras
import dev.melisa.musicplayer.data.MetadataApplyOutcome
import dev.melisa.musicplayer.data.MetadataWriteFailure
import dev.melisa.musicplayer.data.MovedTrack
import dev.melisa.musicplayer.data.MusicLibrary
import dev.melisa.musicplayer.data.PendingMetadataWriter
import dev.melisa.musicplayer.data.ReorderError
import dev.melisa.musicplayer.data.SongArt
import dev.melisa.musicplayer.data.SongFileSync
import dev.melisa.musicplayer.data.SongMetadata
import dev.melisa.musicplayer.data.TagEditor
import dev.melisa.musicplayer.data.TagWriteResult
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.data.deleteSongFile
import dev.melisa.musicplayer.data.songFileInfo
import dev.melisa.musicplayer.data.weightedShuffle
import dev.melisa.musicplayer.playback.PlaybackService
import dev.melisa.musicplayer.ui.components.AddToPlaylistDialog
import dev.melisa.musicplayer.ui.components.BulkSelectionBar
import dev.melisa.musicplayer.ui.components.BulkTagsDialog
import dev.melisa.musicplayer.ui.components.BulkTextFieldDialog
import dev.melisa.musicplayer.ui.components.CompressSheet
import dev.melisa.musicplayer.ui.components.CompressUiState
import dev.melisa.musicplayer.ui.components.CyberDivider
import dev.melisa.musicplayer.ui.components.EqualizerBars
import dev.melisa.musicplayer.ui.components.LocalSongSelection
import dev.melisa.musicplayer.ui.components.ManageTagsDialog
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.NewPlaylistDialog
import dev.melisa.musicplayer.ui.components.PlayPauseGlyph
import dev.melisa.musicplayer.ui.components.ReorderDialog
import dev.melisa.musicplayer.ui.components.ReorderUiState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.rememberSongSelectionState
import dev.melisa.musicplayer.ui.components.SongCover
import dev.melisa.musicplayer.ui.components.SongEditAction
import dev.melisa.musicplayer.ui.screens.ArtistScreen
import dev.melisa.musicplayer.ui.screens.CollectionEntry
import dev.melisa.musicplayer.ui.screens.CollectionScreen
import dev.melisa.musicplayer.ui.screens.CollectionsBrowserScreen
import dev.melisa.musicplayer.ui.screens.DashboardScreen
import dev.melisa.musicplayer.ui.screens.EditCollectionScreen
import dev.melisa.musicplayer.ui.screens.EditMetadataScreen
import dev.melisa.musicplayer.ui.screens.FilesScreen
import dev.melisa.musicplayer.ui.screens.FreeUpSpaceScreen
import dev.melisa.musicplayer.ui.screens.HearDifferenceScreen
import dev.melisa.musicplayer.ui.screens.NowPlayingScreen
import dev.melisa.musicplayer.ui.screens.PlaylistsDestination
import dev.melisa.musicplayer.ui.screens.SearchScreen
import dev.melisa.musicplayer.ui.screens.SettingsScreen
import dev.melisa.musicplayer.ui.screens.SongsScreen
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.Motion
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "MusicPlayerApp"
private const val SEARCH_PULL_PREVIEW = 0.55f
private const val SEARCH_PULL_COMMIT = 0.7f

@Composable
fun MusicPlayerApp(settings: AppSettings) {
    RequestNotificationPermission(settings)
    var current by rememberSaveable { mutableStateOf(Destination.Dashboard) }
    // Search is an overlay, not a tab. It can be pulled down from the top of any
    // scrolled page; the top bar's magnifier opens it with the same animation.
    var searchProgress by remember { mutableFloatStateOf(0f) }
    var searchPull by remember { mutableFloatStateOf(0f) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchAnimJob by remember { mutableStateOf<Job?>(null) }
    val searchScope = rememberCoroutineScope()
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    // Browsing state for the contextual pages opened from the player.
    var filesPath by rememberSaveable { mutableStateOf("") }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var openArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var editAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var editArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var artVersion by remember { mutableStateOf(0) }

    val onEnd = settings.hand == HoldingHand.Right

    val context = LocalContext.current
    val extras = remember { LibraryExtras(context) }

    LaunchedEffect(settings.syncToFiles) {
        extras.syncToFiles = settings.syncToFiles
        if (!settings.syncToFiles) SongFileSync.cancelPending()
    }

    // Playback lives in PlaybackService; this is only a remote control for it.
    val controller = rememberPlaybackController(context)

    var isPlaying by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    // Paint the last completed scan immediately; the launch scan refreshes it in place.
    var tracks by remember { mutableStateOf(extras.cachedTracks().takeIf { it.isNotEmpty() }) }
    var isScanning by remember { mutableStateOf(settings.libraries.isNotEmpty()) }
    var nowPlaying by remember { mutableStateOf<Track?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    val latestTracks by rememberUpdatedState(tracks)

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                val match = latestTracks?.firstOrNull { it.uri.toString() == id }
                when {
                    match != null -> nowPlaying = match
                    // A rename rewrites the queue item before the rescan can know the
                    // new URI. Keep the track on screen with its new address instead
                    // of blanking the player until the list catches up.
                    id != null && nowPlaying?.uri?.toString() != id ->
                        nowPlaying = nowPlaying?.copy(uri = Uri.parse(id))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Never leave the transport showing "playing" after a failed source.
                isPlaying = false
                android.util.Log.w(TAG, "playback error", error)
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }

    // Re-scans when folders change, and after tag edits (refreshToken). The previous
    // list (from the cache on launch) stays on screen the whole time; when there is no
    // cache at all, the list screens show their full scanning state instead.
    LaunchedEffect(settings.libraries, refreshToken) {
        isScanning = true
        try {
            val scanned = MusicLibrary.scan(context, settings.libraries, extras.trackMetaCache)
            tracks = scanned
            // First-seen stamps order Songs by date added.
            extras.registerAdded(scanned.map { it.uri.toString() to it.lastModifiedMs })
            nowPlaying = nowPlaying?.let { old ->
                scanned.firstOrNull { it.uri == old.uri } ?: old
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Keep whatever was on screen. A first scan that failed must not leave the
            // list stuck in "scanning" forever, so fall back to an empty library.
            android.util.Log.w("MusicPlayerApp", "library scan failed", t)
            if (tracks == null) tracks = emptyList()
        } finally {
            isScanning = false
        }
    }

    // Position for both the mini player and the full player, polled while a track exists.
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    // The track whose play has already been recorded this session. Without it the poll
    // loop would bump the play counter every half second while a song keeps playing.
    var heardMarkedUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nowPlaying, controller) {
        while (nowPlaying != null) {
            positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            durationMs = controller?.duration?.takeIf { it > 0L } ?: (nowPlaying?.durationMs ?: 0L)
            // Listening history feeds the "Unheard songs" smart list. Only real playback
            // counts, and only after a short stretch, so skipping through a list does not
            // mark every row as heard.
            val track = nowPlaying
            if (track != null && controller?.isPlaying == true &&
                positionMs >= listenedThresholdMs(track.durationMs)
            ) {
                val uri = track.uri.toString()
                if (heardMarkedUri != uri) {
                    extras.markListened(uri)
                    heardMarkedUri = uri
                }
            }
            delay(500)
        }
    }

    fun play(track: Track, queue: List<Track>? = null, shuffle: Boolean? = null) {
        val player = controller ?: return
        // The queue is the list the song was picked from, in the order it is shown,
        // so the item that follows a song is its next sibling in that list. Never end
        // up with an empty queue (or one that lost the selected song): that would stop
        // playback as soon as this one track finished.
        val base = (queue ?: tracks).orEmpty().ifEmpty { listOf(track) }

        val wantShuffle = shuffle ?: shuffleEnabled
        val ordered = if (wantShuffle) {
            listOf(track) + weightedShuffle(base.filter { it.uri != track.uri }) {
                extras.rating(it.uri.toString())
            }
        } else if (base.any { it.uri == track.uri }) {
            base
        } else {
            listOf(track) + base
        }

        val index = ordered.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        nowPlaying = track
        player.setMediaItems(
            ordered.map { it.toMediaItem() },
            index,
            0L,
        )
        player.prepare()
        player.play()
    }

    /** Re-shuffles what is left of the queue around the song that is playing now. */
    fun reshuffleRemaining() {
        val player = controller ?: return
        val currentTrack = nowPlaying ?: return
        val items = (0 until player.mediaItemCount).mapNotNull { i ->
            val id = player.getMediaItemAt(i).mediaId
            latestTracks?.firstOrNull { it.uri.toString() == id }
        }
        val rest = weightedShuffle(items.filter { it.uri != currentTrack.uri }) {
            extras.rating(it.uri.toString())
        }
        val position = player.currentPosition
        player.setMediaItems(
            (listOf(currentTrack) + rest).map { it.toMediaItem() },
            0,
            position,
        )
        player.prepare()
        player.play()
    }

    /**
     * Disliking the song that is currently playing means "not this one": score it and
     * skip. Disliking any other song only changes its score.
     */
    fun dislike(track: Track) {
        extras.dislike(track.uri.toString())
        if (nowPlaying?.uri == track.uri) {
            controller?.seekToNext()
        }
    }

    fun shuffleEverything() {
        val queue = tracks.orEmpty()
        if (queue.isEmpty()) return
        shuffleEnabled = true
        play(queue.first(), queue, shuffle = true)
    }

    val albumEntries = remember(tracks, extras.addedAt) {
        tracks.orEmpty()
            .mapNotNull { track -> track.album?.takeIf { it.isNotBlank() }?.let { it to track } }
            .groupBy({ it.first }, { it.second })
            .map { (name, list) ->
                val artists = list.mapNotNull {
                    it.artist?.takeIf { artist -> artist.isNotBlank() }
                }.distinct().sorted()
                CollectionEntry(
                    name = name,
                    count = list.size,
                    subtitle = artists.joinToString(", "),
                    coverUri = list.firstOrNull()?.uri?.toString(),
                    artist = artists.firstOrNull(),
                    addedAt = list.maxOfOrNull {
                        extras.addedAt(it.uri.toString()) ?: 0L
                    } ?: 0L,
                    durationMs = list.sumOf { it.durationMs },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val knownArtists = remember(tracks) {
        tracks.orEmpty()
            .mapNotNull { it.artist?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    val knownAlbums = remember(tracks) {
        tracks.orEmpty()
            .mapNotNull { it.album?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    val artistEntries = remember(tracks, extras.addedAt) {
        tracks.orEmpty()
            .mapNotNull { track -> track.artist?.takeIf { it.isNotBlank() }?.let { it to track } }
            .groupBy({ it.first }, { it.second })
            .map { (name, list) ->
                CollectionEntry(
                    name = name,
                    count = list.size,
                    subtitle = null,
                    artist = name,
                    addedAt = list.maxOfOrNull {
                        extras.addedAt(it.uri.toString()) ?: 0L
                    } ?: 0L,
                    durationMs = list.sumOf { it.durationMs },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    var dialogTrack by remember { mutableStateOf<Track?>(null) }
    var editingTrack by remember { mutableStateOf<Track?>(null) }
    // The bitrate audition page opened from the editor.
    var compareTrack by remember { mutableStateOf<Track?>(null) }
    var compareDetails by remember { mutableStateOf<AudioFileDetails?>(null) }
    // The song whose file the user asked to delete; the confirmation dialog reads it.
    var deleteTarget by remember { mutableStateOf<Track?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }

    // Bulk selection shared by every SongRow/track row in the app.
    val songSelection = rememberSongSelectionState()
    var bulkTitleTarget by remember { mutableStateOf<List<Track>?>(null) }
    var bulkArtistTarget by remember { mutableStateOf<List<Track>?>(null) }
    var bulkAlbumTarget by remember { mutableStateOf<List<Track>?>(null) }
    var bulkTagsTarget by remember { mutableStateOf<List<Track>?>(null) }
    var bulkPlaylistTarget by remember { mutableStateOf<List<Track>?>(null) }
    var showBulkPlaylistPicker by remember { mutableStateOf(false) }
    var showNewBulkPlaylist by remember { mutableStateOf(false) }
    var bulkCompressTarget by remember { mutableStateOf<List<Track>?>(null) }
    var bulkDeleteTarget by remember { mutableStateOf<List<Track>?>(null) }
    val bulkEditScope = rememberCoroutineScope()
    var bulkEditRunning by remember { mutableStateOf(false) }
    var bulkEditDone by remember { mutableStateOf(0) }
    var bulkEditTotal by remember { mutableStateOf(0) }
    var bulkEditMessage by remember { mutableStateOf<String?>(null) }

    // Compression: the sheet is global so a batch keeps running while the user browses.
    var compressTracks by remember { mutableStateOf<List<Track>?>(null) }
    var compressState by remember { mutableStateOf(CompressUiState()) }
    var compressJob by remember { mutableStateOf<Job?>(null) }
    var showFreeSpace by rememberSaveable { mutableStateOf(false) }
    val compressScope = rememberCoroutineScope()
    val deleteScope = rememberCoroutineScope()

    // Folder reordering is a batch too; the dialog lives above the tabs so switching
    // screens cannot hide a running move.
    var reorderState by remember { mutableStateOf<ReorderUiState?>(null) }
    val reorderScope = rememberCoroutineScope()
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    // Folder move/explode is a disk batch too; keep its progress and result global so
    // switching tabs cannot leave an invisible operation running.
    var folderOpKind by remember { mutableStateOf<FolderOpKind?>(null) }
    var folderOpMessage by remember { mutableStateOf<String?>(null) }
    val folderOpScope = rememberCoroutineScope()

    // Metadata edits that must wait for playback to stop before touching the file.
    var metadataWriteFailure by remember { mutableStateOf<MetadataWriteFailure?>(null) }
    val metadataScope = rememberCoroutineScope()
    var metadataFlushJob by remember { mutableStateOf<Job?>(null) }

    /**
     * A top-level destination change closes whatever album/artist page was open on top
     * of the old tab. Without this the new tab renders underneath the old page and the
     * user has to press Back to see what they just tapped.
     */
    fun animateSearchTo(target: Float) {
        searchAnimJob?.cancel()
        searchAnimJob = searchScope.launch {
            animate(
                initialValue = searchProgress,
                targetValue = target,
                animationSpec = tween(if (target > searchProgress) 280 else 230),
            ) { value, _ -> searchProgress = value }
        }
    }

    fun revealForPull(pull: Float): Float =
        ((pull - SEARCH_PULL_PREVIEW) / (1f - SEARCH_PULL_PREVIEW)).coerceIn(0f, 1f)

    fun openSearch() {
        if (searchOpen && searchProgress >= 1f) return
        searchPull = 0f
        searchOpen = true
        animateSearchTo(1f)
    }

    fun closeSearch() {
        if (!searchOpen && searchProgress <= 0f) return
        searchPull = 0f
        searchOpen = false
        animateSearchTo(0f)
    }

    fun commitSearchPull() {
        searchPull = 1f
        searchOpen = true
        animateSearchTo(1f)
    }

    fun cancelSearchPull() {
        searchPull = 0f
        animateSearchTo(0f)
    }

    fun dismissSearchImmediately() {
        searchAnimJob?.cancel()
        searchOpen = false
        searchProgress = 0f
        searchPull = 0f
    }

    fun navigate(destination: Destination) {
        if (destination == Destination.Search) {
            openSearch()
            return
        }
        dismissSearchImmediately()
        songSelection.clear()
        openAlbum = null
        openArtist = null
        current = destination
    }

    /** Only one collection page is ever on top; opening one closes the other. */
    fun showAlbum(name: String?) {
        if (name.isNullOrBlank()) return
        songSelection.clear()
        openArtist = null
        openAlbum = name
    }

    fun showArtist(name: String?) {
        if (name.isNullOrBlank()) return
        songSelection.clear()
        openAlbum = null
        openArtist = name
    }

    fun openCompress(selection: List<Track>) {
        if (compressState.running || selection.isEmpty()) return
        compressTracks = selection
        compressState = CompressUiState()
    }

    fun closeCompress() {
        if (compressState.running) return
        compressTracks = null
        compressState = CompressUiState()
        compressJob = null
    }

    /**
     * Points the queue at the compressed file after a replace, so deleting the original
     * does not kill playback of the track that is playing.
     */
    fun replacePlayingTrack(track: Track, newUri: Uri) {
        if (nowPlaying?.uri != track.uri) return
        val player = controller
        if (player == null) {
            nowPlaying = track.copy(uri = newUri)
            return
        }
        val index = (0 until player.mediaItemCount).firstOrNull { i ->
            player.getMediaItemAt(i).mediaId == track.uri.toString()
        }
        if (index == null) {
            nowPlaying = track.copy(uri = newUri)
            return
        }
        val wasPlaying = player.isPlaying
        val position = player.currentPosition
        runCatching {
            player.replaceMediaItem(
                index,
                player.getMediaItemAt(index).buildUpon()
                    .setUri(newUri)
                    .setMediaId(newUri.toString())
                    .build(),
            )
            player.seekTo(index, position)
            if (wasPlaying) player.play()
        }
        nowPlaying = track.copy(uri = newUri)
    }

    /**
     * Re-opens a media item whose file was rewritten on disk (tag edit, rating /
     * favourite sync, rename). ExoPlayer keeps the file handle and buffered range it
     * opened before the write; on some providers the truncate-and-copy leaves that
     * range stale, so the audio stops while the player still reports PLAYING and the
     * position keeps ticking. Replacing the item forces a fresh read at the same spot.
     */
    fun reopenPlayingTrack(
        oldUri: Uri,
        newUri: Uri = oldUri,
        fallbackPositionMs: Long = positionMs,
        fallbackWasPlaying: Boolean = isPlaying,
    ) {
        val player = controller ?: return
        val index = (0 until player.mediaItemCount).firstOrNull { i ->
            player.getMediaItemAt(i).mediaId == oldUri.toString()
        } ?: return
        // Only the item actually playing needs a restart; queue neighbours can wait.
        if (index != player.currentMediaItemIndex) return
        if (player.playbackState == Player.STATE_ENDED) return

        val position = maxOf(player.currentPosition.coerceAtLeast(0L), fallbackPositionMs)
        val resume = player.playWhenReady || player.isPlaying || fallbackWasPlaying
        if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
            android.util.Log.d(TAG, "reopening playing item at ${position}ms")
        }
        val result = runCatching {
            player.replaceMediaItem(
                index,
                player.getMediaItemAt(index).buildUpon()
                    .setUri(newUri)
                    .setMediaId(newUri.toString())
                    .build(),
            )
            player.prepare()
            player.seekTo(index, position)
            if (resume) player.play() else player.pause()
        }
        if (result.isFailure) {
            android.util.Log.w(TAG, "could not reopen playing item", result.exceptionOrNull())
        }
        if (nowPlaying?.uri == oldUri) nowPlaying = nowPlaying?.copy(uri = newUri)
    }

    /**
     * Moves the app's bookkeeping with files that a folder reorder relocated. SAF changes
     * the document URI, so ratings, tags, favourites, history, first-seen stamps and
     * playlists have to follow, and any queued copy of the song needs its new address.
     */
    fun applyMovedTrack(moved: MovedTrack) {
        val oldUri = moved.oldUri
        val newUri = moved.newUri
        if (oldUri == newUri) return

        extras.rekey(oldUri.toString(), newUri.toString())

        val player = controller
        if (nowPlaying?.uri == oldUri) {
            // Reopening keeps the current position instead of restarting the song.
            val position = player?.currentPosition?.coerceAtLeast(0L) ?: positionMs
            reopenPlayingTrack(oldUri, newUri, position, isPlaying)
            nowPlaying = nowPlaying?.copy(uri = newUri)
        } else if (player != null) {
            val index = (0 until player.mediaItemCount).firstOrNull { i ->
                player.getMediaItemAt(i).mediaId == oldUri.toString()
            }
            if (index != null) {
                runCatching {
                    player.replaceMediaItem(
                        index,
                        player.getMediaItemAt(index).buildUpon()
                            .setUri(newUri)
                            .setMediaId(newUri.toString())
                            .build(),
                    )
                }
            }
        }
        if (editingTrack?.uri == oldUri) editingTrack = editingTrack?.copy(uri = newUri)
    }

    /**
     * Updates the app's view of an edited song immediately, but leaves the file alone
     * while it is playing. The file is written by the pending-edit job once the track
     * is paused or replaced.
     */
    fun queueDeferredMetadata(track: Track, metadata: SongMetadata, fileName: String) {
        val title = metadata.title.ifBlank {
            songFileInfo(track.uri).name.substringBeforeLast('.').ifBlank { track.title }
        }
        val artist = metadata.artist.takeIf { it.isNotBlank() }
        val album = metadata.album.takeIf { it.isNotBlank() }
        val updated = track.copy(
            title = title,
            artist = artist,
            album = album,
            year = metadata.year,
        )

        tracks = tracks?.map { if (it.uri == track.uri) updated else it }
        if (nowPlaying?.uri == track.uri) nowPlaying = updated
        // The file still has its old size/mtime, so the scan cache keeps the edited
        // values instead of reverting until the deferred write happens.
        extras.cacheEditedMetadata(updated, title, artist, album, metadata.year)
        extras.queueMetadataEdit(track.uri.toString(), metadata, fileName)
    }

    /**
     * Applies one title, artist or album value to a batch, preserving every other tag.
     *
     * Non-playing files are rewritten now; the playing file is queued through the
     * deferred-editor path so a bulk edit cannot introduce the gap the single editor
     * avoids. The in-memory list and scan cache update immediately.
     */
    fun setBulkMetadata(
        target: List<Track>,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ) {
        if (bulkEditRunning || target.isEmpty()) return
        bulkEditRunning = true
        bulkEditDone = 0
        bulkEditTotal = target.size
        bulkEditMessage = null
        bulkEditScope.launch {
            SongFileSync.cancelPending()
            var failed = 0
            val updates = mutableMapOf<String, Track>()
            try {
                target.forEach { track ->
                    val metadata = TagEditor.read(context, track.uri)
                    if (metadata == null) {
                        failed++
                    } else {
                        val next = metadata.copy(
                            title = title ?: metadata.title,
                            artist = artist ?: metadata.artist,
                            album = album ?: metadata.album,
                        )
                        if (nowPlaying?.uri == track.uri && isPlaying) {
                            queueDeferredMetadata(track, next, songFileInfo(track.uri).name)
                        } else {
                            when (TagEditor.write(context, track.uri, next)) {
                                TagWriteResult.Success -> {
                                    updates[track.uri.toString()] = track.copy(
                                        title = next.title.takeIf { it.isNotBlank() }
                                            ?: track.title,
                                        artist = next.artist.takeIf { it.isNotBlank() },
                                        album = next.album.takeIf { it.isNotBlank() },
                                    )
                                }
                                else -> failed++
                            }
                        }
                    }
                    bulkEditDone++
                }

                if (updates.isNotEmpty()) {
                    tracks = tracks?.map { updates[it.uri.toString()] ?: it }
                    val current = nowPlaying
                    val updatedCurrent = updates[current?.uri?.toString()]
                    if (current != null && updatedCurrent != null) {
                        nowPlaying = updatedCurrent
                        val position = controller?.currentPosition?.coerceAtLeast(0L) ?: positionMs
                        reopenPlayingTrack(
                            current.uri,
                            current.uri,
                            position,
                            fallbackWasPlaying = false,
                        )
                    }
                    updates.values.forEach { updated ->
                        extras.cacheEditedMetadata(
                            updated,
                            updated.title,
                            updated.artist,
                            updated.album,
                            updated.year,
                        )
                    }
                    refreshToken++
                }
            } finally {
                bulkEditRunning = false
            }
            if (failed > 0) {
                bulkEditMessage = context.resources.getQuantityString(
                    R.plurals.bulk_metadata_failed,
                    failed,
                    failed,
                )
            }
        }
    }

    fun commonValue(target: List<Track>, selector: (Track) -> String?): String =
        target.mapNotNull(selector)
            .filter { it.isNotBlank() }
            .distinct()
            .singleOrNull()
            .orEmpty()

    // A queued edit waits until its own track is no longer actively playing. Pausing,
    // changing track, or relaunching makes the pending list eligible; another track may
    // keep playing in the meantime without blocking the write.
    LaunchedEffect(controller, nowPlaying?.uri, isPlaying, extras.pendingMetadata) {
        if (controller == null) return@LaunchedEffect
        if (metadataFlushJob?.isActive == true) return@LaunchedEffect
        if (controller.isPlaying && nowPlaying == null) return@LaunchedEffect
        if (extras.pendingMetadata.isEmpty()) return@LaunchedEffect

        metadataFlushJob = metadataScope.launch {
            // A seek or a buffer underrun can drop isPlaying for a frame. Let the
            // player settle before treating that as the pause the edit is waiting for.
            delay(600L)
            val failedThisPass = mutableSetOf<String>()
            try {
                while (true) {
                    val playing = controller?.isPlaying == true
                    val pending = extras.pendingMetadata.firstOrNull { item ->
                        item.uri !in failedThisPass &&
                            !(playing && nowPlaying?.uri?.toString() == item.uri)
                    } ?: break

                    when (val outcome = PendingMetadataWriter.apply(context, pending)) {
                        is MetadataApplyOutcome.Success -> {
                            extras.clearPendingMetadata(pending.uri)
                            if (outcome.newUri != outcome.oldUri) {
                                applyMovedTrack(MovedTrack(outcome.oldUri, outcome.newUri))
                            } else if (nowPlaying?.uri == outcome.oldUri) {
                                val position = controller?.currentPosition
                                    ?.coerceAtLeast(0L) ?: positionMs
                                // The job may outlive the composition that started it;
                                // never resume from a stale captured isPlaying. This
                                // write only happens while the track is paused or gone.
                                reopenPlayingTrack(
                                    outcome.oldUri,
                                    outcome.newUri,
                                    position,
                                    fallbackWasPlaying = false,
                                )
                            }
                            refreshToken++
                        }

                        is MetadataApplyOutcome.Failed -> {
                            metadataWriteFailure = outcome.failure
                            failedThisPass += pending.uri
                        }
                    }
                }
            } finally {
                metadataFlushJob = null
            }
        }
    }

    /** Reorganises one folder as artist/album/song, then tracks the new URIs. */
    fun startReorderFolder(folderPath: String, folderTracks: List<Track>) {
        if (reorderState?.running == true) return
        val name = folderPath.trim('/').substringAfterLast('/').ifBlank { folderPath }
        if (folderTracks.isEmpty()) {
            // The empty result still gets a dialog; "No songs found" is the message.
            reorderState = ReorderUiState(folderName = name)
            return
        }

        // Same reasoning as compression: a queued tag write copies and rewrites a whole
        // file. Do not let it race a move; the database stays correct either way.
        SongFileSync.cancelPending()

        reorderState = ReorderUiState(
            folderName = name,
            running = true,
            total = folderTracks.size,
        )
        reorderScope.launch {
            try {
                val outcome = FolderReorder.reorder(
                    context = context,
                    tracks = folderTracks,
                    onMoved = { moved -> mainHandler.post { applyMovedTrack(moved) } },
                    onProgress = { completed, total, title ->
                        reorderState = reorderState?.copy(
                            total = total,
                            completed = completed,
                            currentTitle = title,
                        )
                    },
                )

                reorderState = ReorderUiState(
                    folderName = name,
                    total = folderTracks.size,
                    completed = folderTracks.size,
                    moved = outcome.moved.size,
                    alreadySorted = outcome.alreadySorted,
                    missingArtist = outcome.missingArtist,
                    failed = outcome.failed,
                    error = outcome.error == ReorderError.FolderNotWritable,
                )
                if (outcome.moved.isNotEmpty()) refreshToken++
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "folder reorder failed", t)
                // Some files may already have moved; re-key those and rescan.
                reorderState = ReorderUiState(folderName = name, error = true)
                refreshToken++
            }
        }
    }

    fun startFolderMove(
        sourcePath: String,
        destinationPath: String,
        sourceTracks: List<Track>,
        destinationTracks: List<Track>,
    ) {
        if (folderOpKind != null || sourceTracks.isEmpty() || destinationTracks.isEmpty()) return
        folderOpKind = FolderOpKind.Move
        folderOpMessage = null
        folderOpScope.launch {
            try {
                val outcome = FolderReorder.moveFolder(
                    context = context,
                    folderPath = sourcePath,
                    destinationPath = destinationPath,
                    folderTracks = sourceTracks,
                    destinationTracks = destinationTracks,
                )
                if (outcome.error != null) {
                    folderOpMessage = context.getString(R.string.folder_op_failed)
                } else {
                    outcome.movedTracks.forEach { applyMovedTrack(it) }
                    refreshToken++
                }
            } catch (_: Exception) {
                folderOpMessage = context.getString(R.string.folder_op_failed)
            } finally {
                folderOpKind = null
            }
        }
    }

    fun startFolderExplode(folderPath: String, folderTracks: List<Track>) {
        if (folderOpKind != null || folderTracks.isEmpty()) return
        folderOpKind = FolderOpKind.Explode
        folderOpMessage = null
        folderOpScope.launch {
            try {
                val outcome = FolderReorder.explodeFolder(
                    context = context,
                    folderPath = folderPath,
                    folderTracks = folderTracks,
                )
                if (outcome.error != null) {
                    folderOpMessage = context.getString(R.string.folder_op_failed)
                } else {
                    outcome.movedTracks.forEach { applyMovedTrack(it) }
                    refreshToken++
                    val movedLabel = context.resources.getQuantityString(
                        R.plurals.folder_items_moved,
                        outcome.movedChildren,
                        outcome.movedChildren,
                    )
                    val blocked = outcome.skipped + outcome.failed
                    when {
                        blocked > 0 -> {
                            val blockedLabel = context.resources.getQuantityString(
                                R.plurals.folder_items_blocked,
                                blocked,
                                blocked,
                            )
                            folderOpMessage = context.getString(
                                R.string.folder_explode_partial,
                                movedLabel,
                                blockedLabel,
                            )
                        }
                        !outcome.deleted -> {
                            folderOpMessage = context.getString(
                                R.string.folder_explode_undeleted,
                                movedLabel,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                folderOpMessage = context.getString(R.string.folder_op_failed)
            } finally {
                folderOpKind = null
            }
        }
    }

    // The database writes ratings / favourites / tags back into the audio file on a
    // debounced background job. That is the same in-place rewrite as a manual tag edit,
    // so give playback the same chance to re-open the file.
    DisposableEffect(extras, controller) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        extras.onFileRewritten = { uri ->
            main.post { reopenPlayingTrack(Uri.parse(uri)) }
        }
        onDispose { extras.onFileRewritten = null }
    }

    fun startCompression(bitrateKbps: Int, keepOriginal: Boolean) {
        val batch = compressTracks ?: return
        if (batch.isEmpty() || compressState.running) return

        // A queued tag write copies the whole file; letting it run while the transcoder
        // reads the same file can hand the decoder a half-rewritten one. Dropping the
        // queue is safe: the database stays correct and the next edit rewrites the tags.
        SongFileSync.cancelPending()

        val eligible = batch.filter { track ->
            CompressAudio.willSave(track.sizeBytes, track.durationMs, bitrateKbps)
        }
        if (eligible.isEmpty()) return
        val skipped = batch.size - eligible.size

        compressState = CompressUiState(
            running = true,
            total = eligible.size,
            currentTitle = eligible.first().title,
            skipped = skipped,
        )
        compressJob = compressScope.launch {
            var freed = 0L
            var failed = 0
            var kept = 0
            var lastFailure: CompressFailure? = null
            var cancelled = false
            try {
                eligible.forEachIndexed { index, track ->
                    compressState = compressState.copy(
                        currentTitle = track.title,
                        completed = index,
                        progress = 0f,
                    )
                    val outcome = CompressAudio.compress(
                        context = context,
                        source = track.uri,
                        targetBitrateKbps = bitrateKbps,
                        keepOriginal = keepOriginal,
                        sourceBytes = track.sizeBytes,
                        sourceDurationMs = track.durationMs,
                    ) { fraction ->
                        compressState = compressState.copy(
                            progress = (index + fraction) / eligible.size,
                        )
                    }

                    when (outcome) {
                        is CompressOutcome.Success -> {
                            val result = outcome.result
                            val oldUri = track.uri.toString()
                            val newUri = result.newUri.toString()
                            if (result.replaced) {
                                extras.rekey(oldUri, newUri)
                                freed += (result.sourceBytes - result.newBytes).coerceAtLeast(0L)
                                replacePlayingTrack(track, result.newUri)
                            } else {
                                extras.copyState(oldUri, newUri)
                                kept++
                            }
                            if (editingTrack?.uri == track.uri) {
                                editingTrack = track.copy(uri = result.newUri)
                            }
                        }

                        is CompressOutcome.Failure -> {
                            failed++
                            lastFailure = outcome.reason
                            android.util.Log.w(
                                "CompressQueue",
                                "failed: ${track.title} ${outcome.reason}",
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                cancelled = true
            } finally {
                compressState = compressState.copy(
                    running = false,
                    done = true,
                    cancelled = cancelled,
                    progress = if (cancelled) compressState.progress else 1f,
                    completed = eligible.size,
                    freedBytes = freed,
                    failed = failed,
                    kept = kept,
                    skipped = skipped,
                    failure = if (eligible.size == 1 && failed == 1) lastFailure else null,
                )
                refreshToken++
            }
        }
    }

    fun openAddToPlaylist(track: Track) {
        dialogTrack = track
        showPlaylistPicker = true
    }

    fun openTags(track: Track) {
        dialogTrack = track
        showTagEditor = true
    }

    /**
     * The kebab menu's Edit submenu. "Edit page" opens the full editor; the field
     * edits reuse the bulk dialogs, so one song and many songs share one writer.
     */
    fun editSong(track: Track, action: SongEditAction) {
        when (action) {
            SongEditAction.Page -> editingTrack = track
            SongEditAction.Title -> bulkTitleTarget = listOf(track)
            SongEditAction.Artist -> bulkArtistTarget = listOf(track)
            SongEditAction.Album -> bulkAlbumTarget = listOf(track)
            SongEditAction.Tags -> openTags(track)
        }
    }

    /**
     * Deletes a file only after the confirmation dialog accepted. Ratings, tags,
     * favourites and playlist entries go with it; if it was queued, the queue drops
     * that item so the player can never try to reopen a file that no longer exists.
     */
    fun deleteTrackFile(track: Track) {
        deleteScope.launch {
            if (!deleteSongFile(context, track.uri)) {
                deleteFailed = true
                return@launch
            }
            extras.forget(track.uri.toString())
            if (editingTrack?.uri == track.uri) editingTrack = null

            val player = controller
            val index = player?.let { p ->
                (0 until p.mediaItemCount).firstOrNull { i ->
                    p.getMediaItemAt(i).mediaId == track.uri.toString()
                }
            }
            if (player != null && index != null) {
                player.removeMediaItem(index)
                if (player.mediaItemCount == 0) {
                    player.stop()
                    nowPlaying = null
                }
            } else if (nowPlaying?.uri == track.uri) {
                nowPlaying = null
            }
            refreshToken++
        }
    }

    fun deleteTracks(target: List<Track>) {
        if (target.isEmpty()) return
        deleteScope.launch {
            var failed = 0
            target.forEach { track ->
                if (!deleteSongFile(context, track.uri)) {
                    failed++
                    return@forEach
                }
                extras.forget(track.uri.toString())
                if (editingTrack?.uri == track.uri) editingTrack = null

                val player = controller
                val index = player?.let { p ->
                    (0 until p.mediaItemCount).firstOrNull { i ->
                        p.getMediaItemAt(i).mediaId == track.uri.toString()
                    }
                }
                if (player != null && index != null) {
                    player.removeMediaItem(index)
                    if (player.mediaItemCount == 0) {
                        player.stop()
                        nowPlaying = null
                    }
                } else if (nowPlaying?.uri == track.uri) {
                    nowPlaying = null
                }
            }
            refreshToken++
            if (failed > 0) {
                bulkEditMessage = context.resources.getQuantityString(
                    R.plurals.bulk_delete_failed,
                    failed,
                    failed,
                )
            }
        }
    }

    LaunchedEffect(showNowPlaying, drawerOpen) {
        if (showNowPlaying || drawerOpen) songSelection.clear()
    }

    LaunchedEffect(Unit) {
        if (current == Destination.Search) {
            // Saved state from the old Search tab: lift it into the overlay.
            current = Destination.Dashboard
            searchOpen = true
            searchProgress = 1f
        }
    }

    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = showNowPlaying && !drawerOpen) { showNowPlaying = false }
    BackHandler(enabled = editingTrack != null) { editingTrack = null }
    BackHandler(enabled = showFreeSpace) { showFreeSpace = false }
    BackHandler(enabled = compressTracks != null && !compressState.running) { closeCompress() }
    // Back from a side tab returns to Home instead of quitting the app: losing a
    // scroll position because a thumb slipped is exactly the kind of thing that
    // makes a player feel fragile.
    BackHandler(
        enabled = current != Destination.Dashboard &&
            !drawerOpen &&
            !showNowPlaying &&
            editingTrack == null &&
            !showFreeSpace &&
            compressTracks == null &&
            reorderState == null,
    ) {
        navigate(Destination.Dashboard)
    }

    val searchThresholdPx = with(LocalDensity.current) { 210.dp.toPx() }
    val searchPullEnabled = !searchOpen &&
        !drawerOpen &&
        !showNowPlaying &&
        editingTrack == null &&
        !showFreeSpace &&
        compressTracks == null &&
        reorderState == null &&
        !songSelection.active
    // A gesture that has scrolled the list cannot turn into a search pull when it reaches
    // the top. Only a fresh pull that begins at the top is allowed to start the cue.
    val searchGestureScrolled = remember { booleanArrayOf(false) }
    val searchNestedScroll = remember(searchPullEnabled, searchThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchPull <= 0f || available.y >= 0f) return Offset.Zero
                searchAnimJob?.cancel()
                val next = (searchPull + available.y / searchThresholdPx)
                    .coerceAtLeast(0f)
                val consumed = (next - searchPull) * searchThresholdPx
                searchPull = next
                searchProgress = revealForPull(next)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!searchPullEnabled || searchOpen) return Offset.Zero
                if (consumed.y != 0f || available.y < 0f) {
                    searchGestureScrolled[0] = true
                    return Offset.Zero
                }
                if (searchGestureScrolled[0] || available.y <= 0f) return Offset.Zero
                searchAnimJob?.cancel()
                val next = (searchPull + available.y / searchThresholdPx)
                    .coerceAtMost(1f)
                val consumed = (next - searchPull) * searchThresholdPx
                searchPull = next
                searchProgress = revealForPull(next)
                return Offset(0f, consumed)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (searchPull > 0f) {
                    if (searchPull >= SEARCH_PULL_COMMIT || available.y > 1200f) {
                        commitSearchPull()
                    } else {
                        cancelSearchPull()
                    }
                }
                searchGestureScrolled[0] = false
                return Velocity.Zero
            }
        }
    }

    CompositionLocalProvider(LocalSongSelection provides songSelection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CyberTopBar(
                    title = stringResource(
                        if (searchOpen) Destination.Search.labelRes else current.labelRes,
                    ),
                    isPlaying = isPlaying,
                    hasTrack = nowPlaying != null,
                    isSearch = searchOpen,
                    onMenu = { drawerOpen = true },
                    onSearch = { if (searchOpen) closeSearch() else openSearch() },
                    onCloseSearch = { closeSearch() },
                )
            },
            bottomBar = {
                Column {
                    if (songSelection.active) {
                        BulkSelectionBar(
                            count = songSelection.selected.size,
                            onClear = { songSelection.clear() },
                            onSetArtist = { bulkArtistTarget = songSelection.tracks },
                            onSetAlbum = { bulkAlbumTarget = songSelection.tracks },
                            onSetTags = { bulkTagsTarget = songSelection.tracks },
                            onAddToPlaylist = {
                                bulkPlaylistTarget = songSelection.tracks
                                showBulkPlaylistPicker = true
                            },
                            onCompress = { bulkCompressTarget = songSelection.tracks },
                            onDelete = { bulkDeleteTarget = songSelection.tracks },
                        )
                    }
                    AnimatedVisibility(
                        visible = nowPlaying != null && !songSelection.active,
                        enter = slideInVertically(animationSpec = tween(Motion.Medium)) { it / 2 } +
                            fadeIn(tween(Motion.Medium)) +
                            expandVertically(expandFrom = Alignment.Bottom),
                        exit = slideOutVertically(animationSpec = tween(Motion.Fast)) { it / 2 } +
                            fadeOut(tween(Motion.Fast)) +
                            shrinkVertically(shrinkTowards = Alignment.Bottom),
                    ) {
                        nowPlaying?.let { track ->
                            MiniPlayer(
                                track = track,
                                isPlaying = isPlaying,
                                positionFraction = if (durationMs > 0L) {
                                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                                controlsOnEnd = onEnd,
                                onToggle = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                                onNext = { controller?.seekToNext() },
                                onOpen = { showNowPlaying = true },
                            )
                        }
                    }
                    CyberBottomBar(
                        current = current,
                        menuOnStart = !onEnd,
                        onSelect = { destination ->
                            navigate(destination)
                            drawerOpen = false
                        },
                        onMenu = { drawerOpen = true },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(searchNestedScroll),
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = tween(Motion.Medium)) { it / 10 } +
                            fadeIn(tween(Motion.Medium))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(Motion.Fast)) { -it / 12 } +
                                fadeOut(tween(Motion.Fast)),
                        )
                    },
                    label = "destination",
                ) { destination ->
                    when (destination) {
                        Destination.Dashboard -> DashboardScreen(
                            tracks = tracks,
                            isScanning = isScanning,
                            onRescan = { refreshToken++ },
                            onFreeSpace = { showFreeSpace = true },
                            onOpenSongs = { navigate(Destination.Songs) },
                            onOpenFiles = { navigate(Destination.Files) },
                            onOpenAlbums = { navigate(Destination.Albums) },
                            onOpenArtists = { navigate(Destination.Artists) },
                            onOpenSettings = { navigate(Destination.Settings) },
                        )
                        Destination.Songs -> SongsScreen(
                            tracks = tracks,
                            addedAt = extras.addedAt,
                            currentTrackUri = nowPlaying?.uri?.toString(),
                            isPlaying = isPlaying,
                            hasLibraries = settings.libraries.isNotEmpty(),
                            onPlay = { track, queue -> play(track, queue) },
                            onShuffleAll = { shuffleEverything() },
                            onAddToPlaylist = { openAddToPlaylist(it) },
                            onEditSong = { track, action -> editSong(track, action) },
                            onCompress = { openCompress(listOf(it)) },
                            onLike = { extras.like(it.uri.toString()) },
                            onDislike = { dislike(it) },
                            onDelete = { deleteTarget = it },
                        )
                        Destination.Files -> FilesScreen(
                            tracks = tracks,
                            currentTrackUri = nowPlaying?.uri?.toString(),
                            hasLibraries = settings.libraries.isNotEmpty(),
                            starredFolders = extras.starredFolders,
                            addedAt = extras.addedAt,
                            onToggleFolderStar = { extras.toggleFolderStarred(it) },
                            onReorderFolder = { path, folderTracks ->
                                startReorderFolder(path, folderTracks)
                            },
                            onMoveFolder = { source, destination, folderTracks, destinationTracks ->
                                startFolderMove(
                                    sourcePath = source,
                                    destinationPath = destination,
                                    sourceTracks = folderTracks,
                                    destinationTracks = destinationTracks,
                                )
                            },
                            onExplodeFolder = { path, folderTracks ->
                                startFolderExplode(path, folderTracks)
                            },
                            onPlay = { track, queue -> play(track, queue) },
                            onAddToPlaylist = { openAddToPlaylist(it) },
                            onEditSong = { track, action -> editSong(track, action) },
                            onCompress = { openCompress(listOf(it)) },
                            onLike = { extras.like(it.uri.toString()) },
                            onDislike = { dislike(it) },
                            onDelete = { deleteTarget = it },
                            path = filesPath,
                            onPathChange = {
                                songSelection.clear()
                                filesPath = it
                            },
                        )
                        Destination.Albums -> CollectionsBrowserScreen(
                            entries = albumEntries,
                            emptyText = stringResource(R.string.collections_empty),
                            isArtist = false,
                            libraries = settings.libraries,
                            artVersion = artVersion,
                            starred = extras.starredAlbums,
                            onToggleStar = { extras.toggleAlbumStarred(it) },
                            onOpen = { showAlbum(it) },
                        )
                        Destination.Artists -> CollectionsBrowserScreen(
                            entries = artistEntries,
                            emptyText = stringResource(R.string.collections_empty),
                            isArtist = true,
                            libraries = settings.libraries,
                            artVersion = artVersion,
                            starred = extras.starredArtists,
                            onToggleStar = { extras.toggleArtistStarred(it) },
                            onOpen = { showArtist(it) },
                        )
                        Destination.Search -> Unit
                        Destination.Playlists -> PlaylistsDestination(
                            extras = extras,
                            tracks = tracks,
                            currentTrackUri = nowPlaying?.uri?.toString(),
                            onPlayTrack = { track, queue -> play(track, queue) },
                            onPlayAll = { queue, shuffle -> play(queue.first(), queue, shuffle) },
                        )
                        Destination.Settings -> SettingsScreen(
                            settings = settings,
                            onFreeSpace = { showFreeSpace = true },
                        )
                    }
                }

                // Album/artist pages live inside the tab content, so the bottom bar and
                // the mini player stay visible while browsing a collection.
                PageOverlay(page = openAlbum) { albumName ->
                    val albumTracks = extras.albumTracksInOrder(
                        albumName,
                        tracks.orEmpty()
                            .filter { it.album?.equals(albumName, ignoreCase = true) == true },
                    )
                    BackHandler {
                        if (songSelection.active) songSelection.clear() else openAlbum = null
                    }
                    CollectionScreen(
                        kicker = stringResource(R.string.field_album),
                        title = albumName,
                        tracks = albumTracks,
                        currentTrackUri = nowPlaying?.uri?.toString(),
                        onBack = { openAlbum = null },
                        onPlay = { track, queue -> play(track, queue) },
                        onPlayAll = { queue, shuffle -> if (queue.isNotEmpty()) play(queue.first(), queue, shuffle) },
                        onAddToPlaylist = { openAddToPlaylist(it) },
                        onEditSong = { track, action -> editSong(track, action) },
                        onCompress = { openCompress(listOf(it)) },
                        onLike = { extras.like(it.uri.toString()) },
                        onDislike = { dislike(it) },
                        onDelete = { deleteTarget = it },
                        onEdit = { editAlbum = albumName },
                        isStarred = extras.isAlbumStarred(albumName),
                        onToggleStar = { extras.toggleAlbumStarred(albumName) },
                        onReorder = { reordered ->
                            extras.setAlbumOrder(
                                albumName,
                                reordered.map { it.uri.toString() },
                            )
                        },
                    )
                }
                PageOverlay(page = openArtist) { artistName ->
                    val artistTracks = tracks.orEmpty()
                        .filter { it.artist?.equals(artistName, ignoreCase = true) == true }
                        .sortedBy { it.album.orEmpty().lowercase() }
                    BackHandler {
                        if (songSelection.active) songSelection.clear() else openArtist = null
                    }
                    ArtistScreen(
                        artistName = artistName,
                        tracks = artistTracks,
                        currentTrackUri = nowPlaying?.uri?.toString(),
                        onBack = { openArtist = null },
                        onPlay = { track, queue -> play(track, queue) },
                        onPlayAll = { queue, shuffle -> if (queue.isNotEmpty()) play(queue.first(), queue, shuffle) },
                        onAddToPlaylist = { openAddToPlaylist(it) },
                        onEditSong = { track, action -> editSong(track, action) },
                        onCompress = { openCompress(listOf(it)) },
                        onLike = { extras.like(it.uri.toString()) },
                        onDislike = { dislike(it) },
                        onDelete = { deleteTarget = it },
                        onEdit = { editArtist = artistName },
                        isStarred = extras.isArtistStarred(artistName),
                        onToggleStar = { extras.toggleArtistStarred(artistName) },
                    )
                }

                if (searchOpen || searchProgress > 0f) {
                    BackHandler(enabled = searchOpen) { closeSearch() }
                    val overlayBackground = MaterialTheme.colorScheme.background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(10f)
                            .drawWithContent {
                                clipRect(bottom = size.height * searchProgress) {
                                    drawRect(overlayBackground)
                                    this@drawWithContent.drawContent()
                                }
                            },
                    ) {
                        SearchScreen(
                            tracks = tracks,
                            albums = albumEntries,
                            artists = artistEntries,
                            currentTrackUri = nowPlaying?.uri?.toString(),
                            hasLibraries = settings.libraries.isNotEmpty(),
                            onPlay = { track, queue -> play(track, queue) },
                            onAddToPlaylist = { openAddToPlaylist(it) },
                            onEditSong = { track, action -> editSong(track, action) },
                            onCompress = { openCompress(listOf(it)) },
                            onLike = { extras.like(it.uri.toString()) },
                            onDislike = { dislike(it) },
                            onDelete = { deleteTarget = it },
                            onOpenAlbum = {
                                dismissSearchImmediately()
                                showAlbum(it)
                            },
                            onOpenArtist = {
                                dismissSearchImmediately()
                                showArtist(it)
                            },
                            autoFocus = searchOpen,
                        )
                    }
                }

                if (!searchOpen && searchPull > 0.08f) {
                    SearchPullCue(
                        progress = (searchPull / SEARCH_PULL_COMMIT).coerceIn(0f, 1f),
                        release = searchPull >= SEARCH_PULL_COMMIT,
                    )
                }
            }
        }

        // Drawer: scrim fades in, panel slides in from the holding hand's side.
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(tween(Motion.Fast)),
            exit = fadeOut(tween(Motion.Fast)),
        ) {
            Scrim(onClick = { drawerOpen = false })
        }
        // Align with a full-size Box: AnimatedVisibility does not always forward
        // parent-data modifiers, and a drawer on the wrong side is a UX bug.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (onEnd) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(animationSpec = tween(Motion.Medium)) { if (onEnd) it else -it },
                exit = slideOutHorizontally(animationSpec = tween(Motion.Fast)) { if (onEnd) it else -it },
            ) {
                DrawerSheet(
                    current = if (searchOpen) Destination.Search else current,
                    onSelect = {
                        navigate(it)
                        drawerOpen = false
                    },
                )
            }
        }

        // Full player rises over everything; its own drag gesture closes it.
        AnimatedVisibility(
            visible = showNowPlaying && nowPlaying != null,
            enter = slideInVertically(animationSpec = tween(Motion.Slow)) { it } +
                fadeIn(tween(Motion.Medium)),
            exit = slideOutVertically(animationSpec = tween(Motion.Medium)) { it } +
                fadeOut(tween(Motion.Fast)),
        ) {
            nowPlaying?.let { playing ->
                NowPlayingScreen(
                    track = playing,
                    isPlaying = isPlaying,
                    isFavorite = extras.isFavorite(playing.uri.toString()),
                    shuffleEnabled = shuffleEnabled,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onClose = { showNowPlaying = false },
                    onTogglePlay = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                    onPrevious = { controller?.seekToPrevious() },
                    onNext = { controller?.seekToNext() },
                    onSwipeToPrevious = { controller?.seekToPreviousMediaItem() },
                    onSwipeToNext = { controller?.seekToNextMediaItem() },
                    onSeek = { controller?.seekTo(it) },
                    onToggleShuffle = {
                        shuffleEnabled = !shuffleEnabled
                        if (shuffleEnabled) reshuffleRemaining()
                    },
                    onToggleFavorite = { extras.toggleFavorite(playing.uri.toString()) },
                    onEditMetadata = { editingTrack = playing },
                    rating = extras.rating(playing.uri.toString()),
                    onLike = { extras.like(playing.uri.toString()) },
                    onDislike = { dislike(playing) },
                    onDelete = { deleteTarget = playing },
                    onCompress = { openCompress(listOf(playing)) },
                    onGoToFolder = {
                        filesPath = playing.folder.trimStart('/')
                        showNowPlaying = false
                        navigate(Destination.Files)
                    },
                    onGoToAlbum = {
                        showNowPlaying = false
                        showAlbum(playing.album)
                    },
                    onGoToArtist = {
                        showNowPlaying = false
                        showArtist(playing.artist)
                    },
                )
            }
        }

        // Contextual pages slide in over the current tab.
        PageOverlay(page = editAlbum) { albumBeingEdited ->
            val albumTracks = tracks.orEmpty()
                .filter { it.album?.equals(albumBeingEdited, ignoreCase = true) == true }
            BackHandler { editAlbum = null }
            EditCollectionScreen(
                kicker = stringResource(R.string.field_album),
                title = albumBeingEdited,
                isArtist = false,
                libraries = settings.libraries,
                sampleTrackUri = albumTracks.firstOrNull()?.uri,
                isStarred = extras.isAlbumStarred(albumBeingEdited),
                onToggleStar = { extras.toggleAlbumStarred(albumBeingEdited) },
                onClose = { editAlbum = null },
                onSaved = {
                    artVersion++
                    SongArt.invalidate()
                },
            )
        }

        PageOverlay(page = editArtist) { artistBeingEdited ->
            BackHandler { editArtist = null }
            EditCollectionScreen(
                kicker = stringResource(R.string.field_artist),
                title = artistBeingEdited,
                isArtist = true,
                libraries = settings.libraries,
                sampleTrackUri = null,
                isStarred = extras.isArtistStarred(artistBeingEdited),
                onToggleStar = { extras.toggleArtistStarred(artistBeingEdited) },
                onClose = { editArtist = null },
                onSaved = {
                    artVersion++
                    SongArt.invalidate()
                },
            )
        }

        if (showFreeSpace) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FreeUpSpaceScreen(
                    tracks = tracks.orEmpty(),
                    onBack = { showFreeSpace = false },
                    onCompress = { selection -> openCompress(selection) },
                )
            }
        }

        val editing = editingTrack
        if (editing != null) {
            EditMetadataScreen(
                track = editing,
                extras = extras,
                knownArtists = knownArtists,
                knownAlbums = knownAlbums,
                onClose = { editingTrack = null },
                pending = extras.pendingMetadataFor(editing.uri.toString()),
                deferFileWrite = nowPlaying?.uri == editing.uri && isPlaying,
                onSaved = { newUri ->
                    // This path writes the file now, so any older deferred edit for
                    // the same track is superseded.
                    extras.clearPendingMetadata(editing.uri.toString())
                    val editedIsPlaying = nowPlaying?.uri == editing.uri
                    // Rewriting the file under a live reader is what stops playback
                    // silently; reopen it before the rescan swaps the library list.
                    if (editedIsPlaying) {
                        reopenPlayingTrack(editing.uri, newUri, positionMs, isPlaying)
                    }
                    if (newUri != editing.uri) {
                        extras.rekey(editing.uri.toString(), newUri.toString())
                        if (editedIsPlaying) {
                            nowPlaying = nowPlaying?.copy(uri = newUri)
                        }
                    }
                    editingTrack = null
                    refreshToken++
                },
                onDeferredSaved = { metadata, fileName ->
                    queueDeferredMetadata(editing, metadata, fileName)
                    editingTrack = null
                },
                onCompress = { openCompress(listOf(it)) },
                onHearDifference = { target, details ->
                    // Do not play two sources at once: the comparison page owns audio.
                    controller?.pause()
                    compareDetails = details
                    compareTrack = target
                },
            )
        }

        val comparing = compareTrack
        if (comparing != null) {
            HearDifferenceScreen(
                track = comparing,
                details = compareDetails,
                onClose = { compareTrack = null },
                onSaved = { result ->
                    val oldUri = result.originalUri
                    if (result.replaced) {
                        extras.rekey(oldUri.toString(), result.newUri.toString())
                        replacePlayingTrack(comparing, result.newUri)
                    } else {
                        // The provider refused the delete; the new file exists beside
                        // the old one and keeps the same app-side state.
                        extras.copyState(oldUri.toString(), result.newUri.toString())
                    }
                    if (editingTrack?.uri == oldUri) {
                        editingTrack = editingTrack?.copy(
                            uri = result.newUri,
                            sizeBytes = result.newBytes,
                            durationMs = result.durationMs,
                        )
                    }
                    refreshToken++
                },
            )
        }

        BackHandler(enabled = songSelection.active) { songSelection.clear() }

        val pendingDelete = deleteTarget
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = { Text(stringResource(R.string.action_delete_file)) },
                text = { Text(stringResource(R.string.delete_song_confirm, pendingDelete.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        deleteTrackFile(pendingDelete)
                    }) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (deleteFailed) {
            AlertDialog(
                onDismissRequest = { deleteFailed = false },
                title = { Text(stringResource(R.string.action_delete_file)) },
                text = { Text(stringResource(R.string.delete_song_failed)) },
                confirmButton = {
                    TextButton(onClick = { deleteFailed = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        val runningFolderOp = folderOpKind
        if (runningFolderOp != null) {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        stringResource(
                            if (runningFolderOp == FolderOpKind.Move) {
                                R.string.folder_move
                            } else {
                                R.string.folder_explode
                            },
                        ),
                    )
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(
                                if (runningFolderOp == FolderOpKind.Move) {
                                    R.string.folder_move_running
                                } else {
                                    R.string.folder_explode_running
                                },
                            ),
                        )
                    }
                },
                confirmButton = {},
            )
        }

        val folderMessage = folderOpMessage
        if (runningFolderOp == null && folderMessage != null) {
            AlertDialog(
                onDismissRequest = { folderOpMessage = null },
                title = { Text(stringResource(R.string.files_folder_actions)) },
                text = { Text(folderMessage) },
                confirmButton = {
                    TextButton(onClick = { folderOpMessage = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        val failedMetadataWrite = metadataWriteFailure
        if (failedMetadataWrite != null) {
            AlertDialog(
                onDismissRequest = { metadataWriteFailure = null },
                title = { Text(stringResource(R.string.edit_metadata_title)) },
                text = {
                    Text(
                        stringResource(
                            when (failedMetadataWrite) {
                                MetadataWriteFailure.Rename -> R.string.metadata_rename_failed
                                MetadataWriteFailure.NoWritePermission ->
                                    R.string.metadata_no_write_permission
                                MetadataWriteFailure.Write -> R.string.metadata_write_failed
                            },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { metadataWriteFailure = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        val target = dialogTrack
        if (showPlaylistPicker && target != null) {
            AddToPlaylistDialog(
                playlists = extras.playlists,
                onDismiss = { showPlaylistPicker = false },
                onPick = { playlist ->
                    extras.addToPlaylist(playlist.id, target.uri.toString())
                    showPlaylistPicker = false
                },
                onCreateNew = {
                    showPlaylistPicker = false
                    showNewPlaylist = true
                },
            )
        }

        if (showNewPlaylist && target != null) {
            NewPlaylistDialog(
                onDismiss = { showNewPlaylist = false },
                onCreate = { name ->
                    val playlist = extras.createPlaylist(name)
                    extras.addToPlaylist(playlist.id, target.uri.toString())
                    showNewPlaylist = false
                },
            )
        }

        if (showTagEditor && target != null) {
            // The same frequent tags the full editor offers, excluding those already on
            // this song; adding or removing one updates the list immediately.
            val suggestions = remember(target.uri, extras.tags) {
                extras.frequentTags(exclude = extras.tagsFor(target.uri.toString()), limit = 8)
            }
            ManageTagsDialog(
                tags = extras.tagsFor(target.uri.toString()),
                suggestions = suggestions,
                onAdd = { extras.addTag(target.uri.toString(), it) },
                onRemove = { extras.removeTag(target.uri.toString(), it) },
                onDismiss = { showTagEditor = false },
            )
        }

        bulkTitleTarget?.let { target ->
            BulkTextFieldDialog(
                title = stringResource(R.string.action_change_title),
                label = stringResource(R.string.field_title),
                supporting = pluralStringResource(R.plurals.bulk_apply_to, target.size, target.size),
                initial = commonValue(target) { it.title },
                onDismiss = { bulkTitleTarget = null },
                onConfirm = { value ->
                    bulkTitleTarget = null
                    songSelection.clear()
                    setBulkMetadata(target, title = value)
                },
            )
        }

        bulkArtistTarget?.let { target ->
            BulkTextFieldDialog(
                title = stringResource(R.string.bulk_set_artist_title),
                label = stringResource(R.string.field_artist),
                supporting = pluralStringResource(R.plurals.bulk_apply_to, target.size, target.size),
                initial = commonValue(target) { it.artist },
                suggestions = knownArtists,
                onDismiss = { bulkArtistTarget = null },
                onConfirm = { value ->
                    bulkArtistTarget = null
                    songSelection.clear()
                    setBulkMetadata(target, artist = value)
                },
            )
        }

        bulkAlbumTarget?.let { target ->
            BulkTextFieldDialog(
                title = stringResource(R.string.bulk_set_album_title),
                label = stringResource(R.string.field_album),
                supporting = pluralStringResource(R.plurals.bulk_apply_to, target.size, target.size),
                initial = commonValue(target) { it.album },
                suggestions = knownAlbums,
                onDismiss = { bulkAlbumTarget = null },
                onConfirm = { value ->
                    bulkAlbumTarget = null
                    songSelection.clear()
                    setBulkMetadata(target, album = value)
                },
            )
        }

        bulkTagsTarget?.let { target ->
            val commonTags = remember(target, extras.tags) {
                val lists = target.map { extras.tagsFor(it.uri.toString()) }
                lists.firstOrNull()?.filter { tag ->
                    lists.all { list -> list.any { it.equals(tag, ignoreCase = true) } }
                }.orEmpty()
            }
            BulkTagsDialog(
                title = pluralStringResource(R.plurals.bulk_tags_title, target.size, target.size),
                tags = commonTags,
                note = pluralStringResource(R.plurals.bulk_tags_note, target.size, target.size),
                onAdd = { tag -> target.forEach { extras.addTag(it.uri.toString(), tag) } },
                onRemove = { tag -> target.forEach { extras.removeTag(it.uri.toString(), tag) } },
                onDismiss = {
                    bulkTagsTarget = null
                    songSelection.clear()
                },
            )
        }

        val bulkPlaylist = bulkPlaylistTarget
        if (showBulkPlaylistPicker && bulkPlaylist != null) {
            AddToPlaylistDialog(
                playlists = extras.playlists,
                onDismiss = {
                    showBulkPlaylistPicker = false
                    bulkPlaylistTarget = null
                },
                onPick = { playlist ->
                    bulkPlaylist.forEach { extras.addToPlaylist(playlist.id, it.uri.toString()) }
                    showBulkPlaylistPicker = false
                    bulkPlaylistTarget = null
                    songSelection.clear()
                },
                onCreateNew = {
                    showBulkPlaylistPicker = false
                    showNewBulkPlaylist = true
                },
            )
        }

        if (showNewBulkPlaylist && bulkPlaylist != null) {
            NewPlaylistDialog(
                onDismiss = {
                    showNewBulkPlaylist = false
                    bulkPlaylistTarget = null
                },
                onCreate = { name ->
                    val playlist = extras.createPlaylist(name)
                    bulkPlaylist.forEach { extras.addToPlaylist(playlist.id, it.uri.toString()) }
                    showNewBulkPlaylist = false
                    bulkPlaylistTarget = null
                    songSelection.clear()
                },
            )
        }

        bulkCompressTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { bulkCompressTarget = null },
                title = { Text(stringResource(R.string.action_compress)) },
                text = {
                    Text(pluralStringResource(R.plurals.bulk_compress_confirm, target.size, target.size))
                },
                confirmButton = {
                    TextButton(onClick = {
                        bulkCompressTarget = null
                        songSelection.clear()
                        openCompress(target)
                    }) {
                        Text(stringResource(R.string.action_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bulkCompressTarget = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        bulkDeleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { bulkDeleteTarget = null },
                title = { Text(stringResource(R.string.action_delete_file)) },
                text = {
                    Text(pluralStringResource(R.plurals.bulk_delete_confirm, target.size, target.size))
                },
                confirmButton = {
                    TextButton(onClick = {
                        bulkDeleteTarget = null
                        songSelection.clear()
                        deleteTracks(target)
                    }) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bulkDeleteTarget = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (bulkEditRunning) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.bulk_updating_title)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(
                                R.string.bulk_updating_progress,
                                bulkEditDone,
                                bulkEditTotal,
                            ),
                        )
                    }
                },
                confirmButton = {},
            )
        }

        bulkEditMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { bulkEditMessage = null },
                title = { Text(stringResource(R.string.bulk_result_title)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { bulkEditMessage = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        compressTracks?.let { selection ->
            CompressSheet(
                tracks = selection,
                state = compressState,
                onDismiss = { closeCompress() },
                onStart = { bitrateKbps, keepOriginal ->
                    startCompression(bitrateKbps, keepOriginal)
                },
                onCancel = { compressJob?.cancel() },
            )
        }

        reorderState?.let { state ->
            ReorderDialog(
                state = state,
                onDismiss = { reorderState = null },
            )
        }
        }
    }
}

/**
 * A top-level page that lives on top of the tabs (album, artist, editor).
 * AnimatedContent keeps the outgoing page composed, so the exit actually animates.
 */
@Composable
private fun <T : Any> PageOverlay(
    page: T?,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (slideInHorizontally(animationSpec = tween(Motion.Medium)) { it / 4 } +
                fadeIn(tween(Motion.Medium))).togetherWith(
                slideOutHorizontally(animationSpec = tween(Motion.Fast)) { it / 5 } +
                    fadeOut(tween(Motion.Fast)),
            )
        },
        label = "page-overlay",
    ) { target ->
        if (target != null) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content(target)
            }
        }
    }
}

/** Header: menu, contextual readout, and a live indicator while audio runs. */
/**
 * The pre-search half of the pull gesture: a compact search bar slides in and fills as
 * the user pulls, then gives way to the real Search overlay once the preview threshold
 * is crossed.
 */
@Composable
private fun SearchPullCue(progress: Float, release: Boolean) {
    val fill = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .width((220 + 90 * fill).dp)
                .graphicsLayer {
                    alpha = (fill * 3f).coerceIn(0f, 1f)
                    translationY = (1f - fill) * -8.dp.toPx()
                }
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (release) R.string.search_pull_release else R.string.search_pull_hint,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fill)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun CyberTopBar(
    title: String,
    isPlaying: Boolean,
    hasTrack: Boolean,
    isSearch: Boolean,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu, modifier = Modifier.size(52.dp)) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.menu_open),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasTrack) {
                EqualizerBars(
                    playing = isPlaying,
                    color = MaterialTheme.colorScheme.primary,
                    barCount = 4,
                    barWidth = 2.5.dp,
                    height = 16.dp,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            // On the search screen the same slot closes it and returns to the tab
            // the user came from, so Search never becomes a dead end.
            IconButton(
                onClick = if (isSearch) onCloseSearch else onSearch,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = if (isSearch) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = stringResource(
                        if (isSearch) R.string.action_close else R.string.nav_search,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
    }
}

/** Bottom navigation: big targets, one glowing pill for the active tab. */
@Composable
private fun CyberBottomBar(
    current: Destination,
    menuOnStart: Boolean,
    onSelect: (Destination) -> Unit,
    onMenu: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.outlineVariant,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                if (menuOnStart) {
                    NavItem(
                        icon = Icons.Filled.Menu,
                        label = stringResource(R.string.nav_menu),
                        selected = false,
                        onClick = onMenu,
                    )
                }
                // The three main destinations mirror with the holding hand. Right:
                // Dashboard, Playlists, Songs, Menu. Left: Menu, Songs, Playlists,
                // Dashboard.
                val mainDestinations = if (menuOnStart) {
                    listOf(Destination.Songs, Destination.Playlists, Destination.Dashboard)
                } else {
                    listOf(Destination.Dashboard, Destination.Playlists, Destination.Songs)
                }
                mainDestinations.forEach { destination ->
                    NavItem(
                        icon = destination.icon,
                        label = stringResource(destination.labelRes),
                        selected = current == destination,
                        onClick = { onSelect(destination) },
                    )
                }
                if (!menuOnStart) {
                    NavItem(
                        icon = Icons.Filled.Menu,
                        label = stringResource(R.string.nav_menu),
                        selected = false,
                        onClick = onMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(Motion.Fast),
        label = "nav-tint",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = tween(Motion.Medium),
        label = "nav-pill",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "nav-scale",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .cyberClick(pressedScale = 0.94f, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .width(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                tint = tint,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 0.2.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

/** Compact player above the nav bar: art, title, progress, play and next. */
@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionFraction: Float,
    controlsOnEnd: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = positionFraction,
        animationSpec = tween(480),
        label = "mini-progress",
    )
    NeonPanel(
        container = if (isPlaying) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        corner = 20.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .offset { IntOffset(0, drag.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag < -60.dp.toPx()) onOpen()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, amount ->
                    drag = (drag + amount).coerceIn(-160.dp.toPx(), 8.dp.toPx())
                }
            },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp)
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!controlsOnEnd) {
                    MiniTransport(isPlaying = isPlaying, onToggle = onToggle, onNext = onNext)
                }
                SongCover(track = track, side = 48.dp, playing = isPlaying)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist?.takeIf { it.isNotBlank() }
                            ?: track.album.orEmpty().ifBlank { stringResource(R.string.no_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                if (controlsOnEnd) {
                    MiniTransport(isPlaying = isPlaying, onToggle = onToggle, onNext = onNext)
                }
            }
            // Progress lives at the bottom edge of the card, like a status LED bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun MiniTransport(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
        PlayPauseGlyph(
            isPlaying = isPlaying,
            contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
            tint = MaterialTheme.colorScheme.primary,
            barWidth = 4.dp,
            barHeight = 17.dp,
            gap = 4.dp,
            iconSize = 28.dp,
        )
    }
    IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = stringResource(R.string.action_next),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun Scrim(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun DrawerSheet(
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            SectionKicker(text = stringResource(R.string.drawer_subtitle))
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "GRID // LOCAL",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(18.dp))
            CyberDivider()
            Spacer(Modifier.height(10.dp))

            Destination.entries.forEach { destination ->
                    DrawerRow(
                        destination = destination,
                        selected = current == destination,
                        onClick = { onSelect(destination) },
                    )
                }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.drawer_subtitle).uppercase() + " · 0.1",
                modifier = Modifier.padding(start = 12.dp, bottom = 14.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun DrawerRow(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .cyberClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * Android 13+ gates media notifications behind a runtime permission. Asking once on
 * launch means the foreground notification is already allowed by the time the first
 * song starts, which is the moment MediaSessionService needs to post it.
 */
@Composable
private fun RequestNotificationPermission(settings: AppSettings) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // If declined, Settings -> Playback notifications opens the system page.
    }
    LaunchedEffect(settings.notificationPromptShown) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !settings.notificationPromptShown) {
            settings.markNotificationPromptShown()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Connects to the playback service. Null for the first frame or two while binding. */
@Composable
private fun rememberPlaybackController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { MediaController.releaseFuture(future) }
    }
    return controller
}

/**
 * How much of a track has to play before it counts as heard: ten seconds, or half of a
 * track shorter than that. A quick skip still leaves the song in "Unheard songs".
 */
private fun listenedThresholdMs(durationMs: Long): Long =
    if (durationMs <= 0L) 10_000L else minOf(10_000L, durationMs / 2)

/**
 * Queue items carry the song's identity, not just its URI: the notification and the
 * lock screen read MediaMetadata, so a bare item would show the app name with no title
 * or artist. Metadata survives URI changes through MediaItem.buildUpon().
 */
private fun Track.toMediaItem(target: Uri = uri): MediaItem {
    val id = target.toString()
    return MediaItem.Builder()
        .setUri(target)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title.ifBlank { target.lastPathSegment.orEmpty() })
                .setArtist(artist)
                .setAlbumTitle(album)
                .build(),
        )
        .build()
}

private enum class FolderOpKind { Move, Explode }
