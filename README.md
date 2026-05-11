<div align="center">

```
███╗   ██╗███████╗███████╗████████╗    ████████╗██████╗  █████╗  ██████╗██╗  ██╗███████╗██████╗     ██████╗ ██████╗  ██████╗
████╗  ██║██╔════╝██╔════╝╚══██╔══╝    ╚══██╔══╝██╔══██╗██╔══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗    ██╔══██╗██╔══██╗██╔═══██╗
██╔██╗ ██║█████╗  █████╗     ██║          ██║   ██████╔╝███████║██║     █████╔╝ █████╗  ██████╔╝    ██████╔╝██████╔╝██║   ██║
██║╚██╗██║██╔══╝  ██╔══╝     ██║          ██║   ██╔══██╗██╔══██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗    ██╔═══╝ ██╔══██╗██║   ██║
██║ ╚████║███████╗███████╗   ██║          ██║   ██║  ██║██║  ██║╚██████╗██║  ██╗███████╗██║  ██║    ██║     ██║  ██║╚██████╔╝
╚═╝  ╚═══╝╚══════╝╚══════╝   ╚═╝          ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝    ╚═╝     ╚═╝  ╚═╝ ╚═════╝
```

### ⚡ The Ultimate Offline Android Study Companion for NEET Aspirants ⚡

