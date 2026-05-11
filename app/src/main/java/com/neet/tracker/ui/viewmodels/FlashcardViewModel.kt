package com.neet.tracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neet.tracker.data.database.NEETDao
import com.neet.tracker.data.models.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

// ─── Session Enums & Data Classes ─────────────────────────────────────────────

enum class FlashcardSource(val label: String) {
    NEET_TERMS("NEET Terms"),
    NON_NEET_WORDS("Words"),
    MNEMONICS("Mnemonics"),
    MIXED("Mixed"),
    DUE_ONLY("Due Only")
}

enum class FlashcardMode(val label: String) {
    FLIP_CARD("Flip Card"),
    MULTIPLE_CHOICE("MCQ"),
    TYPE_ANSWER("Type Answer")
}

enum class FlashcardPhase { SETUP, REVIEWING, RESULTS }

data class FlashcardCard(
    val id: String,
    val front: String,
    val back: String,
    val frontLabel: String,
    val backExtra: String = "",
    val subject: String = "",
    val chapter: String = "",
    val type: FlashcardType,
    val tags: List<String> = emptyList(),
    val progress: FlashcardProgress? = null
)

data class SessionCardResult(
    val cardId: String,
    val front: String,
    val quality: Int,
    val responseTimeMs: Long
)

data class FlashcardUiState(
    val phase: FlashcardPhase = FlashcardPhase.SETUP,
    val source: FlashcardSource = FlashcardSource.NEET_TERMS,
    val mode: FlashcardMode = FlashcardMode.FLIP_CARD,
    val sessionSize: Int = 10,
    val subjectFilter: String = "ALL",
    val sessionCards: List<FlashcardCard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val hintVisible: Boolean = false,
    val hintContent: String = "",
    val hintLevel: Int = 0,
    val memoryHook: String = "",
    val memoryHookVisible: Boolean = false,
    val mcqOptions: List<String> = emptyList(),
    val mcqSelected: String? = null,
    val typeInput: String = "",
    val typeChecked: Boolean = false,
    val typeCorrect: Boolean? = null,
    val sessionResults: List<SessionCardResult> = emptyList(),
    val sessionStartTime: Long = 0L,
    val nextDueCount: Int = 0,
    val isLoading: Boolean = false,
    // ── Advanced AI state ────────────────────────────────────────────────────
    val requeuedCardIds: Set<String> = emptySet(),
    val consecutiveFails: Map<String, Int> = emptyMap(),
    val autoHintTriggered: Boolean = false,
    val streakBest: Int = 0,
    val totalRequeues: Int = 0
)

// ─── AI Engine (offline, no API key needed) ───────────────────────────────────

object FlashcardAI {

    fun generateHint(front: String, back: String, level: Int): String {
        val words = front.trim().split(" ")
        return when (level) {
            0 -> "First letter(s): " + words.joinToString("  ") { w ->
                "${w.first().uppercaseChar()}${"_".repeat((w.length - 1).coerceAtLeast(1))}"
            }
            1 -> "Contains ${front.length} characters in ${words.size} word${if (words.size != 1) "s" else ""}"
            2 -> {
                val bw = back.trim().split(" ")
                "Definition starts: \"${bw.take((bw.size / 4).coerceAtLeast(3)).joinToString(" ")}…\""
            }
            3 -> back.take(back.length / 2).trimEnd() + "…"
            else -> back
        }
    }

