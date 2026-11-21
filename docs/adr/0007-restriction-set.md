# ADR-0007: Restriction set

Status: Accepted

## Context

The restriction set closes the known bypasses (see design.md section 4.7).

## Decision

Applied: `setUninstallBlocked(self)`, `setUserControlDisabledPackages([self])` (blocks force-stop and clear data), `DISALLOW_SAFE_BOOT`, `DISALLOW_CONFIG_DATE_TIME`, `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, and `DISALLOW_ADD_MANAGED_PROFILE`. Enough never suspends its own package.

**Not** applied: `DISALLOW_FACTORY_RESET` (recovery reset stays as the last-resort escape) and `DISALLOW_DEBUGGING_FEATURES` (developer options stay available). Debug builds use `android:testOnly="true"` so the developer can run `adb shell dpm remove-active-admin`. Older installs that still have `DISALLOW_DEBUGGING_FEATURES` clear it on the next `applyProtection` pass.

## Consequences

Factory reset from recovery remains possible as the last-resort escape. Debug builds can be removed with adb.
