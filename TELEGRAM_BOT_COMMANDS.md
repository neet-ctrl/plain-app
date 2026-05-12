# PlainApp Telegram Bot — Complete Command Reference

> Every command starts with `/`. Commands are **case-insensitive**.
> Alternative aliases work exactly the same as the primary command.

---

## Auto-Forwarding Services (All ON by default)

| Service | Preference Key | Default |
|---|---|---|
| Forward Notifications | `telegram_bot_forward_notifications` | ✅ ON |
| Forward Calls | `telegram_bot_forward_calls` | ✅ ON |
| Forward SMS | `telegram_bot_forward_sms` | ✅ ON |
| Forward Geofence alerts | `telegram_bot_forward_geofence` | ✅ ON |
| Forward Battery alerts | `telegram_bot_forward_battery_alert` | ✅ ON |
| Forward Stealth Screenshots | `telegram_bot_forward_stealth_shots` | ✅ ON |
| Forward all new Files | `telegram_file_forward_enabled` | ✅ ON |

---

## Session & Authentication

### `/start`
**Description:** Shows a welcome message and current device status.
Sent automatically every time the bot starts up.
**Aliases:** _(none)_
**Usage:** `/start`

---

### `/help`
**Description:** Displays the full list of available commands with short descriptions.
**Aliases:** _(none)_
**Usage:** `/help`

---

### `/commands`
**Description:** Lists all commands with full details (longer version of /help).
**Aliases:** _(none)_
**Usage:** `/commands`

---

### `/stop`
**Description:** Stops the Telegram bot. Restart it from PlainApp Settings → Telegram Bot.
**Aliases:** _(none)_
**Usage:** `/stop`

---

## Messaging & SMS

### `/messages`
**Description:** Lists all SMS conversations. Tap a conversation button to open the thread.
Supports search and pagination via inline buttons.
**Aliases:** _(none)_
**Usage:** `/messages`

---

### `/sms`
**Description:** Opens a specific SMS conversation thread by thread ID.
**Aliases:** _(none)_
**Usage:** `/sms <thread_id>`
**Example:** `/sms 42`

---

### `/sendsms`
**Description:** Sends an SMS from the device to any phone number.
**Aliases:** _(none)_
**Usage:** `/sendsms <number> <text>`
**Example:** `/sendsms +919876543210 Hello how are you?`

---

### `/schedulesms`
**Description:** Schedules an SMS to be sent after a delay in seconds.
**Aliases:** `schedsms`
**Usage:** `/schedulesms <number> <delay_seconds> <text>`
**Example:** `/schedulesms +919876543210 60 Meeting in 1 minute!`

---

### `/mms`
**Description:** Browses MMS multimedia messages. Shows the most recent ones by default.
**Aliases:** _(none)_
**Usage:** `/mms` or `/mms <count>`
**Example:** `/mms 10`

---

### `/forwardsms`
**Description:** Toggles automatic forwarding of incoming SMS messages to this Telegram chat.
**Aliases:** `smsfwd`
**Usage:** `/forwardsms` (shows toggle buttons) or `/forwardsms on` / `/forwardsms off`

---

## Calls

### `/calls`
**Description:** Shows the recent call log with caller names, numbers, direction (in/out/missed), and duration.
Supports search and pagination.
**Aliases:** _(none)_
**Usage:** `/calls`

---

### `/recordings`
**Description:** Lists call recordings. Tap any recording to download it as an audio file.
Supports search and pagination.
**Aliases:** _(none)_
**Usage:** `/recordings`

---

### `/callnow`
**Description:** Initiates an outgoing phone call directly from the device.
**Aliases:** `dial`, `makecall`
**Usage:** `/callnow <number>`
**Example:** `/callnow +919876543210`

---

### `/blocknumber`
**Description:** Shows blocked call numbers. Also lets you add or remove numbers from the block list.
**Aliases:** `blocknum`, `blockcall`
**Usage:** `/blocknumber` (shows list + buttons) or `/blocknumber <number>`
**Example:** `/blocknumber +919999999999`

---

### `/livecall`
**Description:** Opens the live call hub. Shows the current ongoing call status and lets you accept, mute, or end a call remotely.
**Aliases:** `calltracker`
**Usage:** `/livecall`

---

## Contacts

### `/contacts`
**Description:** Browse device contacts. Tap any contact to view details, call, send SMS, or share as a vCard.
Supports search and pagination.
**Aliases:** _(none)_
**Usage:** `/contacts`

---

### `/find`
**Description:** Reverse-lookup a phone number to find matching contacts.
**Aliases:** `findcontact`, `lookup`, `whois`
**Usage:** `/find <number>`
**Example:** `/find +919876543210`

---

### `/addcontact`
**Description:** Interactively add a new contact (name, phone, email).
**Aliases:** `newcontact`
**Usage:** `/addcontact`

---

### `/deletecontact`
**Description:** Delete a contact by name or phone number.
**Aliases:** `delcontact`, `rmcontact`
**Usage:** `/deletecontact <name or number>`
**Example:** `/deletecontact John` or `/deletecontact +919876543210`

---

### `/contactgroups`
**Description:** List all contact groups. Tap a group to see its members.
**Aliases:** `groups`, `cgroups`
**Usage:** `/contactgroups`

---

## Notifications

### `/notifications`
**Description:** Shows recent device notifications with app name, title, body, and timestamp.
Supports search and pagination.
**Aliases:** _(none)_
**Usage:** `/notifications`

