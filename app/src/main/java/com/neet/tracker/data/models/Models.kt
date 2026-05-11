package com.neet.tracker.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// ─── Converters ───────────────────────────────────────────────────────────────

class Converters {
    private val gson = Gson()

    @TypeConverter fun fromStringList(v: List<String>?): String = gson.toJson(v ?: emptyList<String>())
    @TypeConverter fun toStringList(v: String): List<String> = gson.fromJson(v, object : TypeToken<List<String>>() {}.type) ?: emptyList()

    @TypeConverter fun fromCompletionDateList(v: List<CompletionDate>?): String = gson.toJson(v ?: emptyList<CompletionDate>())
    @TypeConverter fun toCompletionDateList(v: String): List<CompletionDate> = gson.fromJson(v, object : TypeToken<List<CompletionDate>>() {}.type) ?: emptyList()

    @TypeConverter fun fromNeetAttemptList(v: List<NeetAttempt>?): String = gson.toJson(v ?: emptyList<NeetAttempt>())
    @TypeConverter fun toNeetAttemptList(v: String): List<NeetAttempt> = gson.fromJson(v, object : TypeToken<List<NeetAttempt>>() {}.type) ?: emptyList()

    @TypeConverter fun fromDiaryEntryList(v: List<DiaryEntry>?): String = gson.toJson(v ?: emptyList<DiaryEntry>())
    @TypeConverter fun toDiaryEntryList(v: String): List<DiaryEntry> = gson.fromJson(v, object : TypeToken<List<DiaryEntry>>() {}.type) ?: emptyList()

    @TypeConverter fun fromPlannerEventList(v: List<PlannerEvent>?): String = gson.toJson(v ?: emptyList<PlannerEvent>())
    @TypeConverter fun toPlannerEventList(v: String): List<PlannerEvent> = gson.fromJson(v, object : TypeToken<List<PlannerEvent>>() {}.type) ?: emptyList()

    @TypeConverter fun fromQuestionDivisionList(v: List<QuestionDivision>?): String = gson.toJson(v ?: emptyList<QuestionDivision>())
    @TypeConverter fun toQuestionDivisionList(v: String): List<QuestionDivision> = gson.fromJson(v, object : TypeToken<List<QuestionDivision>>() {}.type) ?: emptyList()
}

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class Status { EXPECTED, COMPLETED, REVISION, CROSSED }
enum class Subject { PHYSICS, CHEMISTRY, BOTANY, ZOOLOGY, GENERAL }

// ─── Embedded data classes (stored as JSON) ───────────────────────────────────

data class CompletionDate(
    val date: String = "",
    val note: String = ""
)

data class NeetAttempt(
    val id: String = UUID.randomUUID().toString(),
    val year: String = "",
    val rollNo: String = "",
    val marksObtained: String = "",
    val marksheetUri: String = "",
    val questionPaperUri: String = "",
    val solutionPdfUri: String = "",
    val lackDescription: String = ""
)

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class PlannerEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val notes: String = "",
    val sourceFileUri: String = "",
    val timingRange: String = "",
    val day: String = "",
    val dateRange: String = "",
    val month: String = "",
    val status: String = "EXPECTED",
    val remark: String = "",
    val completedAt: Long = 0L,
    val alarmTime: Long = 0L,
    val alarmLabel: String = ""
)

data class QuestionDivision(
    val chapter: String = "",
    val count: Int = 0
)

// ─── Room Entities ────────────────────────────────────────────────────────────

@Entity(tableName = "student_profile")
@TypeConverters(Converters::class)
data class StudentProfile(
    @PrimaryKey val id: String = "profile",
    val name: String = "",
    val photoUri: String = "",
    val dob: String = "",
    val email: String = "",
    val mobile: String = "",
    val aadharNo: String = "",
    val tenthPercentage: String = "",
    val tenthMarksheetUri: String = "",
    val twelfthPercentage: String = "",
    val twelfthMarksheetUri: String = "",
    val targetScore: String = "700/720",
    val dreamRole: String = "MBBS Doctor",
    val neetAttempts: List<NeetAttempt> = emptyList()
)

