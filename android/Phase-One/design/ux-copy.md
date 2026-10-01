# EQO UX copy deck (generated)

Generated from `design/prototype.html` by `design/tools/build-copy-deck.mjs`. Do not edit by hand: change the prototype and run the script.

Conventions: US English (Android's default); neutral example names; Android and Chrome setting names verbatim; every readiness claim is per check; no blame. These follow the binding microcopy principles in `docs/USER-FLOWS.md` section 18.

Placeholders are marked PENDING in the screens (exact Chrome flag, privacy "what is sent" list, legal wording, OEM menu paths) and are not final copy.


## UF-01 First install

### Welcome (S-01)

Requirements: REQ-INS-01/03. Source: USER-FLOWS UF-01 step 1.

- EQO
- Your Only Personal AI Assistant
- EQO works by guiding you through a few Android settings, then runs tasks on your phone with your approval at every sensitive step. Nothing is set up yet.
- Get started
- Read legal and licenses

### Privacy primer (S-02)

Requirements: REQ-PRIV-03/04. Source: USER-FLOWS UF-01 step 2.

- Before we begin
- Three things to know about how EQO treats your information.
- Only what a task needs
- EQO sends your chosen AI model just what it needs. Screen or browser content is shared only after you approve it. Redaction is best-effort, so some sensitive content may still be visible to the model.
- Your key stays on your phone
- Your API key is stored encrypted in the Android Keystore and is never written to logs.
- You stay in control
- EQO asks before anything sensitive. You can pause or stop at any time.
- Continue

Screen reader names (not visible text): Back

### Setup hub (S-03)

Requirements: REQ-INS-01, REQ-ADB-12. Source: USER-FLOWS UF-01 step 3, section 16.2.

- EQO
- Let's get you set up
- EQO can't run tasks until these are ready.
- 0 of 3 required
- About 10 to 15 min
- Model
- Required
- Set up
- Screen control
- Required
- Set up
- Helper connection
- Required
- Set up
- Browser control
- Optional
- Needs helper
- Background mode
- Optional
- Needs helper
- Messages (SMS)
- Optional
- Off
- Continue setup

Screen reader names (not visible text): Back; Details: readiness dashboard; Required steps done


## UF-02 Model setup

### Model setup (S-04)

Requirements: REQ-BYOK-01/02/03, REQ-PRIV-02. Source: USER-FLOWS UF-02.

- Choose your AI model provider
- Paste your OpenRouter API key. It is stored on this phone using the Android Keystore and never written to logs. Using OpenRouter may cost you money on your OpenRouter account. EQO does not charge you.
- OpenRouter API key
- Test connection
- See what failure looks like

Screen reader names (not visible text): Back; Paste your key

### Model failure states (S-05) [variant: 401]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Model check failed
- 401
- 429
- Credit
- Model
- Network
- Unauthorized (401)
- OpenRouter rejected this key. Check you copied the whole key.
- Re-enter key

Screen reader names (not visible text): Back; Variants

### Model failure states (S-05) [variant: 429]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Model check failed
- 401
- 429
- Credit
- Model
- Network
- Rate limited (429)
- Too many requests right now. Try again in 40 seconds. EQO won't retry on its own.
- Try again when ready

Screen reader names (not visible text): Back; Variants

### Model failure states (S-05) [variant: credit]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Model check failed
- 401
- 429
- Credit
- Model
- Network
- Insufficient credit
- Your OpenRouter account has no credit. Add credit at openrouter.ai/credits, then test again.
- Open provider page

Screen reader names (not visible text): Back; Variants

### Model failure states (S-05) [variant: model]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Model check failed
- 401
- 429
- Credit
- Model
- Network
- Incompatible model
- This model doesn't support EQO's tool calls. Pick a model with tool support.
- Choose another model

Screen reader names (not visible text): Back; Variants

### Model failure states (S-05) [variant: net]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Model check failed
- 401
- 429
- Credit
- Model
- Network
- No network
- No internet connection. EQO can't reach OpenRouter.
- Try again

Screen reader names (not visible text): Back; Variants


## UF-03 Screen control

### Screen control: why and how (S-06)

Requirements: REQ-A11Y-01/03. Source: USER-FLOWS UF-03.

- Step 2 of 3
- Let EQO use Screen control
- EQO needs the Accessibility service to read and tap the screen on your behalf.
- EQO can
- Read what is on screen while it works on a task you started
- Tap and type for you during that task
- EQO can't
- Turn itself on. Only you can, in Android settings
- Do sensitive things without asking you first
- You can turn this off at any time in Android settings.
- In Android settings: Accessibility › Downloaded apps › EQO › turn on
- Open Accessibility settings
- Is the switch dimmed or missing? Tell EQO
- Not now

Screen reader names (not visible text): Back

### Android Settings: Accessibility (system)

Requirements: REQ-A11Y-03. Source: Android-owned; shown to make the hand-off visible.

- Android system screen, not EQO
- Accessibility
- Downloaded apps
- ›
- EQO
- Turning this on lets EQO read and tap the screen. You can turn it off here at any time.
- Back to EQO

Screen reader names (not visible text): Use EQO

### Restricted settings repair (S-07)

Requirements: REQ-A11Y-02 (Android 13+). Source: USER-FLOWS UF-03 step 3.

- Android may be blocking this toggle
- This is Android's protection, not an EQO error
- Android sometimes blocks the switch for apps installed outside the Play Store. It is called a "restricted setting".
- The usual path: Settings › Apps › EQO › tap the ⋮ (three dots) in the app info screen › Allow restricted settings
- Wording and placement can differ on your phone. If you can't find it, EQO can't change it for you.
- Open EQO's app info
- I've done it

Screen reader names (not visible text): Back


## UF-04 Wireless ADB

### Helper connection: guided checks (S-08)

Requirements: REQ-ADB-01..06, 12. Source: USER-FLOWS UF-04.

- Connect the helper
- Five checks, one at a time. EQO watches and verifies; it never grants anything for you.
- 1
- Unlock Developer options
- Now
- Open Settings › About phone › Build number and tap Build number 7 times to unlock Developer options.
- On some phones this is Settings › About device › Version › Build number.
- Check now
- Simulate a failure
- 2
- Wi-Fi is on
- Waiting
- 3
- Wireless debugging is on
- Waiting
- 4
- Pair with a pairing code
- Waiting
- 5
- Connect and verify
- Waiting

Screen reader names (not visible text): Back

### ADB failure and repair (S-09) [variant: port]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper connection needs attention
- Pairing
- Connect
- Reboot
- Revoked
- Pairing failed
- Check the pairing code and the pairing port. The pairing port is the small number in the pairing popup, not the one next to your IP address.
- Try pairing again

Screen reader names (not visible text): Back; Variants

### ADB failure and repair (S-09) [variant: conn]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper connection needs attention
- Pairing
- Connect
- Reboot
- Revoked
- Couldn't connect
- The connection port is the one next to your IP address on the main Wireless debugging screen. It is a different number from the pairing port.
- Try connecting again

Screen reader names (not visible text): Back; Variants

### ADB failure and repair (S-09) [variant: reboot]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper connection needs attention
- Pairing
- Connect
- Reboot
- Revoked
- Your phone restarted
- Your phone's wireless debugging connection dropped after a restart. Pair again. Android may show a new pairing code.
- Start pairing again

Screen reader names (not visible text): Back; Variants

### ADB failure and repair (S-09) [variant: revoked]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper connection needs attention
- Pairing
- Connect
- Reboot
- Revoked
- Authorization was revoked
- You revoked debugging authorizations in Android settings. EQO paused. Pair again when you want to continue.
- Start pairing again

Screen reader names (not visible text): Back; Variants


## UF-05 Helper

### Helper authorization (S-10)

Requirements: REQ-ADB-07. Source: USER-FLOWS UF-05 step 2.

- Android system dialog, not EQO
- Allow EQO to use the helper?
- This lets EQO run the commands you approve.
- Don't allow
- Allow

Screen reader names (not visible text): Allow EQO to use the helper

### Authorization declined

Requirements: REQ-ADB-07. Source: USER-FLOWS UF-05 step 2.

- Authorization declined
- EQO can't run helper tasks yet
- EQO can't run helper tasks until you allow it. Your connection checks are still saved.
- Ask again
- Not now

Screen reader names (not visible text): Back

### Helper health and checks (S-11)

Requirements: REQ-ADB-08/09/10. Source: USER-FLOWS UF-05 steps 3-4.

- Checking the helper
- Each check is separate.
- Helper started
- Ready
- Helper authorized
- Ready
- Helper connection: healthy
- Ready
- 3 of 3 checks passed
- Run a harmless command (echo)
- Done

Screen reader names (not visible text): Back


## UF-06 Browser control

### Browser control: consent (S-12)

Requirements: REQ-CDP-01, REQ-PRIV-03/04. Source: USER-FLOWS UF-06 step 1.

- Browser control
- Browser control lets EQO read and use pages in Chrome through Chrome's own debugging tools.
- What this means
- Page content and EQO's script actions are sent to your model provider for processing.
- Redaction is best-effort. Sensitive content may still be visible to the model.
- EQO starts this only after you agree, and only for tasks you run.
- I understand, continue
- Not now
- What exactly is sent?

Screen reader names (not visible text): Back

### What exactly is sent?

Requirements: REQ-PRIV-04. Source: USER-FLOWS UF-06 (button).

- What exactly is sent?
- A summary of the page you asked EQO to work on.
- The actions EQO plans to take on that page.
- Not your API key, and not pages you did not ask it to open.
- Placeholder text. The final list comes from the privacy review before any release.
- Back

Screen reader names (not visible text): Back

### Chrome debugging preparation (S-13)

Requirements: REQ-CDP-02. Source: USER-FLOWS UF-06 step 2.

- Prepare Chrome
- Chrome needs to allow debugging before EQO can connect. EQO can't change this for you.
- Steps for your Chrome version: follow the setting shown here, then restart Chrome. (The exact flag or setting is PENDING and will be filled in from device testing.)
- I've restarted Chrome, check now

Screen reader names (not visible text): Back

### Browser control readiness (S-14)

Requirements: REQ-CDP-03/04. Source: USER-FLOWS UF-06 step 3.

- Browser control is ready
- 3 of 3 functions available
- Read a page
- Passed
- Click on a page
- Passed
- Run a script
- Passed
- Done

Screen reader names (not visible text): Back


## UF-07 Background mode

### Background mode (S-15)

Requirements: REQ-VD-03. Source: USER-FLOWS UF-07.

- Background mode
- Background mode lets EQO work on a separate virtual screen so you can keep using your phone. It works only for compatible apps while the helper is healthy. It is not a guarantee for every app.
- Messages
- Compatible
- Chrome
- Compatible
- Banking apps
- Not supported here
- Turn on Background mode
- See what a failure looks like

Screen reader names (not visible text): Back

### Foreground fallback proposal (S-16)

Requirements: REQ-VD-01/02/04. Source: USER-FLOWS UF-07 step 3.

- Run this task in the foreground?
- EQO couldn't start a virtual screen
- Reason: the helper did not respond. Run this task in the foreground instead? You'll see EQO working on your screen, and you can pause anytime.
- Run in foreground
- Cancel task

Screen reader names (not visible text): Back


## UF-08 Readiness

### Readiness dashboard (S-19)

Requirements: REQ-ADB-10, REQ-CDP-04. Source: USER-FLOWS UF-08.

- Readiness details
- Each check is separate. Green here means exactly what it says, nothing else.
- Model
- Checked just now
- Not set up
- Screen control
- Checked just now
- Not set up
- Developer options
- Checked just now
- Not set up
- Wi-Fi
- Checked just now
- Not set up
- Wireless debugging
- Checked just now
- Not set up
- Paired (pairing port)
- Checked just now
- Not set up
- Connected (connection port)
- Checked just now
- Not set up
- Helper authorized
- Checked just now
- Not set up
- Browser control: read, click, script
- Checked just now
- Not set up
- Background mode
- Checked just now
- Not set up

Screen reader names (not visible text): Back


## UF-09 Tasks

### Home and chat (S-20)

Requirements: REQ-TASK-01. Source: USER-FLOWS UF-09.

- EQO
- Home
- Hi! Tell me what you'd like done on your phone. I'll ask before anything sensitive.
- Text Sam I'm on my way
- Find a pharmacy that is open now
- Ask EQO to do something

Screen reader names (not visible text): Settings; Send

### Task run with controls (S-21, S-23)

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Running
- Text Sam I'm on my way
- 1
- Open Messages
- 2
- Write the message
- 3
- Send it (needs your OK)
- 4
- Check it was sent
- Working on it. Step 1 of 4: opening Messages.
- Demo: next step
- Pause
- Stop
- Take over

Screen reader names (not visible text): Task controls


## UF-10 Approvals

### Approval card (S-22)

Requirements: REQ-TASK-02. Source: USER-FLOWS UF-10 section 11.1.

- EQO
- Needs your OK
- Text Sam I'm on my way
- Messages wants to send a message to Sam
- "On my way, back in 20 minutes" To: Sam
- Approve
- Deny
- Auto-denies in 60s
- Pressing back also denies, and nothing is sent.
- Pause
- Stop
- Take over

Screen reader names (not visible text): Task controls

### Approval denied or timed out

Requirements: REQ-TASK-02, REQ-TASK-07. Source: USER-FLOWS UF-10.

- Denied
- EQO didn't send anything
- The task will ask you how to continue.
- Try a different message
- End the task
- If the screen changes while you decide, EQO cancels that step: "The screen changed while waiting for your answer, so EQO cancelled that step."

Screen reader names (not visible text): Back

### Task stopped

Requirements: REQ-TASK-03. Source: USER-FLOWS UF-10 section 11.2.

- Stopped
- Task ended
- You stopped this task. Steps before this point are listed below.
- Opened Messages
- Wrote the message
- 3
- Send it. Not done.
- Back to home

Screen reader names (not visible text): Back


## UF-11 Result

### Result verification (S-24)

Requirements: REQ-TASK-06. Source: USER-FLOWS UF-11.

- Done. Here's what happened
- Opened Messages
- Wrote the message to Sam
- Sent it, after your approval
- Did it do what you asked?
- Looks right
- Looks wrong

Screen reader names (not visible text): Back


## UF-12 Recovery

### Recovery hub (S-25) [variant: rate]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12.

- Needs attention
- Rate limit
- Helper
- Screen
- Binder
- Unsure
- Rate limited
- Too many requests. EQO won't retry on its own. Try again in 40 seconds or stop.
- Resuming asks again before anything sensitive. EQO never reuses an earlier approval.
- Try again when ready
- Stop the task

Screen reader names (not visible text): Back; Variants

### Recovery hub (S-25) [variant: adb]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12.

- Needs attention
- Rate limit
- Helper
- Screen
- Binder
- Unsure
- Helper connection dropped
- Your phone's wireless debugging connection dropped. Reconnect with the pairing steps. Android may show a new pairing code.
- Resuming asks again before anything sensitive. EQO never reuses an earlier approval.
- Reconnect
- Stop the task

Screen reader names (not visible text): Back; Variants

### Recovery hub (S-25) [variant: a11y]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12.

- Needs attention
- Rate limit
- Helper
- Screen
- Binder
- Unsure
- Screen control was turned off
- The task stopped at step 3. Turn it back on, then choose Resume.
- Resuming asks again before anything sensitive. EQO never reuses an earlier approval.
- Open Screen control
- Stop the task

Screen reader names (not visible text): Back; Variants

### Recovery hub (S-25) [variant: binder]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12.

- Needs attention
- Rate limit
- Helper
- Screen
- Binder
- Unsure
- The helper stopped responding
- EQO will restart its checks.
- Resuming asks again before anything sensitive. EQO never reuses an earlier approval.
- Check the helper
- Stop the task

Screen reader names (not visible text): Back; Variants

### Recovery hub (S-25) [variant: unknown]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12.

- Needs attention
- Rate limit
- Helper
- Screen
- Binder
- Unsure
- EQO isn't sure it finished
- This step may have partially run. Check Messages before resuming.
- Resuming asks again before anything sensitive. EQO never reuses an earlier approval.
- I checked, continue
- Stop the task

Screen reader names (not visible text): Back; Variants

### Set up again (S-29)

Requirements: REQ-ADB-12. Source: USER-FLOWS S-29.

- Set up again
- Choose the step to redo. Everything else stays as it is.
- Model
- Required
- Set up
- Screen control
- Required
- Needs attention
- Helper connection
- Required
- Set up

Screen reader names (not visible text): Back


## UF-13 SMS

### SMS draft (S-26)

Requirements: REQ-SMS-01/03/04. Source: USER-FLOWS UF-13.

- Draft ready
- Nothing is sent yet.
- To
- Sam
- Message
- On my way, back in 20 minutes
- Send…
- Edit
- Hand off to my messaging app

Screen reader names (not visible text): Back

### SMS confirmation (S-22 variant)

Requirements: REQ-SMS-02. Source: USER-FLOWS UF-13 step 2.

- Send this SMS?
- To: Sam
- "On my way, back in 20 minutes"
- This will use your carrier plan.
- Send now
- Cancel

Screen reader names (not visible text): Back

### SMS cancelled

Requirements: REQ-SMS-02. Source: USER-FLOWS UF-13 step 2.

- Nothing sent
- Your draft is saved.
- Back to draft

Screen reader names (not visible text): Back

### SMS hand-off

Requirements: REQ-SMS-04. Source: USER-FLOWS UF-13 step 3.

- Send it yourself
- EQO can't send SMS directly on this phone. Here's the draft. Open it in your messaging app and send it yourself.
- Open my messaging app

Screen reader names (not visible text): Back


## UF-14 Settings and legal

### Settings (S-27)

Requirements: REQ-INS-03. Source: USER-FLOWS S-27.

- Settings
- Model
- OpenRouter
- Set up
- Privacy and data
- What EQO sends
- Capabilities
- Readiness details
- Set up again
- Re-run one step
- Legal and open-source notices
- Works offline

Screen reader names (not visible text): Back

### Legal notices (S-28)

Requirements: REQ-INS-03. Source: USER-FLOWS UF-14.

- Open-source licenses and notices
- Always available here, even offline.
- OpenDroid
- Apache-2.0
- ClosePaw
- Apache-2.0
- Shizuku
- Apache-2.0
- Shizuku-API
- MIT
- Attribution wording and license texts are placeholders until the legal review.

Screen reader names (not visible text): Back


---
45 screen states.
