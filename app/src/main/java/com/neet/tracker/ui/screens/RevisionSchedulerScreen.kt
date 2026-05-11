package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.*
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.RevisionViewModel
import com.neet.tracker.ui.viewmodels.todayStr
import com.neet.tracker.ui.viewmodels.dateAfterDays
import com.neet.tracker.ui.viewmodels.spacedIntervalForRevNum
import java.text.SimpleDateFormat
import java.util.*

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun RevisionType.label(): String = when (this) {
    RevisionType.NEET_CHAPTER    -> "NEET Chapter"
    RevisionType.NOTEBOOK_CHAPTER-> "Notebook Chapter"
    RevisionType.PYQ_CHAPTER     -> "PYQ Chapter"
    RevisionType.PYQ_YEAR        -> "PYQ Year"
    RevisionType.TEST_PAPER      -> "Test Paper"
    RevisionType.SAMPLE_PAPER    -> "Sample Paper"
    RevisionType.PW_TEST         -> "PW Test"
    RevisionType.BOOK            -> "Book"
    RevisionType.LECTURE         -> "Lecture"
    RevisionType.TOPIC           -> "Topic"
    RevisionType.CUSTOM          -> "Custom"
}

fun RevisionType.color(): Color = when (this) {
    RevisionType.NEET_CHAPTER     -> NeonCyan
    RevisionType.NOTEBOOK_CHAPTER -> NeonPurple
    RevisionType.PYQ_CHAPTER      -> NeonGold
    RevisionType.PYQ_YEAR         -> NeonOrange
    RevisionType.TEST_PAPER       -> NeonRed
    RevisionType.SAMPLE_PAPER     -> NeonTeal
    RevisionType.PW_TEST          -> NeonPink
    RevisionType.BOOK             -> NeonGreen
    RevisionType.LECTURE          -> NeonIndigo
    RevisionType.TOPIC            -> NeonCyan
    RevisionType.CUSTOM           -> NeonPurple
}

fun RevisionPriority.label(): String = when (this) {
    RevisionPriority.LOW      -> "Low"
    RevisionPriority.MEDIUM   -> "Medium"
    RevisionPriority.HIGH     -> "High"
    RevisionPriority.CRITICAL -> "Critical"
}

fun RevisionPriority.color(): Color = when (this) {
    RevisionPriority.LOW      -> NeonGreen
    RevisionPriority.MEDIUM   -> NeonGold
    RevisionPriority.HIGH     -> NeonOrange
    RevisionPriority.CRITICAL -> NeonRed
}

fun RevisionStatus.label(): String = when (this) {
    RevisionStatus.PENDING -> "Pending"
    RevisionStatus.DONE    -> "Done"
    RevisionStatus.SKIPPED -> "Skipped"
}

private val dispFmt = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
private val inputFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val displayFmt = SimpleDateFormat("dd MMM yy", Locale.getDefault())
private fun prettyDate(d: String): String = try { dispFmt.format(inputFmt.parse(d)!!) } catch (_: Exception) { d }
private fun prettyDateShort(d: String): String = try { displayFmt.format(inputFmt.parse(d)!!) } catch (_: Exception) { d }

