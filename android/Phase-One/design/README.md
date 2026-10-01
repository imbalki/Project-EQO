# EQO design (Phase One): prototype, copy and identity

Design-only. No app code. Issue #25. The owner chose the **Warm (clay)** identity on 2026-10-02: light, soft, calm; not OpenDroid's look.

## What is here

| File | What it is |
|---|---|
| `prototype.html` | Clickable prototype: 34 screens across the 14 user flows (UF-01 to UF-14), Warm style, true size (390 dp). Open the file in a browser (Google Fonts load online; it falls back to system fonts offline). |
| `ux-copy.md` | The UX copy deck, **generated** from the prototype (45 screen states, including failure variants and the spoken names for screen readers). |
| `accessibility-review.md` | Measured accessibility results and a plain list of what is not yet tested. |
| `tools/build-copy-deck.mjs` | Regenerates `ux-copy.md`: `node android/Phase-One/design/tools/build-copy-deck.mjs`. |
| `identity-options.html`, `identity-v2.html` | The earlier identity explorations (three directions, then the soft Warm/Cool pair) kept for the decision record. |

## How to use the prototype

- Use the list on the left to jump to any screen, or tap buttons to move through a flow. The right panel says why a screen is designed that way and which requirement (REQ-*) and USER-FLOWS section it traces to.
- **Setup is live:** completing Model, Screen control and the five helper checks flips the hub rows to Ready, and the hub then says "You're all set". Use *Reset demo* to start again.
- Failure screens (model errors, ADB problems, recovery) have variant buttons so every error copy can be reviewed.
- **Text size** (100, 130, 160%) and **Right to left** preview the international and accessibility behavior.
- Android's own screens (the Accessibility switch, the helper authorization dialog) are drawn on grey with a black "Android system screen, not EQO" tag, so it is always clear who is speaking.

## Design rules used

- Light, soft surfaces; one warm accent; rounded shapes (Material 3 Expressive direction). No dark panels, no monospace "tech" look.
- Material type scale at true size: body 14, titles 16, headlines 22 to 26. Everything scales with the text-size setting.
- Explain before asking: every permission screen says why, what EQO can and cannot do, and how to turn it off, before any Android settings screen.
- Status is always a word plus a symbol. Readiness is per check, never one generic "ready".
- US English (Android's default), short sentences, no idioms, neutral example names. Android and Chrome setting names stay verbatim.

## Where this goes beyond or differs from `docs/USER-FLOWS.md` (for a later PR that updates that file)

This folder deliberately does not edit `USER-FLOWS.md`. Notes for that follow-up:

1. **S-08 check count:** the screen inventory says "step 1..8 checklist" but UF-04 defines five checks. The prototype shows the five from UF-04 (developer options, Wi-Fi, wireless debugging, paired, connected), and the helper checks (started, authorized, health) appear on S-10 and S-11. Please confirm the intended split.
2. **Android 11 floor (ADR-0003):** wireless debugging exists from Android 11, and the S-07 "restricted settings" card applies only to Android 13 and newer. The prototype shows S-07 only when the person reports a blocked switch and marks the wording as pending device validation.
3. **Phone-brand menu paths vary.** Step 1 of the helper checks adds a hint ("on some phones: Settings > About device > Version > Build number"), seen on the owner's Realme device. This is a hint and still needs a device matrix.
4. **Spelling and examples:** the prototype copy uses "authorization", "licenses" and a neutral example recipient ("Sam").
5. **Voice** is not shown on Home because the brief is silent (CF-09).

## Placeholders (not final copy)

The exact Chrome debugging flag (S-13), the "what exactly is sent" list (S-12), the legal attribution wording (S-28) and OEM menu paths are marked PENDING in the screens. They come from device testing, the privacy review and legal review.

## Not covered yet

Dark theme (not requested), tablet layouts, the Android widgets/notifications design, and the app icon and logo (the mark in the prototype is a placeholder).
