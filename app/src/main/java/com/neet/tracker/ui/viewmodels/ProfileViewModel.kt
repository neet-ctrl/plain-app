package com.neet.tracker.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neet.tracker.data.database.NEETDao
import com.neet.tracker.data.database.NEETDatabase
import com.neet.tracker.data.models.*
import com.neet.tracker.util.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val profile: StateFlow<StudentProfile?> = dao.getProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(p: StudentProfile) = viewModelScope.launch { dao.saveProfile(p) }
}

@HiltViewModel
class NotebookViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val notebooks = dao.getNotebooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(n: Notebook) = viewModelScope.launch { dao.saveNotebook(n) }
    fun delete(n: Notebook) = viewModelScope.launch { dao.deleteNotebook(n) }
    fun chaptersFor(nbId: String) = dao.getNotebookChapters(nbId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveChapter(c: NotebookChapter) = viewModelScope.launch { dao.saveNotebookChapter(c) }
    fun deleteChapter(c: NotebookChapter) = viewModelScope.launch { dao.deleteNotebookChapter(c) }
}

@HiltViewModel
class BookViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val books = dao.getBooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(b: Book) = viewModelScope.launch { dao.saveBook(b) }
    fun delete(b: Book) = viewModelScope.launch { dao.deleteBook(b) }
}

@HiltViewModel
class PYQViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val chapterwiseSources = dao.getPYQChapterwiseSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val yearwiseSources = dao.getPYQYearwiseSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allChapters = dao.getAllPYQChapters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allYears = dao.getAllPYQYears().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveSource(s: PYQSource) = viewModelScope.launch { dao.savePYQSource(s) }
    fun deleteSource(s: PYQSource) = viewModelScope.launch { dao.deletePYQSource(s) }
    fun chaptersFor(srcId: String) = dao.getPYQChapters(srcId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveChapter(c: PYQChapter) = viewModelScope.launch { dao.savePYQChapter(c) }
    fun deleteChapter(c: PYQChapter) = viewModelScope.launch { dao.deletePYQChapter(c) }
    fun yearsFor(bookId: String) = dao.getPYQYears(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveYear(y: PYQYear) = viewModelScope.launch { dao.savePYQYear(y) }
    fun deleteYear(y: PYQYear) = viewModelScope.launch { dao.deletePYQYear(y) }
}

@HiltViewModel
class TestPaperViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val onlineTests = dao.getOnlineTests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val offlineTests = dao.getOfflineTests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTests = dao.getAllTestPapers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(t: TestPaper) = viewModelScope.launch { dao.saveTestPaper(t) }
    fun delete(t: TestPaper) = viewModelScope.launch { dao.deleteTestPaper(t) }
}

@HiltViewModel
class SamplePaperViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val papers = dao.getSamplePapers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(p: SamplePaper) = viewModelScope.launch { dao.saveSamplePaper(p) }
    fun delete(p: SamplePaper) = viewModelScope.launch { dao.deleteSamplePaper(p) }
}

@HiltViewModel
class PWBatchViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val batches = dao.getPWBatches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveBatch(b: PWBatch) = viewModelScope.launch { dao.savePWBatch(b) }
    fun deleteBatch(b: PWBatch) = viewModelScope.launch { dao.deletePWBatch(b) }
    fun testsFor(batchId: String) = dao.getPWTests(batchId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveTest(t: PWTest) = viewModelScope.launch { dao.savePWTest(t) }
    fun deleteTest(t: PWTest) = viewModelScope.launch { dao.deletePWTest(t) }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val dayEntries = dao.getDayPlannerEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val weekEntries = dao.getWeekPlannerEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val monthEntries = dao.getMonthPlannerEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val yearEntries = dao.getYearPlannerEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveDay(e: DayPlannerEntry) = viewModelScope.launch { dao.saveDayPlannerEntry(e) }
    fun saveWeek(e: WeekPlannerEntry) = viewModelScope.launch { dao.saveWeekPlannerEntry(e) }
    fun saveMonth(e: MonthPlannerEntry) = viewModelScope.launch { dao.saveMonthPlannerEntry(e) }
    fun saveYear(e: YearPlannerEntry) = viewModelScope.launch { dao.saveYearPlannerEntry(e) }
}

@HiltViewModel
class DiaryViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val entries = dao.getDiaryEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(e: DailyDiary) = viewModelScope.launch { dao.saveDiaryEntry(e) }
    fun delete(e: DailyDiary) = viewModelScope.launch { dao.deleteDiaryEntry(e) }
}

@HiltViewModel
class DateEventViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val allEvents = dao.getDateEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun eventsForDate(date: String) = dao.getDateEventsForDate(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(e: DateEvent) = viewModelScope.launch { dao.saveDateEvent(e) }
    fun delete(e: DateEvent) = viewModelScope.launch { dao.deleteDateEvent(e) }
}

