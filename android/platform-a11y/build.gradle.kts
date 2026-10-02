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
}
