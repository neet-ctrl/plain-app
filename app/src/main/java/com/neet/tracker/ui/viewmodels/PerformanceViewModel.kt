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
import kotlin.math.sqrt

// ─── Data models ──────────────────────────────────────────────────────────────

enum class ScoreSource(val label: String, val shortLabel: String) {
    ONLINE_TEST("Online Test", "Online"),
    OFFLINE_TEST("Offline Test", "Offline"),
    SAMPLE_PAPER("Sample Paper", "Sample"),
    PW_TEST("PW Batch Test", "PW")
}

data class ScorePoint(
    val id: String,
    val label: String,
    val score: Float,
    val maxScore: Float,
    val percentage: Float,
    val source: ScoreSource,
    val subject: String = "",
    val dateMs: Long,
    val dateLabel: String
)

enum class PerfTimeFilter(val label: String, val days: Int) {
    DAYS_7("7 Days", 7),
    DAYS_30("30 Days", 30),
    DAYS_90("90 Days", 90),
    ALL("All Time", Int.MAX_VALUE)
}

enum class PerfSourceFilter(val label: String) {
    ALL("All"),
    ONLINE("Online"),
    OFFLINE("Offline"),
    SAMPLE("Sample"),
    PW("PW Test")
}

data class PerformanceUiState(
    val allPoints: List<ScorePoint> = emptyList(),
    val filteredPoints: List<ScorePoint> = emptyList(),
    val subjectStats: Map<String, Float> = emptyMap(),
    val sourceAverages: Map<ScoreSource, Float> = emptyMap(),
    val targetScore: Float = 700f,
    val targetMax: Float = 720f,
    val timeFilter: PerfTimeFilter = PerfTimeFilter.ALL,
    val sourceFilter: PerfSourceFilter = PerfSourceFilter.ALL,
    val isLoading: Boolean = true,
    val personalBest: Float = 0f,
    val averageScore: Float = 0f,
    val latestScore: Float? = null,
    val trendDelta: Float = 0f,
    val consistencyScore: Float = 0f,
    val aiInsights: List<String> = emptyList(),
    val totalTests: Int = 0
)

// ─── Offline AI engine ────────────────────────────────────────────────────────

object PerformanceAI {

