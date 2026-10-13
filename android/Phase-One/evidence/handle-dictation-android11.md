# Handle dictation: Android 11 recording investigation

Task: t_e1fe42ac. No device commands or device access were used.

## Conclusion

Android 11 AOSP allows microphone capture from an enabled, system-bound accessibility service without creating a microphone foreground service. EQO can reuse its existing RECORD_AUDIO permission and existing accessibility service declaration. This is not a claim of successful recording on the owner's phone or on every OEM build.

The existing EQOAccessibilityService is bound by the system and the handle uses TYPE_ACCESSIBILITY_OVERLAY with FLAG_NOT_FOCUSABLE. Tapping its mic does not launch an Activity or steal the other app's input focus. It records only after an explicit user tap and stops on the second tap. Cancel and the 90-second cap discard the session without transcription/insertion. The existing recorder's silence/end callback also cancels rather than inserting automatically.

## Primary sources inspected

- Android audio sharing documentation: https://developer.android.com/guide/topics/media/sharing-audio-input
  Android 11 retains Android 10 input priority rules and provides capture-silencing information. Other apps, sensitive capture and calls can constrain recording.
- Android 11 AccessibilityServiceConnection, bindLocked(), lines 94–108:
  https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-11.0.0_r1/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java
  The system binds with BIND_FOREGROUND_SERVICE_WHILE_AWAKE and BIND_INCLUDE_CAPABILITIES.
- Android 11 AudioPolicyService, updateUidStates_l(), lines 612–647:
  https://android.googlesource.com/platform/frameworks/av/+/refs/tags/android-11.0.0_r1/services/audiopolicy/service/AudioPolicyService.cpp
  The accessibility UID branch permits capture when the assistant is not on top and no conflicting sensitive capture/call prevents it. This branch is independent of a microphone foreground service declaration. An unknown/inactive UID can still be silenced.
- Android 11 OomAdjuster capability propagation:
  https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-11.0.0_r1/services/core/java/com/android/server/am/OomAdjuster.java
  BIND_INCLUDE_CAPABILITIES propagates the binding client's capabilities. Do not confuse this system-bound accessibility service path with an ordinary app-started background foreground service.

## Limits for lead/device validation

Cancel is available while recording or transcribing. Once transcription finishes, insertion is an irreversible commit: Cancel is removed before the paste action, and that short paste/verification phase completes rather than falsely reporting that already-pasted text was cancelled. Host fake tests cover this boundary.

Host unit tests cannot prove that live PCM arrives rather than silence, that the runtime permission dialog works on an OEM build, or that Google Keep saves pasted text persistently after leaving the note. The implementation checks the captured native field after ACTION_PASTE; this only verifies current accessibility text, not a target app's eventual disk persistence. No ACTION_SET_TEXT fallback is used. Failed verification retains a sensitive clipboard transcript and asks the owner to paste it manually.

No manifest entry, permission, provider, client or runtime dependency was added. Neither audio nor target text is logged. Provider traffic uses the configured OpenRouter AI audio model, encrypted BYOK credential store and the existing voice network policy.

## Owner-approved clipboard policy (option B)

Android 11 ClipboardService denies clipboard reads to an ordinary accessibility-service UID when another app owns input focus. A non-focusable handle does not meet the focused-UID exception. Clipboard writes are allowed without focus. The implementation therefore never reads, snapshots or restores the clipboard and never takes focus from the target app. It writes the transcript with the sensitive flag and leaves it on the clipboard after either successful insertion or manual-paste fallback, replacing the previous clipboard contents.

Primary source: https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-11.0.0_r1/services/core/java/com/android/server/clipboard/ClipboardService.java — clipboardAccessAllowed(), OP_READ_CLIPBOARD versus OP_WRITE_CLIPBOARD. The read exceptions include the default IME, a privileged background permission, and specific content-capture/autofill services; ordinary accessibility binding is not one of them.

The owner explicitly approved option B on this card: success says "Dictation inserted. It is also on your clipboard." Failed paste/verification says "This app did not accept the text. It is on your clipboard; paste it yourself." No focus-taking bridge, privileged permission, manifest change or new input-method service is introduced. Fake tests cover retention and these disclosures, not real platform paste acceptance.
