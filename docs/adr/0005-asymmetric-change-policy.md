# ADR-0005: Asymmetric change policy

Status: Accepted (amended 2026-11-21: friend PIN removed)

## Context
Impulse changes to limits could bypass the lock. An instant override such as a friend PIN is itself a bypass route, and it makes you depend on another person.

## Decision
Tightening (add an app, lower a limit) applies instantly. Loosening (raise a limit, remove an app, Release) becomes a pending change that applies after 24h, and you can cancel it. There is no instant override.

## Consequences
Impulse bypasses are removed: the urge rarely survives 24h. A real need still gets met, just a day later. The data model, UI and security surface get simpler, because no PIN hashing, lockout or PIN dialog is needed.
