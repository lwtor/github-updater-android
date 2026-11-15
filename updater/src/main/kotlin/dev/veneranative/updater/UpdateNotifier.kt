package dev.veneranative.updater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * Builds the self-update download notification and the `ForegroundInfo` that keeps the worker alive.
 *
 * The update download runs in the foreground so Android does not kill it once the user leaves the
 * screen or locks the device. The notification is both the legal requirement for a foreground
 * service and the only progress the user sees while the download continues in the background.
 *
 * Channel and notification ids are scope-derived: an app that checks two repositories would
 * otherwise share one progress bar between two unrelated downloads.
 */
internal class UpdateNotifier(
    private val context: Context,
    private val scope: String,
    private val strings: UpdaterStrings,
) {
    private val channelId: String get() = "$scope.updates"

    /**
     * Android identifies a notification by an `Int`; the scope already uniquely identifies the
     * update source, so hashing it keeps the same id across process restarts without a mutable
     * counter. A hash collision between two scopes would at worst let one download replace the
     * other's progress notification, which is still a per-channel visual glitch rather than
     * corrupted state — the channels differ, so both remain dismissible and correct on their own.
     */
    private val notificationId: Int get() = scope.hashCode()

    /** Creating an existing channel is a no-op, so this is safe to call before every update. */
    fun ensureChannel() {
        val channel = NotificationChannel(
            channelId,
            strings.channelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = strings.channelDescription
            setShowBadge(false)
        }
        val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        system.createNotificationChannel(channel)
    }

    fun notification(progress: Float): Notification {
        ensureChannel()
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(strings.downloadTitle)
            .setContentText(String.format(strings.progressFormat, percent))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, percent == 0)
            .build()
    }

    /**
     * The worker's foreground promise. The service type is mandatory from API 34; downloading is a
     * data transfer, hence `dataSync`.
     */
    fun foregroundInfo(progress: Float): ForegroundInfo = ForegroundInfo(
        notificationId,
        notification(progress),
        foregroundServiceType(),
    )

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
}
