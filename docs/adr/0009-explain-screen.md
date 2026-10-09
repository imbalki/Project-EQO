# ADR-0009: Read-only Explain screen from other apps

Status: implemented locally; host checks and phone validation recorded in PHASE-ONE-TEST-LOG.md. NOT TESTED ON PHONE.

## Decision

- Entry is a Quick Settings tile and a separately user-enabled ongoing notification. Both launch only a transient translucent entry activity, which finishes before reading. EQO's main/task window never opens first. A short delay allows the shade/activity to disappear; if the active root is EQO or System UI, the flow asks the user to open another app rather than reading behind it. Typed screen requests show shortcut guidance, not an automation plan. Voice input is not a dependency.
- A new `ai.eqo.explain` flow reads the active accessibility tree directly, skipping EQO/foreign subtrees, invisible text and password subtrees. Limits: 500 nodes, 12,000 text characters, depth 50. It never calls action executors, approvals, gestures, typing, takeover or own-window guard mutation.
- Fewer than four labelled nodes, or an image/formula/diagram question, requests an in-memory JPEG through the existing accessibility screenshot primitive used by files/attachments (`takeScreenshotAndEncode`). No Gallery file, staging copy or last_screenshot is created. Model capability comes only from explicit `architecture.input_modalities` for the exact configured OpenRouter model in the existing public model catalog cache. Unknown capability means text-only, with refresh/change-model guidance; never infer vision from model names. Screen/package/text are checked before and after capture; a changed screen discards the image. Password fields or an incomplete privacy traversal mean text-only.
- The selected model and existing encrypted OpenRouter credential path are reused. The system prompt asks for device-language, plain explanations, main controls, next steps and simple study explanations. Screen text and images are untrusted data. There are no automation tools in the request.
- `TYPE_ACCESSIBILITY_OVERLAY` supplies the translucent, large-text result sheet, typed follow-ups and Android TextToSpeech. No SYSTEM_ALERT_WINDOW grant is required. Follow-ups retain the original snapshot/image, not a newly foreground app. Close cancels UI work and active HTTP calls, prevents queued calls from starting, clears the source snapshot, stops/shuts down speech and drops context; disabling accessibility, revoking sharing or launching a replacement sheet also closes it. Pending provider requests are additionally bounded by 90 seconds; late captures/answers cannot refill the closed session or reopen the sheet. Provider retention is outside EQO's control and is disclosed.
- On the inspected origin/main base `4a49832`, vision-locate's consent was absent; remote main was checked again at `92802e7` (voice-only addition) and still has no shared screen-sharing consent. Use the separately authorized `Explain screen: allow sending what is on screen to your AI provider`, OFF by default, plus first-use disclosure. Only Boolean preferences persist. If vision-locate lands before integration, the lead must replace this separate gate with the shared screen-sharing consent; do not ship two consent settings.
- `ScreenProtectionPolicy` is an explicit permissive-default seam: next phase: protected-screen setting. This flow does not add a secure-window policy refusal. Android may itself refuse a protected screenshot; that falls back to text. Skipping password fields and limiting image transmission are separate privacy hygiene, not a protected-app denylist.
- Diagnostics are only source type and success/error class; never screen text, image, question, answer, provider response body or credential. Screen context is not serialized, saved in view state, persisted to preferences, files or conversation stores. Read aloud uses the device's configured TTS engine, which may require network voice data; the phone checklist must verify its behavior.

## UX recovery amendment (t_e84b3eaa)

Owner phone testing showed that a killed notification foreground service made the second Explain
entry disappear. The optional notification is now also posted directly from app callbacks invoked
by the accessibility service on connection and window-state changes. This deliberately avoids
restarting an FGS from every background event on recent Android. The accessibility service is still
subject to OS/user termination; recovery is opportunistic, not a keep-alive guarantee. Edge handle
Explain is the recommended primary entry, with accessibility button and QS/notification alternatives.
All routes still finish the transient activity before capture and log only fixed entry-source labels.

Panel sizes are collapsed header, 25% and 35% of display height. The body background is 85%-opaque,
not the text. See screen temporarily sets the whole overlay to 5% opacity and FLAG_NOT_TOUCHABLE
for five seconds; the timer restores interaction. No dim/full-screen input window is added and
FLAG_SECURE remains set. Timer-only restoration is intentional: a non-touchable panel cannot accept
the same tap without defeating the touch-through requirement. Android 11 tile installation uses
manual editor guidance; API 33+ additionally offers requestAddTileService with system confirmation.

## Validation limits
Fake model/source tests cover decisions, consent, privacy bounds, capability fallback, session reuse and late-result rejection. Robolectric checks exercise Android root extraction, screenshot revalidation, preferences, catalog metadata and notification target routing without network. OEM shade-collapse timing, actual accessibility overlay/keyboard layout, background entry restrictions, screenshots, provider answers and speech require the phone checklist. CI remains the full gate.
