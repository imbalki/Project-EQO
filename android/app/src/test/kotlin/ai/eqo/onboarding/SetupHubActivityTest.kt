package ai.eqo.onboarding

import ai.eqo.EqoApplication
import ai.eqo.MainActivity
import ai.eqo.R
import ai.eqo.data.models.PlanStatus
import ai.eqo.study.CapabilityId
import ai.eqo.study.CapabilityState
import ai.eqo.study.CapabilityStatus
import ai.eqo.study.ReadinessSnapshot
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import ai.eqo.task.TaskActivity
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = EqoApplication::class)
class SetupHubActivityTest {
    private val rows =
        listOf(
            R.id.row_model_key to R.string.setup_hub_row_model_key,
            R.id.row_accessibility to R.string.setup_hub_row_accessibility,
            R.id.row_wireless_adb to R.string.setup_hub_row_wireless_adb,
            R.id.row_helper to R.string.setup_hub_row_helper,
            R.id.row_chrome_consent to R.string.setup_hub_row_chrome_consent,
        )

    @Test
    fun everyRowResolvesEveryStateAndShowsResourceBackedGuidance() {
        val previousConsent = StudySetup.consent
        StudySetup.consent = ConsentDecision.UNANSWERED
        val controller = Robolectric.buildActivity(SetupHubActivity::class.java).setup()
        try {
            assertEveryRowState(controller.get())
        } finally {
            controller.pause().stop().destroy()
            StudySetup.consent = previousConsent
        }
    }

    @Test
    fun everyRowResolvesEveryStateAndKeepsDetailsAndRepairGuidance() {
        // Preserve main's all-row/all-state regression with the UX branch's resource-backed
        // repair guidance: internal probe diagnostics are intentionally no longer user copy.
        everyRowResolvesEveryStateAndShowsResourceBackedGuidance()
    }

    private fun assertEveryRowState(activity: SetupHubActivity) {
        assertNoResourceIds(activity)
        val renderRow =
            SetupHubActivity::class.java.getDeclaredMethod(
                "renderRow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                CapabilityStatus::class.java,
            )
        renderRow.isAccessible = true
        CapabilityState.entries.forEach { state ->
            rows.forEachIndexed { index, (viewId, labelId) ->
                val status =
                    CapabilityStatus(
                        id = CapabilityId.entries[index],
                        state = state,
                        probeName = CapabilityId.entries[index].probeName,
                        detail = "binder server-key pinning SF-1",
                        guidance = "internal diagnostic, not user copy",
                        checkedAtMs = 0L,
                    )
                renderRow.invoke(activity, viewId, labelId, status)
                val expected =
                    activity.getString(
                        R.string.setup_row_format,
                        activity.getString(labelId),
                        activity.getString(SetupHubActivity.stateLabel(state)),
                        activity.getString(SetupHubActivity.rowHint(status.id, state)),
                    )
                assertEquals(expected, activity.findViewById<TextView>(viewId).text.toString())
            }
            assertNoResourceIds(activity)
        }
    }

    @Test
    fun recheckShowsConfirmationEachTimeAndRefreshesWithoutReplacingRows() {
        val previousModelKey = StudySetup.modelKey
        StudySetup.modelKey = ModelKeyState.NOT_SET
        val controller = Robolectric.buildActivity(SetupHubActivity::class.java).setup()
        try {
            val activity = controller.get()
            ShadowToast.reset()
            assertNull(ShadowToast.getTextOfLatestToast())
            val otherRows =
                rows.drop(1).associate { (id, _) ->
                    id to
                        activity
                            .findViewById<TextView>(id)
                            .text
                            .toString()
                            .removePrefix("Next: ")
                }
            StudySetup.modelKey = ModelKeyState.CONNECTED
            val recheck = activity.findViewById<Button>(R.id.setup_recheck_button)
            recheck.performClick()
            assertEquals(activity.getString(R.string.setup_hub_recheck_complete), ShadowToast.getTextOfLatestToast())
            assertTrue(
                activity
                    .findViewById<TextView>(R.id.row_model_key)
                    .text
                    .contains(activity.getString(R.string.state_ready)),
            )
            otherRows.forEach { (id, text) ->
                assertEquals(
                    text,
                    activity
                        .findViewById<TextView>(id)
                        .text
                        .toString()
                        .removePrefix("Next: "),
                )
            }
            assertNoResourceIds(activity)
            ShadowToast.reset()
            recheck.performClick()
            assertEquals(activity.getString(R.string.setup_hub_recheck_complete), ShadowToast.getTextOfLatestToast())
        } finally {
            controller.pause().stop().destroy()
            StudySetup.modelKey = previousModelKey
        }
    }