---

### `/mutenotifs`
**Description:** Mutes or unmutes automatic forwarding of notifications to this chat.
**Aliases:** `mutenotif`, `mutenotifications`, `shutup`, `silence`
**Usage:** `/mutenotifs` (toggle) or `/mutenotifs on` / `/mutenotifs off`

---

### `/logs`
**Description:** Shows the notification log history stored on the device.
**Aliases:** _(none)_
**Usage:** `/logs`

---

## Files & Storage

### `/files`
**Description:** Browses device storage. Tap folders to navigate, tap files to download.
Supports search within any folder.
**Aliases:** _(none)_
**Usage:** `/files`

---

### `/storage`
**Description:** Shows storage usage for internal memory, SD card, and USB if connected.
**Aliases:** `disk`
**Usage:** `/storage`

---

### `/deletefile`
**Description:** Deletes a specific file from the device by its full path.
**Aliases:** `delfile`, `rmfile`
**Usage:** `/deletefile <absolute_path>`
**Example:** `/deletefile /sdcard/Download/old_file.mp3`

---

### `/filehash`
**Description:** Calculates the SHA-256 and MD5 hash of a file — useful for verifying integrity.
**Aliases:** `hash`, `sha256`
**Usage:** `/filehash <absolute_path>`
**Example:** `/filehash /sdcard/Download/photo.jpg`

---

### `/docs`
**Description:** Browses document library (PDF, DOCX, XLS, etc.). Tap to download.
**Aliases:** `documents`, `document`
**Usage:** `/docs` or `/docs <search_keyword>`
**Example:** `/docs invoice`

---

### `/forwardfiles`
**Description:** Toggles automatic forwarding of ALL new files and media added to storage to this chat.
**Aliases:** `filesfwd`, `autofiles`, `allfiles`
**Usage:** `/forwardfiles` or `/forwardfiles on` / `/forwardfiles off`

---

### `/fwdfiles`
**Description:** Shows recently auto-forwarded files.
**Aliases:** `forwardedfiles`, `sentfiles`
**Usage:** `/fwdfiles` or `/fwdfiles <count>`
**Example:** `/fwdfiles 20`

---

### `/filestats`
**Description:** Shows file auto-forward queue statistics: pending / done / failed counts.
**Aliases:** `filequeue`, `uploadstats`
**Usage:** `/filestats`

---

### `/retryfailed`
**Description:** Retries all permanently-failed file uploads in the auto-forward queue.
**Aliases:** `retryfiles`, `retryuploads`
**Usage:** `/retryfailed`

---

## Camera & Media Capture

### `/screenshot`
**Description:** Takes a screenshot of the current screen and sends it.
**Aliases:** _(none)_
**Usage:** `/screenshot`

---

### `/photo`
**Description:** Captures a photo using the device camera and sends it. Default is back camera.
**Aliases:** _(none)_
**Usage:** `/photo` or `/photo front` or `/photo back`

---

### `/audio`
**Description:** Records audio for a chosen duration. Shows an interactive duration picker (5s, 10s, 30s, 60s, or custom).
**Aliases:** _(none)_
**Usage:** `/audio`

---

### `/video`
**Description:** Records a video. First choose front or back camera, then select duration.
**Aliases:** _(none)_
**Usage:** `/video`

---

### `/shots`
**Description:** Shows recent stealth screenshots taken by the app.
**Aliases:** `screenshots`
**Usage:** `/shots` or `/shots <count>`
**Example:** `/shots 10`

---

### `/forwardshots`
**Description:** Toggles automatic forwarding of stealth screenshots to this chat as they are taken.
**Aliases:** `shotsfwd`, `autoshare`
**Usage:** `/forwardshots` or `/forwardshots on` / `/forwardshots off`

---

### `/forwardphotos`
**Description:** Toggles automatic forwarding of new camera photos to this chat.
**Aliases:** `autophotos`, `photofwd`
**Usage:** `/forwardphotos` or `/forwardphotos on` / `/forwardphotos off`

---

### `/intruders`
**Description:** Shows intruder capture photos — selfies taken when someone enters a wrong PIN, wrong bot password, etc.
**Aliases:** `captures`, `intrudercaptures`
**Usage:** `/intruders` or `/intruders <count>`

---

## Apps & App Control

### `/apps`
**Description:** Lists all installed apps with their package names.
**Aliases:** _(none)_
**Usage:** `/apps`

---

### `/blockapp`
**Description:** Interactively pick an app to block on the device. Blocked apps cannot be opened.
**Aliases:** _(none)_
**Usage:** `/blockapp`

---

### `/unblockapp`
**Description:** Interactively pick a blocked app to unblock.
**Aliases:** _(none)_
**Usage:** `/unblockapp`

---

### `/blockedapps`
**Description:** Shows all currently blocked and time-limited apps.
**Aliases:** _(none)_
**Usage:** `/blockedapps`

---

### `/applocker`
**Description:** Opens the per-app lock hub. View and manage which apps require a PIN to open. Each locked app can have its own password, session unlock, attempt log, and reveal-password option.
**Aliases:** `lockapps`, `perapplock`
**Usage:**
- `/applocker` — home hub showing all locked apps and lock/unlock buttons
- `/applocker <pkg|name>` — jump directly to a specific app's lock menu

**Example:** `/applocker com.whatsapp` or `/applocker WhatsApp`

