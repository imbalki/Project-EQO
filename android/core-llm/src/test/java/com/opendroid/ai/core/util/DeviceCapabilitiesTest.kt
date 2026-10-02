// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/test/java/com/opendroid/ai/core/util/DeviceCapabilitiesTest.kt
package com.opendroid.ai.core.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [DeviceCapabilities] — the hardware probe the communication and
 * camera actions consult before touching telephony or camera APIs. The manifest
 * declares telephony and camera as optional, so these probes are what keep the
 * app from crashing on ChromeOS, tablets, and foldables that lack the hardware.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceCapabilitiesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun setFeature(
        name: String,
        supported: Boolean,
    ) {
        shadowOf(context.packageManager).setSystemFeature(name, supported)
    }

    // ── Calling ─────────────────────────────────────────────────────────

    @Test
    @Config(sdk = [34])
    fun `calling is supported when the device reports the calling feature`() {
        setFeature(PackageManager.FEATURE_TELEPHONY_CALLING, true)
        assertTrue(DeviceCapabilities.canMakeCalls(context))
    }

    @Test
    @Config(sdk = [34])
    fun `calling is unsupported on a data-only device that has telephony but not calling`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, true)
        setFeature(PackageManager.FEATURE_TELEPHONY_CALLING, false)
        assertFalse(DeviceCapabilities.canMakeCalls(context))
    }

    @Test
    // TASK-004 Phase 2: lane moved 28 -> 30; EQO minSdk 30 (ADR-0003) makes Robolectric
    // reject the module manifest at sdk 28. Semantics (below API 33) are preserved.
    @Config(sdk = [30])
    fun `calling falls back to the telephony feature below API 33`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, true)
        assertTrue(DeviceCapabilities.canMakeCalls(context))
    }

    @Test
    @Config(sdk = [32])
    fun `calling falls back to telephony on API 32 where the calling feature does not exist`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, true)
        setFeature(PackageManager.FEATURE_TELEPHONY_CALLING, false)
        assertTrue(DeviceCapabilities.canMakeCalls(context))
    }

    @Test
    @Config(sdk = [32])
    fun `sms falls back to telephony on API 32 where the messaging feature does not exist`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, true)
        setFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING, false)
        assertTrue(DeviceCapabilities.canSendSms(context))
    }

    @Test
    // TASK-004 Phase 2: lane moved 28 -> 30; EQO minSdk 30 (ADR-0003) makes Robolectric
    // reject the module manifest at sdk 28. Semantics (below API 33) are preserved.
    @Config(sdk = [30])
    fun `calling is unsupported below API 33 without telephony`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, false)
        assertFalse(DeviceCapabilities.canMakeCalls(context))
    }

    // ── SMS ─────────────────────────────────────────────────────────────

    @Test
    @Config(sdk = [34])
    fun `sms is supported when the device reports the messaging feature`() {
        setFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING, true)
        assertTrue(DeviceCapabilities.canSendSms(context))
    }

    @Test
    @Config(sdk = [34])
    fun `sms is unsupported when the device has telephony but no messaging`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, true)
        setFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING, false)
        assertFalse(DeviceCapabilities.canSendSms(context))
    }

    @Test
    // TASK-004 Phase 2: lane moved 28 -> 30; EQO minSdk 30 (ADR-0003) makes Robolectric
    // reject the module manifest at sdk 28. Semantics (below API 33) are preserved.
    @Config(sdk = [30])
    fun `sms falls back to the telephony feature below API 33`() {
        setFeature(PackageManager.FEATURE_TELEPHONY, true)
        assertTrue(DeviceCapabilities.canSendSms(context))
    }

    // ── Camera ──────────────────────────────────────────────────────────

    @Test
    fun `camera is supported when any camera is present`() {
        setFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        assertTrue(DeviceCapabilities.hasCamera(context))
    }

    @Test
    fun `camera is unsupported when the device reports no camera`() {
        setFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        assertFalse(DeviceCapabilities.hasCamera(context))
    }
}
