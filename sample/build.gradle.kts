plugins {
    id("com.android.application")
}

android {
    namespace = "dev.veneranative.updater.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.veneranative.updater.sample"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":updater"))
    implementation(libs.kotlinx.coroutines.android)
}
