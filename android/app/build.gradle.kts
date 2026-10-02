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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // androidx.core for androidx.core.content.FileProvider in AndroidManifest.xml
    // (authority ai.eqo.app.fileprovider); lint's MissingClass check flagged it (#10).
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
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
