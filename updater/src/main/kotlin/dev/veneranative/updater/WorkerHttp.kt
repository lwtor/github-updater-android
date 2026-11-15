package dev.veneranative.updater

import okhttp3.OkHttpClient

/**
 * The process-wide handle the download worker uses to reach the HTTP client the app configured.
 *
 * The worker cannot receive the client through its constructor: WorkManager reconstructs workers
 * reflectively from a persisted class name and a `(Context, WorkerParameters)` signature, so there
 * is no channel through which an object graph could be injected. [Updater.create] therefore stores
 * the client here, and the worker reads it back. When the process was killed and the worker is
 * restarted before the app ran `create` again, [resolve] falls back to a plain client so the
 * download still completes (it just loses the app's proxy/DNS configuration for that run).
 */
internal object WorkerHttp {
    @Volatile
    var client: OkHttpClient? = null

    fun resolve(): OkHttpClient = client ?: OkHttpClient()
}
