plugins {
    alias(libs.plugins.android.library)
    // AGP 9 built-in Kotlin (compiler pinned to 2.4.0 via root buildscript; no org.jetbrains.kotlin.android plugin)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    // Namespace matches the moved code's `import ai.eqo.R` (R handling,
    // TASK-004 extraction map decision 8), so no moved file changes for resources.
    namespace = "ai.eqo"
    compileSdk = 36

    defaultConfig {
        minSdk = 30

        // TASK-009: the instrumentation/test APK hosts the single EQO
        // accessibility service on a real device (androidTest-side wiring
        // authorized by the lead, :platform-a11y scope only).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        warningsAsErrors = true
        // Same reviewed lint config as :app (no new entries added): the four
        // severity=ignore entries there are the accepted version-currency
        // suppressions for the mandated TASK-001 pins (compileSdk 36 ...),
        // reviewed in the TASK-003 review. All other checks still fail the build.
        lintConfig = file("../app/lint.xml")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-agent"))
    implementation(project(":core-llm"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    // TASK-009 androidTest (device records + stub Hilt graph). All artifacts
    // come from the shared version catalog (lint UseTomlInstead); the pins are
    // the same ones this task originally hardcoded (hilt 2.60.1, runner 1.7.0).
    // Library `implementation` deps are not on the androidTest compile
    // classpath, so the projects and jars the fakes reference are declared
    // explicitly. Hilt refuses @HiltAndroidApp in a library's androidTest, so
    // the test APK hosts dagger.hilt.android.testing.HiltTestApplication.
    androidTestImplementation(project(":core-agent"))
    androidTestImplementation(project(":core-llm"))
    androidTestImplementation(project(":core-security"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.hilt.android)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(platform(libs.okhttp.bom))
    androidTestImplementation(libs.okhttp)
    androidTestImplementation(libs.androidx.test.runner)
    kspAndroidTest(libs.hilt.compiler)
}
