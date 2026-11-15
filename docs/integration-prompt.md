# AI 接入提示词

- 适用对象：想让 AI 编码模型把本库接入自己 Android 项目的使用者
- 对应版本：`dev.veneranative:updater:1.0.0`（`updaterSdkVersion`）
- 库的定位：把 Venera Native（漫画阅读器）里的 GitHub Release 自升级能力抽出来，做成可配置的独立库；来源与许可见 `NOTICE`
- 配套材料：本仓库 `README.md`（API 全貌）、`:sample`（可直接运行的最小接入参考）

## 用法

1. 复制下面整个代码块作为提示词发给 AI 模型。
2. 如果模型能读文件，同时把本仓库路径（尤其 `updater/src/main/AndroidManifest.xml`、`updater/src/main/kotlin/dev/veneranative/updater/UpdaterConfig.kt`、`sample/`）给它，准确率会明显更高。
3. 提示词里已写明「库已自带清单合并与 R8 规则」，这是最容易接错的地方，不要删掉那几段。

---

````markdown
# 任务：把 github-updater-android 接入当前 Android 项目

## 你的角色
你是负责落地的 Android 工程师。下面是规格说明，不是建议。标「必须」的都是会在运行期炸的点，不允许简化。
先通读，动手，最后按第 11 节逐条自检。

## 1. 硬前提（不满足就停下来汇报，不要自行降级方案）
- 消费方：JDK 17、compileSdk ≥ 37（AAR 写入 `minCompileSdk` 约束，低于 37 直接构建失败）、minSdk ≥ 26。
- 升级来源必须是**公开 GitHub 仓库**，且最新 release 挂了 APK 资产。这套实现读 `releases/latest`，没有别的数据源。
- 依赖坐标 `dev.veneranative:updater:1.0.0` **不在 Maven Central**。二选一：
  a) 在库仓库执行 `sh gradlew :updater:publishToMavenLocal`，消费方仓库配置里加 `mavenLocal()`；
  b) 把 `updater` 模块源码放进消费方工程（保留 `namespace = "dev.veneranative.updater"`）。
  选 b 时第 5 节的清单合并、第 6 节的 consumer-rules 要一起带过去，别只拷 Kotlin 文件。

## 2. 能力边界（先理解，再接线）
职责：查仓库最新 release → 与已装 versionCode 比较 → 前台下载 APK（`.part` 断点续传、ZIP 头校验）→ 交系统安装器。

**库负责**：GitHub API 调用（匿名）、选 APK 资产、版本号解析策略、WorkManager 前台下载与进度通知、
FileProvider 授权、清单合并、R8 keep 规则、缓存与状态持久化（按 `scope` 隔离）。

**库不负责**（不要试图补）：
- 不做静默安装（需 root / 设备管理员），安装确认弹窗永远由系统弹。
- 不提供任何 UI：对话框、角标、进度条都由消费方自己画。
- 不回报安装结果：`install()` 只告诉你安装器是否拉起成功，用户在系统弹窗点没点取消拿不到。
- 不做多源聚合：一个 `UpdaterConfig` 对应一个更新源。

## 3. 依赖接入（三步）
```kotlin
// settings.gradle.kts —— 本地发布的场景才需要 mavenLocal()
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); mavenLocal() }
}

// app/build.gradle.kts
dependencies { implementation("dev.veneranative:updater:1.0.0") }
```
库通过 `api` 暴露 OkHttp 与 kotlinx-coroutines（`Updater.create` 收 `OkHttpClient`，
`UpdateGateway` 返回 `Flow`），所以这两个类型在消费方编译期可见；但**不要**把这两者降到大版本以下
（OkHttp 4.x 与本库不兼容）。

