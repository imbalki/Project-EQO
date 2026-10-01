# TASK-011: Virtual display spike (S4)

- Status: todo
- Depends on: 007, 008; needs a physical device
- Area: android
- Gate: 6 (DEV-09)
- Models: author `mimo-v2.6-pro`, reviewer `glm-5.3-flash`
- Branch: agent/android/<issue>-virtual-display

## Goal
Create a virtual display, launch a compatible app into it, perceive, act, and clean up. Foreground fallback with explicit consent when the app is not compatible.

## Scope
`:platform-vd` from ClosePaw. Known stale-state race in the lifecycle arbiter: reproduce it first, then fix with a test. Display and input use reflection against framework services, so results vary by device; test per device.

## Acceptance criteria
- [ ] Create, launch, perceive, act, destroy works on the test device
- [ ] After teardown: display count back to baseline, no orphan helper process
- [ ] Double-destroy is safe
- [ ] Incompatible app triggers the consent-gated foreground fallback

## Evidence required
`dumpsys display` before and after, device logs.

## Notes
No universal background guarantee. Say so in the UI copy.
