// Origin: EQO-authored handle adapter reusing the AI voice recorder, settings and provider.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.DictationTarget
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.core.llm.InputAudio
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

internal object HandleDictation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: HandleDictationSession? = null
    private var target: DictationTarget? = null
    private var permissionHelp = false

    @Suppress("TooGenericExceptionCaught") // Service UI boundary: never expose credential/node exception payloads.
    fun tap(context: Context) {
        try {
            val current = session
            if (current?.state == DictationState.RECORDING) {
                current.stop()
            } else if (current?.state !in setOf(DictationState.TRANSCRIBING, DictationState.INSERTING)) {
                start(context)
            }
        } catch (_: Exception) {
            cancel()
            changed(DictationState.FAILED, DictationFailure.RECORDING)
        }
    }

    private fun start(context: Context) {
        if (!ai.eqo.handle.prepareHandlePanel(requestPause = true)) {
            changed(DictationState.FAILED, DictationFailure.TARGET)
            return
        }
        val app = context.applicationContext
        val settings = VoiceSettings(app)
        val key = AndroidProviderCredentialStore(app).read(ProviderCredentialId.ApiKey("openrouter"))
        val permission = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val setup =
            DictationSetup(
                key = !(key as? CredentialStoreResult.Success)?.value.isNullOrBlank(),
                model = settings.engine == VoiceEngine.OPENROUTER && !settings.audioModel.isNullOrBlank(),
                consent = settings.consent,
                permission = permission,
            )
        val service = context as? EQOAccessibilityService
        target = if (setup.failure() == null && service != null) DictationTarget.capture(service) else null
        val captured = target
        val model = settings.audioModel.orEmpty()
        val language = settings.language
        val next =
            HandleDictationSession(
                scope,
                { VoiceAudioRecorder(app.cacheDir, scope) },
                { file ->
                    withContext(Dispatchers.IO) {
                        // Same disclosure, BYOK store and bounded network policy as TaskVoiceInput.
                        check(VoiceSettings(app).consent)
                        val catalog = VoiceModelPicker.loadModels(app)
                        TaskPlanningRuntime.voiceProvider(app).transcribe(
                            model,
                            InputAudio(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)),
                            language,
                            catalog,
                        )
                    }
                },
                ::changed,
            )
        session = next
        next.start(setup, captured?.let { it::insert })
    }

    fun cancel() {
        if (session?.state == DictationState.INSERTING) return
        session?.cancel()
        target?.close()
        target = null
    }

    private fun changed(
        state: DictationState,
        failure: DictationFailure?,
    ) {
        EdgeHandleFeatures.dictationBusy =
            state in setOf(DictationState.RECORDING, DictationState.TRANSCRIBING, DictationState.INSERTING)
        EdgeHandleFeatures.dictationCommitting = state == DictationState.INSERTING
        permissionHelp = failure == DictationFailure.PERMISSION
        EdgeHandleFeatures.dictationListening = state == DictationState.RECORDING
        EdgeHandleFeatures.dictationStatus =
            when (state) {
                DictationState.IDLE -> 0
                DictationState.RECORDING -> R.string.dictation_listening
                DictationState.TRANSCRIBING -> R.string.dictation_transcribing
                DictationState.INSERTING -> R.string.dictation_transcribing
                DictationState.INSERTED -> R.string.dictation_inserted
                DictationState.CANCELLED -> R.string.dictation_cancelled
                DictationState.FAILED -> failureMessage(failure)
            }
        if (!EdgeHandleFeatures.dictationBusy) {
            target?.close()
            target = null
        }
        EdgeHandleFeatures.dictationChanged()
    }

    internal fun failureMessage(failure: DictationFailure?): Int =
        when (failure) {
            DictationFailure.KEY -> R.string.dictation_key
            DictationFailure.MODEL -> R.string.dictation_model
            DictationFailure.CONSENT -> R.string.dictation_consent
            DictationFailure.PERMISSION -> R.string.dictation_permission
            DictationFailure.TARGET -> R.string.dictation_target
            DictationFailure.COPIED -> R.string.dictation_copied
            else -> R.string.dictation_error
        }

    fun openSettings(context: Context) {
        cancel()
        context.startActivity(
            Intent(
                context,
                if (permissionHelp) TaskActivity::class.java else ai.eqo.onboarding.SetupHubActivity::class.java,
            ).putExtra(TaskActivity.FOCUS_REQUEST, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
