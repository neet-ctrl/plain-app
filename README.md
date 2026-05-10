# NEET Tracker Pro

> A full-featured Android study companion built for serious NEET aspirants — track every resource, annotate PDFs, plan every day, and never miss a revision.

---

## What It Does

NEET Tracker Pro is a native Android app (Kotlin + Jetpack Compose) that centralises everything a NEET student needs: asset management, smart planning, a universal file viewer with annotation tools, a daily diary, PYQ tracking, mnemonic labs, and much more — all in one offline-first app with a deep-space dark UI.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Hilt DI |
| Database | Room v2 |
| Navigation | Compose Navigation (type-safe routes) |
| Image Loading | Coil |
| Fonts | Orbitron · Exo 2 (Google Fonts) |
| Build | Gradle 8 (Kotlin DSL) |
| CI/CD | GitHub Actions (debug + release APK) |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | 35 |

---

## Project Structure

```
app/src/main/java/com/neet/tracker/
├── alarm/
│   ├── AlarmScheduler.kt       — exact-alarm scheduling via AlarmManager
│   ├── AlarmReceiver.kt        — BroadcastReceiver → fires notification
│   ├── BootReceiver.kt         — reschedules alarms after device reboot
│   └── NotificationHelper.kt  — notification channel + display
├── data/
│   ├── models/Models.kt        — all Room entities + embedded data classes
│   └── database/
│       ├── NEETDatabase.kt     — Room DB (v2, destructive migration)
│       ├── NEETDao.kt          — all DAO queries
│       └── DatabaseModule.kt  — Hilt DI module
├── navigation/
│   ├── NavRoutes.kt            — route constants + builders
│   └── NEETNavHost.kt         — NavHost with slide + fade transitions
└── ui/
    ├── theme/                  — deep-space dark theme, neon colors
    ├── components/             — glass cards, search bars, FABs, badges
    ├── dialogs/                — 10+ fully custom dialogs
    ├── screens/                — 30+ screen composables
    └── viewmodels/             — Hilt-injected ViewModels
```

---

## Features

### Universal File Viewer
Opens PDFs and images from anywhere in the app with a full annotation suite:

| Feature | Details |
|---|---|
| PDF rendering | Android PdfRenderer, up to 100 pages per file |
| Pinch-to-zoom + pan | Simultaneous with 1-finger draw in annotZoom mode |
| **Floating Annotation Toolbar** | Draggable + collapsible, fullscreen draw mode |
| Pen | Free-draw strokes, 5 stroke widths |
| Highlighter | 38 % alpha wide strokes |
| Arrow | Curved stroke with filled arrowhead |
| Text labels | Tap-to-place, rich text styling, drag to move |
| Eraser | Touch strokes to erase |
| **Line Pointer** | Bright glowing horizontal reading-ruler, 8 vivid colors, pulsing neon glow, draggable across the page |
| Color palette | 9 annotation colors + 8 dedicated line-pointer colors |
| Undo / Redo | 50-level history per session |
| Clear page | One-tap clear current page annotations |
| Annotations persistence | Auto-saved to JSON in app internal storage |
| Page bookmarks | Per-page bookmark with panel |
| Page marks | Got It / Review / Important / Skip / Key Page / Insight |
| Quick notes | Per-file side notes panel |
| Thumbnail strip | Scrollable page preview grid |
| Page jump dialog | Jump to any page instantly |
| Reading progress | Animated progress bar based on furthest page visited |
| Focus mode | Full-screen immersive read, tap to exit |
| Scroll direction | Vertical ↔ Horizontal swipe toggle |
| Solution viewer | Floating resizable PDF window for answer keys |
| Share + Open externally | Standard Android share sheet |

### Line Pointer (New)
The **Line Pointer** lives inside the floating annotation toolbar. Toggle it on and a glowing neon horizontal line appears across the entire screen — works like a physical reading ruler to keep your eye on the current line. Drag it anywhere on the page. Choose from 8 high-brightness colors: red, orange, yellow, green, cyan, blue, purple, and pink. The line pulses softly and auto-disables when you exit annotation mode.

