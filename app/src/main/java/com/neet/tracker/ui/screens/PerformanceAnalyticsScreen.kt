package com.neet.tracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.ui.components.SpaceBackground
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.*
import kotlin.math.*

// ══════════════════════════════════════════════════════════════════════════════
//  Performance Analytics Screen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PerformanceAnalyticsScreen(
    navController: NavController,
    vm: PerformanceViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pa")
    val glowPulse by infiniteTransition.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), "glow"
    )

    SpaceBackground {
        if (ui.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan, strokeWidth = 3.dp)
            }
            return@SpaceBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ── Header ───────────────────────────────────────────────────────
            PaHeader(navController, ui, glowPulse)

            // ── Filter chips ─────────────────────────────────────────────────
            PaFilters(ui, vm)

            // ── Summary stat cards ───────────────────────────────────────────
            PaSummaryRow(ui, glowPulse)

            // ── Score Trend Chart ────────────────────────────────────────────
            PaSection(title = "SCORE TREND", icon = Icons.Default.Timeline, color = NeonCyan) {
                if (ui.filteredPoints.isEmpty()) {
                    PaEmptyState("No scored tests in this filter range.")
                } else {
                    ScoreTrendChart(ui.filteredPoints, glowPulse)
                }
            }

            // ── Target vs Actual Gauge ───────────────────────────────────────
            PaSection(title = "TARGET vs ACTUAL", icon = Icons.Default.TrackChanges, color = NeonPink) {
                TargetGauge(ui, glowPulse)
            }

            // ── Source Breakdown ─────────────────────────────────────────────
            PaSection(title = "SOURCE BREAKDOWN", icon = Icons.Default.Category, color = NeonGold) {
                SourceBreakdown(ui)
            }

            // ── Subject Accuracy ─────────────────────────────────────────────
            if (ui.subjectStats.isNotEmpty()) {
                PaSection(title = "SUBJECT ACCURACY (PW Tests)", icon = Icons.Default.Science, color = NeonGreen) {
                    SubjectAccuracyChart(ui.subjectStats, glowPulse)
                }
            }

            // ── AI Insights ──────────────────────────────────────────────────
            PaSection(title = "AI INSIGHTS", icon = Icons.Default.AutoAwesome, color = NeonPurple) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.aiInsights.forEachIndexed { idx, msg ->
                        PaInsightCard(msg, glowPulse, idx)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Header
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PaHeader(navController: NavController, ui: PerformanceUiState, glowPulse: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonCyan.copy(0.4f))
                .background(Brush.linearGradient(listOf(NeonCyan.copy(0.25f), NeonPurple.copy(0.12f))), RoundedCornerShape(12.dp))
                .border(1.dp, NeonCyan.copy(0.45f), RoundedCornerShape(12.dp))
                .clickable { navController.popBackStack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = NeonCyan, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Performance Analytics", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text("${ui.totalTests} scored tests · AI-powered insights", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.42f))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(6.dp, CircleShape, spotColor = NeonCyan.copy(glowPulse * 0.4f))
                .background(Brush.radialGradient(listOf(NeonCyan.copy(0.22f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.BarChart, null, tint = NeonCyan.copy(glowPulse), modifier = Modifier.size(22.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Filters
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PaFilters(ui: PerformanceUiState, vm: PerformanceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Time filter
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerfTimeFilter.values().forEach { f ->
                val sel = ui.timeFilter == f
                PaFilterChip(f.label, sel, NeonCyan) { vm.setTimeFilter(f) }
            }
        }
        // Source filter
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PerfSourceFilter.values().toList()) { f ->
                val sel = ui.sourceFilter == f
                PaFilterChip(f.label, sel, NeonPurple) { vm.setSourceFilter(f) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Summary stat cards
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PaSummaryRow(ui: PerformanceUiState, glowPulse: Float) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val pts = ui.filteredPoints
        val avg = if (pts.isEmpty()) 0f else pts.map { it.percentage }.average().toFloat()
        val best = pts.maxOfOrNull { it.percentage } ?: 0f
        val latest = pts.lastOrNull()?.percentage

        item {
            PaStatCard(
                icon    = Icons.Default.Assessment,
                label   = "Avg Score",
                value   = "${avg.toInt()}%",
                sub     = "${pts.size} tests",
                color   = NeonCyan,
                pulse   = glowPulse
            )
        }
        item {
            PaStatCard(
                icon    = Icons.Default.EmojiEvents,
                label   = "Personal Best",
                value   = "${best.toInt()}%",
                sub     = if (best > 0) "${(best * ui.targetMax / 100f).toInt()}/${ui.targetMax.toInt()}" else "—",
                color   = NeonGold,
                pulse   = glowPulse
            )
        }
        if (latest != null) {
            item {
                val trendColor = when {
                    ui.trendDelta >  2f -> NeonGreen
                    ui.trendDelta < -2f -> NeonRed
                    else                -> NeonGold
                }
                val trendLabel = when {
                    ui.trendDelta >  0.5f -> "↑ +${ui.trendDelta.toInt()}%"
                    ui.trendDelta < -0.5f -> "↓ ${ui.trendDelta.toInt()}%"
                    else                  -> "→ Stable"
                }
                PaStatCard(
                    icon    = Icons.Default.TrendingUp,
                    label   = "Latest",
                    value   = "${latest.toInt()}%",
                    sub     = trendLabel,
                    color   = trendColor,
                    pulse   = glowPulse
                )
            }
        }
        item {
            PaStatCard(
                icon    = Icons.Default.Speed,
                label   = "Consistency",
                value   = if (ui.consistencyScore < 5f) "High" else if (ui.consistencyScore < 12f) "Moderate" else "Variable",
                sub     = "±${ui.consistencyScore.toInt()}% σ",
                color   = if (ui.consistencyScore < 8f) NeonGreen else NeonOrange,
                pulse   = glowPulse
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Score Trend Line Chart (Canvas)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ScoreTrendChart(points: List<ScorePoint>, glowPulse: Float) {
    val sourceColorMap = mapOf(
        ScoreSource.ONLINE_TEST  to NeonCyan,
        ScoreSource.OFFLINE_TEST to NeonGold,
        ScoreSource.SAMPLE_PAPER to NeonGreen,
        ScoreSource.PW_TEST      to NeonPurple
    )

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val usedSources = points.map { it.source }.distinct()
            usedSources.forEach { src ->
                val c = sourceColorMap[src] ?: NeonCyan
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(c, CircleShape))
                    Text(src.shortLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = c.copy(0.8f))
                }
            }
        }

        // Y-axis labels + chart area
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Y-axis labels
            Column(
                modifier = Modifier.width(32.dp).height(220.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("100", "75", "50", "25", "0").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(0.3f), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.width(4.dp))

            // Canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(220.dp)
            ) {
                val w = size.width
                val h = size.height
                val padTop    = 8.dp.toPx()
                val padBottom = 8.dp.toPx()
                val chartH    = h - padTop - padBottom

                // Grid lines at 0%, 25%, 50%, 75%, 100%
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    val y = padTop + chartH * (1f - fraction)
                    drawLine(Color.White.copy(0.07f), Offset(0f, y), Offset(w, y), 1f)
                }

                if (points.size == 1) {
                    val pt = points.first()
                    val x = w / 2f
                    val y = padTop + chartH * (1f - pt.percentage / 100f)
                    val c = sourceColorMap[pt.source] ?: NeonCyan
                    drawCircle(c, 8.dp.toPx(), Offset(x, y))
                    drawCircle(Color(0xFF0A1025), 5.dp.toPx(), Offset(x, y))
                    return@Canvas
                }

                val xStep = w / (points.size - 1).toFloat()

                // Build bezier path
                val path     = Path()
                val fillPath = Path()

                points.forEachIndexed { i, pt ->
                    val x = i * xStep
                    val y = padTop + chartH * (1f - pt.percentage / 100f)
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h - padBottom)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (i - 1) * xStep
                        val prevY = padTop + chartH * (1f - points[i - 1].percentage / 100f)
                        val cx1 = prevX + xStep * 0.45f
                        val cx2 = x    - xStep * 0.45f
                        path.cubicTo(cx1, prevY, cx2, y, x, y)
                        fillPath.cubicTo(cx1, prevY, cx2, y, x, y)
                    }
                }

                val lastX = (points.size - 1) * xStep
                fillPath.lineTo(lastX, h - padBottom)
                fillPath.close()

                // Draw gradient fill
                drawPath(
                    fillPath,
                    Brush.verticalGradient(
                        colors    = listOf(NeonCyan.copy(0.28f * glowPulse), Color.Transparent),
                        startY    = padTop,
                        endY      = h - padBottom
                    )
                )

                // Draw line (per-segment color by source)
                for (i in 1 until points.size) {
                    val prevX = (i - 1) * xStep
                    val prevY = padTop + chartH * (1f - points[i - 1].percentage / 100f)
                    val curX  = i * xStep
                    val curY  = padTop + chartH * (1f - points[i].percentage / 100f)
                    val c     = sourceColorMap[points[i].source] ?: NeonCyan
                    val seg   = Path()
                    val cx1   = prevX + xStep * 0.45f
                    val cx2   = curX  - xStep * 0.45f
                    seg.moveTo(prevX, prevY)
                    seg.cubicTo(cx1, prevY, cx2, curY, curX, curY)
                    drawPath(seg, c, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Draw dots
                points.forEachIndexed { i, pt ->
                    val x = i * xStep
                    val y = padTop + chartH * (1f - pt.percentage / 100f)
                    val c = sourceColorMap[pt.source] ?: NeonCyan
                    drawCircle(c, 6.dp.toPx(), Offset(x, y))
                    drawCircle(Color(0xFF0A1025), 3.5.dp.toPx(), Offset(x, y))
                }
            }
        }

        // X-axis labels (every N points to avoid crowding)
        val step = maxOf(1, points.size / 6)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelPoints = points.filterIndexed { i, _ -> i % step == 0 || i == points.size - 1 }
            labelPoints.forEach { pt ->
                Text(
                    pt.dateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White.copy(0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Target vs Actual Arc Gauge
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TargetGauge(ui: PerformanceUiState, glowPulse: Float) {
    val pts = ui.filteredPoints
    val avgPct = if (pts.isEmpty()) 0f else pts.map { it.percentage }.average().toFloat()
    val targetPct = if (ui.targetMax > 0) ui.targetScore / ui.targetMax * 100f else 97.2f
    val fraction  = (avgPct / 100f).coerceIn(0f, 1f)
    val targetFrac = (targetPct / 100f).coerceIn(0f, 1f)

    val gaugeColor = when {
        avgPct >= targetPct       -> NeonGreen
        avgPct >= targetPct - 10f -> NeonGold
        avgPct >= targetPct - 20f -> NeonOrange
        else                      -> NeonRed
    }

    val animFraction by animateFloatAsState(
        targetValue    = fraction,
        animationSpec  = tween(1200, easing = FastOutSlowInEasing),
        label          = "gauge"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Arc gauge
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset  = stroke / 2f
                val rect   = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                // Background arc
                drawArc(
                    color       = Color.White.copy(0.08f),
                    startAngle  = 150f,
                    sweepAngle  = 240f,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = rect,
                    style       = Stroke(stroke, cap = StrokeCap.Round)
                )

                // Target marker
                val targetAngle = 150f + 240f * targetFrac
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r  = (size.width - stroke) / 2f
                val markerX = cx + r * cos(Math.toRadians(targetAngle.toDouble())).toFloat()
                val markerY = cy + r * sin(Math.toRadians(targetAngle.toDouble())).toFloat()
                drawCircle(NeonPink, 5.dp.toPx(), Offset(markerX, markerY))

                // Progress arc
                drawArc(
                    brush       = Brush.sweepGradient(
                        listOf(gaugeColor.copy(0.7f), gaugeColor, gaugeColor.copy(0.9f))
                    ),
                    startAngle  = 150f,
                    sweepAngle  = 240f * animFraction,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = rect,
                    style       = Stroke(stroke, cap = StrokeCap.Round)
                )

                // Glow halo
                drawArc(
                    color       = gaugeColor.copy(0.15f * glowPulse),
                    startAngle  = 150f,
                    sweepAngle  = 240f * animFraction,
                    useCenter   = false,
                    topLeft     = Offset(inset - 4.dp.toPx(), inset - 4.dp.toPx()),
                    size        = Size(rect.width + 8.dp.toPx(), rect.height + 8.dp.toPx()),
                    style       = Stroke(stroke + 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${avgPct.toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = gaugeColor,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("avg", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
            }
        }

        // Stats column
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PaGaugeStat("Your Avg",  "${avgPct.toInt()}%",   "${(avgPct * ui.targetMax / 100f).toInt()}/${ui.targetMax.toInt()}", gaugeColor)
            PaGaugeStat("Target",    "${targetPct.toInt()}%", "${ui.targetScore.toInt()}/${ui.targetMax.toInt()}", NeonPink)
            val gap = targetPct - avgPct
            PaGaugeStat(
                "Gap",
                if (gap <= 0) "Met ✓" else "${gap.toInt()}% to go",
                if (gap <= 0) "On target!" else "${(gap * ui.targetMax / 100f).toInt()} marks",
                if (gap <= 0) NeonGreen else NeonOrange
            )
        }
    }
}

@Composable
private fun PaGaugeStat(label: String, value: String, sub: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
        Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(0.35f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Source Breakdown
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SourceBreakdown(ui: PerformanceUiState) {
    val colorMap = mapOf(
        ScoreSource.ONLINE_TEST  to NeonCyan,
        ScoreSource.OFFLINE_TEST to NeonGold,
        ScoreSource.SAMPLE_PAPER to NeonGreen,
        ScoreSource.PW_TEST      to NeonPurple
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScoreSource.values().forEach { src ->
            val avg = ui.sourceAverages[src] ?: -1f
            if (avg >= 0f) {
                val color = colorMap[src] ?: NeonCyan
                val count = ui.allPoints.count { it.source == src }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(src.shortLabel, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f), modifier = Modifier.width(52.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(0.07f))
                    ) {
                        val animW by animateFloatAsState(avg / 100f, tween(900, easing = FastOutSlowInEasing), label = "bar_$src")
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animW)
                                .shadow(4.dp, RoundedCornerShape(5.dp), spotColor = color.copy(0.4f))
                                .background(Brush.horizontalGradient(listOf(color.copy(0.7f), color)), RoundedCornerShape(5.dp))
                        )
                    }
                    Text("${avg.toInt()}%", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                    Text("($count)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(0.3f))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Subject Accuracy Horizontal Bar Chart (PW Tests)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SubjectAccuracyChart(subjectStats: Map<String, Float>, glowPulse: Float) {
    val subjectColorMap = mapOf(
        "PHYSICS"   to NeonCyan,
        "CHEMISTRY" to NeonOrange,
        "BOTANY"    to NeonGreen,
        "ZOOLOGY"   to NeonPurple
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        subjectStats.entries.sortedByDescending { it.value }.forEach { (subj, pct) ->
            val color = subjectColorMap[subj.uppercase()] ?: NeonIndigo
            val animW by animateFloatAsState(pct / 100f, tween(900, easing = FastOutSlowInEasing), label = "subj_$subj")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Subject label
                Row(
                    modifier = Modifier.width(80.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(subj.take(4), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f), fontWeight = FontWeight.Bold)
                }
                // Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(0.07f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animW)
                            .shadow(6.dp, RoundedCornerShape(7.dp), spotColor = color.copy(0.5f * glowPulse))
                            .background(Brush.horizontalGradient(listOf(color.copy(0.65f), color)), RoundedCornerShape(7.dp))
                    )
                }
                Text("${pct.toInt()}%", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  AI Insight card
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PaInsightCard(msg: String, glowPulse: Float, index: Int) {
    val colors = listOf(NeonPurple, NeonCyan, NeonGold, NeonTeal, NeonPink)
    val c = colors[index % colors.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = c.copy(0.15f))
            .background(c.copy(0.08f), RoundedCornerShape(14.dp))
            .border(0.5.dp, c.copy(0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = c.copy(0.8f * glowPulse), modifier = Modifier.size(15.dp).padding(top = 1.dp))
        Text(msg, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.78f), lineHeight = 18.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Shared helper composables
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PaSection(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = color.copy(0.15f))
            .background(
                Brush.linearGradient(listOf(Color(0xFF0F1830), color.copy(0.07f), Color(0xFF090F22))),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, color.copy(0.22f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        }
        content()
    }
}

@Composable
private fun PaStatCard(icon: ImageVector, label: String, value: String, sub: String, color: Color, pulse: Float) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = color.copy(pulse * 0.3f))
            .background(
                Brush.linearGradient(listOf(Color(0xFF0F1830), color.copy(0.12f))),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, color.copy(0.30f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(0.18f), RoundedCornerShape(10.dp))
                .border(0.5.dp, color.copy(0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
        Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(0.3f))
    }
}

@Composable
private fun PaFilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .shadow(if (selected) 6.dp else 0.dp, RoundedCornerShape(20.dp), spotColor = color.copy(0.35f))
            .background(if (selected) color.copy(0.22f) else Color.White.copy(0.05f), RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) color.copy(0.65f) else Color.White.copy(0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) color else Color.White.copy(0.50f), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

@Composable
private fun PaEmptyState(msg: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.BarChart, null, tint = Color.White.copy(0.20f), modifier = Modifier.size(40.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.35f), textAlign = TextAlign.Center)
        }
    }
}