private fun ordinalRevNum(n: Int): String = when (n) {
    1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th"
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

@Composable
fun RevisionSchedulerScreen(
    navController: NavController,
    vm: RevisionViewModel = hiltViewModel()
) {
    val today         by remember { mutableStateOf(todayStr()) }
    val todayRevs     by vm.todayRevisions.collectAsState()
    val overdueRevs   by vm.overdueRevisions.collectAsState()
    val doneRevs      by vm.doneRevisions.collectAsState()
    val allRevs       by vm.allRevisions.collectAsState()
    val todayCount    by vm.todayCount.collectAsState()
    val overdueCount  by vm.overdueCount.collectAsState()
    val upcomingRevs  by vm.getUpcoming(7).collectAsState(initial = emptyList())

    var selectedTab    by remember { mutableIntStateOf(0) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<RevisionItem?>(null) }
    var filterSubject  by remember { mutableStateOf<Subject?>(null) }
    var filterType     by remember { mutableStateOf<RevisionType?>(null) }

    val tabs = listOf("Today", "Upcoming", "All", "Done")

    SpaceBackground(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editTarget = null; showAddDialog = true },
                containerColor = NeonCyan,
                contentColor   = DeepNavy,
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            NEETTopBar(
                title     = "Revision Scheduler",
                breadcrumb = "Home",
                onBack    = { navController.popBackStack() }
            )

            // ── Summary Badges ────────────────────────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                item { RevStatBadge("Overdue",  overdueCount, NeonRed,    Icons.Default.Warning) }
                item { RevStatBadge("Today",    todayCount,   NeonGold,   Icons.Default.Today) }
                item { RevStatBadge("Upcoming", upcomingRevs.count { it.scheduledDate > today }, NeonCyan, Icons.Default.CalendarMonth) }
                item { RevStatBadge("Done",     doneRevs.size, NeonGreen, Icons.Default.CheckCircle) }
                item { RevStatBadge("Total",    allRevs.size,  NeonPurple, Icons.Default.FormatListBulleted) }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.Transparent,
                contentColor     = NeonCyan,
                edgePadding      = 16.dp,
                indicator = { positions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier
                            .tabIndicatorOffset(positions[selectedTab])
                            .padding(horizontal = 12.dp)
                            .clip(CircleShape),
                        color = NeonCyan,
                        height = 2.dp
                    )
                }
            ) {
                tabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                tab,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == i) NeonCyan else GlassWhite.copy(0.5f)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when (selectedTab) {
                0 -> TodayTab(
                    overdue  = overdueRevs,
                    today    = todayRevs,
                    todayStr = today,
                    onDone     = { vm.markDone(it) },
                    onSkip     = { vm.markSkipped(it.id) },
                    onEdit     = { editTarget = it; showAddDialog = true },
                    onDelete   = { vm.delete(it) },
                    onRescheduleAllOverdue = { vm.rescheduleAllOverdue(overdueRevs) }
                )
                1 -> UpcomingTab(
                    items    = upcomingRevs.filter { it.scheduledDate > today },
                    onDone   = { vm.markDone(it) },
                    onEdit   = { editTarget = it; showAddDialog = true },
                    onDelete = { vm.delete(it) }
                )
                2 -> AllTab(
                    all          = allRevs,
                    filterSubject= filterSubject,
                    filterType   = filterType,
                    onFilterSubject = { filterSubject = it },
                    onFilterType    = { filterType = it },
                    onDone   = { vm.markDone(it) },
                    onSkip   = { vm.markSkipped(it.id) },
                    onEdit   = { editTarget = it; showAddDialog = true },
                    onDelete = { vm.delete(it) }
                )
                3 -> DoneTab(
                    items    = doneRevs,
                    onEdit   = { editTarget = it; showAddDialog = true },
                    onDelete = { vm.delete(it) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddEditRevisionDialog(
            existing  = editTarget,
            onSave    = { vm.save(it); showAddDialog = false; editTarget = null },
            onDismiss = { showAddDialog = false; editTarget = null }
        )
    }
}

// ─── Stat Badge ───────────────────────────────────────────────────────────────

@Composable
private fun RevStatBadge(label: String, count: Int, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .background(color.copy(0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.30f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Column {
                Text(count.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 16.sp)
                Text(label, color = color.copy(0.7f), fontSize = 9.sp)
            }
        }
    }
}

// ─── Today Tab ────────────────────────────────────────────────────────────────

@Composable
private fun TodayTab(
    overdue: List<RevisionItem>,
    today: List<RevisionItem>,
    todayStr: String,
    onDone: (RevisionItem) -> Unit,
    onSkip: (RevisionItem) -> Unit,
    onEdit: (RevisionItem) -> Unit,
    onDelete: (RevisionItem) -> Unit,
    onRescheduleAllOverdue: () -> Unit
) {
    if (overdue.isEmpty() && today.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircleOutline, null, tint = NeonGreen.copy(0.4f), modifier = Modifier.size(60.dp))
                Spacer(Modifier.height(12.dp))
                Text("All clear for today!", color = NeonGreen.copy(0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("No revisions pending", color = GlassWhite.copy(0.4f), fontSize = 12.sp)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (overdue.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = NeonRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("OVERDUE (${overdue.size})", color = NeonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRescheduleAllOverdue) {
                        Text("Move all to Today", color = NeonOrange, fontSize = 10.sp)
                    }
                }
            }
            items(overdue, key = { it.id }) { item ->
                RevisionCard(
                    item      = item,
                    isOverdue = true,
                    onDone    = { onDone(item) },
                    onSkip    = { onSkip(item) },
                    onEdit    = { onEdit(item) },
                    onDelete  = { onDelete(item) }
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        if (today.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Today, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("TODAY (${today.size})", color = NeonGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(today, key = { it.id }) { item ->
                RevisionCard(
                    item      = item,
                    isOverdue = false,
                    onDone    = { onDone(item) },
                    onSkip    = { onSkip(item) },
                    onEdit    = { onEdit(item) },
                    onDelete  = { onDelete(item) }
                )
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ─── Upcoming Tab ─────────────────────────────────────────────────────────────

@Composable
private fun UpcomingTab(
    items: List<RevisionItem>,
    onDone: (RevisionItem) -> Unit,
    onEdit: (RevisionItem) -> Unit,
    onDelete: (RevisionItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CalendarMonth, null, tint = NeonCyan.copy(0.35f), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No upcoming revisions (next 7 days)", color = GlassWhite.copy(0.4f), fontSize = 13.sp)
            }
        }
        return
    }

    val grouped = items.groupBy { it.scheduledDate }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        grouped.forEach { (date, dayItems) ->
            item {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(prettyDate(date), color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(NeonCyan.copy(0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${dayItems.size}", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(dayItems, key = { it.id }) { item ->
                RevisionCard(
                    item      = item,
                    isOverdue = false,
                    onDone    = { onDone(item) },
                    onSkip    = null,
                    onEdit    = { onEdit(item) },
                    onDelete  = { onDelete(item) }
                )
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ─── All Tab ──────────────────────────────────────────────────────────────────

@Composable
private fun AllTab(
    all: List<RevisionItem>,
    filterSubject: Subject?,
    filterType: RevisionType?,
    onFilterSubject: (Subject?) -> Unit,
    onFilterType: (RevisionType?) -> Unit,
    onDone: (RevisionItem) -> Unit,
    onSkip: (RevisionItem) -> Unit,
    onEdit: (RevisionItem) -> Unit,
    onDelete: (RevisionItem) -> Unit
) {
    val displayed = remember(all, filterSubject, filterType) {
        all.filter { r ->
            (filterSubject == null || r.subject == filterSubject) &&
            (filterType    == null || r.type == filterType)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Subject filter
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            item { RevFilterChip("All", filterSubject == null, NeonCyan) { onFilterSubject(null) } }
            items(Subject.values().size) { i ->
                val s = Subject.values()[i]
                RevFilterChip(s.name.take(4), filterSubject == s, s.errorColor()) { onFilterSubject(s) }
            }
        }
        // Type filter
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            item { RevFilterChip("All Types", filterType == null, NeonPurple) { onFilterType(null) } }
            items(RevisionType.values().size) { i ->
                val t = RevisionType.values()[i]
                RevFilterChip(t.label().take(9), filterType == t, t.color()) { onFilterType(t) }
            }
        }

        Text("${displayed.size} items", color = NeonCyan.copy(0.4f), fontSize = 10.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))

        if (displayed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No items match the filter", color = GlassWhite.copy(0.35f), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(displayed, key = { it.id }) { item ->
                    val today = todayStr()
                    RevisionCard(
                        item      = item,
                        isOverdue = item.scheduledDate < today && item.status == RevisionStatus.PENDING,
                        onDone    = { onDone(item) },
                        onSkip    = { onSkip(item) },
                        onEdit    = { onEdit(item) },
                        onDelete  = { onDelete(item) }
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }
}

// ─── Done Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun DoneTab(
    items: List<RevisionItem>,
    onEdit: (RevisionItem) -> Unit,
    onDelete: (RevisionItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = NeonGreen.copy(0.35f), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No completed revisions yet", color = GlassWhite.copy(0.4f), fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { item ->
            RevisionCard(
                item      = item,
                isOverdue = false,
                onDone    = null,
                onSkip    = null,
                onEdit    = { onEdit(item) },
                onDelete  = { onDelete(item) }
            )
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ─── Revision Card ────────────────────────────────────────────────────────────

@Composable
private fun RevisionCard(
    item: RevisionItem,
    isOverdue: Boolean,
    onDone: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val accentColor = if (isOverdue) NeonRed else item.type.color()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(accentColor.copy(0.08f), CardGradientEnd.copy(0.9f))
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .border(1.dp, accentColor.copy(if (isOverdue) 0.55f else 0.25f), RoundedCornerShape(14.dp))
    ) {
        // Priority indicator stripe (left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(4.dp)
                .background(item.priority.color(), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
        )

        Column(modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            // Top Row: title + badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title.ifBlank { "(No title)" },
                        color = GlassWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Revision number
                        Text(
                            "${ordinalRevNum(item.revisionNumber)} Rev",
                            color = NeonGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        // Subject
                        Text(item.subject.name.take(3), color = item.subject.errorColor(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        // Type badge
                        Box(
                            modifier = Modifier
                                .background(item.type.color().copy(0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(item.type.label().take(10), color = item.type.color(), fontSize = 8.sp)
                        }
                    }
                }

                // Status + Priority column
                Column(horizontalAlignment = Alignment.End) {
                    // Status badge
                    val sColor = when (item.status) {
                        RevisionStatus.PENDING -> if (isOverdue) NeonRed else NeonGold
                        RevisionStatus.DONE    -> NeonGreen
                        RevisionStatus.SKIPPED -> NeonOrange
                    }
                    Box(
                        modifier = Modifier
                            .background(sColor.copy(0.15f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, sColor.copy(0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isOverdue && item.status == RevisionStatus.PENDING) "OVERDUE" else item.status.label(),
                            color = sColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    // Priority
                    Box(
                        modifier = Modifier
                            .background(item.priority.color().copy(0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(item.priority.label(), color = item.priority.color(), fontSize = 8.sp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Date + source row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, tint = NeonCyan.copy(0.55f), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(prettyDateShort(item.scheduledDate), color = NeonCyan.copy(0.7f), fontSize = 10.sp)
                if (item.sourceName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("•", color = GlassWhite.copy(0.3f), fontSize = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(item.sourceName, color = GlassWhite.copy(0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.isSpacedRepetition) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.AutoGraph, null, tint = NeonTeal.copy(0.7f), modifier = Modifier.size(11.dp))
                }
            }

            if (item.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.notes, color = GlassWhite.copy(0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Action buttons
            if (onDone != null || onSkip != null || item.status == RevisionStatus.DONE) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = NeonCyan.copy(0.7f), modifier = Modifier.size(16.dp))
                    }
                    // Delete
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    if (onSkip != null) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, NeonOrange.copy(0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Skip", color = NeonOrange, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (onDone != null) {
                        Button(
                            onClick = onDone,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.85f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, null, tint = DeepNavy, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Done", color = DeepNavy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, null, tint = NeonCyan.copy(0.5f), modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.45f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = CardGradientEnd,
            title = { Text("Delete Revision?", color = NeonRed) },
            text  = { Text("This revision item will be permanently deleted.", color = GlassWhite.copy(0.8f)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = NeonRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = NeonCyan) }
            }
        )
    }
}

// ─── Filter Chip ──────────────────────────────────────────────────────────────

@Composable
private fun RevFilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) color.copy(0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) color else color.copy(0.22f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (selected) color else GlassWhite.copy(0.5f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Add / Edit Revision Dialog ───────────────────────────────────────────────

@Composable
private fun AddEditRevisionDialog(
    existing: RevisionItem?,
    onSave: (RevisionItem) -> Unit,
    onDismiss: () -> Unit
) {
    val today = todayStr()

    var title          by remember { mutableStateOf(existing?.title ?: "") }
    var subject        by remember { mutableStateOf(existing?.subject ?: Subject.PHYSICS) }
    var type           by remember { mutableStateOf(existing?.type ?: RevisionType.NEET_CHAPTER) }
    var sourceName     by remember { mutableStateOf(existing?.sourceName ?: "") }
    var scheduledDate  by remember { mutableStateOf(existing?.scheduledDate ?: today) }
    var revisionNumber by remember { mutableIntStateOf(existing?.revisionNumber ?: 1) }
    var priority       by remember { mutableStateOf(existing?.priority ?: RevisionPriority.MEDIUM) }
    var notes          by remember { mutableStateOf(existing?.notes ?: "") }
    var useSpaced      by remember { mutableStateOf(existing?.isSpacedRepetition ?: false) }

    // Spaced repetition: auto-compute scheduled date from rev number
    LaunchedEffect(useSpaced, revisionNumber) {
        if (useSpaced && existing == null) {
            val interval = spacedIntervalForRevNum(revisionNumber)
            scheduledDate = dateAfterDays(interval)
        }
    }

    // Date picker helper: simple +/- day buttons
    val cal = remember(scheduledDate) {
        try {
            val c = Calendar.getInstance()
            c.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(scheduledDate) ?: Date()
            c
        } catch (_: Exception) { Calendar.getInstance() }
    }
    fun shiftDate(days: Int) {
        cal.add(Calendar.DAY_OF_YEAR, days)
        scheduledDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy.copy(0.97f))
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(NeonCyan.copy(0.15f), NeonPurple.copy(0.1f))))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (existing == null) Icons.Default.Add else Icons.Default.Edit,
                            null, tint = NeonCyan, modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (existing == null) "Schedule Revision" else "Edit Revision",
                            color = GlassWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = GlassWhite.copy(0.6f))
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Title
                    item {
                        RevDialogLabel("Title *")
                        RevDialogField(title, { title = it }, "e.g. Ray Optics, Electrochemistry, NEET 2022 PYQ")
                    }

                    // Subject
                    item {
                        RevDialogLabel("Subject")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Subject.values().forEach { s ->
                                val sel = subject == s
                                Box(
                                    modifier = Modifier
                                        .background(if (sel) s.errorColor().copy(0.25f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (sel) s.errorColor() else GlassBorder, RoundedCornerShape(8.dp))
                                        .clickable { subject = s }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(s.name.take(4), color = if (sel) s.errorColor() else GlassWhite.copy(0.6f), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Revision Type
                    item {
                        RevDialogLabel("Revision Type")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(RevisionType.values().size) { i ->
                                val t = RevisionType.values()[i]
                                val sel = type == t
                                Box(
                                    modifier = Modifier
                                        .background(if (sel) t.color().copy(0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (sel) t.color() else GlassBorder.copy(0.3f), RoundedCornerShape(8.dp))
                                        .clickable { type = t }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(t.label().take(10), color = if (sel) t.color() else GlassWhite.copy(0.55f), fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Source Name
                    item {
                        RevDialogLabel("Source Name (optional)")
                        RevDialogField(sourceName, { sourceName = it }, "e.g. NCERT Chapter 9, Allen Mock #12")
                    }

                    // Revision number
                    item {
                        RevDialogLabel("Revision Number")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (revisionNumber > 1) revisionNumber-- }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Remove, null, tint = NeonCyan)
                            }
                            Box(
                                modifier = Modifier
                                    .background(NeonCyan.copy(0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("${ordinalRevNum(revisionNumber)} Revision", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            IconButton(onClick = { revisionNumber++ }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Add, null, tint = NeonCyan)
                            }
                        }
                    }

                    // Spaced Repetition
                    item {
                        RevDialogLabel("Spaced Repetition")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GlassSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder.copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-schedule next revision", color = GlassWhite.copy(0.9f), fontSize = 12.sp)
                                Text(
                                    "Rev 1→+1d · 2→+3d · 3→+7d · 4→+14d · 5→+30d",
                                    color = NeonTeal.copy(0.65f), fontSize = 9.sp
                                )
                            }
                            Switch(
                                checked = useSpaced,
                                onCheckedChange = { useSpaced = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonTeal, checkedTrackColor = NeonTeal.copy(0.35f))
                            )
                        }
                    }

                    // Scheduled Date
                    item {
                        RevDialogLabel("Scheduled Date")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GlassSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, NeonCyan.copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { shiftDate(-1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.ChevronLeft, null, tint = NeonCyan)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(prettyDate(scheduledDate), color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(scheduledDate, color = GlassWhite.copy(0.4f), fontSize = 10.sp)
                            }
                            IconButton(onClick = { shiftDate(1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.ChevronRight, null, tint = NeonCyan)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        // Quick date shortcuts
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Today" to 0, "+1d" to 1, "+3d" to 3, "+7d" to 7, "+14d" to 14, "+30d" to 30).forEach { (label, days) ->
                                Box(
                                    modifier = Modifier
                                        .background(NeonCyan.copy(0.1f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(6.dp))
                                        .clickable { scheduledDate = if (days == 0) today else dateAfterDays(days) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(label, color = NeonCyan, fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    // Priority
                    item {
                        RevDialogLabel("Priority")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RevisionPriority.values().forEach { p ->
                                val sel = priority == p
                                Box(
                                    modifier = Modifier
                                        .background(if (sel) p.color().copy(0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (sel) p.color() else GlassBorder, RoundedCornerShape(8.dp))
                                        .clickable { priority = p }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(p.label(), color = if (sel) p.color() else GlassWhite.copy(0.6f), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Notes
                    item {
                        RevDialogLabel("Notes (optional)")
                        BasicTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            textStyle = TextStyle(color = GlassWhite, fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 60.dp)
                                .background(GlassSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder.copy(0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            decorationBox = { inner ->
                                if (notes.isEmpty()) Text("Any notes about this revision…", color = GlassWhite.copy(0.3f), fontSize = 13.sp)
                                inner()
                            }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Save button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepNavy)
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (title.isBlank()) return@Button
                            val item = RevisionItem(
                                id               = existing?.id ?: UUID.randomUUID().toString(),
                                title            = title.trim(),
                                subject          = subject,
                                type             = type,
                                sourceName       = sourceName.trim(),
                                sourceId         = existing?.sourceId ?: "",
                                scheduledDate    = scheduledDate,
                                revisionNumber   = revisionNumber,
                                priority         = priority,
                                status           = existing?.status ?: RevisionStatus.PENDING,
                                notes            = notes.trim(),
                                isSpacedRepetition = useSpaced,
                                intervalDays     = spacedIntervalForRevNum(revisionNumber),
                                nextRevisionDate = "",
                                completedAt      = existing?.completedAt ?: 0L,
                                createdAt        = existing?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(item)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape    = RoundedCornerShape(12.dp),
                        enabled  = title.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, null, tint = DeepNavy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (existing == null) "Schedule Revision" else "Save Changes",
                            color = DeepNavy, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RevDialogLabel(text: String) {
    Text(text, color = NeonCyan.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun RevDialogField(value: String, onChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = GlassWhite, fontSize = 13.sp),
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSurface, RoundedCornerShape(10.dp))
            .border(1.dp, GlassBorder.copy(0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = GlassWhite.copy(0.3f), fontSize = 13.sp)
            inner()
        }
    )
}