---

### `/launch`
**Description:** Launches any installed app by package name or app name.
**Aliases:** `open`
**Usage:** `/launch <package_name_or_app_name>`
**Example:** `/launch com.whatsapp` or `/launch WhatsApp`

---

### `/launches`
**Description:** Shows the recent app launch history.
**Aliases:** `launchhistory`
**Usage:** `/launches` or `/launches <count>`
**Example:** `/launches 20`

---

### `/screentime`
**Description:** Shows per-app screen time usage statistics.
**Aliases:** `usagestats`, `usage`
**Usage:** `/screentime` or `/screentime <days>`
**Example:** `/screentime 7`

---

### `/clearcache`
**Description:** Interactively pick an app and clear its cache.
**Aliases:** `cacheclean`
**Usage:** `/clearcache`

---

## Location & Geofencing

### `/location`
**Description:** Gets the device's current GPS location and sends a Google Maps link.
**Aliases:** _(none)_
**Usage:** `/location`

---

### `/livelocation`
**Description:** Starts a live location stream, sending GPS coordinates periodically.
**Aliases:** `live`
**Usage:** `/livelocation` or `/livelocation <count>`
**Example:** `/livelocation 10` (send 10 location updates)

---

### `/tracklocation`
**Description:** Shows recent recorded location points from location tracking history.
**Aliases:** `trackloc`
**Usage:** `/tracklocation` or `/tracklocation <count>`
**Example:** `/tracklocation 20`

---

### `/track`
**Description:** Opens the tracking hub overview — shows status of location tracking, keystrokes, stealth shots, etc.
**Aliases:** _(none)_
**Usage:** `/track`

---

### `/geofence`
**Description:** Manages geofence zones — list zones, add new zones, delete zones, and view enter/exit events.
**Aliases:** `gf`, `geofences`
**Usage:**
- `/geofence` — list all configured zones with enable/disable and delete buttons
- `/geofence events` — show recent enter/exit event log (last 20 events)
- `/geofence add` — interactively add a new zone (prompts for `Name, lat, lng, radius_m`)

**Example:** After `/geofence add`, send: `Home, 28.6139, 77.2090, 200`

---

### `/forwardgeofence`
**Description:** Toggles automatic forwarding of geofence enter/exit alerts to this chat.
**Aliases:** `geofencefwd`, `gffwd`
**Usage:** `/forwardgeofence` or `/forwardgeofence on` / `/forwardgeofence off`

---

## Backup

### `/backup`
**Description:** Builds a complete `.plain` backup of ALL private PlainApp data and either sends it directly to Telegram (if ≤ 50 MB) or generates a one-time 30-minute download link (if > 50 MB).

The `.plain` file is a ZIP archive — rename it to `.zip` on your PC to browse the raw contents.

**What is included:**
- 🗄 **Database** — notes, bookmarks, feeds, books, chats, tags, sessions, peers
- 📸 **Stealth screenshots** (images + metadata JSON)
- 📞 **Recorded calls** (.m4a audio + JSON sidecar per call)
- 🎙 **Live captures** (live-camera photos/videos, mic recordings + sidecars)
- 👁 **Intruder captures** (wrong-unlock selfies + metadata)
- ⌨️ **Keystroke log** (all captured keystrokes)
- 🗺 **Location history** (all GPS location points)
- 🌐 **Geofencing data** (zones + event log)
- ⚙️ **All settings & preferences** (DataStore + SharedPreferences: web password, PIN, Telegram token, every toggle, Cloudflare token/hostname, app-block config, bedtime, automation rules, …)
- 🔐 **SSL certificate** (keystore.bks — web console HTTPS cert)
- 🖼 **Note images, feed article images, bookmark favicons**

**What is excluded** (regeneratable, not private):
- Image/thumbnail cache
- Upload temp chunks
- `cloudflared` binary (re-extracted from APK at runtime)

**Aliases:** `bak`, `backupdata`, `exportdata`
**Usage:** `/backup`
**Inline buttons:**
- `📦 Also send as .zip` — re-sends the same file with `.zip` extension (so your PC opens it without renaming)
- `🔄 Fresh backup` — builds and sends a brand-new backup immediately

**Notes:**
- Telegram Bot API max file size is 50 MB. Larger backups get a one-time download link (valid 30 min) served by the on-device Ktor server. If Cloudflare Tunnel is active, the link uses your public domain; otherwise it uses the local LAN IP.
- Restore: use the Backup & Restore page in the app (accepts both `.plain` and `.zip`).

---

### `/restore`
**Description:** Puts the bot into "restore mode" for 5 minutes, then waits for you to send a `.plain` or `.zip` PlainApp backup file directly to this chat.  
Once received the bot will:
1. Download the file from Telegram
2. Unpack and scan the backup contents
3. Restore all data to the device (overwrites current data)
4. Send a detailed per-category count of what was restored
5. Automatically restart the app (no tap needed)

**Aliases:** `restoredata`, `restorebackup`, `importbackup`
**Usage:** `/restore` → then send the `.plain` or `.zip` file as a Telegram document

**Per-category status shown after restore:**
- 🗄 Database files (notes, bookmarks, feeds, books, chats, tags, sessions)
- 📸 Stealth screenshots
- 📞 Call recordings
- 👁 Intruder captures
- 🎙 Live captures
- 🌐 Geofence audio clips
- ⚙️ Settings / DataStore files
- 📋 SharedPreferences files
- 🖼 Note images
- 📰 Feed images
- ⭐ Favicons
- 📦 Total files restored

