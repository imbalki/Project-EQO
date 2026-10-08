// Origin: EQO TASK-073 (#20), bounded phone/system executor JVM/Robolectric regressions.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SystemActionsTest {
    private lateinit var context: Context
    private lateinit var takeover: TakeoverDetector
    private val requests = mutableListOf<ActionPermission>()
    private val globals = mutableListOf<Int>()
    private var grant = false
    private var globalAccepted = true
    private var analysisCalls = 0

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            android.app.AppOpsManager.OPSTR_WRITE_SETTINGS,
            android.os.Process.myUid(),
            context.packageName,
            android.app.AppOpsManager.MODE_ERRORED,
        )
        shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            .setNotificationPolicyAccessGranted(false)
        takeover = TakeoverDetector()
        requests.clear()
        globals.clear()
        grant = false
        globalAccepted = true
        analysisCalls = 0
    }

    private fun automation(
        secure: Boolean = false,
        root: ai.eqo.accessibility.A11yNode? = null,
    ) = EqoAutomation(
        { root },
        { EqoAutomation.ServiceState.AVAILABLE },
        takeover,
        { secure },
    )

    private fun family(
        secure: Boolean = false,
        analyzer: ScreenAnalyzer? = null,
        root: ai.eqo.accessibility.A11yNode? = null,
    ): SystemActions {
        val provider = { automation(secure, root) }
        return SystemActions(
            GatedIntentLauncher(context, provider),
            PermissionRequester {
                requests += it
                grant
            },
            provider,
            analyzer,
        ) {
            globals += it
            globalAccepted
        }
    }

    private suspend fun execute(
        name: String,
        params: Map<String, String> = emptyMap(),
        actions: SystemActions = family(),
    ): ActionResult = executeRegistered(actions.getActions().single { it.name == name }, params, context)

    @Test(timeout = 10_000)
    fun `all 26 executors reject bypass of registry permit`() =
        runTest(timeout = 5.seconds) {
            val actions = family().getActions()
            assertEquals(26, actions.size)
            assertEquals(26, actions.map { it.name }.toSet().size)
            actions.forEach {
                assertFalse(it.name, it.execute(emptyMap(), context).success)
                assertTrue(
                    it.name,
                    ai.eqo.core.agent.ActionSchema
                        .getAction(it.name) != null,
                )
            }
            assertTrue(globals.isEmpty())
            assertTrue(requests.isEmpty())
        }

    @Test(timeout = 10_000)
    fun `radio panels require manual completion and never report a state change`() =
        runTest(timeout = 5.seconds) {
            val expected =
                mapOf(
                    "TOGGLE_WIFI" to Settings.Panel.ACTION_WIFI,
                    "TOGGLE_MOBILE_DATA" to Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
                    "TOGGLE_HOTSPOT" to Settings.Panel.ACTION_WIFI,
                    "TOGGLE_BLUETOOTH" to Settings.ACTION_BLUETOOTH_SETTINGS,
                )
            expected.forEach { (name, action) ->
                val result = execute(name, mapOf("state" to "off"))
                assertTrue(name, result is ActionResult.UserActionRequired)
                assertEquals(action, shadowOf(context as Application).nextStartedActivity.action)
            }
            assertTrue(requests.isEmpty())
        }

    @Test(timeout = 10_000)
    fun `takeover refuses all panel and global UI without side effects`() =
        runTest(timeout = 5.seconds) {
            takeover.onAgentActionStarted()
            takeover.onTouch(TakeoverDetector.TouchSource.USER)
            takeover.onAgentActionFinished()
            listOf(
                "TOGGLE_WIFI",
                "TOGGLE_BLUETOOTH",
                "TOGGLE_HOTSPOT",
                "TOGGLE_MOBILE_DATA",
                "RESTART_DEVICE",
                "LOCK_SCREEN",
                "TAKE_SCREENSHOT",
                "CLOSE_APP",
            ).forEach {
                assertFalse(it, execute(it).success)
            }
            assertTrue(globals.isEmpty())
            assertNull(shadowOf(context as Application).nextStartedActivity)
        }

    @Test(timeout = 10_000)
    fun `restart opens power dialog and close only requests home`() =
        runTest(timeout = 5.seconds) {
            assertTrue(execute("RESTART_DEVICE") is ActionResult.UserActionRequired)
            assertEquals(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, globals.last())
            assertTrue(execute("CLOSE_APP").success)
            assertEquals(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, globals.last())
            globalAccepted = false
            assertFalse(execute("LOCK_SCREEN").success)
            assertFalse(execute("TAKE_SCREENSHOT").success)
        }

    @Test(timeout = 10_000)
    fun `recording refuses donor simulation`() =
        runTest(timeout = 5.seconds) {
            assertFalse(execute("RECORD_SCREEN", mapOf("start" to "true")).success)
            assertFalse(execute("RECORD_SCREEN", mapOf("start" to "false")).success)
            assertTrue(globals.isEmpty())
        }

    @Test(timeout = 10_000)
    fun `permission denial never changes torch brightness or DND`() =
        runTest(timeout = 5.seconds) {
            assertFalse(execute("TOGGLE_FLASHLIGHT").success)
            assertTrue(requests.last() is ActionPermission.Runtime)
            assertFalse(execute("SET_BRIGHTNESS", mapOf("level" to "75")).success)
            assertTrue(requests.last() is ActionPermission.SpecialAccess)
            val brightness = requests.last() as ActionPermission.SpecialAccess
            assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, brightness.settingsAction)
            assertTrue(brightness.packageScoped)
            assertFalse(execute("TOGGLE_DND").success)
            assertEquals(
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                (requests.last() as ActionPermission.SpecialAccess).settingsAction,
            )
            assertNull(shadowOf(context as Application).nextStartedActivity)
        }

    @Test(timeout = 10_000)
    fun `grant answer alone cannot bypass actual system settings grant`() =
        runTest(timeout = 5.seconds) {
            grant = true
            assertFalse(execute("SET_BRIGHTNESS", mapOf("level" to "60")).success)
            assertFalse(execute("TOGGLE_DND").success)
            assertFalse(execute("SET_RINGER_MODE", mapOf("mode" to "silent")).success)
        }

    @Test(timeout = 10_000)
    fun `clipboard round trip and clear do not echo copied text`() =
        runTest(timeout = 5.seconds) {
            val copied = execute("COPY_TO_CLIPBOARD", mapOf("text" to "secret-marker"))
            assertTrue(copied.success)
            assertFalse(copied.data.orEmpty().contains("secret-marker"))
            assertTrue(execute("GET_CLIPBOARD").data.orEmpty().contains("secret-marker"))
            assertTrue(execute("CLEAR_CLIPBOARD").success)
            assertFalse(execute("GET_CLIPBOARD").data.orEmpty().contains("secret-marker"))
        }

    @Test(timeout = 10_000)
    fun `URL normalization and store search preserve donor intent and manual install`() =
        runTest(timeout = 5.seconds) {
            assertTrue(execute("OPEN_URL", mapOf("url" to "example.org/a")).success)
            assertEquals("https://example.org/a", shadowOf(context as Application).nextStartedActivity.dataString)
            assertTrue(execute("OPEN_URL", mapOf("url" to "https://example.org", "browser" to "chrome")).success)
            assertEquals("com.android.chrome", shadowOf(context as Application).nextStartedActivity.`package`)
            assertFalse(execute("OPEN_URL", mapOf("url" to "https://example.org", "browser" to "unknown")).success)
            assertFalse(execute("OPEN_URL", mapOf("url" to "https://user:pass@example.org")).success)
            assertTrue(execute("INSTALL_APP", mapOf("appName" to "notes & tasks")) is ActionResult.UserActionRequired)
            val store = shadowOf(context as Application).nextStartedActivity
            assertEquals("market", store.data!!.scheme)
            assertEquals("notes & tasks", store.data!!.getQueryParameter("q"))
        }

    @Test(timeout = 10_000)
    fun `private mode and clear data never claim privacy or deletion verified`() =
        runTest(timeout = 5.seconds) {
            assertTrue(execute("ENABLE_PRIVATE_MODE") is ActionResult.UserActionRequired)
            assertTrue(execute("CLEAR_BROWSER_DATA") is ActionResult.UserActionRequired)
        }

    @Test(timeout = 10_000)
    fun `protected or unreadable screen never invokes analyzer`() =
        runTest(timeout = 5.seconds) {
            val analyzer =
                ScreenAnalyzer { _, _ ->
                    analysisCalls++
                    ActionResult.Success(mapOf("message" to "analysis"))
                }
            assertFalse(execute("ANALYZE_SCREENSHOT", actions = family(secure = true, analyzer = analyzer)).success)
            assertFalse(execute("ANALYZE_SCREENSHOT", actions = family(analyzer = analyzer)).success)
            assertEquals(0, analysisCalls)
        }

    @Test(timeout = 10_000)
    fun `readable screen reaches explicit analyzer but paused or protected screen does not`() =
        runTest(timeout = 5.seconds) {
            val node =
                android.view.accessibility.AccessibilityNodeInfo
                    .obtain()
            node.text = "Visible title"
            val root = ai.eqo.accessibility.AccessibilityNodeAdapter(node)
            val analyzer =
                ScreenAnalyzer { _, text ->
                    analysisCalls++
                    assertTrue(text.contains("Visible title"))
                    ActionResult.Success(mapOf("message" to "Analysed from screen text, not the image."))
                }
            assertTrue(execute("ANALYZE_SCREENSHOT", actions = family(analyzer = analyzer, root = root)).success)
            val protectedActions = family(secure = true, analyzer = analyzer, root = root)
            assertFalse(execute("ANALYZE_SCREENSHOT", actions = protectedActions).success)
            assertFalse(execute("ANALYZE_SCREENSHOT", actions = family(root = root)).success)
            takeover.onAgentActionStarted()
            takeover.onTouch(TakeoverDetector.TouchSource.USER)
            takeover.onAgentActionFinished()
            assertFalse(execute("ANALYZE_SCREENSHOT", actions = family(analyzer = analyzer, root = root)).success)
            assertEquals(1, analysisCalls)
        }

    @Test(timeout = 10_000)
    fun `actual DND grant permits audio changes and takeover refuses mutations`() =
        runTest(timeout = 5.seconds) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            shadowOf(manager).setNotificationPolicyAccessGranted(true)
            assertTrue(execute("TOGGLE_DND", mapOf("state" to "on")).success)
            assertEquals(android.app.NotificationManager.INTERRUPTION_FILTER_NONE, manager.currentInterruptionFilter)
            assertTrue(execute("SET_RINGER_MODE", mapOf("mode" to "vibrate")).success)
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            assertEquals(android.media.AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
            assertTrue(requests.isEmpty())
            takeover.onAgentActionStarted()
            takeover.onTouch(TakeoverDetector.TouchSource.USER)
            takeover.onAgentActionFinished()
            assertFalse(execute("TOGGLE_DND", mapOf("state" to "off")).success)
            assertEquals(android.app.NotificationManager.INTERRUPTION_FILTER_NONE, manager.currentInterruptionFilter)
            assertFalse(execute("COPY_TO_CLIPBOARD", mapOf("text" to "blocked")).success)
        }

    @Test(timeout = 10_000)
    fun `system and destructive verbs remain outside the task plan allowlist`() {
        val excluded =
            listOf("RESTART_DEVICE", "INSTALL_APP", "CLEAR_BROWSER_DATA", "LOCK_SCREEN", "DELETE_FILE", "WIPE_DATA")
        excluded.forEach { name ->
            listOf(name, name.lowercase()).forEach { verb ->
                val proposal = "{\"steps\":[{\"action\":\"$verb\",\"params\":{}}]}"
                val rejected =
                    runCatching {
                        ai.eqo.core.agent.TaskPlanner
                            .parse(proposal)
                    }
                assertTrue(verb, rejected.isFailure)
            }
        }
    }

    @Test(timeout = 10_000)
    fun `cancelled permissions propagate not success`() =
        runTest(timeout = 5.seconds) {
            val provider = { automation() }
            val actions =
                SystemActions(
                    GatedIntentLauncher(context, provider),
                    PermissionRequester { throw CancellationException("cancelled") },
                    provider,
                    null,
                )
            var cancelled = false
            try {
                execute("SET_BRIGHTNESS", actions = actions)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
}
