# Product decisions (owner)

Decisions made by the owner, in the owner's words where possible. Agents and the lead must follow these and must not re-litigate them. Changes to behaviour, safety posture or ideology need the owner's permission first.

## 2026-10-11

1. **The AI plans and finishes.** No strict whitelist rules in planning. Fixed protections are only: plan approval before running, no typing into password fields, EQO's own windows cannot be tapped by the agent, takeover/pause/stop, and resume only by the user's own tap.
2. **Approval is the default; auto mode is an opt-in setting** with a security warning. It is asked once during first setup, with a gentle line that EQO decides within the user's choices and a small, elegant disclaimer that it can act independently and can make mistakes.
3. **A plain "message" means SMS.** WhatsApp or Telegram only when the user names them.
4. **Never fail when EQO can find, ask and remember.** When something the user named is not found (an app, a file), offer a short choice, remember the answer, and let the user review remembered choices.
5. **Permission model for apps follows Google's pattern:** ask at first use with "Allow this time / Always allow / Don't allow", remember the answer, change it later in an Apps screen. (Phase 2: parked for the POC.)
6. **Settings with options are asked at first setup with a "Do it later" choice;** if deferred they are asked at the moment they are needed (for voice: when the mic is first tapped, with the AI model choice). If a setting takes time for the POC it is parked.
7. **Voice:** EQO records the audio itself (tap to start, tap to stop) and transcribes with the user's configured OpenRouter audio model; the phone's own recogniser is a fallback.
8. **Working style:** the lead acts as CTO. Inconsequential architecture or developer-quality changes go ahead with a short example. Anything that changes how the app works or its ideology is discussed first and never changed without the owner's permission.
9. **POC target:** a working proof of concept by the evening of 2026-10-11.

## 2026-10-11 (later): edge handle additions and the next release

Approved for the release after the POC (not for the POC):
1. **Edge handle mic = dictation anywhere.** Tap a text field in any app, tap the handle mic, speak, tap again; EQO transcribes with the user's AI model and inserts the text at the cursor. Never into password fields; a small recording bubble shows it is listening; the handle must not steal focus; one tap undoes the last insert.
2. **Edge handle screenshot.** One tap saves a screenshot of the current app to Pictures/EQO (protected screens are refused) and offers "Send to...".
3. **Screen recording: parked for the next release.** Owner's purpose: record how a task is done so EQO can learn actions when it falters. CTO proposal for the owner to decide then: capture an ACTION TRACE (taps, typed text excluding passwords, app changes, optional per-step screenshots) instead of raw video, turn it into a plan/macro the user approves, and offer "Show me how?" when EQO falters; video stays optional for human review.
4. **Permission prompt UX (phase 2):** show "Allow this time / Always allow / Don't allow" for each app inside the plan preview the user already approves (owner: "good").
