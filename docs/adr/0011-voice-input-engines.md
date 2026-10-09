# ADR 0011: Pause-tolerant draft-only voice with opt-in OpenRouter audio

Status: Implemented locally; phone validation pending.

## Decision

Phone speech recognition remains the default. Request partial results and silence budgets of
4 seconds complete / 3 seconds possibly complete / 5 seconds minimum speech. These are hints:
a phone speech service may ignore them. Show live partial text and an explicit stop control.
Never automatically restart recognition. Preserve partials on errors/early end; the next mic
tap appends to the current editable draft. A five-second final-result grace prevents a service
that ends without a result from leaving the mic stuck.

Setup offers device-default language, en-IN, hi-IN and extra language tags reported by the
phone recognizer. Actual availability/offline language packs are owned by that service, not
EQO. AI transcription receives the same choice as a language hint and preserves spoken language.

AI voice is explicitly selected and off by default. First use requires disclosure/consent
before microphone permission/capture. AudioRecord captures 16 kHz mono 16-bit PCM into a
random app-cache WAV, with a 60-second limit. A user's stop tap starts transcription; another
tap cancels it. Leaving the task screen cancels capture/transcription.

Reuse OpenRouterProvider, the encrypted BYOK store and the planning runtime's shared bounded,
no-redirect/no-interceptor OkHttp client. Check the configured model's public input modalities
before uploading audio; refuse models without audio and offer Phone voice. Use input_audio
(format wav) in a text chat completion asking only for a verbatim transcript. No planner,
action execution, submit callback, background service or automatic retry is involved.

## Privacy and limitations

Delete the temporary file after success, failure or cancellation, including recording-start
failures. Files stay in cache, not shared storage or chat history. Audio payloads are transient
and their string representation is redacted; no transcript/audio logging is added. Cancellation
cancels the provider HTTP call, but cannot recall audio already sent to OpenRouter or the model
provider. Provider retention and billing remain their policies. Abrupt OS process termination
cannot run finally blocks; EQO voice cache files older than five minutes are removed at the next voice setup.

NOT TESTED ON PHONE: speech-service pause behavior, language availability, AudioRecord on the
Realme, permission UI and real OpenRouter audio-capable models. CI remains the full merge gate.
