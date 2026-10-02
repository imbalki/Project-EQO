plugins {
    alias(libs.plugins.android.library)
    // AGP 9 built-in Kotlin (compiler pinned to 2.4.0 via root buildscript; no org.jetbrains.kotlin.android plugin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.opendroid.ai.core.security"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
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
            // Mirrors upstream app/build.gradle testOptions: KeystoreSecretRecords
            // calls android.util.Log at error boundaries; unmocked Log calls must
            // return defaults instead of throwing (upstream comment #104).
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
