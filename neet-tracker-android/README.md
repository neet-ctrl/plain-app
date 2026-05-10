# NEET Tracker Pro — Full Android Native App

## How to Open in Android Studio

1. **Extract this ZIP** to a folder on your computer
2. **Open Android Studio** (Arctic Fox or newer — Hedgehog/Iguana recommended)
3. Click **File → Open** and select the `neet-tracker-android` folder
4. Wait for Gradle sync to complete (first time may take 3–5 minutes downloading dependencies)
5. Connect your Android phone via USB **or** start an emulator (API 26+)
6. Click the **▶ Run** button

## Requirements

- Android Studio Hedgehog or newer
- Android SDK 35 (install via SDK Manager)
- Java 17 (bundled with Android Studio)
- Min Android: Android 8.0 (API 26)

## App Architecture

```
com.neet.tracker/
├── NEETTrackerApp.kt          — Hilt Application class
├── MainActivity.kt             — Single activity entry point
├── ui/
│   ├── theme/                  — Deep-space dark theme (Orbitron + Exo 2 fonts)
│   ├── components/             — Reusable glass cards, search bars, FABs, badges
│   ├── dialogs/                — All 10+ dialogs (Status, Tag, Remark, Dates, etc.)
│   ├── screens/                — All 30+ screens
│   └── viewmodels/             — All ViewModels (Hilt-injected)
├── data/
│   ├── models/Models.kt        — All Room entities + embedded data classes
│   ├── database/
│   │   ├── NEETDatabase.kt     — Room DB
│   │   ├── NEETDao.kt          — All DAO queries
│   │   └── DatabaseModule.kt   — Hilt DI module
└── navigation/
    ├── NavRoutes.kt            — All route constants + builders
    └── NEETNavHost.kt          — NavHost with slide animations
```

## Features Built

### Main Dashboard (14 modules)
1. **Assets Vault** — Notebooks, Books, PYQs, Tests, Sample Papers, PW Batches
2. **Smart Planner** — Day / Week / Month / Year planners
3. **Daily Diary** — Full rich-text diary with emoji toolbar
4. **Event Log** — Per-date event tracker with shift-to-next feature
5. **NEET Syllabus** — PDF upload & viewer
6. **Lexicon** — NEET terms + English word bank
7. **Mnemonic Lab** — Memory technique tracker
8. **Universe Calendar** — Aggregates ALL dates from every module
9. **Diagrams Atlas** — Botany & Zoology PDF viewers
10. **Chapter Notes** — Per-subject chapter short notes
11. **Wasted Days** — Track waste %, reason, recovery tips
12. **NEET Sequence** — 80+ connected chapter flow with status connectors
13. **Subject Notes** — Subject-wise PDF short notes
14. **Lack Points** — Identify weaknesses & track solutions

### Student Profile
- Photo with animated gradient ring
- Personal info (DOB, Email, Mobile, Aadhar)
- 10th & 12th qualifications + marksheets
- Multiple NEET attempt records (rolls, marks, QP, solutions, lack analysis)
- Target score display

### Asset Module Details
- **Notebooks**: Number + cover photo, chapters with spec/missing/status dialogs
- **Books**: Full CRUD with info, status, tags, remark dialogs
- **PYQ Chapterwise**: Sources → Chapters with dates, wrong Q, remark
- **PYQ Yearwise**: Books → Years with completion tracking
- **Tests (Online/Offline)**: Full metadata — topics, wrong Qs, marks, tags, URL, QP/solution upload
- **Sample Papers**: Same as tests
- **PW Batches**: Batch → Tests with full test metadata

### Dialogs (All Fully Custom)
- Status Selector (Expected / Completed / Revision / Crossed)
- Tag Dialog (preset + custom tags, filter chips)
- Remark Dialog (multiline editor)
- Completion Dates Dialog (multiple dates with notes)
- Wrong Questions Dialog
- Specification / Info Dialog (with rich text toolbar)
- Missing Notes Dialog
- URL Dialog
- Prefix Date Dialog
- Marks Dialog
- Topics Dialog

### Design Language
- Deep-space dark background with animated grid
- Neon glow accents (Cyan, Purple, Gold, Green, Red, Orange)
- Glass morphism cards with animated border glow
- Orbitron font for headings, Exo 2 for body
- Status-color coded cards (Yellow=Expected, Green=Completed, Purple=Revision, Red=Crossed)
- Breadcrumb navigation on every screen
- 3D search bars on every list screen
- Smooth slide + fade navigation transitions
- Staggered card entrance animations

## Tech Stack
- Kotlin + Jetpack Compose
- Navigation Compose (type-safe routes)
- Room Database + Hilt DI
- Coroutines + StateFlow
- Coil (image loading)
- Google Fonts (Orbitron, Exo 2)
- Material 3 Design System