---

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-757575?style=for-the-badge&logo=material-design&logoColor=white)
![Room](https://img.shields.io/badge/Room%20DB-FF6D00?style=for-the-badge&logo=sqlite&logoColor=white)
![Hilt](https://img.shields.io/badge/Hilt%20DI-34A853?style=for-the-badge&logo=google&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Oreo)-0099FF?style=for-the-badge)
![Target SDK](https://img.shields.io/badge/Target%20SDK-35-00C853?style=for-the-badge)
![Offline](https://img.shields.io/badge/100%25-Offline-FF1744?style=for-the-badge)
![No Ads](https://img.shields.io/badge/Zero-Ads-FFD700?style=for-the-badge)
![Version](https://img.shields.io/badge/Version-1.0-7C4DFF?style=for-the-badge)

</div>

---

<div align="center">

```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   "Every feature saves study time. Not wastes it."          ║
║                    — NEET Tracker Pro Design Philosophy      ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

</div>

---

## ◈ TABLE OF CONTENTS

| # | Section |
|:-:|---------|
| 1 | [What Is NEET Tracker Pro?](#what-is-neet-tracker-pro) |
| 2 | [Architecture](#architecture) |
| 3 | [Tech Stack](#tech-stack) |
| 4 | [Deep Space UI Design System](#deep-space-ui-design-system) |
| 5 | [Home Screen — Command Centre](#home-screen--command-centre) |
| 6 | [PDF Viewer & Annotation Engine](#pdf-viewer--annotation-engine) |
| 7 | [Floating Annotation Toolbar](#floating-annotation-toolbar) |
| 8 | [Annotation Data Models](#annotation-data-models) |
| 9 | [Smart Planner System](#smart-planner-system) |
| 10 | [Assets Vault](#assets-vault) |
| 11 | [Study Intelligence Tools](#study-intelligence-tools) |
| 12 | [Diary & Events System](#diary--events-system) |
| 13 | [Alarm & Reminder Engine](#alarm--reminder-engine) |
| 14 | [Student Profile](#student-profile) |
| 15 | [Database Schema](#database-schema) |
| 16 | [Navigation Routes](#navigation-routes) |
| 17 | [Project Structure](#project-structure) |
| 18 | [Build & Run](#build--run) |

---

## What Is NEET Tracker Pro?

**NEET Tracker Pro** is a feature-complete, 100% offline, native Android application built exclusively for NEET (National Eligibility cum Entrance Test) aspirants. Every single feature exists to serve one goal:

```
                    ┌─────────────────────┐
                    │                     │
                    │    7 2 0 / 7 2 0    │
                    │                     │
                    └─────────────────────┘
```

From annotating PDFs with a floating neon toolbar, to tracking every PYQ chapter, test paper, planner event, diary entry, alarm, mnemonic, diagram, and dictionary term — **nothing falls through the cracks.**

---

## Architecture

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                         NEET TRACKER PRO — ARCHITECTURE                         ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║   ┌──────────────────────────────────────────────────────────────────────────┐  ║
║   │                          UI LAYER  (Jetpack Compose)                     │  ║
║   │                                                                          │  ║
║   │  HomeScreen · FileViewerScreen · BooksScreen · PYQScreens                │  ║
║   │  PlannerScreens · NotebooksScreen · ProfileScreen                        │  ║
║   │  DiaryAndEventScreens · MiscScreens · AdvancedScreens                    │  ║
║   │  TestAndSampleScreens · AssetsScreen · PWBatchesScreen                   │  ║
║   └────────────────────────────┬─────────────────────────────────────────────┘  ║
║                                │  StateFlow / Events                             ║
║   ┌────────────────────────────▼─────────────────────────────────────────────┐  ║
║   │                     VIEWMODEL LAYER  (Hilt + StateFlow)                  │  ║
║   │                                                                          │  ║
║   │  ProfileViewModel · PlannerViewModel · BookViewModel · PYQViewModel      │  ║
║   │  NotebookViewModel · DiaryViewModel · DateEventViewModel                 │  ║
║   │  ReminderViewModel · HomeCountViewModel · DictionaryViewModel            │  ║
║   │  SyllabusViewModel · MnemonicsViewModel · + more                         │  ║
║   └────────────────────────────┬─────────────────────────────────────────────┘  ║
║                                │  DAO / Repository                               ║
║   ┌────────────────────────────▼─────────────────────────────────────────────┐  ║
║   │                      DATA LAYER  (Room + Files + Prefs)                  │  ║
║   │                                                                          │  ║
║   │  NEETDao (single DAO · 30+ tables)   NEETDatabase (Room v2)             │  ║
║   │  AnnotationManager (per-PDF JSON)    DataStore Preferences               │  ║
║   │  AlarmScheduler (AlarmManager)       WorkManager (background)            │  ║
║   │  BootReceiver (reboot persistence)   NotificationHelper                  │  ║
║   └──────────────────────────────────────────────────────────────────────────┘  ║
║                                                                                  ║
║   Pattern: MVVM · Single-Activity · Jetpack Navigation · Offline-First · Hilt   ║
╚══════════════════════════════════════════════════════════════════════════════════╝
```

---

## Tech Stack

```
╔══════════════════════╦══════════════════════════════════════════╦═════════════════════╗
║  Category            ║  Library / Tool                          ║  Purpose            ║
╠══════════════════════╬══════════════════════════════════════════╬═════════════════════╣
║  Language            ║  Kotlin  (JVM 17)                        ║  Everything         ║
║  UI Framework        ║  Jetpack Compose + Material 3            ║  All UI surfaces    ║
║  Dependency Inject.  ║  Hilt + KSP code generation              ║  DI across app      ║
║  Database            ║  Room (SQLite) + Gson TypeConverters     ║  Offline storage    ║
║  Navigation          ║  Navigation Compose                      ║  Screen routing     ║
║  Image Loading       ║  Coil Compose                            ║  Photos, PDFs       ║
║  Animations          ║  Lottie Compose                          ║  Lottie files       ║
║  Permissions         ║  Accompanist Permissions                 ║  Runtime perms      ║
║  System UI           ║  Accompanist SystemUIController          ║  Edge-to-edge       ║
║  Preferences         ║  DataStore Preferences                   ║  User settings      ║
║  Background Tasks    ║  WorkManager                             ║  Deferred work      ║
║  Alarms              ║  AlarmManager + BroadcastReceiver        ║  Exact notifications║
║  Annotation Storage  ║  AnnotationManager (org.json)            ║  Per-PDF markup     ║
║  Fonts               ║  Google Fonts (ui-text-google-fonts)     ║  Typography         ║
╚══════════════════════╩══════════════════════════════════════════╩═════════════════════╝
```

| Build Config | Value |
|---|---|
| `compileSdk` | **35** |
| `targetSdk` | **35** |
| `minSdk` | **26** (Android 8.0 Oreo) |
| `applicationId` | `com.neet.tracker` |
| `versionCode` | `1` |
| `versionName` | `1.0` |
| Java compatibility | `VERSION_17` |
| Release build | R8 minification + ProGuard optimised |
| Signing | `keystore.properties` (external, gitignored) |

---

## Deep Space UI Design System

> The app runs a single, uncompromising **Deep Space Neon** dark theme. No light mode. No grey compromises. Pure dark-lab energy.

### Colour Palette

```
╔══════════════════╦════════════╦═════════════════════════════════════════════╗
║  Token           ║  Hex       ║  Usage                                      ║
╠══════════════════╬════════════╬═════════════════════════════════════════════╣
║  DeepNavy        ║  #040B16   ║  App background — the void of space         ║
║  CosmicBlue      ║  #080F20   ║  Surface, card backgrounds                  ║
║  NeonCyan        ║  #00E5FF   ║  Primary accent — primary buttons, borders  ║
║  NeonPurple      ║  #7C4DFF   ║  Secondary accent — REVISION status         ║
║  NeonGold        ║  #FFD700   ║  Tertiary — EXPECTED status, highlights     ║
║  NeonGreen       ║  #00E676   ║  COMPLETED status, success states           ║
║  NeonRed         ║  #FF1744   ║  CROSSED status, delete, errors             ║
║  NeonOrange      ║  #FF6D00   ║  Annotation toolbar glow accent             ║
║  NeonPink        ║  #FF4081   ║  Diary, highlights, feminine touches        ║
║  NeonTeal        ║  #1DE9B6   ║  Sub-accents, alternate highlights          ║
║  NeonIndigo      ║  #536DFE   ║  Buttons, secondary borders                 ║
║  GlassSurface    ║  #1AFFFFFF ║  Frosted glass overlay                      ║
║  GlassBorder     ║  #33FFFFFF ║  Glass card borders                         ║
╚══════════════════╩════════════╩═════════════════════════════════════════════╝
```

### Status Colour System (used everywhere)

| Status | Colour | Token | Meaning |
|--------|:------:|-------|---------|
| `EXPECTED` | 🟡 | `NeonGold #FFD700` | Pending / planned |
| `COMPLETED` | 🟢 | `NeonGreen #00E676` | Done |
| `REVISION` | 🟣 | `NeonPurple #7C4DFF` | Needs revision |
| `CROSSED` | 🔴 | `NeonRed #FF1744` | Skipped / missed |

### Shared UI Components

| Component | Description |
|-----------|-------------|
| `SpaceBackground` | Animated star-field canvas behind every screen |
| `GlassCard` | Frosted-glass morphism card with per-card neon glow shadow |
| `NEETTopBar` | Breadcrumb-aware top bar with animated back button |
| `NeonFAB` | Pulsing neon Floating Action Button |
| `ThreeDIconBox` | Raised 3D icon container with layered depth shadow |
| `NeatSearchBar` | Neon-bordered live search input |
| `NEETCard` | Grid tile with icon, glow colour, and optional bottom row |
| `NeonDivider` | Thin glowing separator line |
| `TagChip` | Compact neon tag label |
| `EmptyState` | Centred icon + message for empty lists |
| `CardIconButton` | Small icon action button inside cards |

---

## Home Screen — Command Centre

```
╔══════════════════════════════════════════════════════════╗
║              HOME SCREEN  (Command Centre)               ║
╠═══════════════════════════╦══════════════════════════════╣
║  Section                  ║  Content                     ║
╠═══════════════════════════╬══════════════════════════════╣
║  Profile Header           ║  Photo · name · target score ║
║                           ║  dream role                  ║
╠═══════════════════════════╬══════════════════════════════╣
║  Universal Alarm Card     ║  All active alarms from every║
║                           ║  module aggregated in one row║
╠═══════════════════════════╬══════════════════════════════╣
║  Upcoming Events          ║  Next date events with       ║
║                           ║  countdown timers            ║
╠═══════════════════════════╬══════════════════════════════╣
║  Quick-Access Grid        ║  All 14+ modules as icon grid║
╠═══════════════════════════╬══════════════════════════════╣
║  Stats Bar                ║  Notebooks / Books / Tests / ║
║                           ║  PYQs item counts at glance  ║
╚═══════════════════════════╩══════════════════════════════╝
```

**All 14+ module cards from Home:**

| # | Module | Accent |
|:-:|--------|:------:|
| 1 | Assets Vault | Cyan |
| 2 | Smart Planner | Purple |
| 3 | Daily Diary | Pink |
| 4 | Date Events | Gold |
| 5 | NEET Syllabus | Orange |
| 6 | Dictionary | Cyan |
| 7 | Mnemonics | Teal |
| 8 | Universe Calendar | Indigo |
| 9 | Diagrams Atlas | Green |
| 10 | Chapter Short Notes | Purple |
| 11 | Subject Short Notes | Gold |
| 12 | Day Waste Tracker | Red |
| 13 | NEET Sequence | Green |
| 14 | Lack Points | Red |
| 15 | Student Profile | Cyan |

---

## PDF Viewer & Annotation Engine

> The crown jewel of the app — a fully custom multi-page PDF renderer with a **floating neon annotation toolbar** supporting 7 tools, emoji stamps, image overlays, and per-PDF JSON persistence.

### Viewer Capabilities

| Feature | Details |
|---------|---------|
| PDF rendering | Android `PdfRenderer` — up to N pages per file |
| Swipe navigation | Left/right swipe between pages |
| Pinch-to-zoom | 2-finger gesture up to 4× zoom |
| 1-finger draw | While zoomed, 2-finger pans; 1-finger draws |
| Line Pointer | Glowing draggable horizontal ruler across the page — 8 vivid neon colours, pulsing glow |
| Solution Viewer | Open linked solution PDF via optional `solutionUri` route parameter |
| Bookmarks | Per-page bookmark toggle, persists per PDF |
| Page indicator | Live counter (e.g. `3 / 24`) |
| Breadcrumb top bar | PDF title + back navigation |
| Specialized routes | `DiagramViewer`, `ShortNoteViewer`, `SubjectNoteViewer` — same engine, different route entry points |

---

## Floating Annotation Toolbar

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     FLOATING ANNOTATION TOOLBAR                          │
│                                                                          │
│  ◉ DRAGGABLE     — grab the neon header row, drag to any screen position │
│  ◉ COLLAPSIBLE   — one tap collapses to a glowing bubble icon            │
│  ◉ RESIZABLE     — drag bottom-right corner handle, clamps 200dp–380dp   │
│                                                                          │
│  ── 7 ANNOTATION TOOLS ──────────────────────────────────────────────── │
│                                                                          │
│  🖊  PEN          Freehand drawing · custom colour · 3 stroke widths     │
│  🖍  HIGHLIGHTER  Semi-transparent wide stroke for text highlight         │
│  ⬡  ERASER       Touch-based eraser removes nearby strokes               │
│  →  ARROW        Straight line with filled arrowhead at endpoint          │
│  T  TEXT         Tap-to-place draggable text box                          │
│       └─ bold · italic · font size · text colour · bg colour · border    │
│  🖼  IMAGE        Tap PDF → pick image → placed at tap with 8 handles    │
│       └─ drag to move · corner handles to resize · delete button         │
│  ⭐  STAMP        90-emoji picker (NEET-relevant) — tap to place          │
│       └─ ✅❌💯⭐🔥💡🧬⚗️🔬📚🧪0️⃣–🔟 and many more                      │
│                                                                          │
│  ── CONTROLS ────────────────────────────────────────────────────────── │
│                                                                          │
│  Colour chips     8 neon colour options per tool                         │
│  Width chips      3 preset stroke widths                                 │
│  Stamp picker     Collapsible 90-emoji grid inside toolbar               │
│  Undo             Removes last stroke / text / image / stamp             │
│  Clear All        Wipes all annotations on current page                  │
│  Done             Collapses toolbar to bubble                            │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Annotation Data Models

```kotlin
// The 7 tools
enum class AnnotationTool { PEN, HIGHLIGHTER, ERASER, ARROW, TEXT, IMAGE, STAMP }

// Freehand / arrow / highlighter strokes
data class AnnotationStroke(
    val points: List<Pair<Float, Float>>,  // normalized 0.0–1.0 coordinates
    val colorArgb: Int,
    val widthDp: Float,
    val tool: String                        // "PEN" | "HIGHLIGHTER" | "ARROW" | "ERASER"
)

// Tap-to-place draggable text labels
data class AnnotationTextBox(
    val id: String,                         // UUID
    val xNorm: Float, val yNorm: Float,     // 0.0–1.0 relative position on page
    val text: String,
    val colorArgb: Int,
    val fontSizeSp: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val bgArgb: Int,                        // background colour (0 = transparent)
    val hasBorder: Boolean
)

// Image overlays with 8 resize handles
data class AnnotationImageBox(
    val id: String,                         // UUID
    val xNorm: Float, val yNorm: Float,     // top-left corner
    val wNorm: Float, val hNorm: Float,     // width + height
    val imagePath: String                   // internal app-files path
)

// Emoji stamps
data class AnnotationStamp(
    val id: String,                         // UUID
    val xNorm: Float, val yNorm: Float,     // centre position
    val emoji: String,                      // e.g. "⭐", "✅", "🔥"
    val sizeSp: Float
)
```

### Persistence Strategy

| File | Format | Naming convention |
|------|--------|-------------------|
| Strokes (pen/arrow/highlight) | JSON array | `annot_strokes_<pdfHash>.json` |
| Text boxes | JSON array | `annot_texts_<pdfHash>.json` |
| Image overlays | JSON array | `annot_images_<pdfHash>.json` |
| Stamps | JSON array | `annot_stamps_<pdfHash>.json` |

All four files live in the app's private internal files directory. The `pdfHash` is a stable fingerprint of the PDF URI — annotations survive restarts, reboots, and app updates. Cleared only via the explicit **Clear All** action.

---

## Smart Planner System

Four-tier planning covering every time horizon from today to the full exam year:

```
╔════════════════╦═══════════════════════════════════════════════════════╗
║  Planner       ║  What it tracks                                       ║
╠════════════════╬═══════════════════════════════════════════════════════╣
║  Day Planner   ║  Events per calendar date · timing range              ║
║                ║  source PDF link · set alarm · status                 ║
╠════════════════╬═══════════════════════════════════════════════════════╣
║  Week Planner  ║  Weekly schedule block · date range label             ║
║                ║  event list · status tracking                         ║
╠════════════════╬═══════════════════════════════════════════════════════╣
║  Month Planner ║  Monthly event list · progress status                 ║
╠════════════════╬═══════════════════════════════════════════════════════╣
║  Year Planner  ║  Year session milestones · annual planning            ║
╚════════════════╩═══════════════════════════════════════════════════════╝
```

**Every `PlannerEvent` stores:**

```
name · notes · sourceFileUri · timingRange
day / dateRange / month
status: EXPECTED | COMPLETED | REVISION | CROSSED
remark · completedAt (timestamp) · alarmTime · alarmLabel
```

- Tap any event → opens linked source PDF in FileViewerScreen with full annotation engine
- Set alarm per event via `TimePickerDialog` → fires native notification
- One-tap status update → colour-coded indicator updates live
- Remark field for post-study notes

---

## Assets Vault

```
╔═══════════════════╦══════════════════════════════════════════════════════════╗
║  Asset Type       ║  Features                                                ║
╠═══════════════════╬══════════════════════════════════════════════════════════╣
║  Notebook Vault   ║  Register physical notebooks with cover photo            ║
║                   ║  Each notebook → chapters with: name · specifications    ║
║                   ║  missing notes · status · completion dates · tags        ║
╠═══════════════════╬══════════════════════════════════════════════════════════╣
║  Book Library     ║  Full CRUD for reference books                           ║
║                   ║  Subject filter · tag filter · search · status           ║
║                   ║  Info dialog · remark dialog · add/edit/delete           ║
╠═══════════════════╬══════════════════════════════════════════════════════════╣
║  PYQ Archive      ║  Chapter-wise: source → chapters with dates, wrong-Q,   ║
║  (Prev. Year Qs)  ║  completion history, remarks, status                     ║
║                   ║  Year-wise: book → year entries with same tracking       ║
╠═══════════════════╬══════════════════════════════════════════════════════════╣
║  Test Papers      ║  Online tests + Offline tests tracked separately         ║
║                   ║  Question paper PDF · solution PDF · marks obtained      ║
║                   ║  Topics asked · wrong questions · tags · URL · date      ║
╠═══════════════════╬══════════════════════════════════════════════════════════╣
║  Sample Papers    ║  Same model as test papers — separate catalogue          ║
╠═══════════════════╬══════════════════════════════════════════════════════════╣
║  PW Batches       ║  Physics Wallah batch organiser                          ║
║                   ║  Each batch → list of PW tests                           ║
║                   ║  Per-test: subject · marks · paper + solution PDFs · URL ║
╚═══════════════════╩══════════════════════════════════════════════════════════╝
```

---

## Study Intelligence Tools

### NEET Syllabus
Upload the official NEET syllabus PDF once — view, replace, or remove from a dedicated glass card screen. Always one tap away.

---

### Dictionary System

| Dictionary | Stores | Extras |
|------------|--------|--------|
| **NEET Lexicon** | Term · Definition · Chapter · Subject · Serial No · Tags | Attach any reference file, view in FileViewerScreen |
| **Non-NEET Vocab** | Word · Meaning · Example sentence | General English vocabulary bank |

Filter by Subject (Physics / Chemistry / Botany / Zoology / General) and by tag.

---

### Mnemonics Vault
Store every memory aid with: name · chapter · subject · description · tags · attached reference file.  
Searchable and subject-filterable. Never lose a mnemonic again.

---

### Diagram Gallery
Upload chapter diagrams organised by subject. View any diagram in the `DiagramViewer` with the full annotation engine active — label, draw, and stamp directly on diagrams.

---

### Chapter Short Notes
Attach per-chapter short-note PDFs (subject → chapter). View in `ShortNoteViewer` with annotation support.

---

### Subject Short Notes
One consolidated reference PDF per subject (Physics / Chemistry / Botany / Zoology / General). Swappable any time.

---

### NEET Sequence Tracker
Track chapter-by-chapter preparation order across all subjects:

```
NeetSequence {
  serialNo · chapterName · subject
  status: EXPECTED | COMPLETED | REVISION | CROSSED
  remark · tags
}
```

Attach the official sequence PDF and open it inline. Colour-coded by status. Serial number ordering enforced.

---

### Day Waste Tracker
Honest daily accountability log:

```
DayWaste {
  date · wastePercentage (0–100%) · reason
  sourceUri · recoverTip
}
```

Log unproductive days with a waste percentage, reason, and recovery tip. The data never lets you forget — and that's the point.

---

### Lack Points Log
Personal weakness registry:

```
LackPoint {
  point   — what is lacking
  solution — concrete fix
  status  — EXPECTED (unresolved) | COMPLETED (fixed)
}
```

Track recurring mistakes, conceptual gaps, and their solutions until every gap is closed.

---

### Universe Calendar
A unified chronological timeline merging **three data sources** into one scrollable view:
- 📔 Diary entries (by date)
- 📅 Date events (exams, mock tests, deadlines)
- 📋 Day planner entries

Every date that has any activity gets a card — the most comprehensive view of your NEET journey.

---

## Diary & Events System

### Daily Diary

Personal study journal — one date can have multiple diary entries (`DiaryEntry` list embedded per day):

```
DailyDiary {
  date · nickName (e.g. "The Grind Day") · content
  tags · DiaryEntry[] { content · timestamp }
}
```

- Tag system for mood / topic / subject
- Search by date, nickname, content, or tag
- Rich text content per entry

### Date Events

Exam-day and event-day tracker — for NEET exam, mock dates, hall-ticket releases, counselling, etc.:

```
DateEvent {
  date · name · detail · url · location · fileUri
  totalQuestions · questionDivisions[{ chapter · count }]
  timeRange · remark · status · crossReason
  alarmTime · alarmLabel
}
```

| Feature | Detail |
|---------|--------|
| PDF attachment | Hall ticket, question paper, or any linked document |
| Alarm | Set a reminder for the event date/time |
| Status | EXPECTED → COMPLETED / CROSSED |
| Cross reason | Log why an event was missed or skipped |
| Question division | Break down total questions per chapter |
| Countdown | Visible on Home screen for upcoming events |

---

## Alarm & Reminder Engine

```
╔══════════════════╦════════════════════════════════════════════════════════╗
║  Source          ║  How alarms are created                                ║
╠══════════════════╬════════════════════════════════════════════════════════╣
║  Day Planner     ║  Per-event alarm via TimePickerDialog                  ║
║  Date Events     ║  Per-exam alarm via TimePickerDialog                   ║
║  Reminders       ║  Standalone reminders — one-shot or repeating          ║
╚══════════════════╩════════════════════════════════════════════════════════╝
```

**Reminder entity:**

```kotlin
Reminder {
  title · message · triggerAtMillis
  isRepeating · repeatIntervalHours (default 24h)
  isActive
  linkedEntityId · linkedEntityType  // optional link back to source
}
```

| Component | Role |
|-----------|------|
| `AlarmScheduler` | Schedules exact alarms via `AlarmManager` |
| `AlarmReceiver` | `BroadcastReceiver` — fires `NotificationHelper` on trigger |
| `BootReceiver` | Re-schedules all active alarms after device reboot |
| `WorkManager` | Handles any deferred background scheduling tasks |
| **Universal Alarm Card** | Home screen card aggregating ALL pending alarms from every module in one place — cancel any alarm with one tap |

---

## Student Profile

A complete student identity card stored in a singleton `StudentProfile` entity:

| Field | Detail |
|-------|--------|
| Name | Full name |
| Photo | Profile picture from gallery (stored as local URI) |
| Date of Birth | For age / countdown calculations |
| Email + Mobile | Contact info |
| Aadhaar No. | National ID tracking |
| 10th Percentage | With optional marksheet PDF upload + viewer |
| 12th Percentage | With optional marksheet PDF upload + viewer |
| Target Score | Editable (default `700/720`) |
| Dream Role | Editable (default `MBBS Doctor`) |
| NEET Attempts | List of past attempts with: year · roll no · marks obtained · marksheet PDF · question paper PDF · solution PDF · lack description |

Profile header (photo + name + target score) is surfaced directly on the Home screen.

---

## Database Schema

Single **Room** database (`NEETDatabase`) accessed through a **single DAO** (`NEETDao`):

```
╔══════════════════════════╦══════════════════════════════════════════════╗
║  Table                   ║  Entity & Purpose                            ║
╠══════════════════════════╬══════════════════════════════════════════════╣
║  student_profile         ║  StudentProfile (singleton — id = "profile") ║
║  notebooks               ║  Notebook (physical notebook registry)       ║
║  notebook_chapters       ║  NotebookChapter (per notebook)              ║
║  books                   ║  Book (reference book library)               ║
║  pyq_sources             ║  PYQSource (chapterwise / yearwise groups)   ║
║  pyq_chapters            ║  PYQChapter (per chapterwise source)         ║
║  pyq_years               ║  PYQYear (per yearwise book)                 ║
║  test_papers             ║  TestPaper (online + offline tests)          ║
║  sample_papers           ║  SamplePaper (sample paper catalogue)        ║
║  pw_batches              ║  PWBatch (PW batch groups)                   ║
║  pw_tests                ║  PWTest (per PW batch)                       ║
║  day_planner             ║  DayPlannerEntry (date → PlannerEvent[])     ║
║  week_planner            ║  WeekPlannerEntry (weekLabel → events[])     ║
║  month_planner           ║  MonthPlannerEntry (month → events[])        ║
║  year_planner            ║  YearPlannerEntry (yearSession → events[])   ║
║  diary_entries           ║  DailyDiary (date + tags + DiaryEntry[])     ║
║  date_events             ║  DateEvent (exam/event with alarm + PDF)     ║
║  dictionary_neet         ║  DictionaryNeet (NEET terminology)           ║
║  dictionary_non_neet     ║  DictionaryNonNeet (English vocabulary)      ║
║  mnemonics               ║  Mnemonic (memory aids + attached files)     ║
║  diagrams                ║  Diagram (chapter diagrams by subject)       ║
║  chapter_short_notes     ║  ChapterShortNote (subject-chapter PDFs)     ║
║  subject_short_notes     ║  SubjectShortNote (one PDF per subject)      ║
║  day_waste               ║  DayWaste (productivity accountability)      ║
║  neet_sequence           ║  NeetSequence (chapter order + status)       ║
║  neet_sequence_pdf       ║  NeetSequencePdf (singleton PDF reference)   ║
║  lack_points             ║  LackPoint (weakness + solution tracking)    ║
║  neet_syllabus           ║  NEETSyllabus (singleton PDF reference)      ║
║  reminders               ║  Reminder (alarm / notification entities)    ║
╚══════════════════════════╩══════════════════════════════════════════════╝
```

**TypeConverters (Gson-backed):** `List<String>` · `List<CompletionDate>` · `List<NeetAttempt>` · `List<DiaryEntry>` · `List<PlannerEvent>` · `List<QuestionDivision>`

**Subjects enum:** `PHYSICS` · `CHEMISTRY` · `BOTANY` · `ZOOLOGY` · `GENERAL`

---

## Navigation Routes

```
home
├── profile
├── assets
│   ├── notebooks  →  notebook_chapters/{notebookId}/{notebookNo}
│   ├── books
│   ├── pyq
│   │   ├── pyq_chapterwise  →  pyq_chapterwise_detail/{sourceId}/{sourceName}
│   │   └── pyq_yearwise     →  pyq_yearwise_detail/{bookId}/{bookName}
│   ├── test_papers
│   │   ├── online_tests
│   │   └── offline_tests
│   ├── sample_papers
│   └── pw_batches  →  pw_batch_tests/{batchId}/{batchName}
├── planner
│   ├── day_planner     →  day_planner_detail/{date}
│   ├── week_planner    →  week_planner_detail/{weekId}
│   ├── month_planner   →  month_planner_detail/{monthId}
│   └── year_planner    →  year_planner_detail/{yearId}
├── daily_diary         →  diary_entry/{diaryId}
├── date_events         →  date_event_detail/{date}
├── universal_calendar
├── neet_syllabus
├── dictionary
│   ├── dictionary_neet
│   └── dictionary_non_neet
├── mnemonics
├── diagrams            →  diagrams_subject/{subject}
├── chapter_short_notes →  chapter_short_notes_subject/{subject}
├── subject_short_notes
├── day_waste
├── neet_sequence
├── lack_points
└── file_viewer/{encodedUri}/{title}?solutionUri={solutionUri}
    ├── diagram_viewer/{subject}/{title}/{encodedUri}
    ├── short_note_viewer/{subject}/{title}/{encodedUri}
    └── subject_note_viewer/{subject}/{title}/{encodedUri}
```

All URI params are URL-encoded. All navigation builder functions live in `NavRoutes.kt`.

---

## Project Structure

```
app/src/main/java/com/neet/tracker/
│
├── alarm/
│   ├── AlarmReceiver.kt           ← BroadcastReceiver → fires notification
│   ├── AlarmScheduler.kt          ← exact alarm scheduling via AlarmManager
│   ├── BootReceiver.kt            ← re-registers all alarms after reboot
│   └── NotificationHelper.kt     ← notification channel + builder + display
│
├── data/
│   ├── database/
│   │   ├── DatabaseModule.kt      ← Hilt module providing DB + DAO singletons
│   │   ├── NEETDao.kt             ← single DAO (all 30+ tables, all queries)
│   │   └── NEETDatabase.kt        ← Room database definition + TypeConverters
│   └── models/
│       └── Models.kt              ← all 30+ entity + embedded data classes
│
├── navigation/
│   ├── NavRoutes.kt               ← route string constants + builder functions
│   └── NEETNavHost.kt            ← NavHost with all composable destinations
│
├── ui/
│   ├── components/
│   │   └── CommonComponents.kt   ← GlassCard, NEETTopBar, SpaceBackground…
│   ├── dialogs/
│   │   └── Dialogs.kt            ← all dialog composables (10+)
│   ├── screens/
│   │   ├── AdvancedScreens.kt    ← UniversalCalendar, Diagrams, ShortNotes…
│   │   ├── AssetsScreen.kt       ← assets hub grid
│   │   ├── BooksScreen.kt        ← book library with search + tag filter
│   │   ├── DiaryAndEventScreens.kt ← daily diary + date events
│   │   ├── FileViewerScreen.kt   ← full PDF viewer + 7-tool annotation engine
│   │   ├── HomeScreen.kt         ← dashboard / command centre
│   │   ├── MiscScreens.kt        ← Syllabus, Dictionary, Mnemonics, DayWaste…
│   │   ├── NotebooksScreen.kt    ← notebook vault
│   │   ├── PlannerScreens.kt     ← day / week / month / year planners
│   │   ├── ProfileScreen.kt      ← student profile + NEET attempt history
│   │   ├── PWBatchesScreen.kt    ← Physics Wallah batch + test tracker
│   │   ├── PYQScreens.kt         ← PYQ chapterwise & yearwise trackers
│   │   └── TestAndSampleScreens.kt ← test + sample paper tracker
│   ├── theme/
│   │   ├── Theme.kt              ← Deep Space Neon palette + Material 3 scheme
│   │   └── Typography.kt         ← Google Fonts custom typography
│   └── viewmodels/
│       ├── ProfileViewModel.kt
│       ├── ReminderViewModel.kt
│       └── [12+ other ViewModels]
│
├── util/
│   ├── AnnotationManager.kt      ← JSON-based annotation persistence engine
│   └── UriUtils.kt               ← URI → internal app-files copy helper
│
├── MainActivity.kt               ← single activity, edge-to-edge Compose setup
└── NEETTrackerApp.kt             ← @HiltAndroidApp application class
```

---

## Build & Run

### Requirements

| Tool | Minimum |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 35 |
| Gradle | 8.x (via wrapper — no manual install) |
| Device / Emulator | Android 8.0+ (API 26+) |

### Steps

```bash
# 1. Clone
git clone https://github.com/your-username/neet-tracker-pro.git
cd neet-tracker-pro

# 2. Open in Android Studio
#    File → Open → select project root
#    Gradle sync runs automatically

# 3. Run debug build
./gradlew installDebug

# 4. Release build (requires keystore.properties in root)
./gradlew assembleRelease
```

### keystore.properties (release only, gitignored)

```properties
storeFile=path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

> **Note:** This app requires zero internet access. No Firebase. No analytics. No crash reporting. No ads. No tracking. All data stays on your device, forever.

---

<div align="center">

```
╔══════════════════════════════════════════════════════════════════════╗
║                                                                      ║
║          Built for the ones who want  7 2 0 / 7 2 0                 ║
║                                                                      ║
║              भारत के डॉक्टर — NEET Tracker Pro के साथ।              ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
```

![Made for NEET](https://img.shields.io/badge/Made%20for-NEET%20Aspirants-FF1744?style=for-the-badge)
![Offline First](https://img.shields.io/badge/Offline-First-00E676?style=for-the-badge)
![No Ads Ever](https://img.shields.io/badge/No%20Ads-Ever-FFD700?style=for-the-badge)
![Dark Mode Only](https://img.shields.io/badge/Dark%20Mode-Only-7C4DFF?style=for-the-badge)
![720 Goal](https://img.shields.io/badge/Goal-720%2F720-00E5FF?style=for-the-badge)

</div>
