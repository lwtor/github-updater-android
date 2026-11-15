package dev.veneranative.updater

/**
 * Everything an app must decide. One instance describes one update source.
 *
 * @property owner GitHub user or organisation that publishes the releases.
 * @property repository GitHub repository whose latest release is the update source.
 * @property fileProviderAuthority must match the authority merged from the library manifest.
 *   Default derivation: `packageName + ".updater"`.
 * @property scope namespaces every piece of on-disk state the SDK owns (cache directory,
 *   SharedPreferences, notification channel and the unique work name), so one app can host several
 *   independent update sources without them colliding.
 * @property versionResolver maps a release to the Android versionCode it competes with.
 * @property strings user-visible text; the SDK ships Chinese defaults, override them per app.
 */
data class UpdaterConfig(
    val owner: String,
    val repository: String,
    /** Must match the authority merged from the library manifest. Default derivation: packageName + ".updater". */
    val fileProviderAuthority: String,
    val scope: String = "self_update",
    val versionResolver: ReleaseVersionResolver = ReleaseVersionResolver.AssetNameSuffix(),
    val strings: UpdaterStrings = UpdaterStrings(),
)

/** User-visible strings; the SDK ships Chinese defaults, override them per app. */
data class UpdaterStrings(
    val channelName: String = "应用更新",
    val channelDescription: String = "显示应用内新版本 APK 的下载进度",
    val downloadTitle: String = "正在下载新版本",
    /** `String.format` pattern receiving the integer percentage, e.g. "已下载 %d%%". */
    val progressFormat: String = "已下载 %d%%",
)

/** Maps a release to the Android versionCode it competes with. */
fun interface ReleaseVersionResolver {
    fun versionCodeOf(releaseTag: String, apkAssetName: String): Long?

    /**
     * Reads the trailing digits of an asset name: `app-v1.9.3-1009003.apk` -> 1009003.
     *
     * This is the primary scheme because the asset name is produced by the release tooling and
     * therefore always carries the exact versionCode that was built.
     *
     * @param separator character in front of the versionCode; the part after the last occurrence
     *   must parse as a `Long`.
     */
    class AssetNameSuffix(private val separator: Char = '-') : ReleaseVersionResolver {
        override fun versionCodeOf(releaseTag: String, apkAssetName: String): Long? {
            val stem = apkAssetName.substringBeforeLast('.', missingDelimiterValue = "").trim()
            if (stem.isEmpty()) return null
            return stem.substringAfterLast(separator).toLongOrNull()
        }
    }

    /**
     * Parses `v1.9.3` as `major * majorMultiplier + minor * minorMultiplier + patch`.
     *
     * The defaults reproduce the `1.9.3 -> 1_009_003` scheme: three digits are reserved per field,
     * so each component must stay within `0..999` for the mapping to remain monotonic.
     */
    class TagScheme(
        private val majorMultiplier: Long = 1_000_000L,
        private val minorMultiplier: Long = 1_000L,
    ) : ReleaseVersionResolver {
        override fun versionCodeOf(releaseTag: String, apkAssetName: String): Long? {
            val segments = releaseTag.trim()
                .removePrefix("v")
                .removePrefix("V")
                .split('.')
            if (segments.size !in 2..3) return null
            val major = segments[0].toIntOrNull() ?: return null
            val minor = segments.getOrNull(1)?.toIntOrNull() ?: return null
            val patch = segments.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            if (major < 0 || minor < 0 || patch < 0) return null
            if (major > 999 || minor > 999 || patch > 999) return null
            return major * majorMultiplier + minor * minorMultiplier + patch
        }
    }

    /** Tries [primary] and falls back to [fallback] when it returns null. */
    class Or(
        private val primary: ReleaseVersionResolver,
        private val fallback: ReleaseVersionResolver,
    ) : ReleaseVersionResolver {
        override fun versionCodeOf(releaseTag: String, apkAssetName: String): Long? =
            primary.versionCodeOf(releaseTag, apkAssetName)
                ?: fallback.versionCodeOf(releaseTag, apkAssetName)
    }
}
