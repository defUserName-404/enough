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

1. Open Enough → **▪▪** → **CHECK FOR UPDATE**.
2. With a newer release published: **UPDATE TO x.y.z**. Expect download,
   SHA-256 check, install prompt, limits kept afterwards.
3. With no newer release: expect "up to date", no error.

## D. Uninstall flow

1. **▪▪** → **START UNINSTALL · 24H**. Expect a 24h countdown, cancel works.
2. (Optional, needs 24h) After the wait: notification "ENOUGH IS OFF · YOU CAN
   UNINSTALL IT NOW", then uninstall normally.

## E. Quick log check (needs a computer, any phone)

```bash
adb logcat -c
# use the app for a few minutes, then:
adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime'
```

Expect no output.

## Result

Record: date, release version, phone model, Android version, pass/fail per
section, and anything odd. Any failure in A, B4 or C blocks the release.
