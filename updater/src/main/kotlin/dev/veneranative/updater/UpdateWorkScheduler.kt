package dev.veneranative.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * The single door the self-update download is started and stopped through.
 *
 * One unique work per scope (`"$scope.download"`): an update download is a one-off task, and
 * `REPLACE` means tapping "update" again restarts the download from scratch rather than queueing a
 * second worker behind the first. The resumable `.part` handling in [ApkDownloader] makes a restart
 * cheap anyway.
 *
 * The scope and the user-visible strings travel in the work's input data rather than through a
 * process-global. WorkManager persists input data, so a worker that runs after the process died
 * still knows which directory to write into and how to label its notification.
 */
internal class UpdateWorkScheduler(val workName: String) {

    fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun start(
        context: Context,
        downloadUrl: String,
        targetName: String,
        scope: String,
        strings: UpdaterStrings,
    ) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.INPUT_DOWNLOAD_URL to downloadUrl,
                    UpdateDownloadWorker.INPUT_TARGET_NAME to targetName,
                    UpdateDownloadWorker.INPUT_SCOPE to scope,
                    UpdateDownloadWorker.INPUT_CHANNEL_NAME to strings.channelName,
                    UpdateDownloadWorker.INPUT_CHANNEL_DESCRIPTION to strings.channelDescription,
                    UpdateDownloadWorker.INPUT_DOWNLOAD_TITLE to strings.downloadTitle,
                    UpdateDownloadWorker.INPUT_PROGRESS_FORMAT to strings.progressFormat,
                ),
            )
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context): Operation =
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName)
}
