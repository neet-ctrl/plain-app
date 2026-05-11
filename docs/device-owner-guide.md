# Device Owner Mode — Complete Guide for PlainApp

## Table of Contents

1. [What is Device Owner?](#what-is-device-owner)
2. [Device Admin vs Device Owner](#device-admin-vs-device-owner)
3. [Requirements Before Running the Command](#requirements-before-running-the-command)
4. [Step-by-Step Setup](#step-by-step-setup)
5. [Verify It Worked](#verify-it-worked)
6. [How to Remove Device Owner](#how-to-remove-device-owner)
7. [What You Gain — Feature Comparison](#what-you-gain--feature-comparison)
8. [Permissions PlainApp Grants Itself Automatically](#permissions-plainapp-grants-itself-automatically)
9. [All Device Owner Superpowers](#all-device-owner-superpowers)
10. [Common Errors and Fixes](#common-errors-and-fixes)
11. [Security Implications](#security-implications)
12. [Quick Reference Card](#quick-reference-card)

---

## What is Device Owner?

Android has a layered privilege system for apps:

```
Normal App
    ↓
Device Admin          ← Can lock screen, set password policy. Removable from Settings.
    ↓
Device Owner          ← Highest privilege a non-system app can ever hold. One per device.
    ↓
System App / ROM      ← Built into the OS. Cannot be achieved without flashing.
```

**Device Owner** is an Android Enterprise concept designed for corporate device management (MDM). It gives a single app near-complete control over the device — hardware, software, policies, and other apps.

PlainApp's `PlainDeviceAdminReceiver` is already declared in `AndroidManifest.xml` and supports both Device Admin and Device Owner roles. No app code changes are required to use either level.

---

## Device Admin vs Device Owner

| Capability | Device Admin | Device Owner |
|---|---|---|
| Lock screen remotely | ✅ | ✅ |
| Set password/PIN policy | ✅ | ✅ |
| Remote wipe | ✅ (limited) | ✅ (full enterprise wipe) |
| Revoke from Settings → Security | ✅ (user can do it) | ❌ (cannot be removed normally) |
| Grant permissions to itself | ❌ | ✅ |
| Silent APK install (no dialog) | ❌ | ✅ (Android 12+) |
| Block app uninstall | ❌ | ✅ |
| Lock device to one app (kiosk) | ❌ | ✅ |
| Disable factory reset | ❌ | ✅ (some OEMs) |
| Set global network proxy | ❌ | ✅ |
| Disable USB debugging remotely | ❌ | ✅ |
| Disable camera/bluetooth/USB device-wide | ❌ | ✅ |
| Enforce screen-lock complexity | ✅ | ✅ |
| Wipe after N failed attempts | ✅ | ✅ |
| One ADB command unlocks everything | ❌ | ✅ |

---

## Requirements Before Running the Command

### Mandatory

- **USB Debugging must be ON**
  `Settings → Developer Options → USB Debugging → Enable`
  If Developer Options is not visible: `Settings → About Phone → tap "Build Number" 7 times`

- **ADB must be installed on your computer**
  Download: https://developer.android.com/tools/releases/platform-tools
  Or install via package manager:
  ```bash
  # macOS
  brew install android-platform-tools

  # Linux (Debian/Ubuntu)
  sudo apt install android-tools-adb

  # Windows — download platform-tools ZIP from Google and add to PATH
  ```

- **PlainApp must already be installed** on the device before running the command.

### The Critical Restriction — Accounts

> **Android blocks setting Device Owner if any accounts are signed in on the device.**
> This is Google's policy to prevent malicious enterprise enrollment.

You have three paths:

#### Path A — Fresh / Factory Reset Device (Easiest)
1. Factory reset the device
2. During first-boot setup wizard — **skip adding a Google account** (tap "Skip" or "Set up later")
3. Install PlainApp via ADB: `adb install app-debug.apk`
4. Run the Device Owner command
5. Add your Google account afterward — accounts added **after** Device Owner is set are fine

#### Path B — Existing Device with Accounts (Remove → Set → Re-add)
1. Go to `Settings → Accounts` → remove every account (Google, Samsung, etc.)
2. Run the Device Owner command
3. Re-add all your accounts afterward

#### Path C — Existing Device, Keep Accounts (Not Possible)
There is no workaround. Android enforces this at the OS level. You must use Path A or B.

---

## Step-by-Step Setup

### Step 1 — Connect the device and verify ADB sees it

```bash
adb devices
```

Expected output:
```
List of devices attached
R5CW3XXXXXX    device
```

If it shows `unauthorized` — unlock your phone and tap **"Allow"** on the USB debugging dialog.

If it shows nothing — check your USB cable, try a different port, or install the device's USB driver (Windows).

---

### Step 2 — Confirm no accounts are signed in (if not using a fresh device)

```bash
adb shell dumpsys account | grep "Account {"
```

If output is empty — you are clear to proceed.
If accounts are listed — remove them from `Settings → Accounts` first.

---

### Step 3 — Confirm PlainApp is installed

```bash
adb shell pm list packages | grep plain
```

Expected: `package:com.ismartcoding.plain`

If not installed, install it first:
```bash
adb install path/to/app-debug.apk
```

---

### Step 4 — Set Device Owner

```bash
adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
```

**Success output:**
```
Active admin set to component {com.ismartcoding.plain/com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver}
and it's the device owner.
```

If you see this — you are done. Device Owner is set permanently until explicitly removed.

---

## Verify It Worked

### Via ADB

```bash
adb shell dpm list-owners
```

Expected output:
```
Current Device Owner:
  com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
```

### Via PlainApp Web Panel

Open the web panel → navigate to **Permissions** page.
The **"Device Owner (Silent APK Install)"** card at the bottom of the Protected Permissions section will show **✅ Granted** (green).

### Via Telegram Bot

Send `/permissions` to the bot.
Look for `DEVICE_OWNER` in the ADB permissions section — it will show ✅.

---

## How to Remove Device Owner

> ⚠️ **Warning:** Once Device Owner is set, it cannot be removed from `Settings → Security → Device Admin Apps` the normal way. Android blocks this UI path for Device Owners. The PIN guard in PlainApp further protects this page.

### Method 1 — Via ADB (Recommended while you still have ADB access)

```bash
adb shell dpm remove-active-admin com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
```

### Method 2 — Via PlainApp itself (if Device Owner)

PlainApp can call `dpm.clearDeviceOwnerApp()` from within the app. A "Remove Device Owner" button can be added to the app settings. This is the only app-side removal path.

### Method 3 — Factory Reset (Nuclear option)

A factory reset always removes Device Owner. This wipes all data on the device.

### What You CANNOT Do

- Remove from `Settings → Security → Device Admin Apps` (blocked by Android for Device Owners)
- Uninstall the app while it is Device Owner (Android blocks uninstallation)
- Use any third-party app to remove it

---

## What You Gain — Feature Comparison

### APK Installation

| Method | Without Device Owner | With Device Owner |
|---|---|---|
| Bot `/update <url>` | System Install dialog appears. User must tap "Install". | **Fully silent. Zero taps. Automatic.** |
| Bot: Send .apk file to chat | System Install dialog appears | **Fully silent. Zero taps. Automatic.** |
| Web panel: Upload from browser | System Install dialog appears | **Fully silent. Zero taps. Automatic.** |
| Web panel: Install from URL | System Install dialog appears | **Fully silent. Zero taps. Automatic.** |
| Web panel: Install from storage path | System Install dialog appears | **Fully silent. Zero taps. Automatic.** |

### Security

| Feature | Without Device Owner | With Device Owner |
|---|---|---|
| User can uninstall PlainApp | ✅ Yes (from Settings → Apps) | ❌ Blocked by policy |
| User can remove Device Admin | ✅ Yes (from Settings → Security) | ❌ Not possible normally |
| User can factory reset | ✅ Yes | ❌ Can be blocked (some OEMs) |
| PlainApp can wipe device | ❌ | ✅ Via `dpm.wipeData()` |

---

## Permissions PlainApp Grants Itself Automatically

Once Device Owner, PlainApp calls `dpm.setPermissionGrantState(componentName, permission, PERMISSION_GRANT_STATE_GRANTED)` for each permission — no ADB, no user prompt, no Settings navigation required.

All of these can be auto-granted in one tap:

| Permission | What it Enables in PlainApp |
|---|---|
| `WRITE_SECURE_SETTINGS` | Airplane mode toggle, secure settings changes |
| `READ_LOGS` | Read system logcat remotely |
| `READ_PHONE_STATE` | Phone state, IMEI, carrier info |
| `READ_CALL_LOG` | Full call history read access |
| `WRITE_CALL_LOG` | Call log modification |
| `CHANGE_NETWORK_STATE` | Mobile data toggle, network control |
| `PACKAGE_USAGE_STATS` | Screen time stats, app launch history |
| `WRITE_EXTERNAL_STORAGE` | Full external storage write |
| `READ_EXTERNAL_STORAGE` | Full external storage read |
| `MANAGE_EXTERNAL_STORAGE` | Access all files including app-private folders |
| `CHANGE_WIFI_STATE` | Wi-Fi toggle, connect/disconnect networks |
| `ACCESS_WIFI_STATE` | Read Wi-Fi details, scan results |
| `ACCESS_FINE_LOCATION` | Precise GPS location |
| `ACCESS_BACKGROUND_LOCATION` | Location even when app is in background |
| `RECORD_AUDIO` | Microphone access (call recording, live mic) |
| `CAMERA` | Camera access (live camera, photo capture) |
| `READ_SMS` | Full SMS inbox read |
| `SEND_SMS` | Send SMS programmatically |
| `RECEIVE_SMS` | Intercept incoming SMS |
| `PROCESS_OUTGOING_CALLS` | Intercept and modify outgoing calls |
| `READ_CONTACTS` | Full contacts read |
| `WRITE_CONTACTS` | Contacts add/edit/delete |
| `GET_ACCOUNTS` | List all accounts on device |
| `TETHER_PRIVILEGED` | Mobile hotspot / tethering control |
| `READ_CLIPBOARD_IN_BACKGROUND` | Clipboard read when app is not foreground |

**Result:** After setting Device Owner once, every permission that previously required an individual ADB `pm grant` command is granted automatically by PlainApp on first launch — permanently.

---

## All Device Owner Superpowers

### 1. Silent APK Install (Android 12+)
Uses `PackageInstaller` with `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`. The system install dialog never appears. The APK installs and replaces the old version in the background without any user interaction.

```kotlin
// What PlainApp does internally when Device Owner:
val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
```

### 2. Grant Permissions to Itself
```kotlin
dpm.setPermissionGrantState(
    adminComponent,
    packageName,
    permission,
    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
)
```

### 3. Lock Task Mode (Kiosk Mode)
Pin the device to a single app. The user cannot press Home, Recent Apps, or access any other app. Useful for locking a device to PlainApp only.
```kotlin
dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
activity.startLockTask()
```
Exit kiosk: call `activity.stopLockTask()` from within PlainApp.

### 4. Block Uninstall
```kotlin
dpm.setUninstallBlocked(adminComponent, targetPackage, true)
```
User gets "This action is not allowed by your administrator" when they try to uninstall PlainApp.

### 5. Remote Enterprise Wipe
```kotlin
dpm.wipeData(0) // Wipe device
dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE) // Also wipe SD card
dpm.wipeData(DevicePolicyManager.WIPE_RESET_PROTECTION_DATA) // Bypass Factory Reset Protection
```

### 6. Disable Factory Reset (some OEMs)
```kotlin
val restrictions = PersistableBundle()
restrictions.putBoolean(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_SETUP, true)
dpm.setApplicationRestrictions(adminComponent, packageName, restrictions)
```

### 7. Set Global Network Proxy
Route all device traffic through your own server:
```kotlin
val proxy = ProxyInfo.buildDirectProxy("your.proxy.server", 8080)
dpm.setRecommendedGlobalProxy(adminComponent, proxy)
```

### 8. Disable USB Debugging Remotely
```kotlin
dpm.setSecureSetting(adminComponent, Settings.Global.ADB_ENABLED, "0")
```

### 9. Disable Hardware Features Device-Wide
```kotlin
dpm.setCameraDisabled(adminComponent, true)    // Disable camera for all apps
dpm.setBluetoothDisabled(adminComponent, true) // Disable Bluetooth
```

### 10. Enforce Screen Lock Policy
```kotlin
dpm.setMaximumFailedPasswordsForWipe(adminComponent, 10) // Wipe after 10 wrong PINs
dpm.setPasswordMinimumLength(adminComponent, 6)          // Minimum 6-digit PIN
dpm.setPasswordQuality(adminComponent, DevicePolicyManager.PASSWORD_QUALITY_NUMERIC)
```

### 11. Set Password / PIN Remotely
```kotlin
dpm.resetPassword("newPin1234", 0) // Change device PIN remotely (Android < 8)
// Android 8+: use resetPasswordWithToken() with a pre-set token
```

### 12. Hide Other Apps
```kotlin
dpm.setApplicationHidden(adminComponent, "com.example.app", true)
// App disappears from launcher and Settings → Apps. Not uninstalled, just hidden.
```

---

## Common Errors and Fixes

### Error: "Not allowed to set the device owner because there are already some accounts on the device"
**Fix:** Remove all accounts from `Settings → Accounts` first, then re-run.

### Error: "java.lang.IllegalArgumentException: Unknown admin"
**Fix:** PlainApp is not installed, or was installed after the command. Install PlainApp first, then run the command.

### Error: "java.lang.SecurityException: Neither user ... nor current process has android.permission..."
**Fix:** You are running the command on a non-debug build or an older ADB version. Use `adb shell` (not just `adb`) and ensure USB debugging is authorized.

### Error: "adb: error: no devices/emulators found"
**Fix:** USB cable issue, driver issue (Windows), or USB debugging not enabled. Try: `adb kill-server && adb start-server && adb devices`

### Error: "Active admin is already set"
**Fix:** Either Device Admin or Device Owner is already set. Check with:
```bash
adb shell dpm list-owners
```
If Device Admin is already set but NOT Device Owner, remove it first:
```bash
adb shell dpm remove-active-admin com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
```
Then re-run the `set-device-owner` command.

### Error: "The device has already been provisioned"
**Fix:** This means a Device Owner was set before (possibly by another app). Only one Device Owner is allowed. You must factory reset to clear it.

---

## Security Implications

### What this means for device security

Setting PlainApp as Device Owner is a serious, irreversible action (without factory reset). Understand what you are doing:

| Implication | Detail |
|---|---|
| **PlainApp cannot be uninstalled** | Even from Settings → Apps, uninstall is blocked |
| **PlainApp cannot be removed from Device Admin** | Settings → Security → Device Admin Apps shows it but removal is blocked |
| **PlainApp controls the device** | It can wipe, lock, modify, and restrict any aspect of the device |
| **Removal requires ADB or factory reset** | No GUI path to remove without those |
| **Your Telegram bot becomes extremely powerful** | Whoever controls the bot controls the phone entirely |

### Securing the bot after setting Device Owner

Because the bot now controls a Device Owner, these protections are critical:

1. **Bot password** — set a strong bot password so only you can send commands
2. **Chat ID whitelist** — bot already restricts to your chat ID only
3. **Telegram account security** — enable 2FA on your Telegram account
4. **Bot token** — keep your bot token secret; never share it; regenerate it if ever exposed (`/revoke` in BotFather)

---

## Quick Reference Card

```
════════════════════════════════════════════════════════
  PLAINAPP DEVICE OWNER — QUICK REFERENCE
════════════════════════════════════════════════════════

  SET DEVICE OWNER (run once, never again):
  ─────────────────────────────────────────
  adb shell dpm set-device-owner \
    com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver

  VERIFY:
  ─────────────────────────────────────────
  adb shell dpm list-owners

  REMOVE (via ADB):
  ─────────────────────────────────────────
  adb shell dpm remove-active-admin \
    com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver

  REQUIREMENTS:
  ─────────────────────────────────────────
  ✓ USB Debugging ON (Developer Options)
  ✓ ADB authorized (tap Allow on phone)
  ✓ PlainApp installed
  ✓ No Google accounts on device
    (remove → set owner → re-add accounts)

  WHAT YOU GET:
  ─────────────────────────────────────────
  ✓ Silent APK install (zero taps)
  ✓ Auto-grant all permissions to itself
  ✓ Block uninstall of PlainApp
  ✓ Remote enterprise wipe
  ✓ Kiosk/lock-task mode
  ✓ Disable camera/BT/USB device-wide
  ✓ Global proxy
  ✓ Enforce PIN policy
  ✓ Hide apps device-wide

════════════════════════════════════════════════════════
```

---

## Related Files in PlainApp Codebase

| File | Purpose |
|---|---|
| `app/src/main/java/com/ismartcoding/plain/helpers/ApkUpdateHelper.kt` | `isDeviceOwner()`, `install()`, `installSilently()` |
| `app/src/main/java/com/ismartcoding/plain/receivers/PlainDeviceAdminReceiver.kt` | Device Admin + Device Owner receiver |
| `app/src/main/java/com/ismartcoding/plain/ui/DeviceAdminUnlockActivity.kt` | PIN guard before deactivation |
| `app/src/main/java/com/ismartcoding/plain/web/schemas/AppGraphQL.kt` | `DEVICE_OWNER` in `protectedPermissionsStatus` |
| `app/src/main/java/com/ismartcoding/plain/telegram/TelegramBotManager.kt` | `DEVICE_OWNER` in `/permissions` bot command |
| `app/src/main/AndroidManifest.xml` | `PlainDeviceAdminReceiver` declaration |
| `plain-web/src/views/device-hub/ApkInstallView.vue` | Web panel APK installer (3 methods) |
| `plain-web/src/views/permissions/PermissionsStatusView.vue` | Permissions page showing Device Owner status |
