plugins {
    id("com.android.library")
}

android {
    namespace = "dev.veneranative.updater"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

group = "dev.veneranative"
version = providers.gradleProperty("updaterSdkVersion").getOrElse("1.0.0")

dependencies {
    // okhttp and coroutines leak into the public API (OkHttpClient in `Updater.create`,
    // Flow/StateFlow on `UpdateGateway` and `Updater.status`), so consumers must see them.
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)

    // FileProvider exposes the downloaded APK to the system installer.
    implementation(libs.androidx.core.ktx)
    // The download runs as a WorkManager worker so it survives the process and the device's own
    // constraints.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // android.jar only carries org.json stubs.
    testImplementation(libs.json)
}
