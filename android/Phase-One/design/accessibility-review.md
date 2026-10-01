# Accessibility review of the EQO prototype (Warm)

Date: 2026-10-02. Subject: `design/prototype.html` (34 screens). Method: scripted checks run in a browser against the live prototype (rendering every screen, then measuring), plus a read of the generated copy deck. These are measurements of a **web mockup**, not of a built Android app: they inform the design and set targets for the Compose build, and do not replace testing on devices.

## Results (final build)

| Check | Target | Result |
|---|---|---|
| Touch targets | 48 dp minimum (Android guidance; WCAG 2.2 target size AA is 24 px) | **127 of 127 controls are at least 48 dp** across 34 screens |
| Accessible names | every control has one | **0 unnamed controls** (inputs use labels or placeholders; icon-only buttons carry `aria-label`) |
| Decorative icons | hidden from screen readers | **0 icons exposed** (all `aria-hidden`) |
| Minimum text size | 12 px or more (body 14) | **0 text nodes below 12 px** |
| Text contrast | 4.5:1 for normal text | **13 of 13 pairs pass**; lowest is 5.00:1 (terracotta text button on warm white) |
| Layout at large text | no clipped or sideways-scrolling content | **0 overflow cases** at 100%, 130% and 160% text, left-to-right, and at 100% and 160% right-to-left |
| Headings | each screen has a heading | 34 of 34 after the fix below |
| Status announcements | changes announced to screen readers | partial, see gaps |

Contrast pairs measured (ratio): ink on background 13.49; muted on background 5.44; muted on tonal card 5.04; muted on white card 5.76; white on terracotta 5.29; dark on soft button 9.83; ready green 5.14; needs-OK amber 6.93; stop red 5.50; terracotta text button 5.00; ink on amber card 12.51; muted on amber card 5.05; ink on red card 11.99.

## Problems found and fixed during the review

1. Back buttons, suggestion chips and the send button were 44 dp: raised to 48.
2. Variant tabs were 40 dp: raised to 48.
3. The Android switch mock was 32 dp high: its tap area is now 48 dp.
4. Pause, Stop and Take over were 46 dp: raised to 48.
5. "Android system screen" tags were 11 px: raised to 12.
6. The Home screen had no heading: an invisible "Home" heading was added so screen readers can orient.
7. Copy: mixed British and US spelling was unified to US English; a lowercase status line ("open messages") was rewritten; "greyed out" became "dimmed".

## Gaps and things not tested (be honest about these)

- **No screen reader run.** TalkBack was not used; reading order, focus order after each navigation, and the spoken text of the approval timer are untested. Needs a device pass in the Compose build.
- **Live announcements are thin.** Only two status regions exist. Hub row changes (Ready, Needs helper), check results and the approval countdown must announce themselves in the real app.
- **Non-text contrast (3:1)** for card borders, focus rings, chip outlines and icons was not measured.
- **Color-blindness simulation** was not run. Status already uses a word plus a symbol, not color alone, which is the main mitigation.
- **Real font scaling:** the prototype scales text with a CSS multiplier. Android's own font scale (and display-size changes) can behave differently; test at the largest system settings on Android 11, 12 and 13.
- **Right to left:** layout mirroring was previewed with a direction switch and uses logical spacing, but it was not reviewed by a native reader, and some icons (arrows) mirror while others should not.
- **Translation length:** only a fixed 160% stress test was done. Languages such as German or Finnish need a real pseudo-localization pass.
- **Motion and timing:** the 60-second approval timeout needs a way to extend or hear the time left; this is a recommendation for the build, not yet designed.
- **Cognitive load on long flows** (the five helper checks) needs usability testing with real first-time users; this prototype only shows the intended structure.

## Recommendations carried into the build (TASK-015)

Use Material components with the tokens in `prototype.html`; keep every control at 48 dp or more; expose every status change through live regions; keep the approval timer both visible and spoken, with a way to ask for more time; run TalkBack, largest font and display size, and an RTL pseudo-locale as part of the exit review.
