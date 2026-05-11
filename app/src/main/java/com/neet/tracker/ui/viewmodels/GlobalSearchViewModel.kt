package com.neet.tracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neet.tracker.data.database.NEETDao
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ─── Search Category ──────────────────────────────────────────────────────────

enum class SearchCategory(val label: String) {
    NOTEBOOK_CHAPTER("Chapter"),
    BOOK("Book"),
    PYQ("PYQ"),
    TEST("Test"),
    SAMPLE("Sample Paper"),
    PW("PW Batch"),
    DIARY("Diary"),
    EVENT("Event"),
    DICT_NEET("NEET Term"),
    DICT_WORD("Word"),
    MNEMONIC("Mnemonic"),
    DIAGRAM("Diagram"),
    CHAPTER_NOTE("Chapter Note"),
    DAY_WASTE("Day Waste"),
    NEET_SEQUENCE("NEET Seq"),
    LACK_POINT("Lack Point"),
    ERROR("Error"),
    REVISION("Revision")
}

// ─── Search Result ────────────────────────────────────────────────────────────

data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: SearchCategory,
    val route: String,
    val matchSnippet: String = ""
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun onQueryChange(q: String) { _query.value = q }
    fun clearQuery() { _query.value = "" }

    val results: StateFlow<List<SearchResult>> = _query
        .debounce(180)
        .flatMapLatest { q ->
            if (q.length < 2) return@flatMapLatest flowOf(emptyList())
            searchAll(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun searchAll(q: String): Flow<List<SearchResult>> {
        val lq = q.lowercase()

        val group1 = combine(
            dao.getAllNotebookChapters(),
            dao.getBooks(),
            dao.getPYQChapterwiseSources(),
            dao.getPYQYearwiseSources()
        ) { chapters, books, pyqChapSrcs, pyqYearSrcs ->
            val r = mutableListOf<SearchResult>()
            chapters.filter { lq in it.name.lowercase() || lq in it.specifications.lowercase() || lq in it.remark.lowercase() || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.name, it.remark.take(60).ifBlank { it.specifications.take(60) }, SearchCategory.NOTEBOOK_CHAPTER, Routes.NOTEBOOKS, it.name)) }
            books.filter { lq in it.name.lowercase() || lq in it.remark.lowercase() || lq in it.subject.name.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.name, it.subject.name + if (it.remark.isNotBlank()) " · ${it.remark.take(40)}" else "", SearchCategory.BOOK, Routes.BOOKS, it.name)) }
            pyqChapSrcs.filter { lq in it.name.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.name, "PYQ Chapterwise", SearchCategory.PYQ, Routes.PYQ_CHAPTERWISE, it.name)) }
            pyqYearSrcs.filter { lq in it.name.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.name, "PYQ Yearwise", SearchCategory.PYQ, Routes.PYQ_YEARWISE, it.name)) }
            r
        }

        val group2 = combine(
            dao.getAllPYQChapters(),
            dao.getAllPYQYears(),
            dao.getAllTestPapers(),
            dao.getSamplePapers()
        ) { pyqChaps, pyqYears, tests, samples ->
            val r = mutableListOf<SearchResult>()
            pyqChaps.filter { lq in it.name.lowercase() || lq in it.remark.lowercase() || lq in it.wrongQuestions.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.name, "PYQ Chapter · ${it.remark.take(40)}", SearchCategory.PYQ, Routes.PYQ_CHAPTERWISE, it.name)) }
            pyqYears.filter { lq in it.year.lowercase() || lq in it.remark.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.year, "PYQ Year · ${it.remark.take(40)}", SearchCategory.PYQ, Routes.PYQ_YEARWISE, it.year)) }
            tests.filter { lq in it.name.lowercase() || lq in it.topicsAsked.lowercase() || lq in it.remark.lowercase() || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.name, "${it.type} · ${it.topicsAsked.take(50)}", SearchCategory.TEST, Routes.TEST_PAPERS, it.name)) }
            samples.filter { lq in it.name.lowercase() || lq in it.remark.lowercase() || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.name, it.remark.take(60), SearchCategory.SAMPLE, Routes.SAMPLE_PAPERS, it.name)) }
            r
        }

        val group3 = combine(
            dao.getPWBatches(),
            dao.getDiaryEntries(),
            dao.getDateEvents(),
            dao.getNeetDictionary()
        ) { pwBatches, diaries, events, neetDict ->
            val r = mutableListOf<SearchResult>()
            pwBatches.filter { lq in it.name.lowercase() || lq in it.remark.lowercase() || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.name, it.remark.take(60), SearchCategory.PW, Routes.PW_BATCHES, it.name)) }
            diaries.filter { lq in it.nickName.lowercase() || lq in it.content.lowercase() || lq in it.date || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.nickName.ifBlank { it.date }, it.date + " · " + it.content.take(50), SearchCategory.DIARY, Routes.DAILY_DIARY, it.nickName.ifBlank { it.content.take(40) })) }
            events.filter { lq in it.name.lowercase() || lq in it.detail.lowercase() || lq in it.date || lq in it.remark.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.name.ifBlank { it.date }, it.date + if (it.detail.isNotBlank()) " · ${it.detail.take(40)}" else "", SearchCategory.EVENT, Routes.DATE_EVENTS, it.name)) }
            neetDict.filter { lq in it.term.lowercase() || lq in it.definition.lowercase() || lq in it.chapter.lowercase() || lq in it.subject.name.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.term, "${it.subject.name} · ${it.chapter}", SearchCategory.DICT_NEET, Routes.DICTIONARY_NEET, it.term)) }
            r
        }

        val group4 = combine(
            dao.getNonNeetDictionary(),
            dao.getMnemonics(),
            dao.getAllDiagrams(),
            dao.getAllChapterShortNotes()
        ) { words, mnemonics, diagrams, notes ->
            val r = mutableListOf<SearchResult>()
            words.filter { lq in it.word.lowercase() || lq in it.meaning.lowercase() || lq in it.example.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.word, it.meaning.take(60), SearchCategory.DICT_WORD, Routes.DICTIONARY_NON_NEET, it.word)) }
            mnemonics.filter { lq in it.name.lowercase() || lq in it.description.lowercase() || lq in it.chapter.lowercase() || lq in it.subject.name.lowercase() || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.name, "${it.subject.name} · ${it.chapter}", SearchCategory.MNEMONIC, Routes.MNEMONICS, it.name)) }
            diagrams.filter { lq in it.chapter.lowercase() || lq in it.subject.lowercase() || lq in it.notes.lowercase() || it.labels.any { l -> lq in l.lowercase() } }
                .forEach { r.add(SearchResult(it.id, it.chapter, it.subject, SearchCategory.DIAGRAM, Routes.DIAGRAMS, it.chapter)) }
            notes.filter { lq in it.chapter.lowercase() || lq in it.subject.name.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.chapter, it.subject.name, SearchCategory.CHAPTER_NOTE, Routes.CHAPTER_SHORT_NOTES, it.chapter)) }
            r
        }

        val group5 = combine(
            dao.getNeetSequence(),
            dao.getLackPoints(),
            dao.getErrorEntries(),
            dao.getRevisionItems(),
            dao.getDayWasteEntries()
        ) { sequences, lacks, errors, revisions, dayWastes ->
            val r = mutableListOf<SearchResult>()
            sequences.filter { lq in it.chapterName.lowercase() || lq in it.subject.name.lowercase() || lq in it.remark.lowercase() || it.tags.any { t -> lq in t.lowercase() } }
                .forEach { r.add(SearchResult(it.id, "#${it.serialNo} ${it.chapterName}", it.subject.name, SearchCategory.NEET_SEQUENCE, Routes.NEET_SEQUENCE, it.chapterName)) }
            lacks.filter { lq in it.point.lowercase() || lq in it.solution.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.point, it.solution.take(60), SearchCategory.LACK_POINT, Routes.LACK_POINTS, it.point)) }
            errors.filter { lq in it.description.lowercase() || lq in it.chapter.lowercase() || lq in it.explanation.lowercase() || it.tags.any { t -> lq in t.lowercase() } || lq in it.correctAnswer.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.description.take(60), "${it.subject.name} · ${it.chapter}", SearchCategory.ERROR, Routes.ERROR_NOTEBOOK, it.description.take(60))) }
            revisions.filter { lq in it.title.lowercase() || lq in it.sourceName.lowercase() || lq in it.notes.lowercase() || lq in it.subject.name.lowercase() }
                .forEach { r.add(SearchResult(it.id, it.title, "${it.subject.name} · ${it.type.name}", SearchCategory.REVISION, Routes.REVISION_SCHEDULER, it.title)) }
            dayWastes.filter { lq in it.reason.lowercase() || lq in it.recoverTip.lowercase() || lq in it.date }
                .forEach { r.add(SearchResult(it.id, it.date, it.reason.take(60), SearchCategory.DAY_WASTE, Routes.DAY_WASTE, it.reason)) }
            r
        }

        return combine(group1, group2, group3, group4, group5) { g1, g2, g3, g4, g5 ->
            (g1 + g2 + g3 + g4 + g5).sortedWith(compareBy({ it.category.ordinal }, { it.title }))
        }
    }
}