    fun generateMemoryHook(front: String, back: String): String {
        val lower = front.lowercase()
        if (front.length in 2..6 && front.all { it.isUpperCase() || it == '-' }) {
            return "Acronym: trace each letter to its key word in the definition"
        }
        val bioRoots = mapOf(
            "photo" to "light", "bio" to "life", "syn" to "together/with",
            "chromo" to "color/chromosome", "cyto" to "cell", "phyll" to "leaf",
            "auto" to "self", "hetero" to "different", "phago" to "eating/engulfing",
            "lysis" to "breaking down", "genesis" to "origin/formation",
            "troph" to "nutrition/feeding", "endo" to "inside", "exo" to "outside",
            "hyper" to "excess/above", "hypo" to "deficient/below",
            "proto" to "first/primitive", "mono" to "single", "poly" to "many",
            "micro" to "small", "macro" to "large", "inter" to "between",
            "intra" to "within", "trans" to "across", "mito" to "thread (mitochondria)",
            "chloro" to "green", "derma" to "skin", "osteo" to "bone",
            "cardio" to "heart", "neuro" to "nerve", "hemo" to "blood",
            "angio" to "vessel", "phyte" to "plant", "zoo" to "animal",
            "sporo" to "spore", "gameto" to "gamete/sex cell"
        )
        for ((root, meaning) in bioRoots) {
            if (lower.contains(root)) {
                return "Root '${root}' = '$meaning' — anchor the definition to this Greek/Latin root!"
            }
        }
        val syllables = front.replace(Regex("[^aeiouAEIOU]"), "").length.coerceAtLeast(1)
        val preview = back.split(" ").take(4).joinToString(" ")
        return "Visualize '${front.first().uppercaseChar()}…' ($syllables syllable${if (syllables != 1) "s" else ""}): $preview…"
    }

    fun predictMasteryDays(progress: FlashcardProgress?): String {
        if (progress == null) return "New card — first review will set the schedule"
        val ef = progress.easeFactor
        val interval = progress.intervalDays
        val reps = progress.repetitions
        return when {
            reps == 0 -> "Needs immediate review"
            interval >= 21 -> "Nearly mastered (reviewing every ${interval}d)"
            else -> {
                val projectedDays = generateSequence(interval.toFloat()) { it * ef }
                    .take(10).indexOfFirst { it >= 21 }
                if (projectedDays == -1) "~${10 * interval}d to mastery"
                else "~${projectedDays * interval}d to mastery at current pace"
            }
        }
    }

    fun generateMcqOptions(correct: String, pool: List<String>): List<String> {
        val others = pool.filter { it != correct && it.isNotBlank() }
        val sorted = others.sortedBy { abs(it.length - correct.length) }
        val wrongs = sorted.take(3).toMutableList()
        while (wrongs.size < 3) wrongs.add("None of the above")
        return (wrongs + correct).shuffled()
    }

