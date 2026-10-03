plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ai.eqo"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.eqo.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // TASK-007: device spike harness (HelperSpikeDeviceTest) runs in this app.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // TASK-007 (issue #12): the helper starter is packaged as a loadable library
    // (lib/libeqo-starter.so) and must be EXTRACTED to nativeLibraryDir to be executed
    // over `adb shell` - same reason upstream Shizuku's manager sets useLegacyPackaging.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        warningsAsErrors = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // TASK-007 (issue #12): the EQO privileged helper ships inside the single EQO APK
    // (helper-server = forked Shizuku server + native starter, helper-client = forked
    // Shizuku-API client). No separate helper app is installed.
    implementation(project(":helper-server"))
    implementation(project(":helper-client"))

    // androidx.core for androidx.core.content.FileProvider in AndroidManifest.xml
    // (authority ai.eqo.app.fileprovider); lint's MissingClass check flagged it (#10).
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    // TASK-007: device spike harness (android/Phase-One/evidence/task-007-helper-spike.md).
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(":helper-client"))
    androidTestImplementation(project(":helper-server"))
    // TASK-014: manifest-narrowing guard tests (REQ-SMS-03, G-05) need a merged
    // manifest, which plain JUnit cannot see — Robolectric resolves it.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// TASK-005: MainActivity's deep-link unit test touches android.net Uri/Intent through
// the AlertDialog/Intent companion helpers; unmocked framework calls must return
// defaults like the upstream app/build.gradle testOptions (#104).
android {
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}
