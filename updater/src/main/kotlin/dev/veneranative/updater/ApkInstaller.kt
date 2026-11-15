package dev.veneranative.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** The outcome of handing an APK to the system package installer. */
sealed interface InstallResult {
    /** The system package installer was launched with the APK. */
    data object Launched : InstallResult

    data class Failure(val message: String) : InstallResult
}

/**
 * Hands the downloaded APK to the system package installer via a FileProvider URI
 * (`ACTION_VIEW` with `application/vnd.android.package-archive`). The system installer shows the
 * confirmation dialog itself, and the user approves each install individually.
 *
 * No install result is reported back; the caller only knows whether the installer was launched
 * successfully.
 */
internal class ApkInstaller(private val authority: String) {

    fun install(context: Context, apk: File): InstallResult {
        if (!apk.isFile) return InstallResult.Failure("the downloaded file is missing")
        val uri = runCatching {
            FileProvider.getUriForFile(context, authority, apk)
        }.getOrElse {
            return InstallResult.Failure("unable to expose the APK: ${it.message}")
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            InstallResult.Launched
        } catch (error: Exception) {
            when (error) {
                is android.content.ActivityNotFoundException ->
                    InstallResult.Failure("no installer is available on this device")
                else -> InstallResult.Failure(error.message ?: "unable to launch the installer")
            }
        }
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