**Important notes:**
- **20 MB limit:** Telegram Bot API only accepts files up to 20 MB. Larger backup files must be restored via the in-app Backup & Restore page.
- Sending any command while the bot is waiting cancels the restore mode.
- Data is **overwritten** — ensure you have the right backup before confirming.
- App restarts automatically 3 seconds after restore completes.

---

## Device Control

### `/device`
**Description:** Shows device information: manufacturer, model, Android version, RAM, IMEI, etc.
**Aliases:** _(none)_
**Usage:** `/device`

---

### `/battery`
**Description:** Shows current battery level, charging status, temperature, and health.
**Aliases:** _(none)_
**Usage:** `/battery`

---

### `/batteryhistory`
**Description:** Shows a battery drain chart over a time window.
**Aliases:** `bathistory`, `bathist`
**Usage:** `/batteryhistory` or `/batteryhistory <hours>`
**Example:** `/batteryhistory 12`

---

### `/batteryalert`
**Description:** Configures the low-battery auto-alert. Sends a message when battery drops below the threshold.
**Aliases:** `batalert`, `lowbattery`
**Usage:** `/batteryalert` or `/batteryalert on` / `/batteryalert off` or `/batteryalert on 15` (15% threshold)

---

### `/lockscreen`
**Description:** Locks the device screen instantly.
**Aliases:** `lock`
**Usage:** `/lockscreen`

---

### `/wake`
**Description:** Wakes the device screen and keeps it on for a specified number of seconds.
**Aliases:** `wakescreen`
**Usage:** `/wake` or `/wake <seconds>`
**Example:** `/wake 30`

---

### `/brightness`
**Description:** Gets or sets the screen brightness (0–100).
**Aliases:** `bright`
**Usage:** `/brightness` (show current) or `/brightness <0-100>`
**Example:** `/brightness 70`

---

### `/volume`
**Description:** Gets or sets volume for a specific stream (media, ring, alarm, notification, call).
**Aliases:** `vol`
**Usage:** `/volume` (show all) or `/volume <stream> <0-100>`
**Example:** `/volume media 80` or `/volume ring 50`

---

### `/torch`
**Description:** Turns the flashlight on or off.
**Aliases:** `flashlight`
**Usage:** `/torch` (toggle) or `/torch on` / `/torch off`

---

### `/dnd`
**Description:** Gets or sets Do Not Disturb mode.
**Aliases:** `donotdisturb`
**Usage:** `/dnd` (show status) or `/dnd on` / `/dnd off` / `/dnd toggle`

---

### `/wifi`
**Description:** Shows Wi-Fi state (connected network, IP, signal) and lets you toggle it.
**Aliases:** _(none)_
**Usage:** `/wifi` (show status) or `/wifi on` / `/wifi off`

---

### `/wifiscan`
**Description:** Scans and lists all nearby Wi-Fi networks with SSIDs and signal strength.
**Aliases:** `wifilist`, `scanwifi`
**Usage:** `/wifiscan`

---

### `/mobiledata`
**Description:** Shows mobile data status and lets you toggle it on or off.
**Aliases:** `mobile`, `data`
**Usage:** `/mobiledata` (show status) or `/mobiledata on` / `/mobiledata off`

---

### `/datasettings`
**Description:** Opens the mobile data settings page directly on the device screen.
**Aliases:** _(none)_
**Usage:** `/datasettings`

---

### `/bluetooth`
**Description:** Shows Bluetooth status and paired devices. Toggle Bluetooth on/off.
**Aliases:** `bt`
**Usage:** `/bluetooth` (show status) or `/bluetooth on` / `/bluetooth off`

---

### `/hotspot`
**Description:** Shows mobile hotspot/tethering status and lets you toggle it.
**Aliases:** `tethering`, `wifiap`
**Usage:** `/hotspot` (show status) or `/hotspot on` / `/hotspot off`

---

### `/airplane`
**Description:** Toggles airplane mode on or off.
**Aliases:** `airplanemode`, `aeroplane`
**Usage:** `/airplane` (show status) or `/airplane on` / `/airplane off`

---

### `/vpn`
**Description:** Shows current VPN connection status and active VPN profile name if any.
**Aliases:** _(none)_
**Usage:** `/vpn`

---

### `/sim`
**Description:** Shows SIM card information: carrier name, phone number, MCC/MNC, data state.
**Aliases:** `siminfo`, `carrier`
**Usage:** `/sim`

---

### `/networkinfo`
**Description:** Shows extended network and Wi-Fi details: IP, gateway, DNS, signal, frequency.
**Aliases:** `netinfo`, `wifiinfo`
**Usage:** `/networkinfo`

---

### `/netusage`
**Description:** Shows network data usage (mobile + Wi-Fi) per app over a time window.
**Aliases:** `datausage`
**Usage:** `/netusage` or `/netusage <days>`
**Example:** `/netusage 7`

---

### `/reboot`
**Description:** Reboots the device. Requires root access or Device Admin permission.
**Aliases:** `restart`, `rebootdevice`
**Usage:** `/reboot`

---

## Notifications & Alerts on Device

### `/speak`
**Description:** Makes the device speak any text aloud using text-to-speech.
**Aliases:** `tts`
**Usage:** `/speak <text>`
**Example:** `/speak Hello, someone is watching you`