@Entity(tableName = "notebooks")
@TypeConverters(Converters::class)
data class Notebook(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val notebookNo: String = "",
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notebook_chapters")
@TypeConverters(Converters::class)
data class NotebookChapter(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val notebookId: String = "",
    val name: String = "",
    val specifications: String = "",
    val missingNotes: String = "",
    val status: Status = Status.EXPECTED,
    val tags: List<String> = emptyList(),
    val remark: String = ""
)

@Entity(tableName = "books")
@TypeConverters(Converters::class)
data class Book(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val subject: Subject = Subject.GENERAL,
    val status: Status = Status.EXPECTED,
    val tags: List<String> = emptyList(),
    val remark: String = "",
    val info: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pyq_sources")
@TypeConverters(Converters::class)
data class PYQSource(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "CHAPTERWISE",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pyq_chapters")
@TypeConverters(Converters::class)
data class PYQChapter(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceId: String = "",
    val name: String = "",
    val status: Status = Status.EXPECTED,
    val completionDates: List<CompletionDate> = emptyList(),
    val wrongQuestions: String = "",
    val remark: String = ""
)

@Entity(tableName = "pyq_years")
@TypeConverters(Converters::class)
data class PYQYear(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String = "",
    val year: String = "",
    val status: Status = Status.EXPECTED,
    val wrongQuestions: String = "",
    val completionDates: List<CompletionDate> = emptyList(),
    val remark: String = ""
)

@Entity(tableName = "test_papers")
@TypeConverters(Converters::class)
data class TestPaper(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "ONLINE",
    val status: Status = Status.EXPECTED,
    val prefixDate: String = "",
    val topicsAsked: String = "",
    val wrongQuestions: String = "",
    val questionPaperUri: String = "",
    val solutionUri: String = "",
    val remark: String = "",
    val tags: List<String> = emptyList(),
    val marksObtained: String = "",
    val url: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sample_papers")
@TypeConverters(Converters::class)
data class SamplePaper(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val status: Status = Status.EXPECTED,
    val prefixDate: String = "",
    val wrongQuestions: String = "",
    val questionPaperUri: String = "",
    val solutionUri: String = "",
    val remark: String = "",
    val tags: List<String> = emptyList(),
    val marksObtained: String = "",
    val url: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pw_batches")
@TypeConverters(Converters::class)
data class PWBatch(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val status: Status = Status.EXPECTED,
    val tags: List<String> = emptyList(),
    val remark: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pw_tests")
@TypeConverters(Converters::class)
data class PWTest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val batchId: String = "",
    val name: String = "",
    val subject: String = "",
    val status: Status = Status.EXPECTED,
    val prefixDate: String = "",
    val topicsAsked: String = "",
    val wrongQuestions: String = "",
    val questionPaperUri: String = "",
    val solutionUri: String = "",
    val remark: String = "",
    val tags: List<String> = emptyList(),
    val marksObtained: String = "",
    val url: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "day_planner")
@TypeConverters(Converters::class)
data class DayPlannerEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val events: List<PlannerEvent> = emptyList()
)

@Entity(tableName = "week_planner")
@TypeConverters(Converters::class)
data class WeekPlannerEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val weekLabel: String = "",
    val dateRange: String = "",
    val events: List<PlannerEvent> = emptyList()
)

@Entity(tableName = "month_planner")
@TypeConverters(Converters::class)
data class MonthPlannerEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val month: String = "",
    val events: List<PlannerEvent> = emptyList()
)

@Entity(tableName = "year_planner")
@TypeConverters(Converters::class)
data class YearPlannerEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val yearSession: String = "",
    val events: List<PlannerEvent> = emptyList()
)

@Entity(tableName = "diary_entries")
@TypeConverters(Converters::class)
data class DailyDiary(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val nickName: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "date_events")
@TypeConverters(Converters::class)
data class DateEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val name: String = "",
    val detail: String = "",
    val url: String = "",
    val location: String = "",
    val fileUri: String = "",
    val totalQuestions: Int = 0,
    val questionDivisions: List<QuestionDivision> = emptyList(),
    val timeRange: String = "",
    val remark: String = "",
    val status: String = "EXPECTED",
    val crossReason: String = "",
    val alarmTime: Long = 0L,
    val alarmLabel: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary_neet")
@TypeConverters(Converters::class)
data class DictionaryNeet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val term: String = "",
    val definition: String = "",
    val chapter: String = "",
    val subject: Subject = Subject.GENERAL,
    val tags: List<String> = emptyList(),
    val fileUri: String = "",
    val serialNo: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary_non_neet")
@TypeConverters(Converters::class)
data class DictionaryNonNeet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val word: String = "",
    val meaning: String = "",
    val example: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mnemonics")
@TypeConverters(Converters::class)
data class Mnemonic(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val chapter: String = "",
    val subject: Subject = Subject.GENERAL,
    val tags: List<String> = emptyList(),
    val fileUri: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "diagrams")
@TypeConverters(Converters::class)
data class Diagram(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chapter: String = "",
    val subject: String = "BOTANY",
    val fileUri: String = "",
    val labels: List<String> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chapter_short_notes")
@TypeConverters(Converters::class)
data class ChapterShortNote(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chapter: String = "",
    val subject: Subject = Subject.GENERAL,
    val fileUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "day_waste")
@TypeConverters(Converters::class)
data class DayWaste(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val wastePercentage: Int = 0,
    val reason: String = "",
    val sourceUri: String = "",
    val recoverTip: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "neet_sequence")
@TypeConverters(Converters::class)
data class NeetSequence(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val serialNo: Int = 0,
    val chapterName: String = "",
    val subject: Subject = Subject.GENERAL,
    val status: Status = Status.EXPECTED,
    val remark: String = "",
    val tags: List<String> = emptyList()
)

@Entity(tableName = "subject_short_notes")
@TypeConverters(Converters::class)
data class SubjectShortNote(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subject: Subject = Subject.GENERAL,
    val fileUri: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lack_points")
@TypeConverters(Converters::class)
data class LackPoint(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val point: String = "",
    val solution: String = "",
    val status: Status = Status.EXPECTED,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "neet_syllabus")
data class NEETSyllabus(
    @PrimaryKey val id: String = "syllabus",
    val fileUri: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "neet_sequence_pdf")
data class NeetSequencePdf(
    @PrimaryKey val id: String = "neet_sequence_pdf",
    val fileUri: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
)

// ─── Error Notebook Enums ─────────────────────────────────────────────────────

enum class ErrorType {
    CONCEPT_MISTAKE, SILLY_MISTAKE, CALCULATION_ERROR, NOT_ATTEMPTED,
    TIME_PRESSURE, FORMULA_FORGOT, MISREAD, OVERCONFIDENCE
}

enum class ErrorSource {
    TEST_PAPER, PYQ_CHAPTERWISE, PYQ_YEARWISE, SAMPLE_PAPER,
    PW_TEST, BOOK, LECTURE, SELF_STUDY
}

enum class ErrorStatus { PENDING, UNDERSTOOD, MASTERED }

// ─── Error Notebook Entity ────────────────────────────────────────────────────

@Entity(tableName = "error_entries")
@TypeConverters(Converters::class)
data class ErrorEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val questionNo: String = "",
    val description: String = "",
    val errorType: ErrorType = ErrorType.CONCEPT_MISTAKE,
    val subject: Subject = Subject.GENERAL,
    val chapter: String = "",
    val sourceType: ErrorSource = ErrorSource.SELF_STUDY,
    val sourceName: String = "",
    val myAnswer: String = "",
    val correctAnswer: String = "",
    val explanation: String = "",
    val status: ErrorStatus = ErrorStatus.PENDING,
    val tags: List<String> = emptyList(),
    val imageUri: String = "",
    val revisionCount: Int = 0,
    val lastRevised: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Revision Scheduler Enums ─────────────────────────────────────────────────

enum class RevisionType {
    NEET_CHAPTER, NOTEBOOK_CHAPTER, PYQ_CHAPTER, PYQ_YEAR,
    TEST_PAPER, SAMPLE_PAPER, PW_TEST, BOOK, LECTURE, TOPIC, CUSTOM
}

enum class RevisionPriority { LOW, MEDIUM, HIGH, CRITICAL }
enum class RevisionStatus { PENDING, DONE, SKIPPED }

// ─── Revision Scheduler Entity ────────────────────────────────────────────────

@Entity(tableName = "revision_items")
@TypeConverters(Converters::class)
data class RevisionItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val subject: Subject = Subject.GENERAL,
    val type: RevisionType = RevisionType.CUSTOM,
    val sourceName: String = "",
    val sourceId: String = "",
    val scheduledDate: String = "",
    val revisionNumber: Int = 1,
    val priority: RevisionPriority = RevisionPriority.MEDIUM,
    val status: RevisionStatus = RevisionStatus.PENDING,
    val notes: String = "",
    val isSpacedRepetition: Boolean = false,
    val intervalDays: Int = 1,
    val nextRevisionDate: String = "",
    val completedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Reminder Entity ──────────────────────────────────────────────────────────

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String = "",
    val triggerAtMillis: Long = 0L,
    val isRepeating: Boolean = false,
    val repeatIntervalHours: Int = 24,
    val isActive: Boolean = true,
    val linkedEntityId: String = "",
    val linkedEntityType: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
