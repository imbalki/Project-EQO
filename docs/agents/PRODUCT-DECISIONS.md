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
3. **Screen recording: parked for the next release.** Owner's purpose: record how a task is done so EQO can learn actions when it falters. APPROVED DIRECTION (owner, 2026-10-11, for the next release): capture an ACTION TRACE (taps, typed text excluding passwords, app changes, optional per-step screenshots) instead of raw video, turn it into a plan/macro the user approves, and offer "Show me how?" when EQO falters; video stays optional for human review.
4. **Permission prompt UX (phase 2):** show "Allow this time / Always allow / Don't allow" for each app inside the plan preview the user already approves (owner: "good").

## Next release plan (owner-approved direction, not for the POC)
1. Dictation anywhere from the edge handle mic.
2. Screenshot from the edge handle.
3. "Teach EQO": record an action trace (not video) with a Teach / Stop control on the handle, review and name it, store it as a routine on top of the existing macro and routine system (MacroActions, RoutineDetection, HabitRoutineEngine); when EQO falters it offers "Show me how?".
4. Apps screen with Allow this time / Always allow / Don't allow, shown inside the plan preview.
5. Setup wizard with "Do it later" on every option, and the auto-mode question with the elegant disclaimer.

## 2026-10-12: SMS sending for the POC
10. **Text messages (SMS): for this POC EQO opens the text draft with the message typed and the user presses Send.** Owner: "if it is going to be a long work we will have B for this POC and when auto mode it can send". Sending by itself belongs to the opt-in auto mode (next release), with a setting to turn it off. WhatsApp keeps its current behaviour.
11. **Ask which app only when needed:** when EQO cannot find the app, or several installed apps fit a generic role (for example several browsers), offer the choices with "This time" / "Always". Never ask for a clearly named app, including typos (`open Whatsap`).
