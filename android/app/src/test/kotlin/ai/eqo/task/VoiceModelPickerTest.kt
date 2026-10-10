// Origin: EQO-authored fake shared catalog tests; no network, microphone or credentials.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioUnsupportedException
import ai.eqo.core.llm.providers.CachedModelCatalog
import ai.eqo.core.llm.providers.OpenRouterModelCatalog
import ai.eqo.core.llm.providers.OpenRouterModelRepository
import ai.eqo.onboarding.AndroidModelCatalogCache
import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VoiceModelPickerTest {
    private val catalog =
        """{"data":[
        {"id":"test/text","architecture":{"input_modalities":["text"]}},
        {"id":"test/audio","architecture":{"input_modalities":["text","audio"]}},
        {"id":"google/gemini-2.5-flash","architecture":{"input_modalities":["text","audio"]}},
        {"id":"test/unknown","architecture":{"input_modalities":"audio"}},
        {"id":"test/output","architecture":{"output_modalities":["audio"]}}
        ]}"""

    @Test
    fun filtersExactInputAudioAndDefaultsToAdvertisedGemini() {
        val models = OpenRouterModelCatalog.parse(catalog)
        assertEquals(
            listOf("google/gemini-2.5-flash", "test/audio"),
            VoiceModelPicker.audioModels(models).map { it.id },
        )
        assertEquals("google/gemini-2.5-flash", VoiceModelPicker.defaultAudioModel(models))
        assertEquals("test/audio", VoiceModelPicker.defaultAudioModel(models.filterNot { it.provider == "google" }))
        assertEquals(null, VoiceModelPicker.defaultAudioModel(models.filter { it.id == "test/text" }))
    }

    @Test
    fun modelPreferenceIsSeparateFromPlannerAndTextOnlyIsRejected() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val settings = VoiceSettings(activity)
        AndroidModelCatalogCache(activity).write(CachedModelCatalog(catalog, 1, 1))
        StudyModelChoice.save(activity, "test/text")
        assertEquals("google/gemini-2.5-flash", VoiceModelPicker.selectedModel(activity, settings))
        settings.audioModel = "test/audio"
        assertEquals("test/audio", VoiceModelPicker.selectedModel(activity, VoiceSettings(activity)))
        assertEquals("test/text", StudyModelChoice.read(activity))
        settings.audioModel = "test/text"
        assertThrows(AudioUnsupportedException::class.java) { VoiceModelPicker.selectedModel(activity, settings) }
        settings.engine = VoiceEngine.PHONE
        assertEquals(VoiceEngine.PHONE, VoiceSettings(activity).engine)
        controller.pause().stop().destroy()
    }

    @Test
    fun pickerUsesFakeRepositoryAndRemembersOnlyAnAudioModel() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.setup_hub)
        val cache = AndroidModelCatalogCache(activity)
        val repository = OpenRouterModelRepository(cache, { catalog })
        val settings = VoiceSettings(activity)
        VoiceModelPicker(activity, settings, repository, { it() }).bind()
        activity.findViewById<Button>(R.id.voice_model_setting).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(2, dialog.listView.count)
        dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
        assertEquals("test/audio", settings.audioModel)
        assertFalse(dialog.isShowing)
        activity.findViewById<Button>(R.id.voice_model_setting).performClick()
        repository.load()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowAlertDialog.getLatestAlertDialog()?.getButton(AlertDialog.BUTTON_NEGATIVE)?.performClick()
        controller.pause().stop().destroy()
    }

    @Test
    fun pausedPickerRejectsDelayedResultEvenAfterReturning() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.setup_hub)
        val cache = AndroidModelCatalogCache(activity)
        val repository = OpenRouterModelRepository(cache, { catalog })
        var pending: (() -> Unit)? = null
        val picker = VoiceModelPicker(activity, VoiceSettings(activity), repository, { pending = it })
        picker.bind()
        val button = activity.findViewById<Button>(R.id.voice_model_setting)
        button.performClick()
        assertFalse(button.isEnabled)
        controller.pause()
        picker.pause()
        picker.activate()
        pending?.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(button.isEnabled)
        button.performClick()
        pending?.invoke()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        picker.pause()
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        controller.stop().destroy()
    }

    @Test
    fun emptyCatalogShowsPlainExplanationAndExplicitRetryRefreshes() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.setup_hub)
        var fetches = 0
        val repository =
            OpenRouterModelRepository(AndroidModelCatalogCache(activity), {
                fetches++
                "{\"data\":[]}"
            })
        val picker = VoiceModelPicker(activity, VoiceSettings(activity), repository, { it() })
        picker.bind()
        val button = activity.findViewById<Button>(R.id.voice_model_setting)
        button.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(activity.getString(R.string.voice_models_unavailable), shadowOf(dialog).message)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        button.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, fetches)
        picker.pause()
        controller.pause().stop().destroy()
    }
}