---

### `/stopspeak`
**Description:** Stops text-to-speech immediately if it is currently speaking.
**Aliases:** `shutup`, `ttsstop`
**Usage:** `/stopspeak`

---

### `/vibrate`
**Description:** Makes the device vibrate for a number of seconds.
**Aliases:** _(none)_
**Usage:** `/vibrate` or `/vibrate <seconds>`
**Example:** `/vibrate 3`

---

### `/toast`
**Description:** Shows a quick pop-up (toast) message on the device screen.
**Aliases:** _(none)_
**Usage:** `/toast <text>`
**Example:** `/toast Come home now`

---

### `/show`
**Description:** Shows a persistent banner/overlay on the device screen with the given text.
**Aliases:** `banner`
**Usage:** `/show <text>`
**Example:** `/show Stop using my phone!`

---

### `/findphone`
**Description:** Activates a loud alarm on the device to help find it. Turn off with `off`.
**Aliases:** `ringphone`
**Usage:** `/findphone` or `/findphone on` / `/findphone off`

---

### `/setalarm`
**Description:** Sets a system alarm at the specified time with an optional label.
**Aliases:** `alarm`, `addalarm`
**Usage:** `/setalarm HH:MM [label]`
**Example:** `/setalarm 07:30 Wake up`

---

## Clipboard

### `/clipboard`
**Description:** Reads the current device clipboard content. If text is provided, sets the clipboard to that text.
**Aliases:** `clip`
**Usage:** `/clipboard` (read) or `/clipboard <text>` (set)
**Example:** `/clipboard https://google.com`

---

### `/forwardclipboard`
**Description:** Toggles automatic forwarding of clipboard changes to this chat.
**Aliases:** `clipfwd`, `clipmon`
**Usage:** `/forwardclipboard` or `/forwardclipboard on` / `/forwardclipboard off`

---

## Monitoring & Surveillance

### `/keystrokes`
**Description:** Shows recent captured keystrokes from the keystroke logger.
**Aliases:** `keys`
**Usage:** `/keystrokes` or `/keystrokes <count>`
**Example:** `/keystrokes 50`

---

### `/keytop`
**Description:** Shows the top apps ranked by number of keystrokes captured.
**Aliases:** _(none)_
**Usage:** `/keytop`

---

### `/permissions`
**Description:** Shows the status of every important Android permission for PlainApp.
**Aliases:** `perms`
**Usage:** `/permissions`

---

### `/openperms`
**Description:** Opens a specific permission's settings screen directly on the device screen, so the user can grant or revoke it without navigating through Settings manually.  If no name is given, opens the general permissions overview for PlainApp.
**Aliases:** `permopen`
**Usage:** `/openperms` or `/openperms <permission_name>`
**Example:** `/openperms location` or `/openperms camera`

---

### `/reqperm`
**Description:** Lists every Android permission PlainApp uses as an inline button row. Tapping any button triggers the system grant dialog for that permission directly on the device screen. Use `/reqperm missing` to show only permissions that have not been granted yet.
**Aliases:** `reqperms`, `grantperm`, `askperm`, `permask`
**Usage:** `/reqperm` (all permissions) or `/reqperm missing` (un-granted only)

---

### `/timeline`
**Description:** Shows the device activity timeline — app opens, calls, SMS, location, etc.
**Aliases:** `activity`
**Usage:** `/timeline` or `/timeline <count>`
**Example:** `/timeline 30`

---

### `/soundmeter`
**Description:** Measures the ambient sound level using the microphone for a number of seconds.
**Aliases:** `sound`, `noise`, `dblevel`
**Usage:** `/soundmeter` or `/soundmeter <seconds>`
**Example:** `/soundmeter 10`

---

### `/gyroscope`
**Description:** Reads the live gyroscope rotation rate (rad/s on X, Y, Z axes).
**Aliases:** `gyro`, `rotation`
**Usage:** `/gyroscope`

---

### `/compass`
**Description:** Reads the magnetic compass heading and shows the cardinal direction.
**Aliases:** `heading`, `magnetic`
**Usage:** `/compass`

---

### `/barometer`
**Description:** Reads atmospheric pressure in hPa and estimates altitude in meters.
**Aliases:** `pressure`, `altitude`, `baro`
**Usage:** `/barometer`

---

### `/steps`
**Description:** Shows the step count since last reboot and today's estimated step count.
**Aliases:** `pedometer`, `stepcount`, `stepcounter`
**Usage:** `/steps`

---

### `/proximity`
**Description:** Reads the proximity sensor — reports "near" or "far".
**Aliases:** `prox`, `proxsensor`
**Usage:** `/proximity`

---

## Automation Rules

### `/automations`
**Description:** Lists all configured automation rules with their names, actions, and enabled status.
**Aliases:** `rules`
**Usage:** `/automations`

---

### `/newrule`
**Description:** Creates a new manual automation rule.
**Aliases:** `addrule`
**Usage:** `/newrule <name> <action> <args>`
**Example:** `/newrule silentnight dnd on`

---

### `/newschedule`
**Description:** Creates a new daily scheduled automation rule that fires at a specific time.
**Aliases:** `addschedule`
**Usage:** `/newschedule HH:MM <name> <action> <args>`
**Example:** `/newschedule 22:00 NightMode brightness 10`

---

