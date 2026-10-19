# Handle audio notes and screenshot — t_0c715719

## Approved recording design

The owner's option-A decision on the card supersedes its original no-new-permission restriction only for microphone foreground-service support. The existing handle settings Activity provides a visible, explicit Start recording confirmation and requests microphone access if absent. It stays visible until the service has entered the microphone foreground-service type and started the recorder, then moves the EQO task behind the previous app. There is no automatic or sticky microphone restart.

The only new manifest declarations are FOREGROUND_SERVICE_MICROPHONE and the non-exported RecordingService with foregroundServiceType="microphone". FOREGROUND_SERVICE and RECORD_AUDIO were already present. No runtime library/dependency was added.

Platform references:
- https://developer.android.com/about/versions/11/privacy/foreground-services
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types#microphone

## Behaviour and limits

- Record is default-on and paired with Mic in the shortcuts panel. During capture the handle reduces to a red dot in a touch target; touch/hover expands elapsed time, the captured limit and Stop, then collapses after the existing three-second timeout. The recording service owns a quiet low-importance, soundless, vibration-free notification with Stop only.
- AAC in an MPEG-4 container is written directly to a pending MediaStore audio row, not an unlimited RAM buffer or cache file. Stop finalizes and publishes the dated .m4a. Android 11 uses Music/EQO Recordings; API 31+ uses Recordings/EQO.
- The typed whole-minute limit is snapshotted at start. Empty input, 0 or No limit means no user-configured duration limit. Native maximum-duration signalling is used when representable; monotonic polling also checks limits. Storage and Android/native recorder constraints still apply. Silence does not stop a note.
- Calls/communication audio modes, another capture, client silencing, dictation activity, takeover, unavailable accessibility service, secure/own foreground windows after the visible-start handoff, disabled handle/Record, permission revocation and low storage stop or refuse recording. These checks never resume an automation task.
- Foreground-window checks fail closed when a root or package identity is unavailable. Consequently a lock screen, screen-off transition or an app that exposes no accessible root may stop/save a note; uninterrupted locked/screen-off recording is not claimed. This is deliberately conservative, not a bypass of own/secure-window rules.
- Audio stays local. Done dismisses only ephemeral UI, never the saved file. Initialization failure may remove an incomplete, unplayable pending row; finalization failure never deletes recorded data. Abrupt process death/power loss may leave an unfinalized pending MP4; no unsupported crash-recovery guarantee is made.
- Recording requires enabled notifications and a non-blocked Audio notes channel so a visible indicator remains even when another app, its IME or the owner hides the handle. Disabling notifications during capture stops/saves it. The channel is quiet, not invisible.
- Transcribe is optional and uses the configured OpenRouter AI voice model, credential store, disclosure/consent and existing provider. This implementation deliberately uses the original card's allowed fallback: only whole notes of at most 90 seconds are supported. Longer notes receive a plain message and remain saved/shareable. They are never truncated, split incorrectly, or sent in the background.
- AAC is decoded locally into bounded 16 kHz mono PCM/WAV for the existing WAV-only endpoint. Transcript and audio are never logged. Copy marks clipboard content sensitive. Save as Keep note prepares one CREATE_NOTE through TaskActivity's existing plan preparation/review/approval path, not direct editor typing or a new network client.
- The earlier gated screenshot checkpoint is preserved: default-on switch, overlay removal before capture, password/secure/takeover refusal, MediaStore Pictures/EQO, Share/Done compact result.

## Verification boundary

The required local Gradle checks and repository provenance checks are recorded in the final card comment. JVM/Robolectric tests do not prove native microphone/codec behaviour or Android background execution on a physical device. The lead must test Android 11 screen-off/long capture with another app foreground, native AAC decode and AI transcription, call/competing-recorder interruptions, denied/revoked permissions, secure screens, low storage, real Files/music visibility, receiving share apps and Keep's real Save screen. Android 14+ also needs real while-in-use/FGS and notification-permission checks. No adb or device access is used by this task.
