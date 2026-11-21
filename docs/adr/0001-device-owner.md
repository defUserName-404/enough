# ADR-0001: Device Owner over Device Admin or Accessibility

Status: Accepted

## Context

Device Admin can be deactivated in Settings, and an Accessibility service can be switched off.

## Decision

Use Device Owner. A Device Owner can't be uninstalled until it gives up the role itself.

## Consequences

Setup requires adb and temporarily removing accounts.
