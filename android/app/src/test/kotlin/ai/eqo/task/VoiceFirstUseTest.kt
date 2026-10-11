// Origin: EQO-authored first-use voice regressions; fake catalog/key, no microphone or network.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.OpenRouterModelCatalog
import ai.eqo.onboarding.ModelKeySetupActivity
import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.edit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VoiceFirstUseTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity
    private lateinit var settings: VoiceSettings
    private lateinit var chooser: VoiceFirstUse
    private var voice: TaskVoiceInput? = null
    private var recorded = 0
    private val models =
        OpenRouterModelCatalog.parse(
            """{"data":[
            {"id":"test/text","architecture":{"input_modalities":["text"]}},
            {"id":"test/audio","architecture":{"input_modalities":["audio"]}},
            {"id":"google/gemini-2.5-flash","architecture":{"input_modalities":["audio"]}},
            {"id":"test/output","architecture":{"output_modalities":["audio"]}}
            ]}""",
        )

    @Before
    fun setup() {
        activity = controller.setup().get()
        activity.setContentView(R.layout.task_screen)
        activity.getSharedPreferences("eqo_voice_settings", Activity.MODE_PRIVATE).edit { clear() }
        settings = VoiceSettings(activity)
        chooser = VoiceFirstUse(activity, settings, { models }, { true }, { it() })
    }

    @After
    fun teardown() {
        chooser.close()
        voice?.close()
        controller.pause().stop().destroy()
    }

    private fun tap(): AlertDialog {
        chooser.tap { recorded++ }
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    private fun click(
        dialog: AlertDialog,
        button: Int,
    ) {
        dialog.getButton(button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun firstMicTapShowsChoiceBeforePermissionOrRecording() {
        voice = TaskVoiceInput(activity, firstUseFactory = { _, _ -> chooser })
        activity.findViewById<Button>(R.id.task_voice_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(activity.getString(R.string.voice_first_use_title), shadowOf(dialog).title)
        assertTrue(dialog.isShowing)
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertFalse(settings.hasChoice)
        assertNull(settings.audioModel)
    }

    @Test
    fun chooseLaterLeavesEverythingUnsetAndNextTapAsksAgain() {
        val first = tap()
        click(first, AlertDialog.BUTTON_NEGATIVE)
        assertFalse(settings.hasChoice)
        assertNull(settings.audioModel)
        assertFalse(settings.consent)
        assertEquals(0, recorded)
        val draft = activity.findViewById<EditText>(R.id.task_request)
        draft.setText("keep typing")
        assertTrue(draft.isEnabled)
        assertTrue(tap().isShowing)
        assertEquals("keep typing", draft.text.toString())
    }

    @Test
    fun onlyAudioInputModelsOfferedAndRecommendedPreselectedWithoutSaving() {
        val group = tap().findViewById<RadioGroup>(R.id.voice_first_use_models)
        assertEquals(2, group.childCount)
        val rows = (0 until group.childCount).map { group.getChildAt(it) as RadioButton }
        assertTrue(rows.all { it.text.contains("Audio") && !it.text.contains("Text only") })
        assertTrue(rows.none { it.text.contains("test/text") || it.text.contains("test/output") })
        assertTrue(group.findViewById<RadioButton>(group.checkedRadioButtonId).text.contains("Recommended default"))
        assertNull(settings.audioModel)
    }

    @Test
    fun confirmedAiChoiceRemembersModelConsentAndSkipsNextSheet() {
        val dialog = tap()
        click(dialog, AlertDialog.BUTTON_POSITIVE)
        val restored = VoiceSettings(activity)
        assertTrue(restored.hasChoice)
        assertEquals(VoiceEngine.OPENROUTER, restored.engine)
        assertEquals("google/gemini-2.5-flash", restored.audioModel)
        assertTrue(restored.consent)
        assertEquals(1, recorded)
        chooser.tap { recorded++ }
        assertEquals(2, recorded)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun missingModelAsksAgainEvenIfAiEngineWasPreviouslySet() {
        settings.engine = VoiceEngine.OPENROUTER
        assertFalse(settings.hasChoice)
        assertTrue(tap().isShowing)
        assertEquals(0, recorded)
    }

    @Test
    fun missingKeyExplainsAndLinksToExistingScreenWithoutSavingChoice() {
        chooser = VoiceFirstUse(activity, settings, { models }, { false }, { it() })
        val dialog = tap()
        assertEquals(
            activity.getString(R.string.voice_first_use_key_missing),
            dialog.findViewById<TextView>(R.id.voice_first_use_status).text,
        )
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        val key = dialog.findViewById<Button>(R.id.voice_first_use_key)
        assertEquals(View.VISIBLE, key.visibility)
        key.performClick()
        val destination = shadowOf(activity).nextStartedActivity.component?.className
        assertEquals(ModelKeySetupActivity::class.java.name, destination)
        assertFalse(settings.hasChoice)
        assertFalse(dialog.isShowing)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun unavailableKeyIsNotReportedAsMissingAndPhoneStillWorks() {
        chooser = VoiceFirstUse(activity, settings, { models }, { null }, { it() })
        val dialog = tap()
        assertEquals(
            activity.getString(R.string.voice_first_use_key_unavailable),
            dialog.findViewById<TextView>(R.id.voice_first_use_status).text,
        )
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        click(dialog, AlertDialog.BUTTON_NEUTRAL)
        assertEquals(VoiceEngine.PHONE, settings.engine)
        assertTrue(settings.hasChoice)
    }

    @Test
    fun phoneChoiceWorksWithoutKeyAndSkipsFutureSheet() {
        chooser = VoiceFirstUse(activity, settings, { models }, { false }, { it() })
        val dialog = tap()
        click(dialog, AlertDialog.BUTTON_NEUTRAL)
        assertTrue(VoiceSettings(activity).hasChoice)
        assertEquals(VoiceEngine.PHONE, VoiceSettings(activity).engine)
        assertNull(settings.audioModel)
        assertFalse(settings.consent)
        chooser.tap { recorded++ }
        assertEquals(2, recorded)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun emptyCatalogCannotConfirmAiButPhoneAndLaterRemainAvailable() {
        chooser = VoiceFirstUse(activity, settings, { emptyList() }, { true }, { it() })
        val dialog = tap()
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertEquals(
            activity.getString(R.string.voice_models_unavailable),
            dialog.findViewById<TextView>(R.id.voice_first_use_status).text,
        )
        click(dialog, AlertDialog.BUTTON_NEGATIVE)
        assertFalse(settings.hasChoice)
    }

    @Test
    fun closingRejectsDelayedCatalogResultAndDoesNotChoose() {
        var pending: (() -> Unit)? = null
        chooser = VoiceFirstUse(activity, settings, { models }, { true }, { pending = it })
        val dialog = tap()
        chooser.close()
        pending?.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertFalse(settings.hasChoice)
        assertEquals(0, recorded)
    }

    @Test
    fun explicitSettingsChoiceCanChangeRememberedPhoneToAi() {
        settings.engine = VoiceEngine.PHONE
        chooser.show()
        shadowOf(Looper.getMainLooper()).idle()
        click(ShadowAlertDialog.getLatestAlertDialog(), AlertDialog.BUTTON_POSITIVE)
        assertEquals(VoiceEngine.OPENROUTER, settings.engine)
        assertTrue(settings.hasChoice)
    }
}
