package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.core.llm.providers.CachedModelCatalog
import ai.eqo.core.llm.providers.OpenRouterModelRepository
import android.app.Activity
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ModelPickerTest {
    @Test
    fun `model choice persists exactly at planner seam and does not store credentials`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(ModelSelection.save(context, "provider/model:free"))
        assertEquals("provider/model:free", ModelSelection.read(context))
        val plannerPrefs = context.getSharedPreferences("study_model_choice", android.content.Context.MODE_PRIVATE)
        assertEquals("provider/model:free", plannerPrefs.getString("openrouter_model", null))
        assertEquals(setOf("openrouter_model"), plannerPrefs.all.keys)
    }

    @Test
    fun `timestamped private cache survives recreation and offline load`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val json = """{"data":[{"id":"p/m","name":"Model","pricing":{"prompt":"0","completion":"0.000002"}}]}"""
        assertTrue(AndroidModelCatalogCache(context).write(CachedModelCatalog(json, 100, 100)))
        val result =
            OpenRouterModelRepository(AndroidModelCatalogCache(context), { throw IOException() }, {
                OpenRouterModelRepository.DAY_MILLIS + 200
            }).load()
        assertTrue(result.offline)
        assertEquals("p/m", result.models.single().id)
        assertEquals(100L, result.fetchedAt)
    }

    @Test
    fun `picker filters displays costs and selects id while manual fallback remains available`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.model_key)
        val cache = AndroidModelCatalogCache(activity)
        cache.write(
            CachedModelCatalog(
                """{"data":[{"id":"p/m","name":"Useful model","context_length":128000,
            "pricing":{"prompt":"0","completion":"0.000002"}},{"id":"q/n","name":"Other"}]}""",
                100,
                100,
            ),
        )
        val picker = ModelPicker(activity, OpenRouterModelRepository(cache, { error("No network in tests") }, { 101 }))
        picker.start()
        awaitLoaded(activity)
        activity.findViewById<Button>(R.id.model_picker_button).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val list = descendants(dialog.window!!.decorView).filterIsInstance<ListView>().single()
        val filter = descendants(dialog.window!!.decorView).filterIsInstance<EditText>().single()
        filter.setText("useful P")
        assertEquals(1, list.adapter.count)
        val row = list.adapter.getItem(0).toString()
        assertTrue(row.contains("Input: free"))
        assertTrue(row.contains("Output: \$2"))
        assertTrue(row.contains("128000"))
        list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
        assertEquals("p/m", activity.findViewById<TextView>(R.id.model_model_input).text.toString())
        // Selection is only committed by a successful connection test, not by browsing.
        assertNull(ModelSelection.read(activity))
        activity.findViewById<Button>(R.id.model_picker_manual).performClick()
        val manual = ShadowAlertDialog.getLatestAlertDialog()
        descendants(manual.window!!.decorView).filterIsInstance<EditText>().single().setText("custom/model")
        manual.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("custom/model", activity.findViewById<TextView>(R.id.model_model_input).text.toString())
        picker.close()
        controller.destroy()
    }

    @Test
    fun `empty offline list explains repair and never disables manual entry`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.model_key)
        val repository = OpenRouterModelRepository(AndroidModelCatalogCache(activity), { throw IOException() })
        val picker = ModelPicker(activity, repository)
        picker.start()
        awaitLoaded(activity)
        assertTrue(activity.findViewById<TextView>(R.id.model_catalog_status).text.contains("Check your connection"))
        assertTrue(activity.findViewById<Button>(R.id.model_picker_manual).isEnabled)
        assertFalse(activity.findViewById<Button>(R.id.model_picker_refresh).isEnabled.not())
        picker.close()
        controller.destroy()
    }

    private fun awaitLoaded(activity: Activity) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (activity.findViewById<Button>(R.id.model_picker_refresh).isEnabled) return
            Thread.sleep(10)
        }
        error("Fake catalog did not finish")
    }

    private fun descendants(view: android.view.View): List<android.view.View> =
        listOf(view) +
            if (view is android.view.ViewGroup) {
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            } else {
                emptyList()
            }
}
