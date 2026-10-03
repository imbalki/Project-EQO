// TASK-007 (issue #12): EQO privileged helper client. Forked from RikkaApps/Shizuku-API @ a27f6e41
// (aidl/ + shared/ + api/ + provider/). See android/Phase-One/evidence/task-007-helper-spike.md
// for the provenance and rename details.
plugins {
    alias(libs.plugins.android.library)
    // AGP 9 built-in Kotlin (compiler pinned to 2.4.0 via root buildscript; no org.jetbrains.kotlin.android plugin)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ai.eqo.helper.client"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        // TASK-007: the mismatched-permission negative fixture runs as its own test app.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        warningsAsErrors = true
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
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    // TASK-007: device negative fixture (MismatchedPermissionTest).
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
