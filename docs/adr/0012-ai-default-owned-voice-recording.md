# ADR 0012: AI-default owned voice recording

Status: Implemented locally; review and phone validation pending.
Supersedes ADR 0011's engine default, capture limit and model-selection decisions.

## Context

The task-supplied Realme Android 11 result says the phone recognizer ends after two or three
words despite silence extras. This worktree's test log has no Round 3 heading; the card's
reported device finding is motivation, not a new device result.

## Decision

New/default voice preferences use AI. Preserve an explicitly saved Phone choice. AI uses
AudioRecord, never SpeechRecognizer, for 16 kHz mono signed 16-bit PCM in an app-cache WAV.
Mic starts capture, Stop listening ends it, and Cancel discards in-flight transcription.
Show a wall-clock recording timer and bounded peak level meter. Stop after six seconds of
consecutive quiet PCM (including initial silence), or 90 seconds of capture. A main-thread
90-second deadline also handles a stalled recorder. PCM peak below 650 is considered quiet;
shorter pauses reset with speech. This deterministic threshold is not a speech detector and
must be checked on real hardware in quiet and noisy rooms before claiming OEM reliability.

Reuse the existing shared OpenRouter catalog/cache, provider, bounded client and encrypted
BYOK store. Voice has a separate non-secret model preference; the picker shows only models
advertising exact `audio` input, not audio output. Prefer advertised `google/gemini-2.5-flash`,
otherwise the first advertised audio model by ID. Never assume a hard-coded model is capable.
With no cached catalog, the configured model can be checked by the provider; cached known
text-only choices are refused locally. The provider checks live capability before uploading.
Ask only for verbatim original-language text, preserving names/numbers and without translation.

First use still requires the existing provider-audio consent before permission/capture.
RECORD_AUDIO is requested only on tap. Show plain network, unsupported-model and key failures
with explicit Phone fallback; no automatic retry, engine switch or submission. A successful
transcript only fills the editable draft. Editing/leaving cancels and rejects late results.

## Privacy and verification boundaries

Audio is deleted on success, error, cancel and capture-start failure; recording cancellation
also deletes after the file-owning writer releases its handle. Stale process-death files are
swept using the existing five-minute rule. Audio/transcripts are not logged or persisted as
preferences. Cancellation cannot recall provider uploads; provider retention/billing applies.

Fake PCM, fake recorder/provider and Robolectric catalog/UI tests use no real network or mic.
NOT TESTED ON PHONE: AudioRecord, silence threshold/noise, actual OpenRouter model transcription,
English/Hindi/Hinglish accuracy, consent/permission OEM behavior and fallback language packs.
See PHASE-ONE-TEST-LOG.md's Voice v3 checklist and CURRENT-HANDOFF.md for actual host gates.
