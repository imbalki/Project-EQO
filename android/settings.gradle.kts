pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "eqo-android"
include(":app")
include(":actions-android")
include(":core-agent")
include(":core-llm")
include(":core-security")
include(":platform-a11y")
// TASK-008 (issue #13): wireless-ADB pairing, extracted from the ClosePaw donor plus the
// EQO guided activation flow that gates every privileged feature.
include(":adb-pairing")
// TASK-007 (issue #12): EQO privileged helper, forked from Shizuku + Shizuku-API.
include(":helper-server")
include(":helper-client")
// TASK-010 (issue #15): Chrome DevTools (CDP) spike, extracted from ClosePaw.
include(":browser-cdp")
