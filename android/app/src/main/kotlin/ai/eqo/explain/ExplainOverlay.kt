package ai.eqo.explain

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.core.llm.error.LLMError
import ai.eqo.core.llm.error.LLMException
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/** Accessibility overlay, not an automation control. All context dies with this sheet. */
@Suppress("TooManyFunctions")
class ExplainOverlay private constructor(
    private val service: EQOAccessibilityService,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val manager = service.getSystemService(WindowManager::class.java)
    private var session: ExplainSession? = null
    private var attached = false
    private var closed = false
    private var busy = false
    private var speechReady = false
    private var answer = ""
    private val content =
        LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background =
                GradientDrawable().apply {
                    setColor(Color.argb(242, 24, 28, 36))
                    cornerRadius = 24f
                }
            isSaveEnabled = false
        }
    private val output =
        TextView(service).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isSaveEnabled = false
        }
    private val question =
        EditText(service).apply {
            setHint(R.string.explain_followup)
            textSize = 20f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            minHeight = 64
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            filters = arrayOf(android.text.InputFilter.LengthFilter(ExplainSession.MAX_QUESTION))
        }
    private var speech: TextToSpeech? = null
    private val params =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

    private fun start() {
        (output.parent as? android.view.ViewGroup)?.removeView(output)
        val scroll = ScrollView(service).apply { addView(output) }
        content.addView(scroll, LinearLayout.LayoutParams(-1, (service.resources.displayMetrics.heightPixels * 0.4).toInt()))
        button(R.string.explain_close) { close() }
        if (!ExplainSettings.allowed(service)) {
            output.setText(R.string.explain_consent)
            button(R.string.explain_allow) {
                ExplainSettings.set(service, "allow_provider", true)
                detach()
                content.removeAllViews()
                start()
            }
            attach()
        } else {
            content.addView(question)
            button(R.string.explain_ask) { ask(question.text.toString()) }
            button(R.string.explain_read) { speak() }
            speech =
                TextToSpeech(service) { status ->
                    speechReady = status == TextToSpeech.SUCCESS
                    if (speechReady) {
                        val language = speech?.setLanguage(Locale.getDefault())
                        speechReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
                        if (ExplainSettings.autoRead(service) && answer.isNotBlank()) speak()
                    }
                }
            ask(service.getString(R.string.explain_initial_question))
        }
        // Accessibility disabled while sheet is open: discard the entire in-memory session.
        scope.launch {
            while (!closed) {
                delay(1000)
                if (EQOAccessibilityService.getInstance() !== service) close()
            }
        }
    }

    private fun button(
        label: Int,
        action: () -> Unit,
    ) {
        content.addView(
            Button(service).apply {
                setText(label)
                textSize = 18f
                minHeight = (56 * service.resources.displayMetrics.density).toInt()
                setOnClickListener { action() }
            },
        )
    }

    private fun attach() {
        if (!attached && !closed) {
            manager.addView(content, params)
            attached = true
        }
    }

    private fun detach() {
        if (attached) {
            manager.removeViewImmediate(content)
            attached = false
        }
    }

    @Suppress("TooGenericExceptionCaught") // UI boundary never renders raw provider/capture exceptions
    private fun ask(prompt: String) {
        if (busy || closed || prompt.isBlank()) return
        busy = true
        speech?.stop()
        question.text.clear()
        detach()
        scope.launch {
            try {
                delay(350)
                val active =
                    session ?: ExplainSession(
                        AndroidExplainSource(service),
                        AndroidExplainModel(service),
                        { ExplainSettings.allowed(service) },
                        ready = {
                            output.setText(R.string.explain_loading)
                            attach()
                        },
                    ).also { session = it }
                answer = active.ask(prompt)
                val notice =
                    when (active.textOnlyReason) {
                        "vision" -> service.getString(R.string.explain_text_only_model)
                        "password" -> service.getString(R.string.explain_text_only_password)
                        "capture" -> service.getString(R.string.explain_text_only_capture)
                        else -> ""
                    }
                output.text = notice + answer
                android.util.Log.i("EqoExplain", "explain: ${active.sourceType}, ok")
                attach()
                if (ExplainSettings.autoRead(service)) speak()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                output.setText(errorMessage(failure))
                android.util.Log.i("EqoExplain", "explain: text, error ${failure.javaClass.simpleName}")
                attach()
            } finally {
                busy = false
            }
        }
    }

    private fun errorMessage(failure: Exception): Int =
        when {
            failure is LLMException && failure.error == LLMError.Network -> R.string.explain_no_internet
            failure is IOException && failure !is LLMException -> R.string.explain_no_internet
            failure is LLMException -> R.string.explain_model_refused
            failure.message in setOf("No key set", "No model set") -> R.string.explain_no_key
            failure.message == "Open another app first" -> R.string.explain_open_app
            failure.message == "Screen sharing is off" -> R.string.explain_consent
            else -> R.string.explain_unavailable
        }

    private fun speak() {
        if (!speechReady) {
            android.widget.Toast
                .makeText(service, R.string.explain_tts_unavailable, android.widget.Toast.LENGTH_LONG)
                .show()
        } else if (answer.isNotBlank()) {
            answer.chunked(TextToSpeech.getMaxSpeechInputLength()).forEachIndexed { index, chunk ->
                speech?.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "explain-$index")
            }
        }
    }

    private fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        session?.close()
        session = null
        speech?.stop()
        speech?.shutdown()
        speech = null
        answer = ""
        question.text.clear()
        output.text = ""
        detach()
        if (current === this) current = null
    }

    companion object {
        private var current: ExplainOverlay? = null

        fun open(service: EQOAccessibilityService) {
            current?.close()
            current = ExplainOverlay(service).also { it.start() }
        }
    }
}
