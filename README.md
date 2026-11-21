# Enough

A screen-time limiter for Android that you can't switch off on a whim.

Pick the apps that eat your time and give each one a daily limit. When the limit runs out, Android itself blocks the app until midnight. Making a limit **stricter** works immediately. Giving yourself **more time**, removing a limit or turning Enough off waits **24 hours**, so the urge has time to pass.

- Android blocks the app itself ("Can't open this app"). There's no overlay, so nothing needs to be dismissed.
- While Enough is active, it can't be uninstalled, force-stopped or cleared.
- Usage tab: screen time per app, the last 7 days, and trends.
- No accounts, no analytics. Everything stays on the phone. The only network use is when you tap **UPDATE** in the app (see below).

Requires **Android 11 or newer**.

## Install

1. Download the latest `enough-x.y.z.apk` from [Releases](../../releases).
2. Open it and allow installing from your browser or file manager when asked.

**Updating:** open Enough, tap **▪▪** (top right), then **CHECK FOR UPDATE**. If a newer release exists, tap **UPDATE TO x.y.z**. Enough downloads it from GitHub, checks the SHA-256, and installs it. It never checks in the background. Your limits are kept, because every release is signed with the same key:

```
SHA-256 F6:DE:ED:A0:78:C4:08:EC:F6:A0:6E:B2:4F:FD:DB:F9:D6:ED:DD:9C:EC:B6:7A:98:02:56:DB:1F:41:36:2B:14
```

## Set up (one time, about 10 minutes)

Android only lets an app become "device owner", which is what makes it impossible to uninstall, on a phone with **no accounts and only one user**. You add your accounts back right after.

1. Open Enough, pick your apps and limits, then tap **NEXT**.
2. Grant **usage access**. On Xiaomi, Samsung, Oppo, Vivo and Huawei, also allow **autostart** and **no battery restrictions**. Enough shows a button for each.
3. Remove **every account**: Settings → Accounts. That includes Google, Mi or Samsung, WhatsApp and email. Also delete Dual apps, Second space and guest users.
4. Choose one:
   - **On this phone** (no computer needed):
     1. Turn on *Developer options*, then *Wireless debugging*. Wi-Fi must be connected.
     2. Tap *Pair device with pairing code*.
     3. Pull down the notification shade and type the 6 digits into Enough's notification.
     4. Enough finishes by itself and turns Wireless debugging off again.
   - **With a computer**: connect over USB and run the command Enough shows (`adb shell dpm set-device-owner …`).
5. Add your accounts back.

**Xiaomi / HyperOS:** also turn on *USB debugging (Security settings)* in Developer options. It needs a SIM and a Mi account just for that step.

## Uninstall (no computer needed)

1. Open Enough and tap **▪▪** (top right), then **START UNINSTALL · 24H**.
2. Wait 24 hours. You can cancel during that time.
3. Enough turns off all blocks and its own protection, and notifies you: "ENOUGH IS OFF · YOU CAN UNINSTALL IT NOW". Then long-press the app icon and uninstall it like any other app.

If you are completely stuck, a factory reset from recovery always works. Enough deliberately doesn't block that.

## FAQ

**Why isn't it on Google Play?** Play is unlikely to accept an app that deliberately resists uninstalling, and that sets itself up through Wireless debugging. Releases here are signed and reproducible from source.

**Can I get around it?** Opening the app in a browser still works, so add your browser as a limited app too. Everything else (uninstalling, clearing data, changing the clock, safe mode, a second user) is blocked.

**Is my data sent anywhere?** No. Your data never leaves the phone. Network access is limited to the phone itself (`127.0.0.1`, during on-phone setup) and to `api.github.com` plus GitHub's download servers when you tap **UPDATE**. A test fails the build if any other host appears in the code.

## Build from source

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug build installs as `app.enough.debug` and is a "test only" app (it can't self-update; install the release APK once instead), so a developer can remove it with `adb shell dpm remove-active-admin app.enough.debug/app.enough.admin.EnoughAdminReceiver`.

Release builds are signed in GitHub Actions when a `vX.Y.Z` tag is pushed. Design notes are in [`docs/`](docs/).

## License

[GPL-3.0](LICENSE)
