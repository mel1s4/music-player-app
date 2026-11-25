package dev.melisa.musicplayer.compression

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.melisa.musicplayer.MainActivity
import dev.melisa.musicplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps a compression run alive when the app is not.
 *
 * A plain coroutine in the activity keeps working while the screen is hidden, but
 * Android is free to reclaim a cached process that is burning CPU. A foreground
 * service puts the run in the same bracket as playback: the process is protected,
 * the user sees progress and can stop it from the shade, and the job is no longer
 * tied to a composition that rotation can throw away.
 *
 * The queue itself belongs to [CompressionRunner]; this service only starts the
 * run, mirrors it into a notification and stops itself once the run is over.
 */
class CompressService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private var inForeground = false
    private var wakeLock: PowerManager.WakeLock? = null
    // Encoder progress arrives dozens of times a second; the shade only needs the
    // percentage and the line to change.
    private var lastPercent = -1
    private var lastText: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
        scope.launch {
            CompressionRunner.state.collect { state ->
                when (state.phase) {
                    CompressPhase.Running -> showProgress(state)
                    CompressPhase.Done -> showResult(state)
                    CompressPhase.Idle -> stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            CompressionRunner.cancel()
            // The action can arrive after the run ended (or wake this service just to
            // deliver it); do not leave an idle service behind.
            if (!CompressionRunner.isRunning) stopSelf()
            return START_NOT_STICKY
        }
        val state = CompressionRunner.state.value
        // startForegroundService demands a foreground notification even if the run
        // ended between the call and this callback.
        showProgress(state)
        if (state.phase != CompressPhase.Running) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android 15 caps long-running data processing at six hours and crashes the app
     * when the cap is hit unless the service handles it. Stop cleanly and let the
     * sheet show the partial result.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "compression service timed out")
        CompressionRunner.cancel()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun showProgress(state: CompressJobState) {
        val percent = (state.progress * 100).roundToInt().coerceIn(0, 100)
        val text = progressText(state)
        if (inForeground && percent == lastPercent && text == lastText) return
        lastPercent = percent
        lastText = text
        val notification = baseBuilder(withCancel = true)
            .setContentTitle(getString(R.string.compress_notification_title))
            .setContentText(text)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (!inForeground) {
            inForeground = true
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType())
        } else {
            notifications.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun showResult(state: CompressJobState) {
        val text = resultText(state)
        val notification = baseBuilder(withCancel = false)
            .setContentTitle(getString(R.string.compress_done_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        inForeground = false
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        // The run is over, but the summary is worth keeping in the shade; the
        // notification outlives the service and opens the app where the sheet shows it.
        notifications.notify(NOTIFICATION_ID, notification)
        releaseWakeLock()
        stopSelf()
    }

    private fun baseBuilder(withCancel: Boolean): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_compress_notification)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (withCancel) {
            builder.addAction(0, getString(R.string.compress_cancel_job), cancelIntent())
        }
        return builder
    }

    private fun progressText(state: CompressJobState): String {
        val title = state.currentTitle.orEmpty()
        return if (state.total > 1) {
            getString(
                R.string.compress_progress_batch,
                (state.completed + 1).coerceAtMost(state.total),
                state.total,
                title,
            )
        } else {
            getString(
                R.string.compress_progress,
                (state.progress * 100).roundToInt().coerceIn(0, 100),
                title,
            )
        }
    }

    private fun resultText(state: CompressJobState): String {
        val summary = when {
            state.cancelled && state.freedBytes > 0L ->
                getString(R.string.compress_done_cancelled, formatSize(state.freedBytes))
            state.cancelled -> getString(R.string.compress_done_cancelled_zero)
            state.freedBytes > 0L -> getString(R.string.compress_done_freed, formatSize(state.freedBytes))
            state.kept > 0 -> getString(R.string.compress_done_kept_copy)
            else -> getString(R.string.compress_done_title)
        }
        return if (state.failed > 0) {
            summary + "\n" + getString(R.string.compress_done_failures, state.failed)
        } else {
            summary
        }
    }

    private fun formatSize(bytes: Long): String =
        Formatter.formatShortFileSize(this, bytes)

    @SuppressLint("InlinedApi") // The constants are inlined; the type is only read on its own API level.
    private fun serviceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15 added a type for exactly this: transcoding media.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.compress_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.compress_channel_description)
            setShowBadge(false)
        }
        notifications.createNotificationChannel(channel)
    }

    /**
     * A foreground service is not by itself a guarantee that the CPU stays awake once
     * the screen turns off; a partial wake lock is. The timeout matches the system's
     * own budget for long-running foreground work.
     */
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, CompressService::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "compression"
        private const val NOTIFICATION_ID = 0x434F4D50
        private const val ACTION_CANCEL = "dev.melisa.musicplayer.action.CANCEL_COMPRESSION"
        private const val WAKE_LOCK_TAG = "MusicPlayer:Compression"
        private const val TAG = "CompressService"

        /** The longest a single run may hold the CPU awake. */
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CompressService::class.java),
            )
        }
    }
}
