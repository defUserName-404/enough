# ADR-0007: Restriction set

Status: Accepted

## Context

The restriction set closes the known bypasses (see design.md section 4.7).

## Decision

Applied: `setUninstallBlocked(self)`, `setUserControlDisabledPackages([self])` (blocks force-stop and clear data), `DISALLOW_SAFE_BOOT`, `DISALLOW_CONFIG_DATE_TIME`, `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_ADD_MANAGED_PROFILE`, and `DISALLOW_DEBUGGING_FEATURES` (release builds only).

**Not** applied: `DISALLOW_FACTORY_RESET`, so recovery reset stays as the last-resort escape. Debug builds use `android:testOnly="true"` so the developer can run `adb shell dpm remove-active-admin`.

## Consequences

Factory reset from recovery remains possible as the last-resort escape. Debug builds can be removed with adb.
