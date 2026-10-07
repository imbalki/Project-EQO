# ADR-0006: EQO-owned, process-scoped helper consent

Status: implemented locally; independent security review and device validation pending.

## Context

PR #77 starts the embedded helper but authorization stops before binder health: no client requested permission, and the donor server attempted to launch a manager permission activity that EQO does not ship. An app-id manager check also bypassed execution consent, so merely adding a dialog would not enforce denial.

## Decision

The foreground wireless setup screen calls the bundled client `requestPermission` and registers the existing application Binder confirmation callback. The server asks that attached process to show an EQO-branded platform AlertDialog, protected by `protectConfirmationDialog` / `ConfirmationTouchGuard`. No exported activity, broadcast, external target UID, new permission or separate manager app is introduced.

Permission request and confirmation-result transactions to the service are synchronous. [Android documents](https://developer.android.com/reference/android/os/Binder#getCallingPid()) PID=0 for remote oneway calls, so PID-scoped consent must not use those calls. Both handlers reject nonpositive authenticated PIDs; the service rejects oneway request/result transactions before dispatch. Server-to-application prompt/result callbacks remain oneway. The client waits for a result for 55 seconds; the server expires pending requests after 60 seconds using elapsed realtime. Confirmation must match the actual calling UID and PID, pending request code, attached client, exact EQO package and an unshared UID. A completed request cannot be replayed. Deny/back, timeout, interruption or leaving the setup screen fails closed. The authorization sequence reports `HELPER_NOT_AUTHORIZED`; binder health only runs after a confirmed grant.

Grant is process/session-scoped: only that client record is changed, never all records for a UID, a runtime permission or persisted configuration. New attachments ignore prior donor configuration grants. Existing manager execution and arbitrary permission-flag grant bypasses are removed. The only permitted flag update revokes the caller's own process. Cancellation invalidates pending consent and revokes under the same lock used to apply a grant; timeout cleanup does not wait for the UI queue. A previously authorized reconnect closes its prompt without revoking the existing session.

All request, confirmation-result and revocation calls use a shared single background queue in the EQO process. Queue ordering places cancellation revocation after even a delayed request/reply and before an immediate retry from a new prompt. Lifecycle close does not block the UI; the authorization worker waits for that same queued cleanup acknowledgement, including when lifecycle close has already marked the prompt closed. Interrupted workers finish cleanup before restoring their interrupt flag. Client death clears its pending slot. Closing an unresolved prompt wakes its waiter immediately; a permission read returns its local server result, not a shared field that a queued callback could overwrite.

## Trust assumptions and limits (carry into the draft PR)

- Android Binder authenticates the calling UID/PID; PackageManager correctly maps the installed `ai.eqo.app` package to its UID. The platform's install/update signature checks and private-app storage are trusted. Shared UIDs are rejected for consent.
- The EQO application process and bundled shell/root helper are trusted code. This is not protection against a compromised EQO process, root/shell actor, kernel, or hostile replacement APK installed after uninstall. The server can verify a matching request, not cryptographically attest a human tap inside its own trusted app.
- Binder calls to the trusted live helper and framework make progress normally. Request and confirmation-result calls are synchronous but dispatched off the main/UI thread. Application prompt/result callbacks remain nonblocking. The human-response wait is bounded. Synchronous transport, readiness/permission reads and cancellation acknowledgement are not hard-real-time guarantees against a wedged OS or malicious helper.
- Existing touch policy rejects touch-point obscuration and poisons the gesture until a fresh clean DOWN; partial obscuration elsewhere is accepted, per the established OEM compatibility decision. Android/framework accessibility and input delivery remain trusted.
- Server and client ship together. The corrected synchronous consent wire semantics require a freshly started matching helper; compatibility with a helper process still running old APK code is not claimed.

## Consequences

A process restart requires fresh user consent. Denial does not permanently block retry. The setup hub adds the failed wireless check name to its existing guidance rather than exposing arbitrary exception text. Host tests exercise generated AIDL Proxy/Stub parcel dispatch into production service handlers, explicitly model oneway PID=0 and check synchronous transaction flags; OS startup and package lookup are injected test boundaries. These tests are not proof of kernel Binder transport/identity or device Binder ordering, OEM window focus/overlays, actual helper startup or the five-step activation flow.
