# ADR-0008: On-device provisioning via Wireless debugging

Status: Accepted (2026-11-21)

## Context
Device Owner can only be granted by adb (`dpm set-device-owner`) or by QR/NFC provisioning, which needs a factory reset. Requiring a computer is a big setup hurdle. On Android 11+, *Wireless debugging* exposes adbd over TLS on the device itself, and an app can pair with it using the 6-digit pairing code, the same way LADB and Shizuku do.

## Decision
Enough embeds an adb client (`libadb-android`, Apache-2.0):
- It discovers the pairing and connect ports via mDNS.
- It takes the pairing code through a notification RemoteInput, so the system pairing dialog stays open.
- It connects to `127.0.0.1` and runs `dpm set-device-owner` on itself.
- It then sets `adb_wifi_enabled 0` and deletes its adb key material.

The computer adb path stays as a fallback tab.

## Consequences
- Enough now declares `INTERNET`, which amends NFR-4. Setup sockets are loopback-only through a single constant. A second, user-triggered use was added later: the in-app updater (HTTPS to `api.github.com` only, FR-23). `LoopbackGuardTest` allows networking only in `Updater.kt` and fails on any other host.
- New dependencies:
  - `com.github.MuntashirAkon:libadb-android:3.1.1` and `com.github.MuntashirAkon.spake2-java:spake2-android:2.2.1` (LGPL-3.0, native). Both come from **JitPack**, scoped to those two groups only.
  - `org.bouncycastle:bcprov-jdk15to18:1.81` (MIT), which also generates the self-signed cert, so the GPL-2.0 sun-security-android is avoided.
  - `org.conscrypt:conscrypt-android` for TLS 1.3. It was chosen over the hidden-API route through platform Conscrypt, because the one-shot setup has to be reliable on OEM ROMs.
- ABIs are limited to arm64-v8a and armeabi-v7a. The APK grows by several MB. R8 shrinking for release is a separate task.
- Accounts still have to be removed before setup, because Android enforces that for every adb path.
- MIUI/HyperOS behavior is unverified: mDNS, RemoteInput, and whether "USB debugging (Security settings)" is needed. These are marked `TODO(device)`, with a fallback to the computer tab.
- After activation, Enough turns Wireless debugging off itself; it does not re-apply `DISALLOW_DEBUGGING_FEATURES`, so developer options stay available.
