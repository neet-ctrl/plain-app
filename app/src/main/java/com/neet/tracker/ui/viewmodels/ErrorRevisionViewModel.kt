package com.neet.tracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neet.tracker.data.database.NEETDao
import com.neet.tracker.data.models.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── Error Notebook ViewModel ──────────────────────────────────────────────────

@HiltViewModel
class ErrorViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {

    val allErrors        = dao.getErrorEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalCount       = dao.countErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pendingCount     = dao.countPendingErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val understoodCount  = dao.countUnderstoodErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val masteredCount    = dao.countMasteredErrors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun save(e: ErrorEntry)   = viewModelScope.launch { dao.saveErrorEntry(e) }
    fun delete(e: ErrorEntry) = viewModelScope.launch { dao.deleteErrorEntry(e) }

    fun setStatus(id: String, status: ErrorStatus) =
        viewModelScope.launch { dao.updateErrorStatus(id, status.name) }

    fun markRevised(id: String) =
        viewModelScope.launch { dao.markErrorRevised(id, System.currentTimeMillis()) }
}

// ── Revision Scheduler ViewModel ──────────────────────────────────────────────

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
fun todayStr(): String = dateFmt.format(Date())
fun dateAfterDays(days: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, days)
    return dateFmt.format(cal.time)
}
fun dateAfterDaysFrom(base: String, days: Int): String {
    return try {
        val d = dateFmt.parse(base) ?: Date()
        val cal = Calendar.getInstance()
        cal.time = d
        cal.add(Calendar.DAY_OF_YEAR, days)
        dateFmt.format(cal.time)
    } catch (_: Exception) { dateAfterDays(days) }
}
fun spacedIntervalForRevNum(revNum: Int): Int = when (revNum) {
    1 -> 1; 2 -> 3; 3 -> 7; 4 -> 14; 5 -> 30; else -> 30
}

@HiltViewModel
class RevisionViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {

    private val today = todayStr()

    val todayRevisions    = dao.getRevisionsByDate(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val overdueRevisions  = dao.getOverdueRevisions(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val doneRevisions     = dao.getDoneRevisions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRevisions      = dao.getRevisionItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todayCount        = dao.countTodayRevisions(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val overdueCount      = dao.countOverdueRevisions(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun getUpcoming(days: Int = 7): Flow<List<RevisionItem>> {
        val end = dateAfterDays(days)
        return dao.getRevisionsBetween(today, end)
    }

    fun save(r: RevisionItem) = viewModelScope.launch { dao.saveRevisionItem(r) }
    fun delete(r: RevisionItem) = viewModelScope.launch { dao.deleteRevisionItem(r) }

    fun markDone(r: RevisionItem) = viewModelScope.launch {
        dao.updateRevisionStatus(r.id, RevisionStatus.DONE.name, System.currentTimeMillis())
        // Auto-create the next spaced revision if spaced repetition is on
        if (r.isSpacedRepetition) {
            val nextRevNum = r.revisionNumber + 1
            val intervalDays = spacedIntervalForRevNum(nextRevNum)
            val nextDate = dateAfterDaysFrom(today, intervalDays)
            val next = r.copy(
                id            = UUID.randomUUID().toString(),
                revisionNumber = nextRevNum,
                scheduledDate = nextDate,
                intervalDays  = intervalDays,
                status        = RevisionStatus.PENDING,
                completedAt   = 0L,
                createdAt     = System.currentTimeMillis()
            )
            dao.saveRevisionItem(next)
        }
    }

    fun markSkipped(id: String) = viewModelScope.launch {
        dao.updateRevisionStatus(id, RevisionStatus.SKIPPED.name, System.currentTimeMillis())
    }

    fun reschedule(id: String, newDate: String) = viewModelScope.launch {
        dao.rescheduleRevision(id, newDate)
    }

    fun rescheduleAllOverdue(overdue: List<RevisionItem>) = viewModelScope.launch {
        overdue.forEach { dao.rescheduleRevision(it.id, today) }
    }
}
