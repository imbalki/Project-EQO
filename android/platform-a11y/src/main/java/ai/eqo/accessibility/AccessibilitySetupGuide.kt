/*
 * EQO (TASK-009): Android 13+ restricted-settings repair guidance. The user
 * grants in Android's own Settings screens; EQO never grants (no secure-settings
 * writes anywhere in this module).
 */
package ai.eqo.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Guided repair flow for enabling EQO's accessibility service, including the
 * Android 13+ "restricted settings" block that can silently refuse the grant for
 * sideloaded apps.
 *
 * Contract: EQO only ever OPENS Android's own Settings screens and READS the
 * current state. The user taps every grant themselves. This class must never
 * write `Settings.Secure` (see `EqoNeverGrantsTest`, which scans this module's
 * sources for secure-settings writes and fails on any match).
 */
object AccessibilitySetupGuide {
    data class Step(
        val order: Int,
        val screen: String,
        val instruction: String,
    )

    /**
     * Steps for API 33+ (Android 13+), where sideloaded apps can have
     * "restricted settings" applied. On API 30-32 the restricted-settings
     * steps are unnecessary but harmless.
     */
    fun stepsForApi33Plus(): List<Step> =
        listOf(
            Step(
                order = 1,
                screen = "Settings > Apps > EQO",
                instruction = "Open the EQO app info page (this app opens it for you).",
            ),
            Step(
                order = 2,
                screen = "Settings > Apps > EQO > More / three-dot menu",
                instruction =
                    "Tap 'Allow restricted settings' and confirm with your screen lock. " +
                        "Android 13+ blocks accessibility grants for sideloaded apps until this is done.",
            ),
            Step(
                order = 3,
                screen = "Settings > Accessibility",
                instruction = "Open Android's Accessibility settings (this app opens it for you).",
            ),
            Step(
                order = 4,
                screen = "Settings > Accessibility > EQO",
                instruction =
                    "Tap EQO, turn the service on, and read the permission dialog before confirming. " +
                        "EQO never enables this for you.",
            ),
            Step(
                order = 5,
                screen = "Settings > Accessibility > EQO",
                instruction =
                    "If the toggle turns itself back off, your OEM build is refusing the grant: " +
                        "report the device model and Android build; do not generalize to other devices.",
            ),
        )

    /** Steps for API 30-32, where there is no restricted-settings gate. */
    fun stepsForApi30To32(): List<Step> =
        listOf(
            Step(
                order = 1,
                screen = "Settings > Accessibility",
                instruction = "Open Android's Accessibility settings (this app opens it for you).",
            ),
            Step(
                order = 2,
                screen = "Settings > Accessibility > EQO",
                instruction =
                    "Tap EQO, turn the service on, and confirm the permission dialog. " +
                        "EQO never enables this for you.",
            ),
        )

    /** Intent that opens the EQO app info page (where 'Allow restricted settings' lives on 13+). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Intent that opens Android's own Accessibility settings screen. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
