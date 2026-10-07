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
    namespace = "ai.eqo.core.llm"
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
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-security"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    // TASK-078: EqoDatabase (macros, notifications, habit events/routines) is generated here.
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.converter.gson)

    implementation(libs.litertlm.android)
    implementation(libs.mlkit.genai.prompt)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver3)
    // F4 (TASK-006 security pass): a real TLS MockWebServer for the https
    // endpoint-probe test (held certificate, no public CA).
    testImplementation(libs.okhttp.tls)
}
