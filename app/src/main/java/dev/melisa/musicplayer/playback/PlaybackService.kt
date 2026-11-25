package dev.melisa.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.melisa.musicplayer.MainActivity
import dev.melisa.musicplayer.compression.CompressSwap
import dev.melisa.musicplayer.compression.CompressionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the player for the whole app.
 *
 * Because playback lives in a foreground service with a MediaSession, it keeps going
 * when the activity is destroyed, and Android shows transport controls on the lock
 * screen and in the notification shade for free.
 *
 * The UI talks to this through a MediaController, never by holding an ExoPlayer.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged instead of blasting the speaker.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Tapping the notification or the lock-screen controls should land in the app.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        // Compression can replace a file while the activity is gone. If that file is
        // in the queue, point the item at the new document before the player tries to
        // reopen a URI the conversion just deleted.
        serviceScope.launch {
            CompressionRunner.state
                .map { it.swaps }
                .distinctUntilChanged()
                .collect { swaps -> swaps.forEach(::adoptCompressedFile) }
        }
    }

    private fun adoptCompressedFile(swap: CompressSwap) {
        val player = mediaSession?.player ?: return
        val oldId = swap.track.uri.toString()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            if (item.mediaId != oldId) continue
            val current = index == player.currentMediaItemIndex
            val position = if (current) player.currentPosition else 0L
            val wasPlaying = player.isPlaying
            runCatching {
                player.replaceMediaItem(
                    index,
                    item.buildUpon()
                        .setUri(swap.newUri)
                        .setMediaId(swap.newUri.toString())
                        .build(),
                )
                if (current) {
                    player.seekTo(index, position)
                    if (wasPlaying) player.play()
                }
            }.onFailure { Log.w(TAG, "could not adopt compressed file", it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Swiping the app away stops playback only if nothing is actually playing —
     * otherwise audio would keep running with no way to get back to it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PlaybackService"
    }
}
