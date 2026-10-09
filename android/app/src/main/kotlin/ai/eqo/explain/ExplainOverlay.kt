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
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.ref.WeakReference
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
    private val panel = ExplainPanelState(android.os.SystemClock::elapsedRealtime)
    private val body = ScrollView(service)
    private val content =
        LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (PADDING_DP * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            background =
                GradientDrawable().apply {
                    setColor("#D9181C24".toColorInt())
                    cornerRadius = PADDING_DP * resources.displayMetrics.density
                }
            isSaveEnabled = false
        }
    private val sheet =
        LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            isSaveEnabled = false
        }
    private val output =
        TextView(service).apply {
            textSize = ANSWER_SP
            setTextColor(Color.WHITE)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isSaveEnabled = false
        }
    private val question =
        EditText(service).apply {
            setHint(R.string.explain_followup)
            textSize = QUESTION_SP
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            minHeight = (TOUCH_TARGET_DP * resources.displayMetrics.density).toInt()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
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
        android.util.Log.i("EqoExplain", "opened")
        buildPanel()
        (output.parent as? android.view.ViewGroup)?.removeView(output)
        val scroll = ScrollView(service).apply { addView(output) }
        content.addView(scroll)
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
            question.setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    ask(question.text.toString())
                    true
                } else {
                    false
                }
            }
            button(R.string.explain_ask) { ask(question.text.toString()) }
            button(R.string.explain_read) { speak() }
            speech =
                TextToSpeech(service) { status ->
                    speechReady = status == TextToSpeech.SUCCESS
                    if (speechReady) {
                        val language = speech?.setLanguage(Locale.getDefault())
                        speechReady = language != TextToSpeech.LANG_MISSING_DATA &&
                            language != TextToSpeech.LANG_NOT_SUPPORTED
                        if (ExplainSettings.autoRead(service) && answer.isNotBlank()) speak()
                    }
                }
            ask(service.getString(R.string.explain_initial_question))
        }
        // Accessibility disabled while sheet is open: discard the entire in-memory session.
        scope.launch {
            while (!closed) {
                delay(SERVICE_CHECK_MS)
                if (EQOAccessibilityService.getInstance() !== service ||
                    (session != null && !ExplainSettings.allowed(service))
                ) {
                    close()
                }
            }
        }
    }

    private fun buildPanel() {
        sheet.removeAllViews()
        (content.parent as? android.view.ViewGroup)?.removeView(content)
        val header = LinearLayout(service)
        header.setBackgroundColor("#D9181C24".toColorInt())
        val drag =
            Button(service).apply {
                setText(R.string.explain_resize)
                setOnClickListener {
                    panel.size = ExplainPanelState.Size.entries[(panel.size.ordinal + 1) % 3]
                    applyPanel()
                }
            }
        var down = 0f
        drag.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> down = event.rawY
                android.view.MotionEvent.ACTION_UP -> {
                    val distance = down - event.rawY
                    if (kotlin.math.abs(distance) > DRAG_THRESHOLD_DP * service.resources.displayMetrics.density) {
                        val change = if (distance > 0) 1 else -1
                        panel.size = ExplainPanelState.Size.entries[(panel.size.ordinal + change).coerceIn(0, 2)]
                        applyPanel()
                    } else {
                        view.performClick()
                    }
                }
            }
            true
        }
        header.addView(drag, LinearLayout.LayoutParams(0,
            (CHIP_HEIGHT_DP * service.resources.displayMetrics.density).toInt(), 1f))
        header.addView(
            Button(service).apply {
                setText(R.string.explain_see_screen)
                setOnClickListener {
                    panel.seeScreen()
                    question.clearFocus()
                    service
                        .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(sheet.windowToken, 0)
                    applyPanel()
                    scope.launch {
                        delay(ExplainPanelState.SEE_MS)
                        if (!closed) applyPanel()
                    }
                }
            },
        )
        header.addView(
            Button(service).apply {
                setText(R.string.explain_close)
                setOnClickListener { close() }
            },
        )
        sheet.addView(header)
        body.removeAllViews()
        body.addView(content)
        sheet.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        applyPanel()
    }

    private fun applyPanel() {
        params.height =
            panel.height(
                manager.currentWindowMetrics.bounds.height(),
                (CHIP_HEIGHT_DP * service.resources.displayMetrics.density).toInt(),
            )
        params.alpha = panel.alpha
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE or
            if (panel.seeThrough) {
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                0
            }
        body.visibility = if (panel.size == ExplainPanelState.Size.SMALL) View.GONE else View.VISIBLE
        if (attached) manager.updateViewLayout(sheet, params)
    }

    private fun button(
        label: Int,
        action: () -> Unit,
    ) {
        content.addView(
            Button(service).apply {
                setText(label)
                textSize = BUTTON_SP
                minHeight = (TOUCH_TARGET_DP * service.resources.displayMetrics.density).toInt()
                setOnClickListener { action() }
            },
        )
    }

    private fun attach() {
        if (!attached && !closed) {
            try {
                manager.addView(sheet, params)
                attached = true
            } catch (_: WindowManager.BadTokenException) {
                android.util.Log.i("EqoExplain", "explain: text, error BadTokenException")
                close()
            } catch (_: WindowManager.InvalidDisplayException) {
                android.util.Log.i("EqoExplain", "explain: text, error InvalidDisplayException")
                close()
            }
        }
    }

    private fun detach() {
        if (attached) {
            attached = false
            try {
                manager.removeViewImmediate(sheet)
            } catch (_: IllegalArgumentException) {
                // Android already removed the overlay when the accessibility service disconnected.
            }
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
                delay(DETACH_DELAY_MS)
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
                output.text = service.getString(R.string.explain_result, notice, answer)
                android.util.Log.i("EqoExplain", "explain: ${active.sourceType}, ok")
                attach()
                if (ExplainSettings.autoRead(service)) speak()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                output.setText(errorMessage(failure))
                val source = session?.sourceType ?: "text"
                android.util.Log.i("EqoExplain", "explain: $source, error ${failure.javaClass.simpleName}")
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
                val queue = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                speech?.speak(chunk, queue, null, "explain-$index")
            }
        }
    }

    private fun close() {
        if (closed) return
        closed = true
        android.util.Log.i("EqoExplain", "closed")
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
        if (current?.get() === this) current = null
    }

    companion object {
        private var current: WeakReference<ExplainOverlay>? = null
        private const val PADDING_DP = 24
        private const val ANSWER_SP = 22f
        private const val QUESTION_SP = 20f
        private const val BUTTON_SP = 18f
        private const val TOUCH_TARGET_DP = 56
        private const val CHIP_HEIGHT_DP = 64
        private const val DRAG_THRESHOLD_DP = 24
        private const val SERVICE_CHECK_MS = 1000L
        private const val DETACH_DELAY_MS = 350L

        fun open(service: EQOAccessibilityService) {
            current?.get()?.close()
            val overlay = ExplainOverlay(service)
            current = WeakReference(overlay)
            overlay.start()
        }
    }
}
