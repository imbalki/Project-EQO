# TASK-006: OpenRouter BYOK, secret storage, redaction, error handling

- Status: todo
- Depends on: 004
- Area: android
- Gate: 8 (DEV-10, DEV-13)
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-pro` (security-sensitive)
- Branch: agent/android/<issue>-byok-security

## Goal
The user pastes their own OpenRouter key, picks a model, tests the connection, and the key never leaks.

## Scope
`:core-llm`, `:core-security`. Keystore-backed key storage. One redaction pipeline for logs and crash reports that fails closed. Typed errors for 401, 429, offline and timeout. Show cost disclosure before first use (OpenRouter credits cost real money).

## Acceptance criteria
- [ ] Key stored encrypted; a test scans log output and finds no key text
- [ ] Redactor exception results in withheld text, never raw text
- [ ] Fuzz test: 1000 synthetic secrets through the redactor, zero leaks
- [ ] Connection test works against a mock OpenAI-compatible server for success, 401, 429, offline
- [ ] Cost disclosure screen shown before the key is first used

## Evidence required
Unit test output.

## Notes
Most unit-testable task; a good early check on model quality.