    private fun assertNoResourceIds(activity: SetupHubActivity) {
        rows.forEach { (id, _) ->
            val text = activity.findViewById<TextView>(id).text.toString()
            assertFalse("Raw resource id in row: $text", Regex("\\b\\d{10}\\b").containsMatchIn(text))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = EqoApplication::class)
class StudyUxPresentationTest {
    private val rows =
        listOf(
            R.id.row_model_key to R.string.setup_hub_row_model_key,
            R.id.row_accessibility to R.string.setup_hub_row_accessibility,
            R.id.row_wireless_adb to R.string.setup_hub_row_wireless_adb,
            R.id.row_helper to R.string.setup_hub_row_helper,
            R.id.row_chrome_consent to R.string.setup_hub_row_chrome_consent,
        )

    @Test
    fun nextStepUsesDisplayOrderWithoutStrandingLaterActionsBehindUnavailableRows() {
        val snapshot = ReadinessSnapshot()
        assertEquals(CapabilityId.MODEL_KEY, SetupHubActivity.nextCapability(snapshot))
        CapabilityId.entries.forEach { id ->
            snapshot.record(CapabilityStatus(id, CapabilityState.READY, id.probeName))
        }
        assertNull(SetupHubActivity.nextCapability(snapshot))
        snapshot.record(status(CapabilityId.WIRELESS_ADB, CapabilityState.GATED))
        snapshot.record(status(CapabilityId.CHROME_CONSENT, CapabilityState.NOT_STARTED))
        assertEquals(CapabilityId.CHROME_CONSENT, SetupHubActivity.nextCapability(snapshot))
        snapshot.record(status(CapabilityId.MODEL_KEY, CapabilityState.IN_PROGRESS))
        assertEquals(CapabilityId.CHROME_CONSENT, SetupHubActivity.nextCapability(snapshot))
        snapshot.record(status(CapabilityId.ACCESSIBILITY, CapabilityState.FAILED))
        assertEquals(CapabilityId.ACCESSIBILITY, SetupHubActivity.nextCapability(snapshot))
        snapshot.record(status(CapabilityId.ACCESSIBILITY, CapabilityState.READY))
        snapshot.record(status(CapabilityId.CHROME_CONSENT, CapabilityState.READY))
        assertEquals(CapabilityId.MODEL_KEY, SetupHubActivity.nextCapability(snapshot))
    }

    @Test
    fun exactlyOneNextRowMovesAfterRecheck() {
        val previous = StudySetup.modelKey
        StudySetup.modelKey = ModelKeyState.NOT_SET
        val controller = Robolectric.buildActivity(SetupHubActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertEquals(listOf(R.id.row_model_key), selectedRows(activity))
            assertTrue(activity.findViewById<TextView>(R.id.setup_next).text.startsWith("Next:"))
            StudySetup.modelKey = ModelKeyState.CONNECTED
            activity.findViewById<Button>(R.id.setup_recheck_button).performClick()
            assertEquals(listOf(R.id.row_accessibility), selectedRows(activity))
        } finally {
            controller.pause().stop().destroy()
            StudySetup.modelKey = previous
        }
    }

    @Test
    fun allEntryScreensRenderPlainTextAndHaveAccessibleTargets() {
        listOf(
            MainActivity::class.java,
            SetupHubActivity::class.java,
            ModelKeySetupActivity::class.java,
            AccessibilitySetupActivity::class.java,
            WirelessAdbSetupActivity::class.java,
            ChromeConsentActivity::class.java,
            TaskActivity::class.java,
            ai.eqo.legal.LegalNoticesActivity::class.java,
        ).forEach { type ->
            val controller = Robolectric.buildActivity(type).setup()
            try {
                checkViews(controller.get().findViewById(android.R.id.content))
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    fun homeHidesInactiveHelperAndTaskOffersSetup() {
        val home = Robolectric.buildActivity(MainActivity::class.java).setup()
        val task = Robolectric.buildActivity(TaskActivity::class.java).setup()
        try {
            assertEquals(View.GONE, home.get().findViewById<Button>(R.id.privileged_action_button).visibility)
            assertTrue(
                task
                    .get()
                    .findViewById<TextView>(R.id.task_state)
                    .text
                    .isNotBlank(),
            )
            assertTrue(task.get().findViewById<Button>(R.id.task_setup_button).performClick())
        } finally {
            home.pause().stop().destroy()
            task.pause().stop().destroy()
        }
    }

    @Test
    fun taskStatesAndReceiptsResolveWordsWithoutChangingResumeConfirmation() {
        val controller = Robolectric.buildActivity(TaskActivity::class.java).setup()
        try {
            val activity = controller.get()
            val targetFormatter = TaskActivity::class.java.getDeclaredMethod("approvalTarget", String::class.java)
            targetFormatter.isAccessible = true
            val message = "Keep literal to= and body= text inside the draft"
            val target = targetFormatter.invoke(activity, "to=, body=$message") as String
            assertTrue(target.contains(message))
            assertTrue(target.contains(activity.getString(R.string.task_no_recipient)))
            val renderStatus = TaskActivity::class.java.getDeclaredMethod("renderPlanStatus", PlanStatus::class.java)
            renderStatus.isAccessible = true
            PlanStatus.entries.forEach { status ->
                renderStatus.invoke(activity, status)
                val text = activity.findViewById<TextView>(R.id.task_state).text.toString()
                assertTrue(text.isNotBlank())
                assertFalse(text == status.name)
                assertTrue(activity.findViewById<Button>(R.id.task_resume_button).isEnabled)
            }
            val renderReceipt = TaskActivity::class.java.getDeclaredMethod("renderReceipt", RunReceipt::class.java)
            renderReceipt.isAccessible = true
            val steps =
                listOf(
                    StepProgress("1-observe", "observe", StepProgressState.DONE),
                    StepProgress("2-scroll", "scroll", StepProgressState.UNKNOWN, detail = "binder SF-1"),
                    StepProgress(
                        "3-compose-draft",
                        "compose_sms",
                        StepProgressState.PENDING,
                        detail = "approval timed out",
                    ),
                )
            renderReceipt.invoke(activity, RunReceipt(steps, "STOPPED"))
            val text = activity.findViewById<TextView>(R.id.task_receipt).text.toString()
            assertTrue(text.contains(activity.getString(R.string.task_step_scroll)))
            assertTrue(text.contains("Unknown result"))
            assertFalse(text.contains("2-scroll"))
            val container = activity.findViewById<ViewGroup>(R.id.task_steps_container)
            val pendingText = (container.getChildAt(2) as TextView).text
            assertTrue(pendingText.contains(activity.getString(R.string.task_did_not_run)))
            checkViews(activity.findViewById(android.R.id.content))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun darkThemeUsesExplicitReadableTextAndBackground() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val attributes =
                controller.get().obtainStyledAttributes(
                    intArrayOf(android.R.attr.textColorPrimary, android.R.attr.colorBackground),
                )
            assertEquals(android.graphics.Color.WHITE, attributes.getColor(0, 0))
            assertEquals(android.graphics.Color.parseColor("#121212"), attributes.getColor(1, 0))
            attributes.recycle()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun checkViews(view: View) {
        if (view is TextView) {
            val text = view.text.toString()
            assertFalse(
                text,
                Regex("binder|SF-1|server-key pinning|REQ-[A-Z]+|\\b\\d{10}\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text),
            )
            if (view is Button || view.isClickable) {
                assertTrue(view.minimumHeight >= (MIN_TOUCH_DP * view.resources.displayMetrics.density).toInt())
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) checkViews(view.getChildAt(index))
        }
    }

    private fun selectedRows(activity: Activity): List<Int> =
        rows.map { it.first }.filter { activity.findViewById<TextView>(it).isSelected }

    private fun status(
        id: CapabilityId,
        state: CapabilityState,
    ): CapabilityStatus = CapabilityStatus(id, state, id.probeName, guidance = "internal guidance")

    companion object {
        private const val MIN_TOUCH_DP = 48
    }
}
