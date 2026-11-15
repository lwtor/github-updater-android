// AGP 9 ships Kotlin support built in, so applying only the Android plugin is enough — no
// separate `org.jetbrains.kotlin.android` plugin and no Kotlin version to keep in sync.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
}
