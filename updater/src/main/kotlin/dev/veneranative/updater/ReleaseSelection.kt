package dev.veneranative.updater

/**
 * Pure rules that turn a GitHub release into an update decision. No I/O so the decision logic
 * stays JVM-unit-testable.
 */
internal object ReleaseSelection {
    const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
    private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

    /** Picks the APK to install; prefers assets GitHub identifies as Android packages. */
    fun pickUpdateApk(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.asSequence()
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .sortedWith(
                compareByDescending<ReleaseAsset> { it.contentType == APK_CONTENT_TYPE }
                    .thenByDescending { it.sizeBytes },
            )
            .firstOrNull()

    /** An update needs a known versionCode that is strictly larger than the installed one. */
    fun isUpdate(availableVersionCode: Long?, installedVersionCode: Long): Boolean =
        availableVersionCode != null && availableVersionCode > installedVersionCode

    /** Keeps a release asset name from ever escaping the cache directory as a path fragment. */
    fun sanitizeAssetFileName(name: String): String = name.replace(UNSAFE_FILE_CHARS, "_")
}
