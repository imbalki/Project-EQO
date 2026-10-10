# Google Keep note creation (t_821c6c33)

NOT TESTED ON PHONE. This flow is based on the card's supplied technical widget
observations for com.google.android.keep on Android 11, not a new device run.
No private note text, screenshot, account, or device identifier is recorded.

The package-keyed AppControlHints table is included in RegistryPlanVocabulary's
system prompt consumed by TaskPlanner. For add/write/create a note in Keep:

1. OPEN_APP appName=Google Keep.
2. WAIT durationMs=3000.
3. CLICK_TEXT text=Create a note (content-description of
   speed_dial_create_close_button). A missing entry fails on the first probe,
   becomes control_not_found and displays the existing plain Needs you status.
   The loop stops: no later typing or Back, no automatic alternate route.
4. CLICK_TEXT text=id:new_note_button. The speed-dial items have no labels;
   new_list_button, new_drawing_button and new_photo_note are not text notes.
5. TYPE_TEXT searchText=id:editable_title, content=<requested title>.
6. Only when supplied: TYPE_TEXT searchText=id:edit_note_text,
   content=<requested body>. Never invent a body for a title-only request.
7. PRESS_BACK to autosave and return to the list.

There is no Take a note bar in the observed version. The toolbar EditText is
Search Keep, not a note field. Explicit id: references match resource IDs only,
with an exact full ID or slash-delimited suffix; no partial-label fallback, sole
input fallback or focused-input retargeting is allowed for an ID reference.
Normal text/content-description matching remains supported. Production searches
only rootInActiveWindow; existing own-EQO-window, password and takeover guards
still apply. IDs identify controls, not authority to bypass approval.

The note-edit draft guard permits Create a note and id:new_note_button as known
local navigation; arbitrary IDs and Send/Share alternatives remain rejected.
Tests use synthetic content and a fake sequence of home, unlabelled speed-dial,
and editor trees. Fake planner tests verify prompt consumption and approved
proposal bytes, not live-model reliability or actual autosave.

## Owner checklist (record build commit and pass/fail, synthetic content only)

- [ ] Start on Keep home; add/write/create a note called Test in Keep previews
      all six title-only steps, no body. Execute and confirm Back saves the title.
- [ ] Give an explicit synthetic body; preview includes seven steps, correct
      title/body IDs; confirm both fields and return to list after Back.
- [ ] Verify the unlabelled speed-dial chooses text note, never list/photo/drawing.
- [ ] Start inside an editor or other screen without Create a note: Needs you
      immediately, no search typing, later typing or Back. Correct screen and
      approve a new plan explicitly; do not auto-retry the old plan.
- [ ] Missing title/body ID with only Search Keep present: no text in toolbar.
- [ ] EQO approval/handle window in front, Stop/Pause or takeover: no mutation.
- [ ] Different Keep version/language or delayed UI: missing controls stop
      honestly; no guessed label or hidden fallback. Check counts/status only;
      do not save actual note contents or screenshots to logs.