### Main Dashboard — 14 Modules

| # | Module | What It Tracks |
|---|---|---|
| 1 | Assets Vault | Notebooks, Books, PYQs, Tests, Sample Papers, PW Batches |
| 2 | Smart Planner | Day / Week / Month / Year planners with alarms |
| 3 | Daily Diary | Rich-text diary with emoji toolbar |
| 4 | Event Log | Per-date events with shift-to-next |
| 5 | NEET Syllabus | PDF upload & viewer |
| 6 | Lexicon | NEET terms + English word bank |
| 7 | Mnemonic Lab | Memory technique tracker |
| 8 | Universe Calendar | Aggregates all dates from every module |
| 9 | Diagrams Atlas | Botany & Zoology PDF viewers |
| 10 | Chapter Notes | Per-subject short notes |
| 11 | Wasted Days | Waste % · reason · recovery tips |
| 12 | NEET Sequence | 80+ connected chapters with status connectors |
| 13 | Subject Notes | Subject-wise short PDF notes |
| 14 | Lack Points | Identify weaknesses, track solutions |

### Asset Module Details

- **Notebooks** — cover photo, numbered chapters, spec / missing / status dialogs
- **Books** — full CRUD, info, status, tags, remark dialogs
- **PYQ Chapterwise** — sources → chapters with dates, wrong-Q count, remarks
- **PYQ Yearwise** — books → years with completion tracking
- **Tests (Online / Offline)** — topics, wrong Qs, marks, tags, URL, QP / solution upload
- **Sample Papers** — same as tests
- **PW Batches** — batch → tests with full test metadata

### Student Profile
- Animated gradient profile photo ring
- Personal info (DOB, email, mobile, Aadhaar)
- 10th & 12th qualifications + marksheets
- Multiple NEET attempt records (rolls, marks, QP, solutions, lack analysis)
- Target score display

### Alarm / Reminder Infrastructure
- `AlarmScheduler` schedules exact alarms via `AlarmManager`
- `BootReceiver` reschedules all saved alarms after device reboot
- Alarm UI available on every planner event card and date event card
- `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` declared in manifest

### Custom Dialogs (10+)
Status Selector · Tag Dialog · Remark Dialog · Completion Dates · Wrong Questions · Specification / Info (rich text) · Missing Notes · URL Dialog · Prefix Date · Marks · Topics

---

## Design Language

- Deep-space dark background with animated grid
- Neon glow accents — Cyan · Purple · Gold · Green · Red · Orange
- Glass morphism cards with animated border glow
- **Orbitron** for headings · **Exo 2** for body text
- Status-color coded cards — Yellow=Expected · Green=Completed · Purple=Revision · Red=Crossed
- Breadcrumb navigation on every screen
- 3D search bars on every list screen
- Smooth slide + fade navigation transitions
- Staggered card entrance animations

---

## Build & Run

### Requirements
- Android Studio Hedgehog or newer
- Android SDK 35 (install via SDK Manager)
- Java 17 (bundled with Android Studio)

### Steps
1. Clone the repository
2. Open Android Studio → **File → Open** → select the project root
3. Wait for Gradle sync (first run: ~3–5 min downloading dependencies)
4. Connect an Android device (API 26+) via USB or start an emulator
5. Click **▶ Run**

---

## CI / CD

| Workflow | Trigger | Output |
|---|---|---|
| `debug.yml` | push to any branch | `app-debug.apk` artifact |
| `release.yml` | push to `main` | signed `app-release.apk` artifact |

Release signing uses a JKS keystore decoded from the `KEYSTORE_BASE64` GitHub secret.

---

## Annotation Data Storage

Annotations are persisted per-file in app internal storage:

```
files/annotations/
  annot_<hash>.json        — stroke data (pen, highlighter, arrow)
  annot_texts_<hash>.json  — text box data
```

Each file is keyed by a hash of the file URI. Data survives app restarts and is cleared only via the explicit "Clear Page" action.
