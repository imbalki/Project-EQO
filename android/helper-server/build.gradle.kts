// TASK-007 (issue #12): EQO privileged helper server. Forked from RikkaApps/Shizuku @ b844bc49
// (server/ + starter/ + common/ + manager native starter) and RikkaApps/Shizuku-API @ a27f6e41
// (server-shared/). See android/Phase-One/evidence/task-007-helper-spike.md for the provenance
// and rename details. The native starter is built with the pinned NDK (TASK-001 toolchain).
plugins {
    alias(libs.plugins.android.library)
    // AGP 9 built-in Kotlin (compiler pinned to 2.4.0 via root buildscript; no org.jetbrains.kotlin.android plugin)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ai.eqo.helper.server"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 30

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
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
    implementation(project(":helper-client"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.gson)
    implementation(libs.parcelablelist)
    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
