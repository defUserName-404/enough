# Enough — Requirements

## Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | List the installed launchable apps (excluding self and Settings) so the user can pick targets. |
| FR-2 | Set a per-app daily limit of 5–240 min in steps of 5. |
| FR-3 | Track each target's foreground time since local midnight. |
| FR-4 | When used ≥ limit, block the app (suspended, with a system "App paused" dialog) until the next midnight. |
| FR-5 | Send one notification when 2 min are left on a target. |
| FR-6 | At local midnight, unblock all targets and reset usage. |
| FR-7 | **Tightening** changes (add an app, lower a limit) apply instantly. |
| FR-8 | **Loosening** changes (remove an app, raise a limit, Uninstall) become *pending* and apply after 24h. They can be cancelled at any time. |
| FR-9 | There is no instant override (no PIN, no password). The 24h wait is the only path to loosen. *(Friend PIN dropped 2026-11-21.)* |
| FR-12 | While active, the app can't be uninstalled, force-stopped or have its data cleared. |
| FR-13 | **Uninstall** (internally `Change.Release`; a loosening change): unblock everything, lift all restrictions, drop Device Owner. After that the app uninstalls normally. |
| FR-14 | Enforcement survives reboots. |
| FR-15 | **Fail closed**: if usage access is revoked or usage can't be read, block all targets and send a notification asking to re-grant. |
| FR-16 | Block the known bypasses: changing date/time and safe mode boot. Developer options stay available. |
| FR-17 | UI: exactly **2 onboarding screens** and **1 home screen** (plus an edit sheet). |
| FR-18 | **OEM helper checklist (works on any Android 11+ phone).** All core APIs are AOSP. Onboarding screen 2 adds extra items only when the manufacturer needs them, using an `OemHelper` with a table of intents:<br>• **Xiaomi/Redmi/POCO:** Autostart, via `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`, plus Battery: no restrictions.<br>• **Oppo/Realme/OnePlus (ColorOS):** startup manager.<br>• **Vivo:** background power.<br>• **Huawei/Honor:** app launch.<br>• **Samsung:** "Never sleeping apps".<br>• **Pixel/AOSP/other:** no extra items.<br>Each intent falls back to `ACTION_APPLICATION_DETAILS_SETTINGS` if it fails to resolve. |
| FR-19 | Block extra users and profiles: `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH` and `DISALLOW_ADD_MANAGED_PROFILE`. These cover MIUI **Second space**, guest mode and Dual apps. |
| FR-20 | **Computer-free setup.** On Android 11+, onboarding screen 2 defaults to an *On this phone* tab. Enough pairs with the phone's own Wireless debugging (pairing code typed into a Enough notification), connects to its own adbd on `127.0.0.1`, runs `dpm set-device-owner` on itself, then turns Wireless debugging off. dpm errors (accounts present, extra users, already provisioned) are shown as plain instructions. The *With a computer* tab keeps the adb command as a fallback. Pairing keys are deleted after success. |
| FR-21 | **Usage insight.** Home shows whole-phone *screen time today* (union of foreground time across launchable apps; excludes the app itself, System UI and the launcher), plus the limited-apps total. A *Usage* tab shows the last 7 days as a bar chart with the average and the change vs the previous 7 days, and a per-app list for the selected day. Each row has a 7-day mini chart, its trend, its limit status and a shortcut to set a limit. Days that Android has already pruned are shown as "no data", and trends are hidden when the comparison week is incomplete. |
| FR-22 | **Name and wording.** The app is shown as **Enough** (package id `app.enough`). Durations use the `4H 01M` format. System block dialogs show Enough's own support message instead of "contact your IT admin". |
| FR-23 | **In-app update.** Settings (gear icon) has CHECK FOR UPDATE; Enough also checks once per app launch and shows an UPDATE banner on Home when a newer release exists. It resolves the latest GitHub release via `github.com/.../releases/latest` (redirect to the tag), downloads `enough-x.y.z.apk` + checksum from the public release download URLs (CDN via redirect), verifies SHA-256, and installs with PackageInstaller (silent as device owner). Only on user tap. Does **not** use the rate-limited REST API. Android rejects a package signed with a different key. |

## Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Platform: minSdk 30 (Android 11), compileSdk/targetSdk 34 (installed locally). Must work on any Android 11+ device. The primary test device is MIUI 12.5+/HyperOS, and Pixel/Samsung behavior follows AOSP. |
| NFR-2 | Enforcement latency: an app is blocked ≤ 30 s after its limit is reached. |
| NFR-3 | Battery < 1 %/day. No ticking while the screen is off. |
| NFR-4 | Privacy: no analytics, all data stays on the device. The `INTERNET` permission exists for (a) loopback adb during computer-free setup (FR-20, sockets to `127.0.0.1`) and (b) the in-app update check (FR-23, once per app launch and on demand; HTTPS to `github.com` release URLs; CDN hosts only via redirect). A test guards both. *(Amended 2026-11-21, see ADR-0008.)* |
| NFR-5 | APK < 5 MB. Cold start < 1 s. |
| NFR-6 | Reliability: UsageStats is the source of truth, so a crash or reboot loses no usage data. |
| NFR-7 | Security: no secrets are stored. The 24h timer is computed from the system clock, which `DISALLOW_CONFIG_DATE_TIME` + auto time protect. |
| NFR-8 | Testability: all decision logic is pure Kotlin with an injected `Clock`, unit-testable without Android. |
| NFR-9 | Accessibility: TalkBack labels, 48 dp touch targets, dynamic text size. |
