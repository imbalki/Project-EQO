# ADR-0005: bounded CONNECT-plane key enrollment after wireless pairing

Status: Accepted by task lead for PR #77 (Refs #20)
Date: 2026-10-06

## Context

AOSP's system_server pairing service generates an RSA key per pairing session; adbd generates a different
CONNECT TLS key per daemon process. Pinning the PAIR certificate on CONNECT therefore fails on every connection.
SPAKE2 authenticates the PAIR session via the six-digit code and TLS exporter, but does not authenticate the
separate CONNECT certificate. The authenticated peer GUID does not carry a CONNECT public key either.

## Decision

After a fresh, user-tapped Pair and connect flow successfully completes SPAKE2 and authenticated peer info,
permit one in-memory CONNECT-key enrollment opportunity at the same numeric loopback address and the entered
CONNECT port, expiring 30 seconds after success using a monotonic clock. Consume it before dialing, including on
failure. Capture one certificate key, require the same key on all TLS callbacks and negotiated-peer recheck,
and persist only after TLS success, within the window. Do not persist the opportunity or give it to ordinary
reconnect/helper-start paths. Every subsequent connection requires the exact stored pin or fails closed with
Needs re-pair. Explicit fresh pairing can replace an earlier pin. Existing HMAC/client-key binding, private
no-backup storage, loopback guard and exact helper-command allowlist remain unchanged.

## Consequences

This is bounded trust-on-first-use, not cryptographic authentication of the CONNECT key by SPAKE2. A malicious
local listener winning the entered CONNECT port during the enrollment window can still be enrolled. The
short window, same address/port and explicit fresh pairing narrow this accepted first-use risk, not remove it.
An adbd restart/reboot rotates its per-process key: the owner must pair again; no automatic pin replacement.

Host tests cover the policy with real X.509 keys, not Android Conscrypt/adbd interoperability. Realme testing
must cover initial pair/connect, pinned reconnect, wrong code/port, expired enrollment, revocation, reboot,
and helper start/authorization/binder health. PR #77 stays unmerged pending review/device validation.