@HiltViewModel
class DictionaryViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val neetTerms = dao.getNeetDictionary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val nonNeetTerms = dao.getNonNeetDictionary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveNeet(d: DictionaryNeet) = viewModelScope.launch { dao.saveNeetDictionary(d) }
    fun deleteNeet(d: DictionaryNeet) = viewModelScope.launch { dao.deleteNeetDictionary(d) }
    fun saveNonNeet(d: DictionaryNonNeet) = viewModelScope.launch { dao.saveNonNeetDictionary(d) }
    fun deleteNonNeet(d: DictionaryNonNeet) = viewModelScope.launch { dao.deleteNonNeetDictionary(d) }
}

@HiltViewModel
class MnemonicViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val mnemonics = dao.getMnemonics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(m: Mnemonic) = viewModelScope.launch { dao.saveMnemonic(m) }
    fun delete(m: Mnemonic) = viewModelScope.launch { dao.deleteMnemonic(m) }
}

@HiltViewModel
class DiagramViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    fun diagramsFor(subject: String) = dao.getDiagrams(subject).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(d: Diagram) = viewModelScope.launch { dao.saveDiagram(d) }
    fun delete(d: Diagram) = viewModelScope.launch { dao.deleteDiagram(d) }
}

@HiltViewModel
class ShortNoteViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    fun notesFor(subject: String) = dao.getChapterShortNotes(subject).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(n: ChapterShortNote) = viewModelScope.launch { dao.saveChapterShortNote(n) }
    fun delete(n: ChapterShortNote) = viewModelScope.launch { dao.deleteChapterShortNote(n) }
}

@HiltViewModel
class DayWasteViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val entries = dao.getDayWasteEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(d: DayWaste) = viewModelScope.launch { dao.saveDayWaste(d) }
    fun delete(d: DayWaste) = viewModelScope.launch { dao.deleteDayWaste(d) }
}

@HiltViewModel
class NeetSequenceViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val sequence = dao.getNeetSequence().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sequencePdf = dao.getNeetSequencePdf().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(s: NeetSequence) = viewModelScope.launch { dao.saveNeetSequence(s) }
    fun delete(s: NeetSequence) = viewModelScope.launch { dao.deleteNeetSequence(s) }
    fun updateStatus(id: String, status: Status) = viewModelScope.launch { dao.updateNeetSequenceStatus(id, status) }
    fun saveSequencePdf(uri: String) = viewModelScope.launch { dao.saveNeetSequencePdf(NeetSequencePdf(fileUri = uri)) }
    fun clearSequencePdf() = viewModelScope.launch { dao.saveNeetSequencePdf(NeetSequencePdf(fileUri = "")) }
}

@HiltViewModel
class SubjectNoteViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    fun noteFor(subject: String) = dao.getSubjectShortNote(subject).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(n: SubjectShortNote) = viewModelScope.launch { dao.saveSubjectShortNote(n) }
}

@HiltViewModel
class LackPointViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val points = dao.getLackPoints().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(p: LackPoint) = viewModelScope.launch { dao.saveLackPoint(p) }
    fun delete(p: LackPoint) = viewModelScope.launch { dao.deleteLackPoint(p) }
}

@HiltViewModel
class SyllabusViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val syllabus = dao.getSyllabus().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(s: NEETSyllabus) = viewModelScope.launch { dao.saveSyllabus(s) }
}

// ── Backup / Restore ──────────────────────────────────────────────────────────

enum class BackupState { IDLE, RUNNING, SUCCESS, ERROR }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val db: NEETDatabase
) : ViewModel() {

    private val _state   = MutableStateFlow(BackupState.IDLE)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    fun createBackup(context: Context, folderUri: Uri) {
        viewModelScope.launch {
            _state.value   = BackupState.RUNNING
            _message.value = "Creating backup…"
            val result = BackupManager.createBackup(context, db, folderUri)
            if (result.isSuccess) {
                _state.value   = BackupState.SUCCESS
                _message.value = "Backup saved: ${result.getOrNull()}"
            } else {
                _state.value   = BackupState.ERROR
                _message.value = "Backup failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun restoreBackup(context: Context, fileUri: Uri) {
        viewModelScope.launch {
            _state.value   = BackupState.RUNNING
            _message.value = "Restoring backup…"
            val result = BackupManager.restoreBackup(context, db, fileUri)
            if (result.isSuccess) {
                _state.value   = BackupState.SUCCESS
                _message.value = "Restore complete — ${result.getOrNull()} records merged."
            } else {
                _state.value   = BackupState.ERROR
                _message.value = "Restore failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun resetState() {
        _state.value   = BackupState.IDLE
        _message.value = ""
    }
}

@HiltViewModel
class HomeCountViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {
    val diaryCount   = dao.countDiaryEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val eventCount   = dao.countEventDates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val dictCount    = dao.countNeetDict().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mnemonicCount = dao.countMnemonics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val diagramCount = dao.countDiagrams().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val chapterNoteCount = dao.countChapterNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val dayWasteCount = dao.countDayWaste().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val sequenceCount = dao.countNeetSequence().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val lackCount    = dao.countLackPoints().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Assets aggregation (notebooks + books + PYQ + tests + sample papers + PW batches)
    val assetsCount  = combine(
        dao.countNotebooks(), dao.countBooks(), dao.countPYQSources(),
        dao.countTestPapers(), dao.countSamplePapers(), dao.countPWBatches()
    ) { arr -> arr.sum() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
