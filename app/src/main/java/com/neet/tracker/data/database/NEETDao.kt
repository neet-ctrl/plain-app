package com.neet.tracker.data.database

import androidx.room.*
import com.neet.tracker.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NEETDao {

    // ── Profile ───────────────────────────────────────────────────────────────
    @Query("SELECT * FROM student_profile WHERE id='profile' LIMIT 1")
    fun getProfile(): Flow<StudentProfile?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(p: StudentProfile)

    // ── Notebooks ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM notebooks ORDER BY createdAt ASC")
    fun getNotebooks(): Flow<List<Notebook>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNotebook(n: Notebook)
    @Delete suspend fun deleteNotebook(n: Notebook)

    @Query("SELECT * FROM notebook_chapters WHERE notebookId=:nbId ORDER BY rowid ASC")
    fun getNotebookChapters(nbId: String): Flow<List<NotebookChapter>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNotebookChapter(c: NotebookChapter)
    @Delete suspend fun deleteNotebookChapter(c: NotebookChapter)

    // ── Books ─────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM books ORDER BY createdAt ASC")
    fun getBooks(): Flow<List<Book>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBook(b: Book)
    @Delete suspend fun deleteBook(b: Book)

    // ── PYQ ───────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM pyq_sources WHERE type='CHAPTERWISE' ORDER BY createdAt ASC")
    fun getPYQChapterwiseSources(): Flow<List<PYQSource>>
    @Query("SELECT * FROM pyq_sources WHERE type='YEARWISE' ORDER BY createdAt ASC")
    fun getPYQYearwiseSources(): Flow<List<PYQSource>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePYQSource(s: PYQSource)
    @Delete suspend fun deletePYQSource(s: PYQSource)

    @Query("SELECT * FROM pyq_chapters WHERE sourceId=:srcId ORDER BY rowid ASC")
    fun getPYQChapters(srcId: String): Flow<List<PYQChapter>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePYQChapter(c: PYQChapter)
    @Delete suspend fun deletePYQChapter(c: PYQChapter)

    @Query("SELECT * FROM pyq_years WHERE bookId=:bookId ORDER BY year ASC")
    fun getPYQYears(bookId: String): Flow<List<PYQYear>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePYQYear(y: PYQYear)
    @Delete suspend fun deletePYQYear(y: PYQYear)

    // ── Test Papers ───────────────────────────────────────────────────────────
    @Query("SELECT * FROM test_papers WHERE type='ONLINE' ORDER BY createdAt ASC")
    fun getOnlineTests(): Flow<List<TestPaper>>
    @Query("SELECT * FROM test_papers WHERE type='OFFLINE' ORDER BY createdAt ASC")
    fun getOfflineTests(): Flow<List<TestPaper>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTestPaper(t: TestPaper)
    @Delete suspend fun deleteTestPaper(t: TestPaper)

    // ── Sample Papers ─────────────────────────────────────────────────────────
    @Query("SELECT * FROM sample_papers ORDER BY createdAt ASC")
    fun getSamplePapers(): Flow<List<SamplePaper>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSamplePaper(s: SamplePaper)
    @Delete suspend fun deleteSamplePaper(s: SamplePaper)

    // ── PW Batches ────────────────────────────────────────────────────────────
    @Query("SELECT * FROM pw_batches ORDER BY createdAt ASC")
    fun getPWBatches(): Flow<List<PWBatch>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePWBatch(b: PWBatch)
    @Delete suspend fun deletePWBatch(b: PWBatch)

    @Query("SELECT * FROM pw_tests WHERE batchId=:batchId ORDER BY createdAt ASC")
    fun getPWTests(batchId: String): Flow<List<PWTest>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePWTest(t: PWTest)
    @Delete suspend fun deletePWTest(t: PWTest)

    // ── Planner ───────────────────────────────────────────────────────────────
    @Query("SELECT * FROM day_planner ORDER BY date ASC")
    fun getDayPlannerEntries(): Flow<List<DayPlannerEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDayPlannerEntry(e: DayPlannerEntry)

    @Query("SELECT * FROM week_planner ORDER BY rowid ASC")
    fun getWeekPlannerEntries(): Flow<List<WeekPlannerEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeekPlannerEntry(e: WeekPlannerEntry)

    @Query("SELECT * FROM month_planner ORDER BY month ASC")
    fun getMonthPlannerEntries(): Flow<List<MonthPlannerEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMonthPlannerEntry(e: MonthPlannerEntry)

    @Query("SELECT * FROM year_planner ORDER BY yearSession ASC")
    fun getYearPlannerEntries(): Flow<List<YearPlannerEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveYearPlannerEntry(e: YearPlannerEntry)

    // ── Diary ─────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun getDiaryEntries(): Flow<List<DailyDiary>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDiaryEntry(e: DailyDiary)
    @Delete suspend fun deleteDiaryEntry(e: DailyDiary)

    // ── Date Events ───────────────────────────────────────────────────────────
    @Query("SELECT * FROM date_events ORDER BY date ASC")
    fun getDateEvents(): Flow<List<DateEvent>>
    @Query("SELECT * FROM date_events WHERE date=:date ORDER BY createdAt ASC")
    fun getDateEventsForDate(date: String): Flow<List<DateEvent>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDateEvent(e: DateEvent)
    @Delete suspend fun deleteDateEvent(e: DateEvent)

    // ── Dictionary ────────────────────────────────────────────────────────────
    @Query("SELECT * FROM dictionary_neet ORDER BY serialNo ASC")
    fun getNeetDictionary(): Flow<List<DictionaryNeet>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNeetDictionary(d: DictionaryNeet)
    @Delete suspend fun deleteNeetDictionary(d: DictionaryNeet)

    @Query("SELECT * FROM dictionary_non_neet ORDER BY createdAt ASC")
    fun getNonNeetDictionary(): Flow<List<DictionaryNonNeet>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNonNeetDictionary(d: DictionaryNonNeet)
    @Delete suspend fun deleteNonNeetDictionary(d: DictionaryNonNeet)

    // ── Mnemonics ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM mnemonics ORDER BY createdAt ASC")
    fun getMnemonics(): Flow<List<Mnemonic>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMnemonic(m: Mnemonic)
    @Delete suspend fun deleteMnemonic(m: Mnemonic)

    // ── Diagrams ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM diagrams WHERE subject=:subject ORDER BY chapter ASC")
    fun getDiagrams(subject: String): Flow<List<Diagram>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDiagram(d: Diagram)
    @Delete suspend fun deleteDiagram(d: Diagram)

    // ── Chapter Short Notes ───────────────────────────────────────────────────
    @Query("SELECT * FROM chapter_short_notes WHERE subject=:subject ORDER BY chapter ASC")
    fun getChapterShortNotes(subject: String): Flow<List<ChapterShortNote>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChapterShortNote(n: ChapterShortNote)
    @Delete suspend fun deleteChapterShortNote(n: ChapterShortNote)

    // ── Day Waste ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM day_waste ORDER BY date DESC")
    fun getDayWasteEntries(): Flow<List<DayWaste>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDayWaste(d: DayWaste)
    @Delete suspend fun deleteDayWaste(d: DayWaste)

    // ── NEET Sequence ─────────────────────────────────────────────────────────
    @Query("SELECT * FROM neet_sequence ORDER BY serialNo ASC")
    fun getNeetSequence(): Flow<List<NeetSequence>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNeetSequence(s: NeetSequence)
    @Delete suspend fun deleteNeetSequence(s: NeetSequence)
    @Query("UPDATE neet_sequence SET status=:status WHERE id=:id")
    suspend fun updateNeetSequenceStatus(id: String, status: Status)

    // ── Subject Short Notes ───────────────────────────────────────────────────
    @Query("SELECT * FROM subject_short_notes WHERE subject=:subject LIMIT 1")
    fun getSubjectShortNote(subject: String): Flow<SubjectShortNote?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubjectShortNote(n: SubjectShortNote)

    // ── Lack Points ───────────────────────────────────────────────────────────
    @Query("SELECT * FROM lack_points ORDER BY createdAt ASC")
    fun getLackPoints(): Flow<List<LackPoint>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLackPoint(p: LackPoint)
    @Delete suspend fun deleteLackPoint(p: LackPoint)

    // ── Syllabus ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM neet_syllabus LIMIT 1")
    fun getSyllabus(): Flow<NEETSyllabus?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyllabus(s: NEETSyllabus)

    // ── Neet Sequence PDF ─────────────────────────────────────────────────────
    @Query("SELECT * FROM neet_sequence_pdf WHERE id='neet_sequence_pdf' LIMIT 1")
    fun getNeetSequencePdf(): Flow<NeetSequencePdf?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNeetSequencePdf(p: NeetSequencePdf)

    // ── Reminders ─────────────────────────────────────────────────────────────
    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun getReminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders WHERE isActive=1 ORDER BY triggerAtMillis ASC")
    fun getActiveReminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders WHERE linkedEntityId=:entityId ORDER BY triggerAtMillis ASC")
    fun getRemindersForEntity(entityId: String): Flow<List<Reminder>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReminder(r: Reminder)
    @Delete suspend fun deleteReminder(r: Reminder)
    @Query("DELETE FROM reminders WHERE id=:id")
    suspend fun deleteReminderById(id: String)
    @Query("UPDATE reminders SET isActive=:active WHERE id=:id")
    suspend fun setReminderActive(id: String, active: Boolean)

    // ── Error Notebook ────────────────────────────────────────────────────────
    @Query("SELECT * FROM error_entries ORDER BY createdAt DESC")
    fun getErrorEntries(): Flow<List<ErrorEntry>>
    @Query("SELECT * FROM error_entries WHERE subject=:subject ORDER BY createdAt DESC")
    fun getErrorsBySubject(subject: String): Flow<List<ErrorEntry>>
    @Query("SELECT * FROM error_entries WHERE status=:status ORDER BY createdAt DESC")
    fun getErrorsByStatus(status: String): Flow<List<ErrorEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveErrorEntry(e: ErrorEntry)
    @Delete suspend fun deleteErrorEntry(e: ErrorEntry)
    @Query("UPDATE error_entries SET status=:status WHERE id=:id")
    suspend fun updateErrorStatus(id: String, status: String)
    @Query("UPDATE error_entries SET revisionCount=revisionCount+1, lastRevised=:time WHERE id=:id")
    suspend fun markErrorRevised(id: String, time: Long)
    @Query("SELECT COUNT(*) FROM error_entries") fun countErrors(): Flow<Int>
    @Query("SELECT COUNT(*) FROM error_entries WHERE status='PENDING'") fun countPendingErrors(): Flow<Int>
    @Query("SELECT COUNT(*) FROM error_entries WHERE status='UNDERSTOOD'") fun countUnderstoodErrors(): Flow<Int>
    @Query("SELECT COUNT(*) FROM error_entries WHERE status='MASTERED'") fun countMasteredErrors(): Flow<Int>

    // ── Revision Scheduler ────────────────────────────────────────────────────
    @Query("SELECT * FROM revision_items ORDER BY scheduledDate ASC")
    fun getRevisionItems(): Flow<List<RevisionItem>>
    @Query("SELECT * FROM revision_items WHERE scheduledDate=:date AND status='PENDING' ORDER BY priority DESC")
    fun getRevisionsByDate(date: String): Flow<List<RevisionItem>>
    @Query("SELECT * FROM revision_items WHERE scheduledDate<:date AND status='PENDING' ORDER BY scheduledDate ASC")
    fun getOverdueRevisions(date: String): Flow<List<RevisionItem>>
    @Query("SELECT * FROM revision_items WHERE scheduledDate>=:start AND scheduledDate<=:end ORDER BY scheduledDate ASC, priority DESC")
    fun getRevisionsBetween(start: String, end: String): Flow<List<RevisionItem>>
    @Query("SELECT * FROM revision_items WHERE status='DONE' ORDER BY completedAt DESC")
    fun getDoneRevisions(): Flow<List<RevisionItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRevisionItem(r: RevisionItem)
    @Delete suspend fun deleteRevisionItem(r: RevisionItem)
    @Query("UPDATE revision_items SET status=:status, completedAt=:time WHERE id=:id")
    suspend fun updateRevisionStatus(id: String, status: String, time: Long)
    @Query("UPDATE revision_items SET scheduledDate=:date, status='PENDING' WHERE id=:id")
    suspend fun rescheduleRevision(id: String, date: String)
    @Query("SELECT COUNT(*) FROM revision_items WHERE scheduledDate=:date AND status='PENDING'")
    fun countTodayRevisions(date: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM revision_items WHERE scheduledDate<:date AND status='PENDING'")
    fun countOverdueRevisions(date: String): Flow<Int>

    // ── Calendar aggregation ──────────────────────────────────────────────────
    @Query("SELECT date FROM day_planner")
    fun getAllDayPlannerDates(): Flow<List<String>>
    @Query("SELECT date FROM date_events")
    fun getAllDateEventDates(): Flow<List<String>>
    @Query("SELECT date FROM diary_entries")
    fun getAllDiaryDates(): Flow<List<String>>
    @Query("SELECT * FROM pyq_chapters ORDER BY rowid ASC")
    fun getAllPYQChapters(): Flow<List<PYQChapter>>
    @Query("SELECT * FROM pyq_years ORDER BY year ASC")
    fun getAllPYQYears(): Flow<List<PYQYear>>
    @Query("SELECT * FROM test_papers ORDER BY createdAt ASC")
    fun getAllTestPapers(): Flow<List<TestPaper>>
    @Query("SELECT * FROM pw_tests ORDER BY createdAt ASC")
    fun getAllPWTests(): Flow<List<PWTest>>

    // ── Global Search extras ──────────────────────────────────────────────────
    @Query("SELECT * FROM notebook_chapters ORDER BY rowid ASC")
    fun getAllNotebookChapters(): Flow<List<NotebookChapter>>
    @Query("SELECT * FROM diagrams ORDER BY subject ASC")
    fun getAllDiagrams(): Flow<List<Diagram>>
    @Query("SELECT * FROM chapter_short_notes ORDER BY subject ASC")
    fun getAllChapterShortNotes(): Flow<List<ChapterShortNote>>

    // ── Flashcard Progress ────────────────────────────────────────────────────
    @Query("SELECT * FROM flashcard_progress ORDER BY dueDate ASC")
    fun getAllFlashcardProgress(): Flow<List<FlashcardProgress>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFlashcardProgress(p: FlashcardProgress)
    @Query("SELECT COUNT(*) FROM flashcard_progress WHERE dueDate <= :today AND dueDate != ''")
    fun countDueFlashcards(today: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM dictionary_neet")
    fun countNeetDictionary(): Flow<Int>
    @Query("SELECT COUNT(*) FROM dictionary_non_neet")
    fun countNonNeetDictionary(): Flow<Int>
    @Query("SELECT COUNT(*) FROM mnemonics")
    fun countMnemonics(): Flow<Int>

    // ── Home card counts ──────────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM notebooks")         fun countNotebooks(): Flow<Int>
    @Query("SELECT COUNT(*) FROM books")             fun countBooks(): Flow<Int>
    @Query("SELECT COUNT(*) FROM pyq_sources")       fun countPYQSources(): Flow<Int>
    @Query("SELECT COUNT(*) FROM test_papers")       fun countTestPapers(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sample_papers")     fun countSamplePapers(): Flow<Int>
    @Query("SELECT COUNT(*) FROM pw_batches")        fun countPWBatches(): Flow<Int>
    @Query("SELECT COUNT(*) FROM diary_entries")     fun countDiaryEntries(): Flow<Int>
    @Query("SELECT COUNT(DISTINCT date) FROM date_events") fun countEventDates(): Flow<Int>
    @Query("SELECT COUNT(*) FROM dictionary_neet")   fun countNeetDict(): Flow<Int>
    @Query("SELECT COUNT(*) FROM mnemonics")         fun countMnemonics(): Flow<Int>
    @Query("SELECT COUNT(*) FROM diagrams")          fun countDiagrams(): Flow<Int>
    @Query("SELECT COUNT(*) FROM chapter_short_notes") fun countChapterNotes(): Flow<Int>
    @Query("SELECT COUNT(*) FROM day_waste")         fun countDayWaste(): Flow<Int>
    @Query("SELECT COUNT(*) FROM neet_sequence")     fun countNeetSequence(): Flow<Int>
    @Query("SELECT COUNT(*) FROM lack_points")       fun countLackPoints(): Flow<Int>
}
