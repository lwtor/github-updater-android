package dev.veneranative.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

/**
 * Downloads a release APK in the foreground so the transfer survives the user leaving the screen
 * or locking the device.
 *
 * The worker owns no update state: it receives the asset URL, target name, scope and the
 * user-visible strings as input, streams the bytes through [ApkDownloader] (which resumes a
 * `.part` on the next attempt), reports progress via `setProgress`, and returns the finished
 * file's absolute path in its output data.
 *
 * Public because WorkManager instantiates it by name from persisted state; see
 * `consumer-rules.pro`, which keeps this constructor in minified builds.
 */
class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(INPUT_DOWNLOAD_URL)
        val targetName = inputData.getString(INPUT_TARGET_NAME)
        if (downloadUrl.isNullOrBlank() || targetName.isNullOrBlank()) {
            return Result.failure(workDataOf(OUTPUT_ERROR to "missing download_url or target_name"))
        }
        val scope = inputData.getString(INPUT_SCOPE) ?: DEFAULT_SCOPE

        val notifier = UpdateNotifier(applicationContext, scope, stringsFromInput())
        val downloader = ApkDownloader(WorkerHttp.resolve())
        val target = File(
            updaterCacheDir(applicationContext, scope),
            ReleaseSelection.sanitizeAssetFileName(targetName),
        )

        setForeground(notifier.foregroundInfo(0f))
        return try {
            downloader.download(downloadUrl, target) { progress ->
                val fraction = progress.fraction ?: 0f
                setProgressAsync(workDataOf(PROGRESS_KEY to fraction))
            }
            Result.success(workDataOf(OUTPUT_FILE_PATH to target.absolutePath))
        } catch (failure: Exception) {
            // Cancellation of the worker is reported by WorkManager; a failed download is a failure
            // result, not a retry — the caller re-enqueues with REPLACE when the user taps again.
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            Result.failure(workDataOf(OUTPUT_ERROR to (failure.message ?: "download failed")))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val scope = inputData.getString(INPUT_SCOPE) ?: DEFAULT_SCOPE
        return UpdateNotifier(applicationContext, scope, stringsFromInput()).foregroundInfo(0f)
    }

    private fun stringsFromInput(): UpdaterStrings {
        val defaults = UpdaterStrings()
        return UpdaterStrings(
            channelName = inputData.getString(INPUT_CHANNEL_NAME) ?: defaults.channelName,
            channelDescription = inputData.getString(INPUT_CHANNEL_DESCRIPTION) ?: defaults.channelDescription,
            downloadTitle = inputData.getString(INPUT_DOWNLOAD_TITLE) ?: defaults.downloadTitle,
            progressFormat = inputData.getString(INPUT_PROGRESS_FORMAT) ?: defaults.progressFormat,
        )
    }

    companion object {
        const val INPUT_DOWNLOAD_URL: String = "download_url"
        const val INPUT_TARGET_NAME: String = "target_name"
        const val INPUT_SCOPE: String = "scope"
        const val INPUT_CHANNEL_NAME: String = "channel_name"
        const val INPUT_CHANNEL_DESCRIPTION: String = "channel_description"
        const val INPUT_DOWNLOAD_TITLE: String = "download_title"
        const val INPUT_PROGRESS_FORMAT: String = "progress_format"
        const val OUTPUT_FILE_PATH: String = "file_path"
        const val OUTPUT_ERROR: String = "error"
        const val PROGRESS_KEY: String = "progress"

        private const val DEFAULT_SCOPE = "self_update"
    }
}
