# EQO UX copy deck (generated)

Generated from `design/prototype.html` by `design/tools/build-copy-deck.mjs`. Do not edit by hand: change the prototype and run the script.

Conventions: US English (Android's default); neutral example names; Android and Chrome setting names verbatim; every readiness claim is per check; no blame. These follow the binding microcopy principles in `docs/USER-FLOWS.md` section 18. Review-only controls (variant tabs, "Demo" and "Simulate" buttons) are not included.

Placeholders are marked PENDING in the screens (exact Chrome flag, privacy "what is sent" list, legal wording, OEM menu paths) and are not final copy.


## UF-01 First install

### Welcome (S-01)

Requirements: REQ-INS-01/03. Source: USER-FLOWS UF-01 step 1.

- EQO
- Your Only Personal AI Assistant
- EQO helps with everyday tasks on your phone. You set it up step by step, and you approve anything sensitive.
- Get started
- Legal and licenses

### Privacy primer (S-02)

Requirements: REQ-PRIV-03/04. Source: USER-FLOWS UF-01 step 2.

- Before we begin
- Only what a task needs
- EQO sends your AI model only what a task needs. Screen or browser content is shared only after you approve it. Redaction is best-effort, so the model may still see some sensitive content.
- Your key stays here
- Your API key is stored encrypted in the Android Keystore and never written to logs.
- You stay in control
- EQO asks before anything sensitive. Pause or stop any time.
- What exactly is sent?
- Continue

Screen reader names (not visible text): Back

### Setup hub (S-03)

Requirements: REQ-INS-01, REQ-ADB-12. Source: USER-FLOWS UF-01 step 3, section 16.2.

- EQO
- Let's set up EQO
- Three steps are needed before EQO can run tasks.
- 0 of 3 required
- About 10 to 15 minutes. This is a rough estimate we will measure and correct.
- Model
- Required
- Not set up
- Screen control
- Required
- Not set up
- Helper connection
- Required
- Not set up
- Browser control
- Needs the helper first
- Needs attention
- Background mode
- Needs the helper first
- Needs attention
- Messages (SMS)
- Optional
- Not set up
- Continue

Screen reader names (not visible text): Back; Readiness details; Required steps done

### Setup hub (S-03) [state: all required steps ready]

Requirements: REQ-INS-01, REQ-ADB-12. Source: USER-FLOWS UF-01 step 3, section 16.2.

- EQO
- You're all set
- The required steps are ready. Optional ones can wait.
- 3 of 3 required
- About 10 to 15 minutes. This is a rough estimate we will measure and correct.
- Model
- Required
- Ready
- Screen control
- Required
- Ready
- Helper connection
- Required
- Ready
- Browser control
- Optional
- Not set up
- Background mode
- Optional
- Not set up
- Messages (SMS)
- Optional
- Not set up
- Start using EQO

Screen reader names (not visible text): Back; Readiness details; Required steps done

### Setup hub (S-03) [state: optional steps ready]

Requirements: REQ-INS-01, REQ-ADB-12. Source: USER-FLOWS UF-01 step 3, section 16.2.

- EQO
- You're all set
- The required steps are ready. Optional ones can wait.
- 3 of 3 required
- About 10 to 15 minutes. This is a rough estimate we will measure and correct.
- Model
- Required
- Ready
- Screen control
- Required
- Ready
- Helper connection
- Required
- Ready
- Browser control
- Optional
- Ready
- Background mode
- Optional
- Ready
- Messages (SMS)
- Optional
- Ready
- Start using EQO

Screen reader names (not visible text): Back; Readiness details; Required steps done


## UF-02 Model setup

### Model setup (S-04)

Requirements: REQ-BYOK-01/02/03, REQ-PRIV-02. Source: USER-FLOWS UF-02.

- Connect your AI model
- Paste your OpenRouter API key. It's stored encrypted in the Android Keystore and never written to logs. OpenRouter may charge your account; EQO doesn't.
- OpenRouter API key
- Test connection

Screen reader names (not visible text): Back; Paste your key

### Model setup (S-04) [state: test running]

Requirements: REQ-BYOK-01/02/03, REQ-PRIV-02. Source: USER-FLOWS UF-02.

- Connect your AI model
- Paste your OpenRouter API key. It's stored encrypted in the Android Keystore and never written to logs. OpenRouter may charge your account; EQO doesn't.
- OpenRouter API key
- Checking that EQO can plan and use tools…
- Testing…

Screen reader names (not visible text): Back; Paste your key

### Model setup (S-04) [state: test passed]

Requirements: REQ-BYOK-01/02/03, REQ-PRIV-02. Source: USER-FLOWS UF-02.

- Connect your AI model
- Paste your OpenRouter API key. It's stored encrypted in the Android Keystore and never written to logs. OpenRouter may charge your account; EQO doesn't.
- OpenRouter API key
- Connected to OpenRouter
- You can change models any time.
- Plan
- Tool call
- Verified answer
- Continue

Screen reader names (not visible text): Back; Paste your key

### Model failure states (S-05) [variant: 401]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Couldn't connect
- Key not accepted
- OpenRouter rejected this key. Check that you copied all of it.
- Re-enter key

Screen reader names (not visible text): Back

### Model failure states (S-05) [variant: 429]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Couldn't connect
- Too many requests
- Try again in 40 seconds. EQO won't retry on its own.
- Try again

Screen reader names (not visible text): Back

### Model failure states (S-05) [variant: credit]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Couldn't connect
- No credit left
- Add credit at openrouter.ai/credits, then test again.
- Open OpenRouter

Screen reader names (not visible text): Back

### Model failure states (S-05) [variant: model]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Couldn't connect
- Model not supported
- This model can't use EQO's tools. Choose another model.
- Choose model

Screen reader names (not visible text): Back

### Model failure states (S-05) [variant: net]

Requirements: REQ-BYOK-04. Source: USER-FLOWS UF-02 step 2.

- Couldn't connect
- No connection
- EQO can't reach OpenRouter. Check your internet.
- Try again

Screen reader names (not visible text): Back


## UF-03 Screen control

### Screen control: why and how (S-06)

Requirements: REQ-A11Y-01/03. Source: USER-FLOWS UF-03.

- Step 2 of 3
- Allow Screen control
- This lets EQO read and tap the screen for you.
- EQO can
- Read the screen during a task you start
- Tap and type for you in that task
- EQO can't
- Turn itself on. Only you can
- Act on sensitive steps without asking
- In Android settings: Accessibility › Downloaded apps › EQO › On
- You can turn it off any time.
- Open Accessibility settings
- Switch dimmed or missing?
- Not now

Screen reader names (not visible text): Back

### Screen control: why and how (S-06) [state: after turning it on]

Requirements: REQ-A11Y-01/03. Source: USER-FLOWS UF-03.

- Step 2 of 3
- Allow Screen control
- This lets EQO read and tap the screen for you.
- EQO can
- Read the screen during a task you start
- Tap and type for you in that task
- EQO can't
- Turn itself on. Only you can
- Act on sensitive steps without asking
- Screen control is on. You can turn it off any time in Android settings.
- Continue

Screen reader names (not visible text): Back

### Android Settings: Accessibility (system)

Requirements: REQ-A11Y-03. Source: Android-owned; shown to make the hand-off visible.

- Android system screen, not EQO
- Accessibility
- Downloaded apps
- ›
- EQO
- EQO can read and tap the screen while this is on.
- Back to EQO

Screen reader names (not visible text): Use EQO

### Restricted settings repair (S-07)

Requirements: REQ-A11Y-02 (Android 13+). Source: USER-FLOWS UF-03 step 3.

- Android may be blocking it
- This is Android's protection, not an EQO error
- Android restricts some apps installed outside the Play Store.
- Usual path: Settings › Apps › EQO › ⋮ › Allow restricted settings
- Names can differ on your phone.
- Open EQO's app info
- I've done it

Screen reader names (not visible text): Back

### Android App info (system)

Requirements: REQ-A11Y-02. Source: Android-owned; illustrates the hand-off.

- Android system screen, not EQO
- App info
- EQO
- ⋮
- Allow restricted settings
- Back to EQO

Screen reader names (not visible text): Allow restricted settings


## UF-04 Wireless ADB

### Helper connection: guided checks (S-08)

Requirements: REQ-ADB-01..06, 12. Source: USER-FLOWS UF-04.

- Connect the helper
- Five quick checks. EQO verifies each one.
- 1
- Developer options
- Now
- Settings › About phone › tap Build number 7 times.
- On some phones: About device › Version.
- Check
- 2
- Wi-Fi
- Waiting
- 3
- Wireless debugging
- Waiting
- 4
- Pair with a code
- Waiting
- 5
- Connect
- Waiting

Screen reader names (not visible text): Back

### Helper connection: guided checks (S-08) [state: first three checks done]

Requirements: REQ-ADB-01..06, 12. Source: USER-FLOWS UF-04.

- Connect the helper
- Five quick checks. EQO verifies each one.
- Developer options
- Ready
- Wi-Fi
- Ready
- Wireless debugging
- Ready
- 4
- Pair with a code
- Now
- Tap Pair device with pairing code. Enter the 6-digit code and the pairing port shown in the popup.
- Pairing port
- From the popup. Used once.
- Pairing port and connection port are different numbers. Android changes them each session.
- Check
- 5
- Connect
- Waiting

Screen reader names (not visible text): Back; Pairing code; 6-digit pairing code; Pairing port

### Helper connection: guided checks (S-08) [state: connect step]

Requirements: REQ-ADB-01..06, 12. Source: USER-FLOWS UF-04.

- Connect the helper
- Five quick checks. EQO verifies each one.
- Developer options
- Ready
- Wi-Fi
- Ready
- Wireless debugging
- Ready
- Pair with a code
- Ready
- 5
- Connect
- Now
- On the main Wireless debugging screen, find IP address & port. That connection port is not the pairing port.
- Connection port
- Beside the IP address. Not the pairing port.
- Connect

Screen reader names (not visible text): Back; Connection port

### Helper connection: guided checks (S-08) [state: all five checks done]

Requirements: REQ-ADB-01..06, 12. Source: USER-FLOWS UF-04.

- Connect the helper
- Connected and verified.
- Developer options
- Ready
- Wi-Fi
- Ready
- Wireless debugging
- Ready
- Pair with a code
- Ready
- Connect
- Ready
- Continue

Screen reader names (not visible text): Back

### ADB failure and repair (S-09) [variant: port]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper needs attention
- Pairing failed
- Check the code and the pairing port. That's the small number in the pairing popup, not the one beside your IP address.
- Try pairing again

Screen reader names (not visible text): Back

### ADB failure and repair (S-09) [variant: conn]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper needs attention
- Couldn't connect
- Use the connection port beside your IP address on the main Wireless debugging screen. It differs from the pairing port.
- Try connecting again

Screen reader names (not visible text): Back

### ADB failure and repair (S-09) [variant: reboot]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper needs attention
- Connection dropped
- Your phone restarted. Pair again; Android may show a new code.
- Pair again

Screen reader names (not visible text): Back

### ADB failure and repair (S-09) [variant: revoked]

Requirements: REQ-ADB-05/11, REQ-REC-06. Source: USER-FLOWS UF-04, UF-R2.

- Helper needs attention
- Access was revoked
- You revoked debugging access in Android settings, so EQO paused. Pair again to continue.
- Pair again

Screen reader names (not visible text): Back


## UF-05 Helper

### Helper authorization (S-10)

Requirements: REQ-ADB-07. Source: USER-FLOWS UF-05 step 2.

- Android system dialog, not EQO
- Allow EQO to use the helper?
- This lets EQO run commands you approve.
- Don't allow
- Allow

Screen reader names (not visible text): Allow EQO to use the helper

### Authorization declined

Requirements: REQ-ADB-07. Source: USER-FLOWS UF-05 step 2.

- Not allowed
- EQO can't run helper tasks yet
- It needs your permission first. Your checks are saved.
- Ask again
- Not now

Screen reader names (not visible text): Back

### Helper check (S-11) [variant: ok]

Requirements: REQ-ADB-08/09/10. Source: USER-FLOWS UF-05 steps 1, 3, 4.

- Helper check
- Connection
- Started
- Ready
- Authorized
- Ready
- Healthy, checked just now
- Ready
- 1 of 1 tests passed
- Echo test
- Passed
- –
- Display control
- Off
- –
- Browser channel
- Off
- Done

Screen reader names (not visible text): Back

### Helper check (S-11) [variant: start]

Requirements: REQ-ADB-08/09/10. Source: USER-FLOWS UF-05 steps 1, 3, 4.

- Helper check
- Starting the EQO helper…

Screen reader names (not visible text): Back

### Helper check (S-11) [variant: mixed]

Requirements: REQ-ADB-08/09/10. Source: USER-FLOWS UF-05 steps 1, 3, 4.

- Helper check
- 2 of 3 tests passed. Display control failed.
- Echo test
- Passed
- Display control
- Failed
- Browser channel
- Passed
- Try again

Screen reader names (not visible text): Back

### Helper check (S-11) [variant: fail]

Requirements: REQ-ADB-08/09/10. Source: USER-FLOWS UF-05 steps 1, 3, 4.

- Helper didn't start
- The helper didn't start
- This can happen after an update or restart. Try again; your connection checks are saved.
- Try again

Screen reader names (not visible text): Back


## UF-06 Browser control

### Browser control: consent (S-12)

Requirements: REQ-CDP-01, REQ-PRIV-03/04. Source: USER-FLOWS UF-06 step 1.

- Browser control
- EQO can read and use pages in Chrome through Chrome's debugging tools.
- Please know
- Page content and EQO's actions go to your AI model.
- Redaction is best-effort. The model may still see some sensitive content.
- EQO uses this only for tasks you start, and only after you agree.
- I understand
- Not now
- What exactly is sent?

Screen reader names (not visible text): Back

### What is sent (S-12 detail)

Requirements: REQ-PRIV-04. Source: USER-FLOWS UF-01 and UF-06 (button).

- What is sent
- A summary of the page you asked EQO to use.
- The steps EQO plans to take.
- Never your API key, or pages you didn't ask for.
- Draft list. The final text comes from the privacy review.
- Back

Screen reader names (not visible text): Back

### Prepare Chrome (S-13) [variant: ok]

Requirements: REQ-CDP-02. Source: USER-FLOWS UF-06 step 2.

- Prepare Chrome
- Chrome must allow debugging first. EQO can't change this for you.
- Steps for your Chrome version: follow the setting shown here, then restart Chrome. (Exact steps pending device testing.)
- I restarted Chrome

Screen reader names (not visible text): Back

### Prepare Chrome (S-13) [variant: fail]

Requirements: REQ-CDP-02. Source: USER-FLOWS UF-06 step 2.

- Prepare Chrome
- Chrome's debugging isn't available yet
- Check the setting and restart Chrome.
- Check again

Screen reader names (not visible text): Back

### Prepare Chrome (S-13) [state: checking the endpoint]

Requirements: REQ-CDP-02. Source: USER-FLOWS UF-06 step 2.

- Prepare Chrome
- Chrome must allow debugging first. EQO can't change this for you.
- Steps for your Chrome version: follow the setting shown here, then restart Chrome. (Exact steps pending device testing.)
- Checking Chrome's debugging endpoint…
- I restarted Chrome

Screen reader names (not visible text): Back

### Prepare Chrome (S-13) [state: Chrome verified]

Requirements: REQ-CDP-02. Source: USER-FLOWS UF-06 step 2.

- Prepare Chrome
- Chrome must allow debugging first. EQO can't change this for you.
- Steps for your Chrome version: follow the setting shown here, then restart Chrome. (Exact steps pending device testing.)
- Endpoint verified
- Ready
- Continue

Screen reader names (not visible text): Back

### Browser control readiness (S-14)

Requirements: REQ-CDP-03/04. Source: USER-FLOWS UF-06 step 3.

- Browser control is ready
- 3 of 3 functions work
- Read a page
- Passed
- Click
- Passed
- Run a script
- Passed
- Done

Screen reader names (not visible text): Back


## UF-07 Background mode

### Background mode (S-15)

Requirements: REQ-VD-03. Source: USER-FLOWS UF-07.

- Background mode
- EQO works on a separate virtual screen while you keep using your phone. It works only with compatible apps, while the helper is healthy. It's not a guarantee for every app.
- Messages
- Compatible
- Chrome
- Compatible
- Banking apps
- Not supported here
- Turn on

Screen reader names (not visible text): Back

### Foreground fallback (S-16)

Requirements: REQ-VD-01/02/04. Source: USER-FLOWS UF-07 step 3.

- Run in the foreground?
- EQO couldn't start a virtual screen
- The helper didn't respond. Run in the foreground instead? You'll see EQO work and can pause any time.
- Run in foreground
- Cancel task

Screen reader names (not visible text): Back


## UF-08 Readiness

### Readiness dashboard (S-19)

Requirements: REQ-ADB-10, REQ-CDP-04. Source: USER-FLOWS UF-08.

- Readiness
- Each check stands alone. Green means exactly that, and nothing else.
- Setup
- Model
- Not set up
- Screen control service
- Not set up
- Helper
- Developer options
- Not set up
- Wi-Fi
- Not set up
- Wireless debugging
- Not set up
- Paired (pairing port)
- Not set up
- Connected (connection port)
- Not set up
- Helper started
- Not set up
- Helper authorized
- Not set up
- Helper healthy
- Not set up
- Echo test
- Not set up
- Optional
- Browser: read, click, script
- Not set up
- Display control test
- Not set up
- Messages (SMS)
- Not set up

Screen reader names (not visible text): Back

### Readiness dashboard (S-19) [state: after a re-check]

Requirements: REQ-ADB-10, REQ-CDP-04. Source: USER-FLOWS UF-08.

- Readiness
- Each check stands alone. Green means exactly that, and nothing else.
- Setup
- Model
- Not set up
- Screen control service
- Not set up
- Helper
- Developer options
- Not set up
- Wi-Fi
- Not set up
- Wireless debugging
- Not set up
- Paired (pairing port)
- Not set up
- Connected (connection port)
- Not set up
- Helper started
- Not set up
- Helper authorized
- Not set up
- Helper healthy
- Not set up
- Echo test
- Not set up
- Optional
- Browser: read, click, script
- Not set up
- Display control test
- Not set up
- Messages (SMS)
- Not set up
- Wi-Fi: checked just now.

Screen reader names (not visible text): Back

### Readiness dashboard (S-19) [state: everything ready]

Requirements: REQ-ADB-10, REQ-CDP-04. Source: USER-FLOWS UF-08.

- Readiness
- Each check stands alone. Green means exactly that, and nothing else.
- Setup
- Model
- Ready
- Screen control service
- Ready
- Helper
- Developer options
- Ready
- Wi-Fi
- Ready
- Wireless debugging
- Ready
- Paired (pairing port)
- Ready
- Connected (connection port)
- Ready
- Helper started
- Ready
- Helper authorized
- Ready
- Helper healthy
- Ready
- Echo test
- Ready
- Optional
- Browser: read, click, script
- Ready
- Display control test
- Ready
- Messages (SMS)
- Ready

Screen reader names (not visible text): Back


## UF-09 Tasks

### Home and chat (S-20)

Requirements: REQ-TASK-01. Source: USER-FLOWS UF-09.

- EQO
- Home
- What would you like done? I'll ask before anything sensitive.
- Text Sam I'm on my way
- Find an open pharmacy
- Ask EQO

Screen reader names (not visible text): Settings; Send

### Home and chat (S-20) [state: after the result is confirmed]

Requirements: REQ-TASK-01. Source: USER-FLOWS UF-09.

- EQO
- Home
- What would you like done? I'll ask before anything sensitive.
- Text Sam I'm on my way
- Find an open pharmacy
- Thanks. Marked as done.
- Ask EQO

Screen reader names (not visible text): Settings; Send

### Task run with controls (S-21, S-23)

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Running
- Text Sam I'm on my way
- 1
- Open Messages
- Running · 0:12
- 2
- Write the message
- Pending
- 3
- Send it (needs your OK)
- Pending
- 4
- Check it was sent
- Pending
- Working on it. Step 1 of 4: opening Messages.
- What the model sees
- A summary of the current screen, with private details hidden where possible.
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Task controls

### Task run with controls (S-21, S-23) [state: step 3 of 4]

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Running
- Text Sam I'm on my way
- Open Messages
- Done
- Write the message
- Done
- 3
- Send it (needs your OK)
- Running · 0:12
- 4
- Check it was sent
- Pending
- Working on it. Step 3 of 4: sending it, after your OK.
- What the model sees
- A summary of the current screen, with private details hidden where possible.
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Task controls

### Task run with controls (S-21, S-23) [state: paused]

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Paused, steps kept
- Text Sam I'm on my way
- 1
- Open Messages
- Running · 0:12
- 2
- Write the message
- Pending
- 3
- Send it (needs your OK)
- Pending
- 4
- Check it was sent
- Pending
- Paused. EQO isn't reading your screen or calling the model. Resume when ready.
- What the model sees
- A summary of the current screen, with private details hidden where possible.
- Resume
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Task controls

### Task run with controls (S-21, S-23) [state: taken over, not watching]

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Not observing
- Text Sam I'm on my way
- 1
- Open Messages
- Running · 0:12
- 2
- Write the message
- Pending
- 3
- Send it (needs your OK)
- Pending
- 4
- Check it was sent
- Pending
- You're driving. EQO is holding.
- What the model sees
- A summary of the current screen, with private details hidden where possible.
- Let EQO watch while you drive?
- Off by default. EQO won't read the screen unless you allow it.
- Resume
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Let EQO watch while you drive; Task controls

### Task run with controls (S-21, S-23) [state: taken over, watching allowed]

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Observing (you allowed it)
- Text Sam I'm on my way
- 1
- Open Messages
- Running · 0:12
- 2
- Write the message
- Pending
- 3
- Send it (needs your OK)
- Pending
- 4
- Check it was sent
- Pending
- You're driving. EQO is holding.
- What the model sees
- A summary of the current screen, with private details hidden where possible.
- Let EQO watch while you drive?
- Off by default. EQO won't read the screen unless you allow it.
- Resume
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Let EQO watch while you drive; Task controls

### Task run with controls (S-21, S-23) [state: foreground fallback]

Requirements: REQ-TASK-01/03/05. Source: USER-FLOWS UF-09, UF-10 section 11.2.

- EQO
- Running
- Text Sam I'm on my way
- Foreground (background mode unavailable)
- 1
- Open Messages
- Running · 0:12
- 2
- Write the message
- Pending
- 3
- Send it (needs your OK)
- Pending
- 4
- Check it was sent
- Pending
- Working on it. Step 1 of 4: opening Messages.
- What the model sees
- A summary of the current screen, with private details hidden where possible.
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Task controls


## UF-10 Approvals

### Approval card (S-22)

Requirements: REQ-TASK-02. Source: USER-FLOWS UF-10 section 11.1.

- Needs your OK
- Text Sam I'm on my way
- Messages wants to send this to Sam
- "On my way, back in 20 minutes" To: Sam
- Approve
- Deny
- Auto-denies in 60 s
- Back also denies. Nothing is sent.
- Pause
- Pause after the current step finishes
- Take over
- You drive; EQO holds
- Stop
- Stop this task completely

Screen reader names (not visible text): Back, which denies; Task controls

### Denied or cancelled [variant: denied]

Requirements: REQ-TASK-02, REQ-TASK-07. Source: USER-FLOWS UF-10.

- Denied
- Nothing was sent. What next?
- Edit the message
- End task

Screen reader names (not visible text): Back

### Denied or cancelled [variant: changed]

Requirements: REQ-TASK-02, REQ-TASK-07. Source: USER-FLOWS UF-10.

- Cancelled
- The screen changed while waiting, so EQO cancelled that step. Nothing was sent.
- Edit the message
- End task

Screen reader names (not visible text): Back

### Task stopped

Requirements: REQ-TASK-03. Source: USER-FLOWS UF-10 section 11.2.

- Stopped
- The task ended
- Here's what ran before you stopped it.
- Opened Messages
- Wrote the message
- 3
- Send it. Not done.
- Back to home

Screen reader names (not visible text): Back


## UF-11 Result

### Result verification (S-24)

Requirements: REQ-TASK-06. Source: USER-FLOWS UF-11.

- Done
- Here's what happened.
- Opened Messages
- Wrote the message
- Sent it, with your approval
- Did it do what you asked?
- Looks right
- Looks wrong

Screen reader names (not visible text): Back


## UF-12 Recovery

### Recovery hub (S-25) [variant: rate]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Too many requests
- EQO won't retry on its own. Try again in 40 seconds, or stop.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Try again
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: adb]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Helper disconnected
- Reconnect with the pairing steps. Android may show a new code.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Reconnect
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: a11y]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Screen control is off
- The task paused at step 3. Turn it on again, then resume.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Open Screen control
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: helper]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Helper stopped responding
- EQO will re-run its checks.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Check the helper
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: unsure]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Not sure it finished
- This step may have partly run. Check Messages before resuming.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- I checked
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: chrome]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Chrome debugging is off
- Chrome may have restarted. EQO will re-check.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Re-check Chrome
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: revoked]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Helper access turned off
- You changed a setting, so EQO paused. Turn it on when you want to continue.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Review setting
- Stop the task

