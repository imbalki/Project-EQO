plugins {
    alias(libs.plugins.android.library)
    // AGP 9 built-in Kotlin (compiler pinned to 2.4.0 via root buildscript; no org.jetbrains.kotlin.android plugin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ai.eqo.adb.pairing"
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
        // Shared with :app: TASK-001 version-currency exceptions plus the
        // TASK-008 exact bcpkix 1.84 artifact-path exception. EQO trust-manager
        // source/bytecode checks remain enabled (verified by negative control).
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
    // TASK-008 (issue #13): the extracted wireless-ADB pairing stack. Pins are the donor's
    // (see gradle/libs.versions.toml). BouncyCastle = cert generation + HKDF, Conscrypt =
    // TLS 1.3 with RFC 5705 keying-material export (the pairing PSK mix-in), eddsa = the
    // pure-Kotlin SPAKE2-25519 group arithmetic.
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    implementation(libs.conscrypt.android)
    implementation(libs.eddsa)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
