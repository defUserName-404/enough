# Enough — Design

Mockups: ../design/mockups/*.png

## 4. Design doc

### 4.1 Package layout (`app/src/main/java/app/enough/`)
```
core/      pure Kotlin: Config, PendingChange, ChangePolicy, Enforcer
data/      ConfigStore (DataStore<Config>, JSON serializer)
usage/     UsageReader (UsageStatsManager → Map<pkg, Duration>)
admin/     EnoughAdminReceiver (DeviceAdminReceiver), Policy (wraps DevicePolicyManager)
service/   EnforcementService (foreground), BootReceiver
ui/        OnboardingAppsScreen, OnboardingLockScreen, HomeScreen, EditLimitSheet
```
Also `res/xml/device_admin.xml` and the receiver plus `android.app.device_admin` meta-data in `AndroidManifest.xml`.

### 4.2 Data model
```kotlin
data class Target(val pkg: String, val limitMin: Int)
sealed interface Change { AddOrLower(t: Target); RaiseLimit(t: Target); Remove(pkg); Release }
data class PendingChange(val id: String, val change: Change, val applyAt: Instant)
data class Config(
  val targets: List<Target>, val pending: List<PendingChange>,
  val warned: Map<String, LocalDate>)
```

### 4.3 Core logic (pure)
- `ChangePolicy.classify(change, config): Tighten | Loosen`. Tighten → apply now. Loosen → add `PendingChange(applyAt = now + 24h)`.
- `Enforcer.decide(config, usage, now) → Decision(suspend: Set, unsuspend: Set, warn: Set)`
  - `blocked = targets where usage[pkg] ≥ limit`
  - `warn = targets where limit - usage ≤ 2 min, not yet warned today`
  - If usage can't be read, `blocked = all targets` (fail closed).

### 4.4 Tick (EnforcementService)
1. Apply each pending change where `applyAt ≤ now`. If it's Release, run the release sequence and stop.
2. `usage = UsageReader.sinceMidnight(now)`. On `SecurityException` or no access, fail closed.
3. `decision = Enforcer.decide(...)`, then `Policy.setSuspended(decision.suspend, true)` and `Policy.setSuspended(decision.unsuspend, false)`.
4. Post warnings and persist `warned`.
5. Schedule the next tick: 15 s if a target is in the foreground, 60 s otherwise. Skip ticks while the screen is off.

Restart path: `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`) starts the service. The service is `START_STICKY` and the Device Owner app is exempt from battery optimization.

### 4.5 Policy (Device Owner wrapper)
- `activate()`: applies the restriction set from ADR-0007.
- `release()`: unsuspend all targets, clear the restrictions, `setUninstallBlocked(self,false)`, `clearDeviceOwnerApp(pkg)`.
- `isActive() = dpm.isDeviceOwnerApp(pkg)`.

### 4.6 Screens
1. **Onboarding 1: "What eats your time?"** A searchable list of apps with checkboxes. Each checked app gets a minute stepper. Button: *Next*.
2. **Onboarding 2: "Lock it."**
   - (a) A *Grant usage access* button opens `ACTION_USAGE_ACCESS_SETTINGS` and shows ✓ once granted.
   - (b) *Activate*: shows the copyable `adb shell dpm set-device-owner app.enough/.admin.EnoughAdminReceiver` plus 3 steps (remove accounts, enable USB debugging, run the command). It polls `isDeviceOwnerApp` every 2 s, and on success calls `activate()` and goes to Home.
3. **Home.** One row per target: icon, name, `12 / 20 min` progress bar, and a 🔒 if blocked. A FAB adds an app. Tapping a row opens EditLimitSheet. Two tabs: *LIMITS* (the rows) and *PENDING · n* (each pending change with a countdown and *Cancel request*). A `USAGE →` link beside "Screen time today" opens the Usage screen. A gear icon opens Settings (*Update*, *Uninstall Enough*). An update banner shows on Home only when a newer release exists; Enough checks once per launch.

### 4.7 Bypass matrix
| Bypass | Mitigation |
|---|---|
| Uninstall | Device Owner + `setUninstallBlocked` |
| Force stop / clear data | `setUserControlDisabledPackages` |
| Revoke usage access | Fail closed: block all targets |
| Change the clock | `DISALLOW_CONFIG_DATE_TIME` |
| Safe mode | `DISALLOW_SAFE_BOOT` |
| Raise limit / remove app | 24h wait, no override |
| Web version, or a duplicate/alt client | **Known limitation.** Mitigation: add the browser as a target. |
| Factory reset from recovery | **Accepted.** It's the deliberate last resort. |
| MIUI background killer | FR-18 autostart + no battery restrictions, `START_STICKY`, and the boot receiver. Plus a "last tick" heartbeat: if the gap since the last tick is over 5 min, the next tick re-applies blocks. Blocks persist in the OS anyway, because suspension survives the process dying. |
| MIUI Second space / guest / Dual apps | FR-19 user restrictions |
| MIUI "Find device" / Mi account | Must be signed out only during provisioning (see 4.9). It can be re-added after. |

### 4.9 MIUI / HyperOS provisioning (shown on onboarding screen 2)
1. Settings → About phone → tap *OS version* 7× to enable developer options.
2. Developer options: turn on **USB debugging**, **Install via USB** and **USB debugging (Security settings)**. These need a Mi account and a SIM once.
3. Turn off Find device, then remove **all** accounts: Mi, Google and any others. `dpm` refuses if any account exists.
4. `adb install enough.apk`, then `adb shell dpm set-device-owner app.enough/.admin.EnoughAdminReceiver`
5. Re-add the accounts. Enough detects Device Owner, then applies the restrictions (date/time, safe boot, users/profiles). Developer options stay available.

Risk: some HyperOS builds have reportedly rejected `set-device-owner`. **Milestone 0 is a feasibility spike on the real phone**: install a bare receiver, run `dpm`, and call `setPackagesSuspended` on one app. Stop and report if it fails.

### 4.10 Mockup spec (step 0)
**Brutalist mono, not default Material.** Compose uses only `foundation` plus a custom theme: no Material 3 components, no ripple, no elevation.
- Colors: `#000` and `#FFF` only, plus a single **`#FF2A1F` red** used for LOCKED/blocked and destructive actions. Dark is the default. Light is the inverted variant.
- Type: a heavy grotesk for headings in ALL CAPS (bundled Space Grotesk or Archivo Black, OFL), and **JetBrains Mono** for every number and time (`12:40 / 20:00`). 2 sizes for headings, 1 for body.
- Shape: 0 px corners, 2 px solid borders, 1 px hairline row dividers, no shadows, no gradients.
- Progress: a block bar, solid fill against a 1 px outline, 20 segments.
- Buttons: rectangular outlined `[ + ADD APP ]`. Primary is filled white on black. Pressing inverts the colors instead of rippling.
- App icons are rendered in grayscale, which also adds friction.
- Motion: none, apart from a 1-frame invert on press.
- **01 Apps:** title "What eats your time?", search bar, app rows (icon, name, checkbox, `– 20m +` stepper on checked rows), bottom *Next*.
- **02 Lock:** title "Lock it.", a 4-item checklist (Usage access ✓, Autostart [MIUI], Battery [MIUI], Friend PIN optional), a monospace adb command card with a copy button, *Waiting for activation…* spinner.
- **03 Home:** `ENOUGH` header with a gear icon (opens Settings: *UPDATE* and *UNINSTALL ENOUGH*), `USAGE →` link on the screen-time row, `LIMITS | PENDING · n` tabs. Variants: `03b` update banner, `03c`/`03d` pending tab (filled / empty), `07` Usage screen, `08` Settings. Rows: grayscale icon, `INSTAGRAM`, mono `12:40 / 20:00`, block bar. Blocked rows show red `LOCKED` and a red bar. The PENDING tab lists: `INSTAGRAM → 40:00 · 23:12:05` with `[CANCEL]`. A bottom `[ + ADD APP ]` button instead of a FAB.
- **04 Edit sheet:** minute stepper, and a note: "Raising waits 24h. Lowering applies instantly."

### 4.8 Milestones
0. MIUI feasibility spike (4.9)
1. `core/` + unit tests
2. `admin/` + manual activation on the emulator
3. `usage/` + service tick
4. UI (3 screens)
5. Release flow + bypass checks on a real device

### 4.11 Computer-free setup (FR-20, ADR-0008)
Mockups: `design/mockups/02b-onboarding-lock-phone.png`, `02c-pairing-notification.png`. Implementation plan: `.claude/plans/enough-selfprovision.md`.
- **Summary:** Enough sets itself up as Device Owner using Android 11+ *Wireless debugging*. The user pairs with a pairing code and Enough runs `dpm set-device-owner` on itself over localhost adb, the way LADB does. Cost: needs a localhost-only network permission (amends NFR-4) plus an embedded adb client. The computer adb path stays as the fallback.

### 4.12 Usage insight & naming (FR-21, FR-22)
Mockups: `design/mockups/03-home.png` (LIMITS tab) and `07-usage.png` (USAGE tab).
- `core/UsageHistory` folds 14 days of UsageEvents into per-day, per-app durations, using the same rules as `UsageFolder` (sessions split at local midnight). The day total is the **union** of intervals, so split screen isn't counted twice. Trends compare the last 7 days with the previous 7 and are `null` when the previous week has gaps.
- History is read on IO by the UI only, on tab open and every 60 s while visible. It is never read by the enforcement Ticker.
- DPM `setOrganizationName("Enough")` and the short and long support messages replace the "IT admin" wording in the system block dialogs.