    fun fuzzyMatch(input: String, correct: String): Boolean {
        val a = input.trim().lowercase()
        val b = correct.trim().lowercase()
        if (a == b) return true
        if (a.length < 3 || b.length < 3) return a == b
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in dp.indices) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length] <= 2
    }

    // ── Advanced AI: Generate context-aware session insight ──────────────────

    fun generateSessionInsight(
        accuracy: Int,
        avgMs: Long,
        missed: Int,
        hard: Int,
        requeues: Int,
        nextDueCount: Int
    ): Triple<String, String, String> {
        val i1 = when {
            accuracy >= 90 -> "Excellent retention! Your memory pathways are strong. Interval will extend significantly."
            accuracy >= 75 -> "Good session! Focus the next round on the ${missed + hard} harder cards."
            accuracy >= 55 -> "Consistent daily reviews will rapidly close the gap. Keep going!"
            requeues > 0   -> "$requeues cards were re-queued — this is normal. Repetition is learning."
            else           -> "These cards need more attention. Shorter, more frequent sessions accelerate recall."
        }
        val i2 = "Avg response: ${avgMs / 1000}s/card — " +
            if (avgMs < 4000) "excellent recall speed!" else "try to respond faster to build automaticity."
        val i3 = if (nextDueCount > 0)
            "$nextDueCount cards due tomorrow — schedule a ~${(nextDueCount * 25 / 60).coerceAtLeast(1)}min session."
        else "No cards due tomorrow — you're ahead of schedule!"
        return Triple(i1, i2, i3)
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class FlashcardViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {

    private val _ui = MutableStateFlow(FlashcardUiState())
    val ui: StateFlow<FlashcardUiState> = _ui

    val neetTermCount  = dao.countNeetDictionary().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val wordCount      = dao.countNonNeetDictionary().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val mnemonicCount  = dao.countMnemonics().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val dueCount       = dao.countDueFlashcards(LocalDate.now().toString())
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Config setters
    fun onSourceChange(s: FlashcardSource)  = _ui.update { it.copy(source = s) }
    fun onModeChange(m: FlashcardMode)      = _ui.update { it.copy(mode = m) }
    fun onSizeChange(n: Int)                = _ui.update { it.copy(sessionSize = n) }
    fun onSubjectChange(s: String)          = _ui.update { it.copy(subjectFilter = s) }
    fun onTypeInputChange(s: String)        = _ui.update { it.copy(typeInput = s) }
    fun goToSetup()                         = _ui.update { FlashcardUiState() }

    // Progress cache: cardId → FlashcardProgress
    private val progressCache = mutableMapOf<String, FlashcardProgress>()
    // Full back-text pool for MCQ
    private var fullPoolBacks = listOf<String>()
    // Track card start time
    private var cardStartMs = 0L

    // ── Session start ──────────────────────────────────────────────────────────

    fun startSession() {
        val cfg = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }

            val allProgress = dao.getAllFlashcardProgress().first()
            progressCache.clear()
            allProgress.forEach { p -> progressCache[p.cardId] = p }

            val today = LocalDate.now().toString()
            val pool = mutableListOf<FlashcardCard>()

            if (cfg.source in listOf(FlashcardSource.NEET_TERMS, FlashcardSource.MIXED, FlashcardSource.DUE_ONLY)) {
                dao.getNeetDictionary().first().forEach { t ->
                    pool.add(FlashcardCard(
                        id = t.id, front = t.term, back = t.definition,
                        frontLabel = "NEET Term", backExtra = t.chapter,
                        subject = t.subject.name, chapter = t.chapter,
                        type = FlashcardType.NEET_TERM, tags = t.tags,
                        progress = progressCache[t.id]
                    ))
                }
            }
            if (cfg.source in listOf(FlashcardSource.NON_NEET_WORDS, FlashcardSource.MIXED, FlashcardSource.DUE_ONLY)) {
                dao.getNonNeetDictionary().first().forEach { w ->
                    pool.add(FlashcardCard(
                        id = w.id, front = w.word, back = w.meaning,
                        frontLabel = "Word", backExtra = w.example,
                        subject = "GENERAL", type = FlashcardType.NON_NEET_WORD,
                        progress = progressCache[w.id]
                    ))
                }
            }
            if (cfg.source in listOf(FlashcardSource.MNEMONICS, FlashcardSource.MIXED, FlashcardSource.DUE_ONLY)) {
                dao.getMnemonics().first().forEach { m ->
                    pool.add(FlashcardCard(
                        id = m.id, front = m.name, back = m.description,
                        frontLabel = "Mnemonic", backExtra = m.chapter,
                        subject = m.subject.name, chapter = m.chapter,
                        type = FlashcardType.MNEMONIC, tags = m.tags,
                        progress = progressCache[m.id]
                    ))
                }
            }

            val filtered = if (cfg.subjectFilter == "ALL") pool
                           else pool.filter { it.subject.uppercase() == cfg.subjectFilter }

            val sorted = filtered.sortedWith(
                compareByDescending<FlashcardCard> {
                    val due = it.progress?.dueDate
                    due != null && due.isNotEmpty() && due <= today
                }.thenBy { it.progress?.easeFactor ?: 3f }
                 .thenBy { it.progress?.totalReviews ?: Int.MAX_VALUE }
            )

            val sessionCards = when (cfg.source) {
                FlashcardSource.DUE_ONLY -> sorted.filter { card ->
                    card.progress?.dueDate?.let { it.isNotEmpty() && it <= today } ?: false
                }.take(if (cfg.sessionSize == -1) Int.MAX_VALUE else cfg.sessionSize)
                else -> sorted.take(if (cfg.sessionSize == -1) Int.MAX_VALUE else cfg.sessionSize)
            }

            if (sessionCards.isEmpty()) {
                _ui.update { it.copy(isLoading = false) }
                return@launch
            }

            fullPoolBacks = filtered.map { it.back }.filter { it.isNotBlank() }

            val firstCard = sessionCards.first()
            val mcqOptions = if (cfg.mode == FlashcardMode.MULTIPLE_CHOICE)
                FlashcardAI.generateMcqOptions(firstCard.back, fullPoolBacks)
            else emptyList()

            val dueNextCount = dao.countDueFlashcards(
                LocalDate.now().plusDays(1).toString()
            ).first()

            cardStartMs = System.currentTimeMillis()

            // Auto-hint if card has been historically hard
            val firstCardFails = progressCache[firstCard.id]?.let { p ->
                if (p.totalReviews > 0 && p.correctReviews.toFloat() / p.totalReviews < 0.4f) 1 else 0
            } ?: 0
            val autoHint = firstCardFails > 0
            val autoHintContent = if (autoHint)
                FlashcardAI.generateHint(firstCard.front, firstCard.back, 0)
            else ""

            _ui.update { it.copy(
                phase              = FlashcardPhase.REVIEWING,
                sessionCards       = sessionCards,
                currentIndex       = 0,
                isFlipped          = false,
                isLoading          = false,
                mcqOptions         = mcqOptions,
                mcqSelected        = null,
                typeInput          = "",
                typeChecked        = false,
                typeCorrect        = null,
                hintVisible        = autoHint,
                hintLevel          = if (autoHint) 1 else 0,
                hintContent        = autoHintContent,
                memoryHookVisible  = false,
                memoryHook         = "",
                sessionResults     = emptyList(),
                sessionStartTime   = System.currentTimeMillis(),
                nextDueCount       = dueNextCount,
                requeuedCardIds    = emptySet(),
                consecutiveFails   = emptyMap(),
                autoHintTriggered  = autoHint,
                streakBest         = 0,
                totalRequeues      = 0
            )}
        }
    }

    // ── Flip card (Flip mode) ──────────────────────────────────────────────────

    fun flipCard() = _ui.update { it.copy(isFlipped = !it.isFlipped) }

    // ── Rating (all modes) ─────────────────────────────────────────────────────

    fun submitRating(quality: Int) {          // 0=Again 1=Hard 2=Good 3=Easy
        val state = _ui.value
        val card  = state.sessionCards.getOrNull(state.currentIndex) ?: return
        val ms    = System.currentTimeMillis() - cardStartMs

        // SM-2 update
        val updatedProgress = applySpacedRepetition(progressCache[card.id], quality, card.id, card.type)
        progressCache[card.id] = updatedProgress
        viewModelScope.launch { dao.saveFlashcardProgress(updatedProgress) }

        val result      = SessionCardResult(card.id, card.front, quality, ms)
        val newResults  = state.sessionResults + result

        // Track consecutive fails per card
        val newFails = state.consecutiveFails.toMutableMap()
        if (quality < 2) {
            newFails[card.id] = (newFails[card.id] ?: 0) + 1
        } else {
            newFails.remove(card.id)
        }

        // ── Card requeueing: insert failed card 3 positions later ─────────────
        val newCards = state.sessionCards.toMutableList()
        var newRequeues = state.totalRequeues
        val newRequeuedIds = state.requeuedCardIds.toMutableSet()

        if (quality < 2 && card.id !in state.requeuedCardIds) {
            val reinsertIdx = (state.currentIndex + 3).coerceAtMost(newCards.size)
            newCards.add(reinsertIdx, card)
            newRequeuedIds.add(card.id)
            newRequeues++
        }

        // ── Streak tracking ──────────────────────────────────────────────────
        val currentStreak = newResults.takeLastWhile { it.quality >= 2 }.size
        val bestStreak = maxOf(state.streakBest, currentStreak)

        val nextIdx = state.currentIndex + 1

        if (nextIdx >= newCards.size) {
            _ui.update { it.copy(
                phase           = FlashcardPhase.RESULTS,
                sessionResults  = newResults,
                sessionCards    = newCards,
                consecutiveFails = newFails,
                streakBest      = bestStreak,
                totalRequeues   = newRequeues
            )}
        } else {
            val nextCard = newCards[nextIdx]
            val mcqOptions = if (state.mode == FlashcardMode.MULTIPLE_CHOICE)
                FlashcardAI.generateMcqOptions(nextCard.back, fullPoolBacks)
            else emptyList()

            // Auto-hint for the next card if it's been historically hard
            val nextCardWeakness = progressCache[nextCard.id]?.let { p ->
                p.totalReviews > 0 && p.correctReviews.toFloat() / p.totalReviews < 0.35f
            } ?: false
            // Also auto-hint if this card was just requeued (i.e., the same card coming back)
            val isRequeue = nextCard.id in newRequeuedIds && quality < 2
            val shouldAutoHint = nextCardWeakness || isRequeue
            val autoHintLevel = if (isRequeue) (newFails[nextCard.id] ?: 1).coerceAtMost(3) else 0
            val autoHintContent = if (shouldAutoHint)
                FlashcardAI.generateHint(nextCard.front, nextCard.back, autoHintLevel)
            else ""

            cardStartMs = System.currentTimeMillis()
            _ui.update { it.copy(
                currentIndex     = nextIdx,
                sessionCards     = newCards,
                isFlipped        = false,
                mcqOptions       = mcqOptions,
                mcqSelected      = null,
                typeInput        = "",
                typeChecked      = false,
                typeCorrect      = null,
                hintVisible      = shouldAutoHint,
                hintLevel        = if (shouldAutoHint) autoHintLevel + 1 else 0,
                hintContent      = autoHintContent,
                memoryHookVisible = false,
                sessionResults   = newResults,
                consecutiveFails = newFails,
                requeuedCardIds  = newRequeuedIds,
                streakBest       = bestStreak,
                totalRequeues    = newRequeues,
                autoHintTriggered = shouldAutoHint
            )}
        }
    }

    // ── MCQ answer ────────────────────────────────────────────────────────────

    fun submitMcqAnswer(answer: String) {
        _ui.update { it.copy(
            mcqSelected = answer,
            isFlipped   = true
        )}
    }

    // ── Type answer ───────────────────────────────────────────────────────────

    fun checkTypeAnswer() {
        val state = _ui.value
        val card  = state.sessionCards.getOrNull(state.currentIndex) ?: return
        val correct = FlashcardAI.fuzzyMatch(state.typeInput, card.front)
        _ui.update { it.copy(typeChecked = true, typeCorrect = correct, isFlipped = true) }
    }

    // ── Hints ─────────────────────────────────────────────────────────────────

    fun requestHint() {
        val state = _ui.value
        val card  = state.sessionCards.getOrNull(state.currentIndex) ?: return
        val level = state.hintLevel
        // Escalate hint level faster if the card has been failed in session
        val fails = state.consecutiveFails[card.id] ?: 0
        val effectiveLevel = (level + if (fails >= 2) 1 else 0).coerceAtMost(4)
        val content = FlashcardAI.generateHint(card.front, card.back, effectiveLevel)
        _ui.update { it.copy(
            hintVisible = true,
            hintContent = content,
            hintLevel   = (level + 1).coerceAtMost(4)
        )}
    }

    fun requestMemoryHook() {
        val state = _ui.value
        val card  = state.sessionCards.getOrNull(state.currentIndex) ?: return
        val hook  = FlashcardAI.generateMemoryHook(card.front, card.back)
        _ui.update { it.copy(memoryHook = hook, memoryHookVisible = true) }
    }

    fun dismissHint()       = _ui.update { it.copy(hintVisible = false) }
    fun dismissMemoryHook() = _ui.update { it.copy(memoryHookVisible = false) }

    // ── SM-2 Algorithm ────────────────────────────────────────────────────────

    private fun applySpacedRepetition(
        existing: FlashcardProgress?,
        quality: Int,
        cardId: String,
        cardType: FlashcardType
    ): FlashcardProgress {
        val base = existing ?: FlashcardProgress(cardId = cardId, cardType = cardType)
        val newReps = if (quality < 2) 0 else base.repetitions + 1
        val newEF = (base.easeFactor + (0.1f - (3 - quality) * (0.08f + (3 - quality) * 0.02f)))
            .coerceIn(1.3f, 2.5f)
        val rawInterval = when {
            quality < 2 -> 1
            newReps == 1 -> 1
            newReps == 2 -> 6
            else -> (base.intervalDays * newEF).toInt().coerceAtLeast(1)
        }
        val finalInterval = if (quality == 3)
            (rawInterval * 1.3f).toInt().coerceAtLeast(rawInterval + 1)
        else rawInterval
        val dueDate = LocalDate.now().plusDays(finalInterval.toLong()).toString()
        return base.copy(
            easeFactor     = newEF,
            intervalDays   = finalInterval,
            repetitions    = newReps,
            dueDate        = dueDate,
            totalReviews   = base.totalReviews + 1,
            correctReviews = base.correctReviews + if (quality >= 2) 1 else 0,
            lastReviewed   = System.currentTimeMillis()
        )
    }
}
