package dev.veneranative.updater.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import dev.veneranative.updater.InstallResult
import dev.veneranative.updater.ReleaseAsset
import dev.veneranative.updater.ReleaseVersionResolver
import dev.veneranative.updater.UpdateCheck
import dev.veneranative.updater.UpdateStatus
import dev.veneranative.updater.Updater
import dev.veneranative.updater.UpdaterConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The whole integration: build one [Updater] from one [UpdaterConfig], then drive
 * check / download / install from the UI. No permission, no FileProvider, no service declaration
 * lives in this app — the library manifest supplies all of it.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val updater = Updater.create(
        this,
        UpdaterConfig(
            owner = "lwtor",
            repository = "venera-native",
            fileProviderAuthority = "$packageName.updater",
            versionResolver = ReleaseVersionResolver.Or(
                ReleaseVersionResolver.AssetNameSuffix(),
                ReleaseVersionResolver.TagScheme(),
            ),
        ),
    )

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var installButton: Button

    private var availableAsset: ReleaseAsset? = null
    private var downloadedApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val versionText: TextView = findViewById(R.id.version_text)
        statusText = findViewById(R.id.status_text)
        logText = findViewById(R.id.log_text)
        progressBar = findViewById(R.id.progress_bar)
        downloadButton = findViewById(R.id.download_button)
        installButton = findViewById(R.id.install_button)

        versionText.text = getString(
            R.string.version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )

        findViewById<Button>(R.id.check_button).setOnClickListener { checkForUpdate() }
        downloadButton.setOnClickListener { downloadUpdate() }
        installButton.setOnClickListener { installUpdate() }

        // The SDK's own state, kept in sync by checkAndPrefetch() in a production app.
        scope.launch {
            updater.status.collect { status ->
                when (status) {
                    UpdateStatus.Idle -> Unit
                    is UpdateStatus.UpdateAvailable -> showStatus("发现新版本 code=${status.versionCode}")
                    is UpdateStatus.Downloaded -> {
                        downloadedApk = status.apkFile
                        installButton.isEnabled = true
                        showStatus("已下载 code=${status.versionCode}：${status.apkFile.name}")
                    }
                }
            }
        }
    }

    private fun checkForUpdate() {
        availableAsset = null
        downloadButton.isEnabled = false
        installButton.isEnabled = false
        logText.text = ""
        showStatus("正在检查…")
        scope.launch {
            val check = try {
                updater.check(BuildConfig.VERSION_CODE.toLong())
            } catch (error: Exception) {
                showStatus("检查失败：${error.message}")
                logText.text = error.toString()
                return@launch
            }
            render(check)
        }
    }

    private fun downloadUpdate() {
        val asset = availableAsset ?: return
        downloadButton.isEnabled = false
        showStatus("开始下载 ${asset.name}")
        scope.launch {
            // A foreground download survives leaving this screen; its progress is published by
            // WorkManager, so the observer can run independently of the download coroutine.
            val progressJob = launch {
                updater.observeForegroundProgress().collect { fraction ->
                    val percent = ((fraction ?: 0f) * 100).toInt()
                    progressBar.progress = percent
                    showStatus("下载中 $percent%")
                }
            }
            try {
                val apk = updater.downloadInForeground(asset)
                downloadedApk = apk
                installButton.isEnabled = true
                progressBar.progress = 100
                showStatus("下载完成：${apk.name}（${apk.length() / 1024} KB）")
            } catch (error: Exception) {
                showStatus("下载失败：${error.message}")
            } finally {
                progressJob.cancel()
            }
        }
    }

    private fun installUpdate() {
        val apk = downloadedApk ?: return
        when (val result = updater.install(apk)) {
            InstallResult.Launched -> showStatus("已交给系统安装器，请在系统弹窗中确认")
            is InstallResult.Failure -> showStatus("安装失败：${result.message}")
        }
    }

    private fun render(check: UpdateCheck) {
        when (check) {
            is UpdateCheck.Available -> {
                availableAsset = check.asset
                downloadButton.isEnabled = true
                showStatus("发现新版本 code=${check.versionCode}")
            }
            is UpdateCheck.UpToDate -> showStatus("已是最新版本")
            UpdateCheck.NoInstallableAsset -> showStatus("该发布中没有可安装的 APK")
        }
        logText.text = describe(check)
    }

    /** The last [UpdateCheck], printed in full so the release/asset pick is visible. */
    private fun describe(check: UpdateCheck): String = when (check) {
        is UpdateCheck.UpToDate -> """
            UpdateCheck.UpToDate
            release.tagName = ${check.release.tagName}
            release.name = ${check.release.name ?: "-"}
            release.publishedAt = ${check.release.publishedAt ?: "-"}
            asset.name = ${check.asset.name}
            asset.sizeBytes = ${check.asset.sizeBytes}
            asset.contentType = ${check.asset.contentType ?: "-"}
        """.trimIndent()

        UpdateCheck.NoInstallableAsset -> """
            UpdateCheck.NoInstallableAsset
            the latest release carries no asset this updater can install
        """.trimIndent()

        is UpdateCheck.Available -> """
            UpdateCheck.Available
            versionCode = ${check.versionCode}
            release.tagName = ${check.release.tagName}
            release.name = ${check.release.name ?: "-"}
            release.publishedAt = ${check.release.publishedAt ?: "-"}
            release.notes = ${check.release.notes?.trim() ?: "-"}
            asset.name = ${check.asset.name}
            asset.sizeBytes = ${check.asset.sizeBytes}
            asset.contentType = ${check.asset.contentType ?: "-"}
            asset.downloadUrl = ${check.asset.downloadUrl}
        """.trimIndent()
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