## 4. 装配：一个配置对象，一个入口
```kotlin
val updater = Updater.create(
    context = applicationContext,
    config = UpdaterConfig(
        owner = "your-org",
        repository = "your-repo",
        fileProviderAuthority = "$packageName.updater",   // 必须等于合并进来的 authority
        scope = "self_update",                            // 见第 5 节
        versionResolver = ReleaseVersionResolver.Or(
            ReleaseVersionResolver.AssetNameSuffix(),
            ReleaseVersionResolver.TagScheme(),
        ),
        strings = UpdaterStrings(channelName = "应用更新", downloadTitle = "正在下载新版本"),
    ),
    httpClient = sharedOkHttpClient,   // 传你的，才能复用连接池与代理/DNS 配置
)
```
`UpdaterConfig` 是唯一必改项，字段含义：
| 字段 | 说明 |
| --- | --- |
| `owner` / `repository` | 发布 release 的 GitHub 仓库 |
| `fileProviderAuthority` | 必须等于清单里合并的 `${applicationId}.updater`。若你要换 authority，就得在消费方清单用 `tools:node="replace"` 覆盖 provider，并让 `UpdaterConfig` 与之一致——**两边不一致 = 点安装时抛异常** |
| `scope` | 派生缓存目录、SharedPreferences、通知渠道、unique work 名。同一个 app 挂两个更新源时**必须**给不同的 scope，否则互相 `REPLACE` 会掐掉对方的下载 |
| `versionResolver` | `AssetNameSuffix()` 读资产名尾部 `-<数字>`（默认）；`TagScheme()` 把 `v1.9.3` 折算成 `1_009_003`；`Or` 先前者后后者 |
| `strings` | 通知渠道名/描述、标题、`String.format` 进度模板（默认中文） |

`Updater.create` 会把 client 放进进程级槽位供 worker 取用（worker 由 WorkManager 反射构造，
拿不到对象图）。进程被系统杀掉后 worker 先于 `create` 重启时会退化成裸 `OkHttpClient()`——
功能仍可用，只是那一次丢了代理配置。这是已知取舍，不要试图「修」它。

## 5. 清单：什么都不要写（最容易错的地方）
库的 `AndroidManifest.xml` 已经合并了全部内容：6 个权限（`INTERNET`、`ACCESS_NETWORK_STATE`、
`REQUEST_INSTALL_PACKAGES`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`POST_NOTIFICATIONS`）、
带 `foregroundServiceType="dataSync"` 的 `SystemForegroundService`、authority 为 `${applicationId}.updater`
的 FileProvider（backed by `@xml/updater_file_paths`）。

因此：
- **不要**再手写这些权限、service 或 provider。重复声明 provider 会触发 manifest merger 冲突（同 authority）
  或装出两个 provider（不同 authority）；后者点安装时必崩。
- FileProvider 已经把 `cacheDir/updater/` 整棵子树暴露出去，覆盖所有 scope。**不要**再自己写 `file_paths.xml`。
- 若消费方原本有自己的 FileProvider，保留它，与库的这个共存即可（authority 不同）。

## 6. R8：不需要你做任何事
库自带 `consumer-rules.pro`，已经 keep 了 `UpdateDownloadWorker` 的 `(Context, WorkerParameters)` 构造器。
**不要**再往消费方 proguard 里重复加 keep；也**不要**删除库的 `consumerProguardFiles` 配置。

## 7. 调用时序
```kotlin
// 检查（可用 BuildConfig.VERSION_CODE，也可用 PackageManager 现读）
when (val check = updater.check(installedVersionCode)) {
    is UpdateCheck.Available     -> showUpdate(check.versionCode, check.asset)
    is UpdateCheck.UpToDate      -> showUpToDate()
    UpdateCheck.NoInstallableAsset -> showNoApk()      // release 里没有可识别的 APK
}

// 前台下载（挂 WorkManager + 进度通知，离开界面/锁屏也能继续）；失败抛 DownloadException
val progressJob = scope.launch {
    updater.observeForegroundProgress().collect { fraction -> render(fraction) }  // 0f..1f，未定长时为 null
}
val apk = updater.downloadInForeground(asset)
progressJob.cancel()
updater.cancelForegroundDownload()      // 用户放弃时调用

// 安装：只表示「安装器是否拉起成功」，不回报安装结果
when (val result = updater.install(apk)) {
    InstallResult.Launched        -> Unit
    is InstallResult.Failure      -> showError(result.message)
}
```
也可以完全不用 WorkManager，在当前协程里直接下（有字节级进度，但退到后台可能被杀）：
`updater.download(asset) { progress -> render(progress.fraction) }`。

