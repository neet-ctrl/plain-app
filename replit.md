# NEET Tracker Pro — Android

A comprehensive Android study-tracking app for NEET aspirants. Tracks notebooks, books, PYQ sources, test papers, planner events, diary entries, date events, dictionaries, mnemonics, diagrams, short notes, day-waste logs, NEET sequence, lack points, and student profile — with full alarm/reminder infrastructure.

## Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Hilt DI
- **Database**: Room (version 2)
- **Navigation**: Compose Navigation
- **Build**: Gradle 8 (Kotlin DSL)
- **CI**: GitHub Actions (debug + release APK)

## Project Structure

```
app/
  src/main/
    java/com/neet/tracker/
      alarm/           - AlarmScheduler, AlarmReceiver, BootReceiver, NotificationHelper
      data/
        database/      - NEETDatabase (v2), NEETDao, DatabaseModule
        models/        - Models.kt (all Room entities + data classes)
      di/              - Hilt modules
      navigation/      - Routes + nav graph
      ui/
        components/    - Reusable UI components
        dialogs/       - Reusable dialogs
        screens/       - All screen composables
        theme/         - Theme, colors, typography
        viewmodels/    - ViewModels (including ReminderViewModel)
    AndroidManifest.xml
.github/workflows/
  debug.yml            - CI: build debug APK on every push
  release.yml          - CI: build & sign release APK, upload as artifact
gradle/wrapper/
  gradle-wrapper.properties
gradlew
```

## Alarm / Reminder Infrastructure

- `AlarmScheduler.kt` — schedules/cancels exact alarms via AlarmManager
- `AlarmReceiver.kt` — BroadcastReceiver that fires the notification
- `BootReceiver.kt` — reschedules saved alarms after device reboot
- `NotificationHelper.kt` — creates notification channel and shows notification
- `ReminderViewModel.kt` — manages Reminder entities and coordinates scheduling
- Alarm UI available in every `PlannerEventCard` (all planner screens) and every `DateEventCard`

## CI Workflows

| Workflow | Trigger | Output |
|----------|---------|--------|
| `debug.yml` | push to any branch | `app-debug.apk` artifact |
| `release.yml` | push to `main` | `app-release.apk` artifact (signed) |

Release signing: JKS decoded from `KEYSTORE_BASE64` secret, password `Sh@090609`, alias `my-key`.

## User Preferences

- Android-only project; no web/Node/pnpm workspace.
- Hardcoded keystore credentials in release workflow (as per original requirement).
- Database uses `fallbackToDestructiveMigration()` — no manual migrations needed.
- All alarm permissions declared in AndroidManifest.xml.
