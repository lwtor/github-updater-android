package dev.veneranative.updater

/** Base type for every failure the SDK reports to the caller. */
open class UpdaterException(message: String) : java.io.IOException(message)

/** The GitHub Releases API answered with a non-success status. */
class ReleaseApiException(val statusCode: Int, message: String) : UpdaterException(message)

/** A download attempt failed: connection, HTTP status, truncated transfer or a non-APK body. */
class DownloadException(message: String) : UpdaterException(message)