    fun generateInsights(
        points: List<ScorePoint>,
        target: Float,
        targetMax: Float,
        subjectStats: Map<String, Float>,
        trendDelta: Float,
        consistencyScore: Float
    ): List<String> {
        if (points.isEmpty()) {
            return listOf(
                "No scored tests found yet. Add marks to your Test Papers, Sample Papers, or PW Tests to unlock full analytics.",
                "Tip: Enter marks in 'X/Y' format (e.g. 650/720) for accurate percentage tracking."
            )
        }

        val insights = mutableListOf<String>()
        val avgPct  = points.map { it.percentage }.average().toFloat()
        val targetPct = if (targetMax > 0) (target / targetMax * 100) else 97.2f
        val gap = targetPct - avgPct

        // Trend
        if (points.size >= 4) {
            insights += when {
                trendDelta >  4f -> "Improving! Your last 3 tests averaged ${(avgPct + trendDelta / 2).toInt()}% — up ${trendDelta.toInt()}% from earlier tests. Stay consistent."
                trendDelta < -4f -> "Slight decline detected (${trendDelta.toInt()}% drop). Review recent mistakes and re-attempt weaker topics."
                else             -> "Performance is steady at ~${avgPct.toInt()}%. Push for a breakthrough in your next session."
            }
        }

        // Target gap
        insights += when {
            gap <= 0  -> "You're ON TARGET! Average ${avgPct.toInt()}% meets your goal of ${target.toInt()}/${targetMax.toInt()}. Maintain this level."
            gap <= 5  -> "Almost there — only ${gap.toInt()}% from target (${target.toInt()}/${targetMax.toInt()}). One strong session can close this."
            gap <= 15 -> "${gap.toInt()}% gap from your target. Focus on high-yield chapters in your weak subject to close it fast."
            else      -> "${gap.toInt()}% below target. Increase test frequency and spend extra time analysing wrong answers."
        }

        // Subject weakness
        if (subjectStats.size >= 2) {
            val weakest   = subjectStats.minByOrNull { it.value }
            val strongest = subjectStats.maxByOrNull { it.value }
            if (weakest != null && strongest != null && weakest.key != strongest.key) {
                val diff = strongest.value - weakest.value
                insights += "Subject gap: ${weakest.key} (${weakest.value.toInt()}%) trails ${strongest.key} (${strongest.value.toInt()}%) by ${diff.toInt()}%. Balance study time to maximise total score."
            }
        }

        // Consistency
        if (points.size >= 3) {
            insights += when {
                consistencyScore < 5f  -> "Highly consistent (±${consistencyScore.toInt()}%). Reliable performance is a huge advantage on exam day."
                consistencyScore < 12f -> "Moderate variation (±${consistencyScore.toInt()}%). Work on maintaining focus across different test conditions."
                else                   -> "High score variance (±${consistencyScore.toInt()}%). Identify what makes your bad days bad — sleep, prep time, fatigue?"
            }
        }

        // Personal best encouragement
        val best = points.maxOfOrNull { it.percentage } ?: 0f
        if (best > avgPct + 5) {
            insights += "Personal best: ${best.toInt()}%. You've shown you can do it — replicate those conditions in every test."
        }

        return insights
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class PerformanceViewModel @Inject constructor(private val dao: NEETDao) : ViewModel() {

    private val _ui = MutableStateFlow(PerformanceUiState())
    val ui: StateFlow<PerformanceUiState> = _ui.asStateFlow()

    private val dateLabelFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    init { loadData() }

    fun setTimeFilter(f: PerfTimeFilter)     { _ui.update { it.copy(timeFilter = f) };  reFilter() }
    fun setSourceFilter(f: PerfSourceFilter) { _ui.update { it.copy(sourceFilter = f) }; reFilter() }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                dao.getAllTestPapers(),
                dao.getSamplePapers(),
                dao.getAllPWTests(),
                dao.getProfile()
            ) { tests, samples, pwTests, profile ->
                Quadruple(tests, samples, pwTests, profile)
            }.collect { (tests, samples, pwTests, profile) ->
                val points = mutableListOf<ScorePoint>()

                // Parse test papers
                tests.forEach { t ->
                    parseMarks(t.marksObtained)?.let { (score, max) ->
                        val pct = (score / max * 100f).coerceIn(0f, 100f)
                        points.add(ScorePoint(
                            id         = t.id,
                            label      = t.name.take(18).ifBlank { "Test" },
                            score      = score,
                            maxScore   = max,
                            percentage = pct,
                            source     = if (t.type == "OFFLINE") ScoreSource.OFFLINE_TEST else ScoreSource.ONLINE_TEST,
                            dateMs     = t.createdAt,
                            dateLabel  = dateLabelFmt.format(Date(t.createdAt))
                        ))
                    }
                }

                // Parse sample papers
                samples.forEach { s ->
                    parseMarks(s.marksObtained)?.let { (score, max) ->
                        val pct = (score / max * 100f).coerceIn(0f, 100f)
                        points.add(ScorePoint(
                            id         = s.id,
                            label      = s.name.take(18).ifBlank { "Sample" },
                            score      = score,
                            maxScore   = max,
                            percentage = pct,
                            source     = ScoreSource.SAMPLE_PAPER,
                            dateMs     = s.createdAt,
                            dateLabel  = dateLabelFmt.format(Date(s.createdAt))
                        ))
                    }
                }

                // Parse PW tests
                pwTests.forEach { pw ->
                    parseMarks(pw.marksObtained)?.let { (score, max) ->
                        val pct = (score / max * 100f).coerceIn(0f, 100f)
                        points.add(ScorePoint(
                            id         = pw.id,
                            label      = pw.name.take(18).ifBlank { "PW" },
                            score      = score,
                            maxScore   = max,
                            percentage = pct,
                            source     = ScoreSource.PW_TEST,
                            subject    = pw.subject,
                            dateMs     = pw.createdAt,
                            dateLabel  = dateLabelFmt.format(Date(pw.createdAt))
                        ))
                    }
                }

                val sorted = points.sortedBy { it.dateMs }

                // Target from profile
                val (tScore, tMax) = parseMarks(profile?.targetScore ?: "700/720")
                    ?: Pair(700f, 720f)

                // Subject stats from PW tests only
                val subjectStats = sorted
                    .filter { it.source == ScoreSource.PW_TEST && it.subject.isNotBlank() }
                    .groupBy { it.subject.uppercase().trim() }
                    .mapValues { (_, pts) -> pts.map { it.percentage }.average().toFloat() }

                // Source averages
                val sourceAvg = ScoreSource.values().associateWith { src ->
                    val pts = sorted.filter { it.source == src }
                    if (pts.isEmpty()) -1f else pts.map { it.percentage }.average().toFloat()
                }

                // Aggregate stats
                val personalBest = sorted.maxOfOrNull { it.percentage } ?: 0f
                val averageScore = if (sorted.isEmpty()) 0f
                                   else sorted.map { it.percentage }.average().toFloat()
                val latestScore  = sorted.lastOrNull()?.percentage

                // Trend: last 3 avg vs previous 3 avg
                val trendDelta = if (sorted.size >= 6) {
                    val recent  = sorted.takeLast(3).map { it.percentage }.average().toFloat()
                    val earlier = sorted.dropLast(3).takeLast(3).map { it.percentage }.average().toFloat()
                    recent - earlier
                } else 0f

                // Consistency (std deviation)
                val consistency = if (sorted.size >= 2) {
                    val mean = sorted.map { it.percentage }.average()
                    sqrt(sorted.sumOf { ((it.percentage - mean) * (it.percentage - mean)).toDouble() } / sorted.size).toFloat()
                } else 0f

                val insights = PerformanceAI.generateInsights(sorted, tScore, tMax, subjectStats, trendDelta, consistency)

                _ui.update { prev ->
                    prev.copy(
                        allPoints        = sorted,
                        filteredPoints   = applyFilters(sorted, prev.timeFilter, prev.sourceFilter),
                        subjectStats     = subjectStats,
                        sourceAverages   = sourceAvg,
                        targetScore      = tScore,
                        targetMax        = tMax,
                        isLoading        = false,
                        personalBest     = personalBest,
                        averageScore     = averageScore,
                        latestScore      = latestScore,
                        trendDelta       = trendDelta,
                        consistencyScore = consistency,
                        aiInsights       = insights,
                        totalTests       = sorted.size
                    )
                }
            }
        }
    }

    private fun reFilter() {
        _ui.update { it.copy(filteredPoints = applyFilters(it.allPoints, it.timeFilter, it.sourceFilter)) }
    }

    private fun applyFilters(
        all: List<ScorePoint>,
        time: PerfTimeFilter,
        source: PerfSourceFilter
    ): List<ScorePoint> {
        val cutoff = if (time == PerfTimeFilter.ALL) 0L
                     else System.currentTimeMillis() - time.days.toLong() * 86_400_000L
        return all.filter { pt ->
            val timeOk   = pt.dateMs >= cutoff
            val sourceOk = when (source) {
                PerfSourceFilter.ALL     -> true
                PerfSourceFilter.ONLINE  -> pt.source == ScoreSource.ONLINE_TEST
                PerfSourceFilter.OFFLINE -> pt.source == ScoreSource.OFFLINE_TEST
                PerfSourceFilter.SAMPLE  -> pt.source == ScoreSource.SAMPLE_PAPER
                PerfSourceFilter.PW      -> pt.source == ScoreSource.PW_TEST
            }
            timeOk && sourceOk
        }
    }

    // ── Parse "X/Y" or "X" → (score, max) ────────────────────────────────────
    private fun parseMarks(raw: String): Pair<Float, Float>? {
        val t = raw.trim()
        if (t.isBlank()) return null
        return if (t.contains("/")) {
            val parts   = t.split("/")
            val obtained = parts.getOrNull(0)?.trim()?.toFloatOrNull() ?: return null
            val total    = parts.getOrNull(1)?.trim()?.toFloatOrNull() ?: 720f
            if (obtained < 0 || total <= 0) return null
            Pair(obtained, total)
        } else {
            val obtained = t.toFloatOrNull() ?: return null
            if (obtained < 0) return null
            Pair(obtained, 720f)
        }
    }
}

// Kotlin doesn't have a Quadruple, so we define one
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
