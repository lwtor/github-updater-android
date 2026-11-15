package dev.veneranative.updater

import java.io.File

/**
 * The single place that decides where a scope's downloads live.
 *
 * Both [Updater] (manual and prefetch downloads) and [UpdateDownloadWorker] (foreground download)
 * resolve their target directory through this helper. They must agree byte-for-byte: the worker
 * writes the APK and the service looks it up again by path, and `updater_file_paths.xml` exposes
 * exactly `cacheDir/updater/`. Keeping one function is what stops the two from drifting apart.
 */
internal fun updaterCacheDir(
    context: android.content.Context,
    scope: String,
): File = File(context.applicationContext.cacheDir, "updater/$scope").apply { mkdirs() }
