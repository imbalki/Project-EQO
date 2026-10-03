plugins {
    alias(libs.plugins.android.library)
    // AGP 9 built-in Kotlin (compiler pinned to 2.4.0 via root buildscript; no org.jetbrains.kotlin.android plugin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ai.eqo.browser.cdp"
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
        // Shared with :app: TASK-001 version-currency exceptions (compileSdk 36 ...).
        // EQO trust-manager source/bytecode checks remain enabled.
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
    // TASK-010 (issue #15): the extracted Chrome DevTools (CDP) stack. kotlinx-serialization
    // drives the CDP JSON framing (buildCdpRequest / parseCdpMessage) and the DevTools
    // /json/version + /json/list endpoint parsing. Coroutines drive the per-command timeout
    // and the suspend setup sequence.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
