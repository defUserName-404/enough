# ADR-0002: Enforce with `setPackagesSuspended`, not overlays

Status: Accepted

## Context

Blocking by overlay would need extra permissions and can flicker. The OS can block an app directly.

## Decision

Enforce with `setPackagesSuspended`. The OS blocks the app itself: greyed icon, "App paused" dialog, no flicker, and no `SYSTEM_ALERT_WINDOW` or Accessibility permission needed. Only the Device Owner can unsuspend.

## Consequences

No overlay or Accessibility permission is needed. Only the Device Owner can unsuspend an app.