Screen reader names (not visible text): Back

### Recovery hub (S-25) [variant: update]

Requirements: REQ-REC-01..10, REQ-TASK-07. Source: USER-FLOWS UF-11, UF-12 (R1 to R8).

- Needs attention
- Helper needs a restart
- EQO was updated. The helper needs a quick restart.
- Resuming asks again before anything sensitive. An earlier approval is never reused.
- Restart helper
- Stop the task

Screen reader names (not visible text): Back

### Set up again (S-29)

Requirements: REQ-ADB-12. Source: USER-FLOWS S-29.

- Set up again
- Pick a step to redo. Everything else stays as is.
- Model
- Required
- Not set up
- Screen control
- Required
- Needs attention
- Helper connection
- Required
- Not set up

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
- Use my messaging app

Screen reader names (not visible text): Back

### Draft saved (back from S-26)

Requirements: REQ-SMS-02. Source: USER-FLOWS UF-13 step 2.

- Draft saved
- Nothing was sent. You can come back to it.
- Open the draft
- Done

Screen reader names (not visible text): Back

### SMS confirmation

Requirements: REQ-SMS-02. Source: USER-FLOWS UF-13 step 2.

- Send this SMS?
- To: Sam
- "On my way, back in 20 minutes"
- Uses your carrier plan.
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
- EQO can't send SMS directly on this phone. Open the draft in your messaging app.
- Open messaging app

Screen reader names (not visible text): Back


## UF-14 Settings and legal

### Settings (S-27)

Requirements: REQ-INS-03. Source: USER-FLOWS S-27.

- Settings
- Model
- OpenRouter
- Not set up
- Privacy
- What EQO sends
- Readiness
- Every check
- Set up again
- Redo one step
- Legal and licenses
- Works offline

Screen reader names (not visible text): Back

### Legal notices (S-28)

Requirements: REQ-INS-03. Source: USER-FLOWS UF-14.

- Licenses and notices
- Always available, even offline.
- OpenDroid
- Apache-2.0
- ClosePaw
- Apache-2.0
- Shizuku
- Apache-2.0
- Shizuku-API
- MIT
- Attribution wording is a placeholder until legal review.

Screen reader names (not visible text): Back


---
73 screen states.
