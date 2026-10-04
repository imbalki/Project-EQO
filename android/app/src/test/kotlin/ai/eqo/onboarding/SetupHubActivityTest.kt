package ai.eqo.onboarding

import ai.eqo.EqoApplication
import ai.eqo.R
import ai.eqo.study.CapabilityId
import ai.eqo.study.CapabilityState
import ai.eqo.study.CapabilityStatus
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
    fun everyRowResolvesEveryStateAndKeepsDetailsAndRepairGuidance() {
        val controller = Robolectric.buildActivity(SetupHubActivity::class.java).setup()
        try {
            assertEveryRowState(controller.get())
        } finally {
            controller.pause().stop().destroy()
        }
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
            rows.forEach { (viewId, labelId) ->
                val status =
                    CapabilityStatus(
                        id = CapabilityId.MODEL_KEY,
                        state = state,
                        probeName = CapabilityId.MODEL_KEY.probeName,
                        detail = "probe result",
                        guidance = "repair guidance",
                        checkedAtMs = 0L,
                    )
                renderRow.invoke(activity, viewId, labelId, status)
                val expected =
                    activity.getString(labelId) + " — " +
                        activity.getString(SetupHubActivity.stateLabel(state)) + "\nprobe result" +
                        if (state == CapabilityState.READY) "" else "\nrepair guidance"
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
                rows.drop(1).associate { (id, _) -> id to activity.findViewById<TextView>(id).text.toString() }
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
            otherRows.forEach { (id, text) -> assertEquals(text, activity.findViewById<TextView>(id).text.toString()) }
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
