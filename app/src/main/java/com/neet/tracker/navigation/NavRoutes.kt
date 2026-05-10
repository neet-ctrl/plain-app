package com.neet.tracker.navigation

object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"

    // Assets
    const val ASSETS = "assets"
    const val NOTEBOOKS = "notebooks"
    const val NOTEBOOK_CHAPTERS = "notebook_chapters/{notebookId}/{notebookNo}"
    const val BOOKS = "books"
    const val PYQ = "pyq"
    const val PYQ_CHAPTERWISE = "pyq_chapterwise"
    const val PYQ_CHAPTERWISE_DETAIL = "pyq_chapterwise_detail/{sourceId}/{sourceName}"
    const val PYQ_YEARWISE = "pyq_yearwise"
    const val PYQ_YEARWISE_DETAIL = "pyq_yearwise_detail/{bookId}/{bookName}"
    const val TEST_PAPERS = "test_papers"
    const val ONLINE_TESTS = "online_tests"
    const val OFFLINE_TESTS = "offline_tests"
    const val SAMPLE_PAPERS = "sample_papers"
    const val PW_BATCHES = "pw_batches"
    const val PW_BATCH_TESTS = "pw_batch_tests/{batchId}/{batchName}"

    // Planner
    const val PLANNER = "planner"
    const val DAY_PLANNER = "day_planner"
    const val DAY_PLANNER_DETAIL = "day_planner_detail/{date}"
    const val WEEK_PLANNER = "week_planner"
    const val WEEK_PLANNER_DETAIL = "week_planner_detail/{weekId}"
    const val MONTH_PLANNER = "month_planner"
    const val MONTH_PLANNER_DETAIL = "month_planner_detail/{monthId}"
    const val YEAR_PLANNER = "year_planner"
    const val YEAR_PLANNER_DETAIL = "year_planner_detail/{yearId}"

    // Other main
    const val DAILY_DIARY = "daily_diary"
    const val DIARY_ENTRY = "diary_entry/{diaryId}"
    const val DATE_EVENTS = "date_events"
    const val DATE_EVENT_DETAIL = "date_event_detail/{date}"
    const val NEET_SYLLABUS = "neet_syllabus"
    const val DICTIONARY = "dictionary"
    const val DICTIONARY_NEET = "dictionary_neet"
    const val DICTIONARY_NON_NEET = "dictionary_non_neet"
    const val MNEMONICS = "mnemonics"
    const val UNIVERSAL_CALENDAR = "universal_calendar"
    const val DIAGRAMS = "diagrams"
    const val DIAGRAMS_SUBJECT = "diagrams_subject/{subject}"
    const val CHAPTER_SHORT_NOTES = "chapter_short_notes"
    const val CHAPTER_SHORT_NOTES_SUBJECT = "chapter_short_notes_subject/{subject}"
    const val DAY_WASTE = "day_waste"
    const val NEET_SEQUENCE = "neet_sequence"
    const val SUBJECT_SHORT_NOTES = "subject_short_notes"
    const val LACK_POINTS = "lack_points"

    // File Viewer
    const val FILE_VIEWER = "file_viewer/{encodedUri}/{title}?solutionUri={solutionUri}"

    // Specialized Viewers
    const val DIAGRAM_VIEWER     = "diagram_viewer/{subject}/{title}/{encodedUri}"
    const val SHORT_NOTE_VIEWER  = "short_note_viewer/{subject}/{title}/{encodedUri}"
    const val SUBJECT_NOTE_VIEWER = "subject_note_viewer/{subject}/{title}/{encodedUri}"
}

fun notebookChaptersRoute(notebookId: String, notebookNo: String) =
    "notebook_chapters/$notebookId/$notebookNo"

fun pyqChapterwiseDetailRoute(sourceId: String, sourceName: String) =
    "pyq_chapterwise_detail/$sourceId/$sourceName"

fun pyqYearwiseDetailRoute(bookId: String, bookName: String) =
    "pyq_yearwise_detail/$bookId/$bookName"

fun pwBatchTestsRoute(batchId: String, batchName: String) =
    "pw_batch_tests/$batchId/$batchName"

fun dayPlannerDetailRoute(date: String) = "day_planner_detail/$date"
fun weekPlannerDetailRoute(weekId: String) = "week_planner_detail/$weekId"
fun monthPlannerDetailRoute(monthId: String) = "month_planner_detail/$monthId"
fun yearPlannerDetailRoute(yearId: String) = "year_planner_detail/$yearId"
fun diaryEntryRoute(diaryId: String) = "diary_entry/$diaryId"
fun dateEventDetailRoute(date: String) = "date_event_detail/${java.net.URLEncoder.encode(date, "UTF-8")}"
fun diagramsSubjectRoute(subject: String) = "diagrams_subject/$subject"
fun chapterShortNotesSubjectRoute(subject: String) = "chapter_short_notes_subject/$subject"

fun fileViewerRoute(encodedUri: String, title: String, solutionUri: String = "") =
    "file_viewer/${java.net.URLEncoder.encode(encodedUri, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}" +
    if (solutionUri.isNotBlank()) "?solutionUri=${java.net.URLEncoder.encode(solutionUri, "UTF-8")}" else ""

fun diagramViewerRoute(subject: String, fileUri: String, title: String) =
    "diagram_viewer/$subject/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(fileUri, "UTF-8")}"

fun shortNoteViewerRoute(subject: String, fileUri: String, title: String) =
    "short_note_viewer/$subject/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(fileUri, "UTF-8")}"

fun subjectNoteViewerRoute(subject: String, fileUri: String, title: String) =
    "subject_note_viewer/$subject/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(fileUri, "UTF-8")}"