## 8. 角标与静默预下载
```kotlin
// 启动时调用一次；永不抛异常（除取消外），失败静默
appScope.launch { updater.checkAndPrefetch(installedVersionCode) }

updater.status.collect { status ->
    when (status) {
        UpdateStatus.Idle -> clearBadge()
        is UpdateStatus.UpdateAvailable -> lightBadge(status.versionCode)      // 有新版本，APK 未就绪
        is UpdateStatus.Downloaded -> showInstallReady(status.apkFile)         // 已下好，可直接 install
    }
}
updater.cachedApk(versionCode)   // 取已下好的 APK；记录失效（文件没了或不是 ZIP）会自动清掉并返回 null
```

## 9. 错误与运行时权限
- 异常层级：`UpdaterException`（基类，继承 `IOException`）→ `ReleaseApiException(statusCode)`（API 非 2xx）、
  `DownloadException`（连接/HTTP/截断/非 APK 响应）。按这三类分别给用户不同文案，不要笼统说「更新失败」。
- API 33+ 通知权限需运行时申请；未授权时下载照常进行，只是**看不到进度通知**。不要因此阻塞更新流程。
- 首次安装若系统未授予「安装未知应用」，安装器会被拦。做法：跳 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`
  授权页，回到前台（`ON_RESUME`）时若已授权且有待装 APK，自动重试 `install()` 一次。

## 10. 发布侧契约（不按这个发，客户端永远收不到更新）
- 公开发布：draft 与 prerelease 不会出现在 `releases/latest`。
- tag 形如 `v1.9.3`；APK 资产名形如 `yourapp-v1.9.3-1009003.apk`，**末尾必须是这次构建真实的 versionCode**。
- 版本竞比规则：release 的 versionCode 必须**严格大于**已装版本才算有更新；相等即视为最新。
- 选资产规则：只看 `*.apk`，优先 contentType 为 `application/vnd.android.package-archive` 的，再按体积取最大。
  同一个 release 别挂多个 APK，除非你确定哪个该被选中。
- 匿名限流 60 次/小时/IP。不要在每次 `onResume` 都调 `check()`；用 `checkAndPrefetch` + `status`。

## 11. 自检清单（逐条执行，别只看编译通过）
1. 合并后的清单里能看到那 6 个权限、带 `dataSync` 的 `SystemForegroundService`、`${applicationId}.updater` 的 provider，
   且**没有**你手写的重复项。`./gradlew :app:processReleaseManifest` 后看 `merged_manifest`。
2. 打 release 变体（开 R8）装真机：检查 → 通知栏出现进度 → 下载完成 → 系统安装弹窗出现。
3. 下载中途杀进程，重进再下：日志出现 `Range` 续传，不从头开始；缓存目录里残留 `.part` 属正常。
4. 断网点更新：抛 `DownloadException`，UI 有可读文案，不留半个 APK。
5. 已是最新版本时：`check()` 返回 `UpToDate`，角标不亮。
6. API 26 设备跑一遍：无前台服务类型相关崩溃。
7. `adb shell dumpsys package <pkg> | grep versionCode` 与 release 资产名尾部数字一致。

## 12. 症状 → 原因对照（排错先查这几条）
| 症状 | 原因 |
| --- | --- |
| 点安装立刻失败 / `Failed to find configured root` | `fileProviderAuthority` 与清单里的 authority 不一致，或你自建了覆盖 provider 却没暴露 `cacheDir/updater/` |
| 安装弹窗一闪就报「未安装」 | 清单缺 `REQUEST_INSTALL_PACKAGES`（有人为了「干净」删掉了它，不要删） |
| 下载在跑但没有通知 | API 33+ 未授予 `POST_NOTIFICATIONS`，属预期行为 |
| 永远提示有新版本 | 资产名尾部数字与真实 versionCode 不一致，或 versionCode 规则与 `resolver` 不匹配 |
| 永远不提示 | release 是 draft/prerelease；或资产名没有尾部数字且未配 `TagScheme`；或版本号没有严格增大 |
| 两个更新入口互相打断下载 | 两个 `UpdaterConfig` 用了同一个 `scope` |
| release 构建下 worker 报类找不到 | 你 vendor 了源码却漏了 `consumer-rules.pro` |
````
