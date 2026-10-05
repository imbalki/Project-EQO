/*
 * EQO (TASK-015, issue #20): model key setup (UF-02, screens S-04/S-05).
 *
 * REQ-BYOK-01: key entry with live setup validation — a rejected key shows a named
 * failure state with a repair action, a valid key shows "Connected".
 * REQ-BYOK-02: the key is stored through the Android-Keystore-backed credential store
 * and is never logged (LogRedactor scrubs registered secrets; nothing here prints it).
 */
package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.core.llm.ConnectionTestRunner
import ai.eqo.core.llm.ConnectionTestState
import ai.eqo.core.llm.error.LLMError
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.study.FailureClass
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import okhttp3.OkHttpClient

class ModelKeySetupActivity : Activity() {
    private val credentialStore by lazy { AndroidProviderCredentialStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_key)
        findViewById<Button>(R.id.model_key_validate_button).setOnClickListener { validate() }
        findViewById<Button>(R.id.setup_return_button).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        findViewById<Button>(R.id.model_key_validate_button).isEnabled = StudySetup.modelKey != ModelKeyState.VALIDATING
        val status = findViewById<TextView>(R.id.model_key_status)
        val guidance = findViewById<TextView>(R.id.model_key_guidance)
        when (StudySetup.modelKey) {
            ModelKeyState.NOT_SET -> {
                status.setText(R.string.model_key_not_set)
                guidance.text = lastFailureGuidance
            }
            ModelKeyState.VALIDATING -> {
                status.setText(R.string.model_key_validating)
                guidance.text = ""
            }
            ModelKeyState.CONNECTED -> {
                status.setText(R.string.model_key_connected)
                guidance.text = ""
            }
            ModelKeyState.FAILED -> {
                status.setText(R.string.model_key_validation_failed)
                guidance.text = lastFailureGuidance
            }
        }
    }

    /**
     * Runs the real provider probe off the main thread (REQ-BYOK-01). The candidate key
     * is registered as a secret before the probe and is never rendered in any outcome.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun validate() {
        val key = findViewById<EditText>(R.id.model_key_input).text.toString().trim()
        val model = findViewById<EditText>(R.id.model_model_input).text.toString().trim()
        if (key.isBlank() || model.isBlank()) {
            lastFailureGuidance = getString(R.string.model_empty)
            StudySetup.modelKey = ModelKeyState.NOT_SET
            render()
            return
        }
        StudySetup.modelKey = ModelKeyState.VALIDATING
        render()
        Thread {
            val state =
                try {
                    ConnectionTestRunner(OkHttpClient())
                        .run(endpoint = OPENROUTER_ENDPOINT, apiKey = key, model = model)
                } catch (e: Exception) {
                    // A probe that could not run is a typed NETWORK state, never a crash and
                    // never a silent pass; the exception text is not rendered (it may echo the
                    // endpoint), only its class decides the state.
                    ConnectionTestState.Failed(
                        provider = "OpenRouter",
                        model = model,
                        error = LLMError.Network,
                        testedAtMillis = System.currentTimeMillis(),
                    )
                }
            runOnUiThread { handleValidation(state, key) }
        }.start()
    }

    private fun handleValidation(
        state: ConnectionTestState,
        key: String,
    ) {
        when (state) {
            is ConnectionTestState.Connected -> {
                ai.eqo.task.StudyModelChoice
                    .save(applicationContext, state.model)
                // REQ-BYOK-02: Keystore-backed storage only.
                val stored = credentialStore.write(ProviderCredentialId.ApiKey("openrouter"), key)
                StudySetup.modelKey =
                    if (stored is CredentialStoreResult.Success) ModelKeyState.CONNECTED else ModelKeyState.FAILED
                lastFailureGuidance =
                    if (stored is CredentialStoreResult.Success) {
                        ""
                    } else {
                        getString(R.string.model_storage_failed)
                    }
            }
            is ConnectionTestState.Failed -> {
                StudySetup.modelKey = ModelKeyState.FAILED
                val recovery = FailureClass.forLlmErrorCode(state.error.code)
                lastFailureGuidance =
                    getString(
                        when (recovery) {
                            FailureClass.MODEL_UNAUTHORIZED -> R.string.model_error_auth
                            FailureClass.MODEL_RATE_LIMITED -> R.string.model_error_rate
                            FailureClass.MODEL_CREDIT -> R.string.model_error_credit
                            FailureClass.MODEL_INCOMPATIBLE -> R.string.model_error_model
                            else -> R.string.model_error_network
                        },
                    )
            }
            else -> {
                StudySetup.modelKey = ModelKeyState.NOT_SET
                lastFailureGuidance = ""
            }
        }
        render()
    }

    private var lastFailureGuidance: String = ""

    companion object {
        /** OpenRouter API base; the probe appends /chat/completions itself. */
        const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1"
    }
}
