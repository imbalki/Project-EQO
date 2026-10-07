// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/SystemActions.kt
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri

internal class SystemActions(
    private val launcher: GatedIntentLauncher,
    private val permissions: PermissionRequester,
    private val automation: () -> EqoAutomation?,
    private val screenAnalyzer: ScreenAnalyzer?,
    private val globalAction: (Int) -> Boolean = { EQOAccessibilityService.getInstance()?.performGlobalAction(it) == true },
) {
    private var isFlashlightOn = false
    private var callbackRegistered = false

    fun getActions(): List<Action> =
        listOf(
            ToggleFlashlightAction(),
            SetVolumeAction(),
            SetBrightnessAction(),
            OpenAppAction(),
            ToggleDndAction(),
            SetWallpaperAction(),
            InstallAppAction(),
            GetSystemInfoAction(),
            SetRingerModeAction(),
            ClearClipboardAction(),
            CopyToClipboardAction(),
            GetClipboardAction(),
            OpenBrowserAction(),
            OpenUrlAction(),
            EnablePrivateModeAction(),
            ClearBrowserDataAction(),
            PanelAction("TOGGLE_WIFI", Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS),
            PanelAction("TOGGLE_MOBILE_DATA", Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_DATA_ROAMING_SETTINGS),
            PanelAction("TOGGLE_HOTSPOT", Settings.Panel.ACTION_WIFI, "android.settings.TETHER_SETTINGS"),
            PanelAction("TOGGLE_BLUETOOTH", Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS),
            GlobalAction("LOCK_SCREEN", AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN),
            GlobalAction("TAKE_SCREENSHOT", AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
            GlobalAction("RESTART_DEVICE", AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),
            GlobalAction("CLOSE_APP", AccessibilityService.GLOBAL_ACTION_HOME),
            RecordScreenAction(),
            AnalyzeScreenshotAction(),
        )

    private fun change(block: () -> Unit): ActionResult? {
        val result =
            automation()?.runAction {
                block()
                A11yResult.success("Android accepted the change request.")
            } ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (result.isSuccess) null else result.toActionResult()
    }

    private suspend fun requestWriteSettings(context: Context): Boolean {
        if (Settings.System.canWrite(context)) return true
        return permissions.request(
            ActionPermission.SpecialAccess(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                true,
                "Allow EQO to modify system settings to set screen brightness.",
                { Settings.System.canWrite(context) },
            ),
        ) &&
            Settings.System.canWrite(context)
    }

    private suspend fun requestDndAccess(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.isNotificationPolicyAccessGranted) return true
        return permissions.request(
            ActionPermission.SpecialAccess(
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                false,
                "Allow EQO Do Not Disturb policy access for this audio change.",
                { manager.isNotificationPolicyAccessGranted },
            ),
        ) &&
            manager.isNotificationPolicyAccessGranted
    }

    // Android 11 restricted radios: donor settings/panel fallback only, never a false state-change success.
    private inner class PanelAction(
        override val name: String,
        private val panel: String,
        private val fallback: String,
    ) : Action {
        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val gate =
                automation()?.runAction { A11yResult.success("Checking radio settings access.") }
                    ?: A11yResult.failure(A11yError.AccessibilityDisabled)
            if (!gate.isSuccess) return gate.toActionResult()
            val raw = (params["state"] ?: params["on"] ?: "toggle").lowercase().trim()
            val description =
                when (raw) {
                    "on", "true", "enable", "yes" -> "on"
                    "off", "false", "disable", "no" -> "off"
                    else -> "to the desired state"
                }
            // REQUEST_ENABLE itself needs CONNECT on new Android; settings needs no Bluetooth grant.
            val first =
                if (name == "TOGGLE_BLUETOOTH" && description == "on" && android.os.Build.VERSION.SDK_INT <= 30) {
                    BluetoothAdapter.ACTION_REQUEST_ENABLE
                } else {
                    panel
                }
            try {
                launcher.open(Intent(first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                launcher.open(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return ActionResult.UserActionRequired("Settings opened; change the radio $description yourself. No state change was verified.")
        }
    }

    private inner class GlobalAction(
        override val name: String,
        private val code: Int,
    ) : Action {
        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val outcome =
                automation()?.runAction {
                    if (globalAction(code)) {
                        A11yResult.success("Android accepted the global action.")
                    } else {
                        A11yResult.failure(A11yError.ActionRejected(name))
                    }
                } ?: A11yResult.failure(A11yError.AccessibilityDisabled)
            if (!outcome.isSuccess) return outcome.toActionResult()
            return when (name) {
                "RESTART_DEVICE" ->
                    ActionResult.UserActionRequired(
                        "Power dialog opened; choose Restart yourself. EQO did not restart the device.",
                    )
                "CLOSE_APP" -> ActionResult.Success(mapOf("message" to "Home screen requested; app was not force-stopped."))
                else -> outcome.toActionResult()
            }
        }
    }

    private class RecordScreenAction : Action {
        override val name = "RECORD_SCREEN"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return ActionResult.Failure(
                "Screen recording is unavailable in this build: the donor only simulated it. " +
                    "No recording was started or stopped.",
            )
        }
    }

    private inner class AnalyzeScreenshotAction : Action {
        override val name = "ANALYZE_SCREENSHOT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            // Refuse protected windows and paused automation before contacting an explicitly provided analyzer.
            val observation = automation()?.observe() ?: A11yResult.failure(A11yError.AccessibilityDisabled)
            if (!observation.isSuccess) return observation.toActionResult()
            val analyzer =
                screenAnalyzer
                    ?: return ActionResult.Failure("Screen analysis provider is not configured; no image or screen text was sent.")
            return analyzer.analyze(params["question"] ?: "What do you see on this screen?", (observation as A11yResult.Success).detail)
        }
    }

    private inner class ToggleFlashlightAction : Action {
        override val name: String = "TOGGLE_FLASHLIGHT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            // Read "state" param — default to "toggle" if missing
            val requestedState =
                (params["state"] ?: params["on"] ?: "toggle")
                    .lowercase()
                    .trim()

            if (!permissions.request(
                    ActionPermission.Runtime(Manifest.permission.CAMERA, "Allow camera access to control the flashlight."),
                ) ||
                androidx.core.content.ContextCompat
                    .checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return ActionResult.Failure("Camera permission was not granted; flashlight did not run.")
            }
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Register torch callback to track actual state (once)
            if (!callbackRegistered) {
                try {
                    cameraManager.registerTorchCallback(
                        object : CameraManager.TorchCallback() {
                            override fun onTorchModeChanged(
                                cameraId: String,
                                enabled: Boolean,
                            ) {
                                isFlashlightOn = enabled
                            }
                        },
                        null,
                    )
                    callbackRegistered = true
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                }
            }

            return try {
                var foundCameraId: String? = null
                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (hasFlash) {
                        foundCameraId = id
                        break
                    }
                }
                val cameraId = foundCameraId ?: cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    // Determine target state — NEVER return NeedsInput
                    val targetOn =
                        when (requestedState) {
                            "on", "true" -> true
                            "off", "false" -> false
                            "toggle" -> !isFlashlightOn
                            else -> !isFlashlightOn // unknown = toggle
                        }
                    change { cameraManager.setTorchMode(cameraId, targetOn) }?.let { return it }
                    isFlashlightOn = targetOn
                    val stateWord = if (targetOn) "on" else "off"
                    ActionResult(true, "Flashlight's $stateWord!", null)
                } else {
                    ActionResult(false, null, "No camera with flashlight support was found.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't toggle the flashlight.")
            }
        }
    }

    private inner class SetVolumeAction : Action {
        override val name: String = "SET_VOLUME"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val typeStr = params["type"] ?: "media"
            val level = params["level"]?.toIntOrNull()?.coerceIn(0, 100) ?: 50
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val streamType =
                when (typeStr.lowercase().trim()) {
                    "ring", "ringtone", "ringer" -> AudioManager.STREAM_RING
                    "alarm" -> AudioManager.STREAM_ALARM
                    "notification", "notif" -> AudioManager.STREAM_NOTIFICATION
                    "system" -> AudioManager.STREAM_SYSTEM
                    else -> AudioManager.STREAM_MUSIC
                }
            if (streamType != AudioManager.STREAM_MUSIC && streamType != AudioManager.STREAM_ALARM && !requestDndAccess(context)) {
                return ActionResult.Failure("DND policy access was not granted; volume did not change.")
            }
            val streamLabel =
                when (streamType) {
                    AudioManager.STREAM_RING -> "Ringtone"
                    AudioManager.STREAM_ALARM -> "Alarm"
                    AudioManager.STREAM_NOTIFICATION -> "Notification"
                    AudioManager.STREAM_SYSTEM -> "System"
                    else -> "Media"
                }
            return try {
                val maxVolume = audioManager.getStreamMaxVolume(streamType)
                val targetVolume = (level * maxVolume) / 100
                change { audioManager.setStreamVolume(streamType, targetVolume, AudioManager.FLAG_SHOW_UI) }?.let { return it }
                ActionResult(true, "$streamLabel volume set to $level%.", null)
            } catch (e: SecurityException) {
                ActionResult(
                    false,
                    null,
                    "I don't have permission to change the $streamLabel volume. Please check your Do Not Disturb settings.",
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't change the volume right now. Please try again.")
            }
        }
    }

    private inner class SetBrightnessAction : Action {
        override val name: String = "SET_BRIGHTNESS"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val level = params["level"]?.toIntOrNull()?.coerceIn(0, 100) ?: 50
            val targetVal = (level * 255) / 100
            if (!requestWriteSettings(
                    context,
                )
            ) {
                return ActionResult.Failure("System settings access was not granted; brightness did not change.")
            }
            return try {
                if (Settings.System.canWrite(context)) {
                    // Disable auto-brightness so manual level takes effect
                    change {
                        check(
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                            ),
                        )
                        check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetVal))
                    }?.let { return it }
                    ActionResult(true, "Done! Brightness is set to $level%.", null)
                } else {
                    ActionResult.Failure("System settings access was revoked; brightness did not change.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't change the brightness right now. Please try again.")
            }
        }
    }

    private inner class OpenAppAction : Action {
        override val name: String = "OPEN_APP"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val appName = params["appName"] ?: return ActionResult(false, null, "appName parameter missing")
            val pm = context.packageManager
            if (appName.equals("settings", ignoreCase = true)) {
                launcher.open(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                kotlinx.coroutines.delay(2000)
                return ActionResult.Success(mapOf("message" to "Settings opened."))
            }
            val alias =
                ai.eqo.core.agent.AliasResolver
                    .appPackage(appName)
            val aliasIntent = alias?.let(pm::getLaunchIntentForPackage)
            if (aliasIntent != null) {
                launcher.open(aliasIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                kotlinx.coroutines.delay(2000)
                return ActionResult.Success(mapOf("message" to "Requested app launched."))
            }
            val mainIntent =
                Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

            // 1. Try to find a match among launcher apps first
            var matchedPackage =
                resolveInfos
                    .find {
                        val label = it.loadLabel(pm).toString()
                        val pkgName = it.activityInfo.packageName
                        label.contains(appName, ignoreCase = true) || pkgName.contains(appName, ignoreCase = true)
                    }?.activityInfo
                    ?.packageName

            // 2. If not found in launcher apps, try matching installed applications as a fallback
            if (matchedPackage == null) {
                try {
                    // Only visible packages; no QUERY_ALL_PACKAGES grant.
                    @android.annotation.SuppressLint("QueryPermissionsNeeded")
                    val packages = pm.getInstalledApplications(0)
                    matchedPackage =
                        packages
                            .find {
                                val label = pm.getApplicationLabel(it).toString()
                                label.contains(appName, ignoreCase = true) || it.packageName.contains(appName, ignoreCase = true)
                            }?.packageName
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Ignore installed applications check exceptions
                }
            }

            return if (matchedPackage != null) {
                val intent = pm.getLaunchIntentForPackage(matchedPackage)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launcher.open(intent)
                    // Wait 2 seconds for app transition to prevent race conditions in subsequent steps
                    kotlinx.coroutines.delay(2000)
                    ActionResult(true, "$appName is open!", null)
                } else {
                    ActionResult(false, null, "Launcher intent not found for $matchedPackage")
                }
            } else {
                ActionResult(false, null, "App '$appName' not installed.")
            }
        }
    }

    private inner class ToggleDndAction : Action {
        override val name: String = "TOGGLE_DND"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val requestedState =
                (params["state"] ?: params["on"] ?: "toggle")
                    .lowercase()
                    .trim()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            return try {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    if (!requestDndAccess(context)) return ActionResult.Failure("DND policy access was not granted; DND did not change.")
                }

                // Determine target state
                val currentlyOn = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                val targetOn =
                    when (requestedState) {
                        "on", "true", "enable", "yes" -> true
                        "off", "false", "disable", "no" -> false
                        "toggle" -> !currentlyOn
                        else -> !currentlyOn
                    }

                // Already in desired state?
                if (targetOn == currentlyOn) {
                    val stateWord = if (currentlyOn) "on" else "off"
                    return ActionResult(true, "Do Not Disturb is already $stateWord!", null)
                }

                val filter = if (targetOn) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
                change { notificationManager.setInterruptionFilter(filter) }?.let { return it }
                val stateWord = if (targetOn) "on" else "off"
                ActionResult(true, "Do Not Disturb is $stateWord.", null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't change Do Not Disturb.")
            }
        }
    }

    private inner class SetWallpaperAction : Action {
        override val name: String = "SET_WALLPAPER"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return try {
                val intent =
                    Intent(Intent.ACTION_SET_WALLPAPER).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                launcher.open(intent)
                ActionResult.UserActionRequired("Wallpaper picker opened; choose and apply the wallpaper yourself.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't open the wallpaper picker.")
            }
        }
    }

    private inner class InstallAppAction : Action {
        override val name: String = "INSTALL_APP"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val appName = params["appName"] ?: return ActionResult(false, null, "appName parameter missing")
            return try {
                val intent =
                    Intent(Intent.ACTION_VIEW, "market://search?q=${Uri.encode(appName)}".toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                launcher.open(intent)
                ActionResult.UserActionRequired("Store search opened; select an app and confirm installation yourself.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                try {
                    val intent =
                        Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=${Uri.encode(appName)}".toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    launcher.open(intent)
                    ActionResult.UserActionRequired("Web store search opened; select an app and confirm installation yourself.")
                } catch (ex: Exception) {
                    if (ex is kotlinx.coroutines.CancellationException) throw ex
                    ActionResult(false, null, "Failed to open the store search.")
                }
            }
        }
    }

    private inner class GetSystemInfoAction : Action {
        override val name: String = "GET_SYSTEM_INFO"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val buildInfo = "OS Version: ${android.os.Build.VERSION.RELEASE}, Model: ${android.os.Build.MODEL}"
            return ActionResult(true, "Here's your system info: $buildInfo", null)
        }
    }

    private inner class SetRingerModeAction : Action {
        override val name: String = "SET_RINGER_MODE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val mode = params["mode"]?.lowercase()?.trim() ?: "normal"
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!requestDndAccess(context)) return ActionResult.Failure("DND policy access was not granted; ringer mode did not change.")
            return try {
                val ringerMode =
                    when (mode) {
                        "silent", "mute" -> AudioManager.RINGER_MODE_SILENT
                        "vibrate", "vibration" -> AudioManager.RINGER_MODE_VIBRATE
                        else -> AudioManager.RINGER_MODE_NORMAL
                    }
                change { audioManager.ringerMode = ringerMode }?.let { return it }
                val label =
                    when (ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                        else -> "normal"
                    }
                ActionResult(true, "Phone is now on $label mode.", null)
            } catch (e: SecurityException) {
                ActionResult(false, null, "I need Do Not Disturb access to change the ringer mode. Please grant it in Settings.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't change the ringer mode right now.")
            }
        }
    }

    private inner class ClearClipboardAction : Action {
        override val name: String = "CLEAR_CLIPBOARD"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                change { clipboard.clearPrimaryClip() }?.let { return it }
                ActionResult(true, "Clipboard cleared!", null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't clear the clipboard.")
            }
        }
    }

    private inner class CopyToClipboardAction : Action {
        override val name: String = "COPY_TO_CLIPBOARD"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val text = params["text"] ?: return ActionResult(false, null, "No text provided to copy")
            return try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("EQO", text)
                change { clipboard.setPrimaryClip(clip) }?.let { return it }
                ActionResult.Success(mapOf("message" to "Copied text to clipboard."))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't copy to clipboard.")
            }
        }
    }

    private inner class GetClipboardAction : Action {
        override val name: String = "GET_CLIPBOARD"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val content = clipData.getItemAt(0).text?.toString() ?: "Empty clipboard"
                    ActionResult(true, "Clipboard contains: \"$content\"", null)
                } else {
                    ActionResult(true, "Clipboard is empty.", null)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't read the clipboard.")
            }
        }
    }

    private inner class OpenBrowserAction : Action {
        override val name: String = "OPEN_BROWSER"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return try {
                // Try Chrome first, then fallback to default browser
                val chromeIntent = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
                if (chromeIntent != null) {
                    chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launcher.open(chromeIntent)
                } else {
                    val intent =
                        Intent(Intent.ACTION_VIEW, "https://www.google.com".toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    launcher.open(intent)
                }
                ActionResult(true, "Browser is open!", null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't open the browser.")
            }
        }
    }

    private inner class OpenUrlAction : Action {
        override val name: String = "OPEN_URL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val url = params["url"] ?: return ActionResult(false, null, "No URL provided")
            return try {
                // Ensure URL has a scheme
                val fullUrl =
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        "https://$url"
                    } else {
                        url
                    }

                val uri = fullUrl.toUri()
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) {
                    return ActionResult.Failure("Provide a valid HTTP or HTTPS URL without embedded credentials.")
                }
                val intent =
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                launcher.open(intent)
                ActionResult.Success(mapOf("message" to "URL opened."))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't open that URL.")
            }
        }
    }

    private inner class EnablePrivateModeAction : Action {
        override val name: String = "ENABLE_PRIVATE_MODE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return try {
                // Chrome incognito intent
                val intent =
                    Intent(Intent.ACTION_VIEW, "https://www.google.com".toUri()).apply {
                        setPackage("com.android.chrome")
                        putExtra("com.android.browser.application_id", "com.android.chrome")
                        putExtra("create_new_tab", true)
                        putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                // Check if Chrome is available
                if (intent.resolveActivity(context.packageManager) != null) {
                    launcher.open(intent)
                    ActionResult.UserActionRequired(
                        "Chrome incognito tab requested; verify private mode before browsing. " +
                            "Android does not verify that Chrome honored this extra.",
                    )
                } else {
                    // Fallback: try to open any browser and tell user
                    val fallback =
                        Intent(Intent.ACTION_VIEW, "https://www.google.com".toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    launcher.open(fallback)
                    ActionResult.UserActionRequired("Browser opened; switch to private mode manually and verify it before browsing.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't open private browsing. Try opening Chrome manually.")
            }
        }
    }

    private inner class ClearBrowserDataAction : Action {
        override val name: String = "CLEAR_BROWSER_DATA"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return try {
                // Open Chrome's clear browsing data settings directly
                val intent =
                    Intent("android.settings.MANAGE_APPLICATIONS_SETTINGS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                // Try Chrome-specific settings first
                val chromeSettingsIntent =
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        setPackage("com.android.chrome")
                        data = "chrome://settings/clearBrowserData".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                try {
                    if (chromeSettingsIntent.resolveActivity(context.packageManager) != null) {
                        launcher.open(chromeSettingsIntent)
                        ActionResult.UserActionRequired("Chrome clear-data page opened; select what to clear and confirm yourself.")
                    } else {
                        throw Exception("Chrome settings not available")
                    }
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    // Fallback: open app info for Chrome
                    val appInfoIntent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:com.android.chrome".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    try {
                        launcher.open(appInfoIntent)
                        ActionResult.UserActionRequired("Chrome app settings opened; choose what data to clear and confirm yourself.")
                    } catch (failure: Exception) {
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        launcher.open(intent)
                        ActionResult.UserActionRequired("App settings opened; find your browser and confirm clearing its data yourself.")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                ActionResult(false, null, "Couldn't open browser settings.")
            }
        }
    }
}