### `/delrule`
**Description:** Deletes an automation rule by its ID.
**Aliases:** `deleterule`
**Usage:** `/delrule <rule_id>`

---

### `/runrule`
**Description:** Manually triggers an automation rule immediately by its ID.
**Aliases:** _(none)_
**Usage:** `/runrule <rule_id>`

---

### `/togglerule`
**Description:** Enables or disables an automation rule by its ID.
**Aliases:** _(none)_
**Usage:** `/togglerule <rule_id>`

---

### `/bedtime`
**Description:** Views or sets the parental bedtime window — the time range during which the device is restricted. During bedtime, apps on the scope list are blocked. The app allow-list is managed from the web panel.
**Aliases:** _(none)_
**Usage:**
- `/bedtime` — show current state, window, and inline control buttons
- `/bedtime on` — enable bedtime (keeps existing window)
- `/bedtime off` — disable bedtime
- `/bedtime set HH:MM HH:MM` — set start and end time (e.g. `/bedtime set 22:00 06:30`)

**Example:** `/bedtime set 23:00 07:00`

---

## Notes & Bookmarks

### `/notes`
**Description:** Browses notes. Tap any note to read it. Supports keyword search.
**Aliases:** _(none)_
**Usage:** `/notes` or `/notes <search_keyword>`
**Example:** `/notes shopping`

---

### `/addnote`
**Description:** Interactively creates a new note on the device.
**Aliases:** _(none)_
**Usage:** `/addnote`

---

### `/editnote`
**Description:** Interactively edits an existing note by ID.
**Aliases:** _(none)_
**Usage:** `/editnote <note_id>`

---

### `/bookmarks`
**Description:** Browses saved bookmarks. Supports keyword search.
**Aliases:** _(none)_
**Usage:** `/bookmarks` or `/bookmarks <search_keyword>`

---

### `/addbookmark`
**Description:** Adds a new URL bookmark.
**Aliases:** _(none)_
**Usage:** `/addbookmark <url>`
**Example:** `/addbookmark https://google.com`

---

## RSS Feeds

### `/feeds`
**Description:** Lists all subscribed RSS feeds. Tap a feed to view its recent entries.
**Aliases:** _(none)_
**Usage:** `/feeds`

---

### `/feedentries`
**Description:** Shows recent RSS feed entries across all feeds. Supports keyword search.
**Aliases:** `feedentry`
**Usage:** `/feedentries` or `/feedentries <search_keyword>`

---

## Media Library

### `/music`
**Description:** Browses the music/audio library. Tap any track to download it.
**Aliases:** `audios`
**Usage:** `/music` or `/music <search_keyword>`

---

### `/videos`
**Description:** Browses the video library. Tap any video to download it.
**Aliases:** `vidlib`
**Usage:** `/videos` or `/videos <search_keyword>`

---

### `/images`
**Description:** Browses the image gallery. Tap any image to send it.
**Aliases:** `gallery`
**Usage:** `/images` or `/images <search_keyword>`

---

## Productivity

### `/pomodoro`
**Description:** Controls the Pomodoro timer. Start, pause, stop, or check status.
**Aliases:** `pom`
**Usage:** `/pomodoro` or `/pomodoro start` / `/pomodoro pause` / `/pomodoro stop` / `/pomodoro status`

---

### `/nowplaying`
**Description:** Shows the currently playing audio track with playback controls (play/pause/next/prev).
**Aliases:** `np`, `player`
**Usage:** `/nowplaying`

---

### `/qrcode`
**Description:** Generates a QR code image for any text or URL and sends it.
**Aliases:** `qr`
**Usage:** `/qrcode <text or URL>`
**Example:** `/qrcode https://example.com` or `/qrcode Hello World`

---

## App Settings

### `/appsettings`
**Description:** Shows a full overview of all app security settings with inline toggle buttons. The single hub for everything below.
**Aliases:** `appsetting`
**Usage:** `/appsettings`

---

### `/hideicon`
**Description:** Hide or show the PlainApp launcher icon on the device home screen.
**Aliases:** `launchericon`, `iconhide`
**Usage:** `/hideicon` (show status) | `/hideicon on` (hide) | `/hideicon off` (show)

---

### `/applock`
**Description:** Enable or disable the app-open PIN lock. Requires a PIN to already be set via `/setpin`.
**Aliases:** *(none — `/applocker` is a different feature for locking individual apps)*
**Usage:** `/applock` (show status) | `/applock on` | `/applock off`

---

### `/biometric`
**Description:** Enable or disable fingerprint / face-unlock for the PlainApp app lock.
**Aliases:** *(none)*
**Usage:** `/biometric` (show status) | `/biometric on` | `/biometric off`

---

### `/appinfog`
**Description:** Enable or disable the App info guard — blocks Android's system "App info" page **for PlainApp only** (long-press PlainApp's icon → App info, or Settings → Apps → PlainApp) behind the PlainApp PIN. Other apps' App Info pages are never interrupted. Requires a PIN to be set first.
**Aliases:** `appinfoguard`
**Usage:** `/appinfog` (show status) | `/appinfog on` | `/appinfog off`

---

### `/setpin`
**Description:** Interactively set or change the app unlock PIN (4–12 digits). If a PIN already exists you will be asked for the current one first.
**Aliases:** `changepin`
**Usage:** `/setpin` → follow interactive prompts

---

