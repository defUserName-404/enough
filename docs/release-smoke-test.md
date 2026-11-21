# Release smoke test (real device)

CI covers unit tests, lint and a debug build. It cannot cover what only a phone
does. Run this before publishing a release, especially after dependency bumps
(Kotlin, Gradle, coroutines, serialization, datastore, bouncycastle,
conscrypt).

Use a **spare phone** if you can. On a daily phone, do the upgrade check (A) and
skip the setup check (B).

## A. Upgrade over an existing install (most important)

Needs: a phone already running the previous release with limits set.

1. Note your limits and which apps are limited (screenshot the home screen).
2. Install the new release APK over it. It must install without uninstalling.
   (Same signing key, so this works.)
3. Open Enough. Expect: no crash, limits and apps identical to step 1.
   - Catches datastore migration problems.
4. Reboot the phone, open Enough. Expect: limits still there, protection active.
5. Open a limited app until its limit runs out. Expect: Android shows
   "Can't open this app". No overlay.
6. **Usage tab:** screen time per app, 7-day history and trends render, no blank
   or zero data.
7. Make a limit **stricter**. Expect: applies immediately.
8. Give an app **more time**. Expect: takes 24 hours, shows a pending change,
   can be cancelled.

## B. Fresh setup and pairing (spare phone only)

Needs: Android 11+, no accounts, one user. Takes about 10 minutes.

1. Install, open, pick apps and limits, tap **NEXT**.
2. Grant usage access (and autostart / no battery restrictions on
   Xiaomi/Samsung/Oppo/Vivo/Huawei).
3. Remove all accounts and extra users.
4. **On-phone pairing:** Developer options → Wireless debugging → Pair device
   with pairing code. Enter the 6 digits in Enough's notification.
   - Expect: pairing succeeds, Enough becomes device owner, Wireless debugging
     turns off by itself.
   - This exercises the crypto libraries (bouncycastle, conscrypt). A failure
     here after a crypto bump is a release blocker.
5. Add accounts back.
6. Confirm protection: Enough can't be uninstalled, force-stopped or have data
   cleared.

## C. In-app update

1. Open Enough → gear → **CHECK FOR UPDATE** (a launch-time check also shows an UPDATE banner on Home).
2. With a newer release published: **UPDATE TO x.y.z**. Expect download,
   SHA-256 check, install prompt, limits kept afterwards.
3. With no newer release: expect "up to date", no error.

## D. Uninstall flow

1. Gear → **START UNINSTALL · 24H**. Expect a 24h countdown, cancel works.
2. (Optional, needs 24h) After the wait: notification "OFF · YOU CAN
   UNINSTALL IT NOW", then uninstall normally.

## E. Quick log check (needs a computer, any phone)

```bash
adb logcat -c
# use the app for a few minutes, then:
adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime'
```

Expect no output.

## F. Xiaomi / HyperOS device checks (not verifiable on an emulator)

The code was checked on an AOSP emulator (API 35); each row below is only about
MIUI/HyperOS behaviour that differs or is unknown. Needs a Xiaomi/Redmi/POCO phone.
When a row fails, fix the code (or the fallback text) named in the last column,
then delete the "Unverified" comment there.

| # | Check | Expect | Where in code |
|---|---|---|---|
| F1 | Open the AUTOSTART and BATTERY rows on the Lock screen | Both MIUI pages open (no fallback to plain Settings) | `core/OemTable.kt`, `oem/OemHelper.kt` |
| F2 | Set battery saver for Enough to "No restrictions", then check `batteryUnrestricted()` | Returns true (MIUI battery saver is separate from the Doze whitelist) | `oem/OemHelper.kt` |
| F3 | Pair on-phone (Wireless debugging) via the notification | Pairing and `dpm set-device-owner` work without "USB debugging (Security settings)"; otherwise NEEDS_SECURITY_SETTINGS / WITH A COMPUTER shows | `setup/AdbClient.kt` |
| F4 | Same run, watch the TLS handshake | conscrypt-android TLS 1.3 works, no hidden-API errors | `setup/AdbClient.kt` |
| F5 | Wait for mDNS self-discovery during pairing | Found within 5 min; otherwise the TIMEOUT text points to WITH A COMPUTER | `setup/NsdDiscovery.kt` |
| F6 | Type the pairing code in the shade notification | Inline reply works; otherwise use split screen | `setup/PairCodeReceiver.kt` |
| F7 | After setup, post any Enough notification | Shows without a permission prompt (POST_NOTIFICATIONS auto-granted) | `service/Notifier.kt`, `admin/Policy.kt` |
| F8 | After setup: `adb logcat -d` and search for "protection missing" | No match on HyperOS | `admin/Policy.kt` (`applyProtection`) |
| F9 | Limit apps incl. Security and Settings; run a limit out | Refused packages land in the unsuspendable list; "App paused" dialog shows for the rest | `admin/Policy.kt` (`setSuspended`), `usage/AppCatalog.kt` |
| F10 | Reboot, wait 2 min, open nothing | Enforcement notification is back and apps still block (LOCKED_BOOT_COMPLETED arrives) | `service/EnforcementService.kt`, `service/BootReceiver.kt` |
| F11 | Use a limited app 10 min incl. floating window and split screen | Count is within ~1 min of reality; Dual apps (user 999) are not counted | `usage/UsageReader.kt` |

## Result

Record: date, release version, phone model, Android version, pass/fail per
section, and anything odd. Any failure in A, B4 or C blocks the release.
