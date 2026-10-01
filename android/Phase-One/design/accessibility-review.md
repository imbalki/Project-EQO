# Accessibility review of the EQO prototype (Warm)

Date: 2026-10-02 (revised after two independent reviews). Subject: `design/prototype.html` (36 screens, 69 documented states). Method: scripted checks run in a browser against the live prototype (render every screen, then measure), the generated copy deck, and two independent reviews by other model families. These are measurements of a **web mockup**, not of a built Android app: they set targets for the Compose build and do not replace testing on devices.

## Results (current build)

| Check | Target | Result |
|---|---|---|
| Touch target size | 48 dp minimum, **height and width** (Android guidance; WCAG 2.2 target size AA is 24 px) | **148 of 148 controls** are at least 48 dp both ways (narrowest is 49.7 dp) |
| Accessible names | every control has one | 0 unnamed controls |
| Decorative icons | hidden from screen readers | 0 icons exposed (all `aria-hidden`) |
| Minimum text size | 12 px or more | 0 text nodes below 12 px, including the take-over and foreground states |
| Text contrast | 4.5:1 for normal text | **13 of 13 pairs pass** (table below) |
| Non-text contrast | 3:1 for the borders of inputs and chips | input and chip borders 3.62:1 on white, 3.42:1 on the page background: pass |
| Large text and layout | no clipped or sideways-scrolling content | 0 overflow cases at 100%, 130% and 160% text left to right, and at 100% and 160% right to left |
| Fits without scrolling | the long, mismatched scrollbar was a complaint | **36 of 36 screens fit with no scrolling at 100% text** (scrollbars are also restyled slim and warm for larger text) |
| Headings | each screen has one | 36 of 36 (Home has an invisible heading) |
| Status announcements | changes announced | 10 live regions across the screens, including the approval card |
| Focus on navigation | focus moves to the new screen's heading | yes (checked) |
| Behaviour | 31 scripted interaction checks | 31 of 31 pass (pause never auto-resumes, Back on approval denies, recovery buttons return to the failed step, take-over observation is off by default) |

### Text contrast pairs (ratio)

| Pair | Ratio |
|---|---|
| Body text (ink) on page background | 13.49 |
| Secondary text on page background | 5.44 |
| Secondary text on soft card | 5.04 |
| Secondary text on white card | 5.76 |
| White button label on terracotta | 5.29 |
| Soft-button label on peach | 9.83 |
| "Ready" chip: green on pale green | 5.14 |
| Needs-OK / attention: amber on pale amber | 6.93 |
| Stop button and error title: red on pale red | 5.50 |
| Text button: terracotta on page background | 5.00 |
| Body text on pale amber card | 12.51 |
| Secondary text on pale amber card | 5.05 |
| Body text on pale red card | 11.99 |

## Corrections after independent review

The first version of this document overstated two results. Reviewer B (a different model family) found:

1. "127 of 127 controls at least 48 dp" was wrong: only heights were measured, and two variant tabs were 46 and 47 dp wide. Fixed (minimum width 48 dp) and the check now measures width as well as height.
2. "0 text nodes below 12 px" was wrong in one reachable state: the "Not observing" badge was 11 px after pressing Take over. Fixed (12 px) and the sweep now covers that state.
3. Four contrast rows were labelled with the wrong surfaces (values were correct). Relabelled above.
4. The status-region count was understated and the approval card had none. The approval card is now an alert region.
5. Focus dropped to the page body after navigation. Screens now move focus to their heading.
6. Input and chip borders measured about 1.3:1. Strengthened to 3.4 to 3.6:1.

Reviewer A (flow traceability) found that Pause, Stop and Take over lacked the spec's literal wording; they now show "Pause after the current step finishes", "You drive; EQO holds" and "Stop this task completely".

## Gaps and things not tested (be honest about these)

- **No screen reader run.** TalkBack was not used. Reading order and the spoken approval timer are untested. Needs a device pass in the Compose build.
- **Real font scaling.** The prototype scales text with a CSS multiplier. Android's font scale and display-size settings can behave differently; test at the largest settings on Android 11, 12 and 13. At 130% and 160% some screens will scroll; that is expected.
- **Right to left** was previewed with a direction switch and logical spacing, with no hard-coded left or right CSS, but not reviewed by a native reader. Some icons mirror (arrows) and some must not; this needs a native check.
- **Translation length:** only a fixed 160% stress test was done. Languages such as German or Finnish need a real pseudo-localization pass.
- **Color-blindness simulation** was not run. Status always uses a word plus a symbol, not color alone.
- **Card hairlines** are 1.2:1 on purpose: cards are identified by their content, not their outline. If a stricter reading of the non-text contrast rule is wanted, they can be darkened.
- **The 60-second approval timeout** needs a way to hear the time left and to ask for more time. A recommendation for the build, not yet designed.
- **Motion** is out of scope for Phase One. Animations are planned for Phase 2.
- **Cognitive load** on the five helper checks needs usability testing with first-time users; the prototype shows the intended structure only.

## Recommendations carried into the build (TASK-015)

Use Material components with the tokens in `prototype.html`; keep every control at 48 dp or more in both dimensions; expose every status change through live regions; move focus to the new screen's heading on navigation; keep the approval timer visible and spoken, with a way to ask for more time; run TalkBack, the largest font and display size, and a right-to-left pseudo-locale as part of the exit review.