### `/removepin`
**Description:** Interactively remove the app unlock PIN. Also disables app lock and biometric unlock. Requires entering the current PIN to confirm.
**Aliases:** `deletepin`
**Usage:** `/removepin` → follow interactive prompts

---

### `/openapp`
**Description:** Brings the PlainApp main screen to the foreground on the device. Useful after a fresh boot or when the app is backgrounded.
**Aliases:** `openappdevice`, `launchapp`
**Usage:** `/openapp`

---

### `/openappinfo`
**Description:** Opens PlainApp's system App Info page (Settings → Apps → PlainApp) directly on the device. If the App Info Guard is enabled, it is pre-verified so the PIN screen is not shown. Useful for checking permissions, storage usage, or clearing cache remotely.
**Aliases:** `appinfo`, `ownappinfo`
**Usage:** `/openappinfo`

---

### `/openwebsettings`
**Description:** Opens the Web Settings page inside PlainApp directly on the device screen, bypassing the app lock PIN and the security gate. Useful for checking or changing the web console port, password, or HTTPS settings without unlocking the app manually.
**Aliases:** `websettings`, `webset`, `opensettings`, `webcon`
**Usage:** `/openwebsettings`

---

### `/openpage`
**Description:** Opens any page inside PlainApp on the device screen right now, bypassing the app lock. Sends a menu of all available pages as inline buttons grouped by category. Tap any button and that screen opens immediately on the device.

**Available page groups:**
| Group | Pages |
|---|---|
| 🏠 Main | Home, App Settings |
| 🌐 Web Console | Web Settings, Web Security, Web Sessions, Web Dev Mode, Cloudflare Tunnel, Tunnel Log, Always On Screen |
| 🔐 Security | App Lock, Launcher Icon, Security Q&A, Telegram Bot |
| 📁 Files & Media | Files, App Files, Images, Videos, Audio, Audio Player, Documents, Apps |
| 💬 Communication | Chat List, Local Chat, Nearby Devices |
| 📝 Productivity | Notes, RSS Feeds, Feed Settings, Scan QR, Scan History, Exchange Rate, Pomodoro Timer, Sound Meter |
| ⚙️ Appearance & App | Custom Features, Notification Settings, Language, Dark Theme, Backup & Restore, How To Use |
| 📺 Hardware | DLNA Receiver, DLNA Cast History |

**Aliases:** `page`, `goto`, `navigate`, `nav`
**Usage:** `/openpage`

---

### `/deviceowner`
**Description:** Device Owner control hub. When PlainApp has Device Owner privileges (set once via ADB), this command unlocks a suite of powerful zero-touch management capabilities. Without Device Owner, only `/deviceowner status` is available.

**Subcommands:**
| Subcommand | Effect |
|---|---|
| `status` | Show Device Owner status and available sub-commands |
| `grantperms` | Auto-grant ALL PlainApp permissions silently |
| `blockinstall on\|off` | Prevent uninstalling PlainApp from the device |
| `kiosk on\|off` | Enable/disable single-app kiosk (lock-task) mode |
| `camera on\|off` | Disable or re-enable the device camera globally |
| `bt on\|off` | Disable or re-enable Bluetooth |
| `usb on\|off` | Disable or re-enable USB debugging |
| `proxy <host:port>` | Set a global network proxy |
| `clearproxy` | Remove the global proxy |
| `wipe` | ⚠️ Factory reset the device (requires typing `CONFIRM`) |

**Aliases:** `dpm`, `owner`, `admincontrol`
**Usage:** `/deviceowner status` | `/deviceowner grantperms` | `/deviceowner kiosk on` | `/deviceowner wipe`

**Enable Device Owner (one-time ADB setup):**
```
adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
```

---

### `/botpassword`
**Description:** Enable or disable the Telegram bot password protection. When enabled, the bot asks for a password at session start (15 min timeout). The master password always works.
**Aliases:** `botpwd`
**Usage:** `/botpassword` (show status) | `/botpassword on` | `/botpassword off`

---

### `/setbotpassword`
**Description:** Interactively change the Telegram bot session password.
**Aliases:** `changebotpassword`, `botpwdset`
**Usage:** `/setbotpassword` → type new password when prompted

---

### `/securityqa`
**Description:** View the current dashboard security question, or start an interactive flow to change both the question and answer used by the web panel's feedback/dashboard gate.
**Aliases:** `securityquestion`, `secqa`, `feedbackqa`
**Usage:** `/securityqa` (show current) | `/securityqa change` (interactive update)

---

### `/update`
**Description:** Update PlainApp on the device remotely — without physically touching the phone. Two methods:
1. **Send APK file** — Send a `.apk` file directly to this bot chat. The bot downloads it from Telegram and triggers installation automatically.
2. **URL** — `/update <url>` downloads the APK from any direct HTTPS link and triggers installation.

**Aliases:** `selfupdate`, `apkupdate`, `updateapp`
**Usage:**
- `/update` — shows help + prompts for URL or file
- `/update https://example.com/PlainApp-debug.apk` — download from URL and install
- Send `.apk` file directly to the bot chat — auto-detected, no command needed

**Install modes:**
| Mode | Requirement | User action needed |
|---|---|---|
| Silent (zero-touch) | Device Owner (one-time ADB setup) | **None** — install happens invisibly |
| System dialog | Default (no setup required) | **One tap** — tap Install on device screen |

**Enable zero-touch silent updates (one-time):**
```
adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
```
After this, all future `/update` installs are completely silent.

