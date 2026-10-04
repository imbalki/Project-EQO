/*
 * EQO (TASK-009): the on-device test app for the accessibility device records.
 * A minimal screen with a button, a text input, a status line and a tall
 * scrollable list so observe/tap/scroll/type can each be proven through the
 * single EQO accessibility service. Layout is programmatic; the resource ids
 * live in res/values/ids.xml so the a11y node tree exposes real view id names.
 */
package ai.eqo.test

import ai.eqo.platform.test.R
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

@SuppressLint("SetTextI18n")
class EqoTestTargetActivity : Activity() {
    private lateinit var scrollView: ScrollView
    private lateinit var status: TextView
    private lateinit var prompt: TextView

    /**
     * TASK-012: user-facing loop controls for the action-loop device scenario
     * (resume / stop). Invoked from the controls' click listeners - i.e. for
     * the explicit user gesture; the resume handler mints the
     * UserResumeConfirmation token for exactly that gesture (SF-4).
     */
    @Volatile
    var onResumeTap: (() -> Unit)? = null

    @Volatile
    var onStopTap: (() -> Unit)? = null

    /** Tapping the red prompt banner counts as tapping the current control. */
    @Volatile
    var onBannerTap: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastInstance = this

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }

        // TASK-009: owner prompts are a full-width banner at the very top of
        // the screen - large, bold, white on red, above the floating
        // accessibility icon - with a vibration + beep on every change so the
        // owner notices the prompt (lead requirement after the small grey
        // prompt line went unseen).
        prompt =
            TextView(this).apply {
                id = R.id.eqo_test_prompt
                text = "PROMPT: none"
                textSize = 28f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.RED)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(16, 24, 16, 24)
                visibility = View.GONE
                setOnClickListener { onBannerTap?.invoke() }
            }
        root.addView(
            prompt,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(
            TextView(this).apply {
                text = "EQO TEST TARGET"
                textSize = 22f
            },
        )

        status =
            TextView(this).apply {
                id = R.id.eqo_test_status
                text = "STATUS EMPTY MARKER"
                textSize = 16f
            }
        root.addView(status)

        // TASK-012: the action-loop scenario's user controls (resume / stop).
        // Large buttons right under the red banner so the owner cannot miss
        // them; the banner itself forwards to the current control.
        val controls =
            LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(
            Button(this).apply {
                id = R.id.eqo_test_resume
                text = "RESUME LOOP"
                textSize = 20f
                setOnClickListener { onResumeTap?.invoke() }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        controls.addView(
            Button(this).apply {
                id = R.id.eqo_test_stop
                text = "STOP LOOP"
                textSize = 20f
                setOnClickListener { onStopTap?.invoke() }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        root.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val button =
            Button(this).apply {
                id = R.id.eqo_test_button
                text = "PRESS ME"
                setOnClickListener { status.text = "BUTTON PRESSED MARKER" }
            }
        root.addView(button)

        val input =
            EditText(this).apply {
                id = R.id.eqo_test_input
                hint = "eqo test input"
            }
        root.addView(input)

        val submit =
            Button(this).apply {
                id = R.id.eqo_test_submit
                text = "SUBMIT TEXT"
                setOnClickListener { status.text = "SUBMITTED: ${input.text}" }
            }
        root.addView(submit)

        // Tall list: eqo_item_00 .. eqo_item_24, most of them below the fold.
        repeat(ITEM_COUNT) { index ->
            root.addView(
                TextView(this).apply {
                    text = "eqo_item_%02d".format(index)
                    textSize = 18f
                    setPadding(0, 40, 0, 40)
                },
            )
        }

        scrollView =
            ScrollView(this).apply {
                id = R.id.eqo_test_scroll
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        setContentView(scrollView)

        // TASK-009: `am start ... --ez runRecords true` runs the device records
        // in-process (see EqoDeviceRecordsDriver).
        if (intent?.getBooleanExtra(EXTRA_RUN_RECORDS, false) == true) {
            EqoDeviceRecordsDriver.start(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (lastInstance === this) lastInstance = null
    }

    /** Current list scroll offset: proof that a11y scroll actually moved the view. */
    fun scrollOffset(): Int = scrollView.scrollY

    /** Marker line the device records assert on after tap/type actions. */
    fun statusText(): String = status.text.toString()

    /** On-screen instruction for the owner-assisted record moments. */
    fun setPrompt(message: String) {
        runOnUiThread {
            if (message == "none") {
                prompt.text = "PROMPT: none"
                prompt.visibility = View.GONE
            } else {
                prompt.text = "PROMPT: $message"
                prompt.visibility = View.VISIBLE
                notifyOwner()
            }
        }
    }

    /**
     * Vibration + short beep so the owner notices a new prompt. Both signals are
     * best-effort and MUST never crash the records activity: this test APK
     * declares no VIBRATE permission by design (the harness adds no
     * permissions), so [Vibrator.vibrate] throws SecurityException on stock
     * builds. TASK-009 fixes: an unguarded vibrate() crashed the activity the
     * moment a prompt was set, closing every owner touch window before it
     * opened. [OwnerPromptSafetyTest] guards this.
     */
    private fun notifyOwner() {
        runCatching {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(500)
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        }
    }

    companion object {
        const val ITEM_COUNT = 25

        /** Intent extra: run the TASK-009 device records at activity start. */
        const val EXTRA_RUN_RECORDS = "runRecords"

        /** Intent extra: comma list of record ids to run (1,2,3,4,5; default all). */
        const val EXTRA_RECORDS = "records"

        @Volatile
        var lastInstance: EqoTestTargetActivity? = null
    }
}
