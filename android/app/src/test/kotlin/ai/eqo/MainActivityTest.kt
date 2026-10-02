package ai.eqo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityTest {
    @Test
    fun eqoPackageNameIsUnderEqoNamespace() {
        assertEquals("ai.eqo", MainActivity::class.java.`package`?.name)
    }

    @Test
    fun legalDeepLinkUsesEqoSchemeAndLegalHost() {
        assertEquals("eqo", MainActivity.LEGAL_SCHEME)
        assertEquals("legal", MainActivity.LEGAL_HOST)
    }

    @Test
    fun legalDeepLinkUriFormat() {
        assertEquals("eqo://legal", "${MainActivity.LEGAL_SCHEME}://${MainActivity.LEGAL_HOST}")
    }

    @Test
    fun legalDeepLinkIntentTargetsEqoApplicationId() {
        // isReturnDefaultValues makes Intent setters/getters no-ops, so the built intent
        // cannot be interrogated. Cross-check the two real sources instead: the Gradle
        // applicationId and the package literal legalDeepLinkIntent() assigns, so the deep
        // link cannot silently target a renamed or foreign application id.
        val applicationId =
            Regex("applicationId = \"([^\"]+)\"")
                .find(File("build.gradle.kts").readText())
                ?.groupValues
                ?.get(1)
        assertEquals("ai.eqo.app", applicationId)
        val activity = File("src/main/kotlin/ai/eqo/MainActivity.kt").readText()
        assertTrue(
            "legalDeepLinkIntent() must target applicationId $applicationId",
            activity.contains("`package` = \"$applicationId\""),
        )
    }
}