**Limitations:**
- Telegram Bot API: files sent directly to the bot are capped at **20 MB**. Use `/update <url>` for larger APKs.
- Non-Device-Owner devices: the system Install dialog appears on screen — one tap required.
- APK must be signed with the same key as the installed version (or debug APK replacing debug APK).

---

## Summary Table — All Aliases

| Primary Command | Alternative Aliases |
|---|---|
| `/find` | `findcontact`, `lookup`, `whois` |
| `/mutenotifs` | `mutenotif`, `mutenotifications`, `shutup`, `silence` |
| `/livelocation` | `live` |
| `/tracklocation` | `trackloc` |
| `/keystrokes` | `keys` |
| `/shots` | `screenshots` |
| `/permissions` | `perms` |
| `/automations` | `rules` |
| `/newrule` | `addrule` |
| `/newschedule` | `addschedule` |
| `/delrule` | `deleterule` |
| `/feedentries` | `feedentry` |
| `/music` | `audios` |
| `/videos` | `vidlib` |
| `/images` | `gallery` |
| `/pomodoro` | `pom` |
| `/torch` | `flashlight` |
| `/speak` | `tts` |
| `/stopspeak` | `shutup`, `ttsstop` |
| `/findphone` | `ringphone` |
| `/show` | `banner` |
| `/wake` | `wakescreen` |
| `/brightness` | `bright` |
| `/volume` | `vol` |
| `/launch` | `open` |
| `/launches` | `launchhistory` |
| `/livecall` | `calltracker` |
| `/netusage` | `datausage` |
| `/storage` | `disk` |
| `/sim` | `siminfo`, `carrier` |
| `/dnd` | `donotdisturb` |
| `/screentime` | `usagestats`, `usage` |
| `/blocknumber` | `blocknum`, `blockcall` |
| `/nowplaying` | `np`, `player` |
| `/forwardsms` | `smsfwd` |
| `/clipboard` | `clip` |
| `/mobiledata` | `mobile`, `data` |
| `/bluetooth` | `bt` |
| `/lockscreen` | `lock` |
| `/forwardphotos` | `autophotos`, `photofwd` |
| `/airplane` | `airplanemode`, `aeroplane` |
| `/schedulesms` | `schedsms` |
| `/batteryhistory` | `bathistory`, `bathist` |
| `/clearcache` | `cacheclean` |
| `/geofence` | `gf`, `geofences` |
| `/addcontact` | `newcontact` |
| `/deletecontact` | `delcontact`, `rmcontact` |
| `/forwardclipboard` | `clipfwd`, `clipmon` |
| `/soundmeter` | `sound`, `noise`, `dblevel` |
| `/qrcode` | `qr` |
| `/docs` | `documents`, `document` |
| `/filehash` | `hash`, `sha256` |
| `/wifiscan` | `wifilist`, `scanwifi` |
| `/timeline` | `activity` |
| `/contactgroups` | `groups`, `cgroups` |
| `/callnow` | `dial`, `makecall` |
| `/deletefile` | `delfile`, `rmfile` |
| `/networkinfo` | `netinfo`, `wifiinfo` |
| `/reboot` | `restart`, `rebootdevice` |
| `/gyroscope` | `gyro`, `rotation` |
| `/compass` | `heading`, `magnetic` |
| `/barometer` | `pressure`, `altitude`, `baro` |
| `/steps` | `pedometer`, `stepcount`, `stepcounter` |
| `/proximity` | `prox`, `proxsensor` |
| `/hotspot` | `tethering`, `wifiap` |
| `/setalarm` | `alarm`, `addalarm` |
| `/batteryalert` | `batalert`, `lowbattery` |
| `/forwardgeofence` | `geofencefwd`, `gffwd` |
| `/forwardshots` | `shotsfwd`, `autoshare` |
| `/forwardfiles` | `filesfwd`, `autofiles`, `allfiles` |
| `/fwdfiles` | `forwardedfiles`, `sentfiles` |
| `/filestats` | `filequeue`, `uploadstats` |
| `/retryfailed` | `retryfiles`, `retryuploads` |
| `/applocker` | `lockapps`, `perapplock` |
| `/intruders` | `captures`, `intrudercaptures` |
| `/appsettings` | `appsetting` |
| `/hideicon` | `launchericon`, `iconhide` |
| `/applock` | *(see `/applocker` for per-app locks)* |
| `/appinfog` | `appinfoguard` |
| `/setpin` | `changepin` |
| `/removepin` | `deletepin` |
| `/openapp` | `openappdevice`, `launchapp` |
| `/openappinfo` | `appinfo`, `ownappinfo` |
| `/openwebsettings` | `websettings`, `webset`, `opensettings`, `webcon` |
| `/openpage` | `page`, `goto`, `navigate`, `nav` |
| `/openperms` | `permopen` |
| `/reqperm` | `reqperms`, `grantperm`, `askperm`, `permask` |
| `/deviceowner` | `dpm`, `owner`, `admincontrol` |
| `/botpassword` | `botpwd` |
| `/setbotpassword` | `changebotpassword`, `botpwdset` |
| `/securityqa` | `securityquestion`, `secqa`, `feedbackqa` |
| `/update` | `selfupdate`, `apkupdate`, `updateapp` |

---

*Total commands: 118 | Total aliases: 130+*
*Generated from `TelegramBotManager.kt` — PlainApp*
