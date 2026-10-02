buildscript {
    dependencies {
        // Pin the AGP built-in Kotlin compiler to Kotlin 2.4.0 (AGP 9.3.1 otherwise
        // pulls Kotlin 2.2.10 transitively). Same approach as the upstream pin
        // verified in android/Phase-One/docs/TOOLCHAIN.md (OpenDroid build.gradle:18).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
