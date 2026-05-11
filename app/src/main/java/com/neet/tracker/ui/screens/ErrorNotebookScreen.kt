package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import com.neet.tracker.ui.viewmodels.ErrorViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun ErrorType.label(): String = when (this) {
    ErrorType.CONCEPT_MISTAKE  -> "Concept"
    ErrorType.SILLY_MISTAKE    -> "Silly"
    ErrorType.CALCULATION_ERROR-> "Calculation"
    ErrorType.NOT_ATTEMPTED    -> "Not Attempted"
    ErrorType.TIME_PRESSURE    -> "Time Pressure"
    ErrorType.FORMULA_FORGOT   -> "Formula Forgot"
    ErrorType.MISREAD          -> "Misread"
    ErrorType.OVERCONFIDENCE   -> "Overconfidence"
}

fun ErrorType.color(): Color = when (this) {
    ErrorType.CONCEPT_MISTAKE   -> NeonRed
    ErrorType.SILLY_MISTAKE     -> NeonOrange
    ErrorType.CALCULATION_ERROR -> NeonGold
    ErrorType.NOT_ATTEMPTED     -> NeonPurple
    ErrorType.TIME_PRESSURE     -> NeonCyan
    ErrorType.FORMULA_FORGOT    -> NeonPink
    ErrorType.MISREAD           -> NeonTeal
    ErrorType.OVERCONFIDENCE    -> NeonIndigo
}

fun ErrorSource.label(): String = when (this) {
    ErrorSource.TEST_PAPER      -> "Test Paper"
    ErrorSource.PYQ_CHAPTERWISE -> "PYQ Chapterwise"
    ErrorSource.PYQ_YEARWISE    -> "PYQ Yearwise"
    ErrorSource.SAMPLE_PAPER    -> "Sample Paper"
    ErrorSource.PW_TEST         -> "PW Test"
    ErrorSource.BOOK            -> "Book"
    ErrorSource.LECTURE         -> "Lecture"
    ErrorSource.SELF_STUDY      -> "Self Study"
}

fun ErrorStatus.label(): String = when (this) {
    ErrorStatus.PENDING    -> "Pending"
    ErrorStatus.UNDERSTOOD -> "Understood"
    ErrorStatus.MASTERED   -> "Mastered"
}

fun ErrorStatus.color(): Color = when (this) {
    ErrorStatus.PENDING    -> NeonRed
    ErrorStatus.UNDERSTOOD -> NeonGold
    ErrorStatus.MASTERED   -> NeonGreen
}

fun Subject.errorColor(): Color = when (this) {
    Subject.PHYSICS   -> NeonGold
    Subject.CHEMISTRY -> NeonCyan
    Subject.BOTANY    -> NeonGreen
    Subject.ZOOLOGY   -> NeonPink
    Subject.GENERAL   -> NeonPurple
}

private val dateFmtEN = SimpleDateFormat("dd MMM yy", Locale.getDefault())
private fun fmtMs(ms: Long): String = if (ms == 0L) "Never" else dateFmtEN.format(Date(ms))

// ─── Main Screen ─────────────────────────────────────────────────────────────

@Composable
fun ErrorNotebookScreen(
    navController: NavController,
    vm: ErrorViewModel = hiltViewModel()
) {
    val all          by vm.allErrors.collectAsState()
    val totalCount   by vm.totalCount.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val undCount     by vm.understoodCount.collectAsState()
    val mastCount    by vm.masteredCount.collectAsState()

    var filterSubject  by remember { mutableStateOf<Subject?>(null) }
    var filterErrType  by remember { mutableStateOf<ErrorType?>(null) }
    var filterStatus   by remember { mutableStateOf<ErrorStatus?>(null) }
    var searchQuery    by remember { mutableStateOf("") }
    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<ErrorEntry?>(null) }
    var expandedId     by remember { mutableStateOf<String?>(null) }

    val displayed = remember(all, filterSubject, filterErrType, filterStatus, searchQuery) {
        all.filter { e ->
            (filterSubject == null || e.subject == filterSubject) &&
            (filterErrType == null || e.errorType == filterErrType) &&
            (filterStatus  == null || e.status == filterStatus) &&
            (searchQuery.isBlank() ||
             e.description.contains(searchQuery, true) ||
             e.chapter.contains(searchQuery, true) ||
             e.sourceName.contains(searchQuery, true) ||
             e.questionNo.contains(searchQuery, true))
        }
    }

    SpaceBackground(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editTarget = null; showAddDialog = true },
                containerColor = NeonRed,
                contentColor = GlassWhite,
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            NEETTopBar(
                title    = "Error Notebook",
                breadcrumb = "Home",
                onBack   = { navController.popBackStack() }
            )

            // ── Stats Row ────────────────────────────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                item { ErrorStatChip("Total",      totalCount,   NeonCyan)   }
                item { ErrorStatChip("Pending",    pendingCount, NeonRed)    }
                item { ErrorStatChip("Understood", undCount,     NeonGold)   }
                item { ErrorStatChip("Mastered",   mastCount,    NeonGreen)  }
            }

            // ── Search Bar ───────────────────────────────────────────────────
            ErrorSearchBar(
                query    = searchQuery,
                onChange = { searchQuery = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Filter Chips ─────────────────────────────────────────────────
            FilterChipRowError(label = "Subject", selectedColor = filterSubject?.errorColor() ?: NeonCyan) {
                ErrorFilterChip("All", filterSubject == null, NeonCyan)         { filterSubject = null }
                Subject.values().forEach { s ->
                    ErrorFilterChip(s.name.take(3), filterSubject == s, s.errorColor()) { filterSubject = s }
                }
            }
            FilterChipRowError(label = "Type", selectedColor = filterErrType?.color() ?: NeonCyan) {
                ErrorFilterChip("All", filterErrType == null, NeonCyan) { filterErrType = null }
                ErrorType.values().forEach { t ->
                    ErrorFilterChip(t.label().take(7), filterErrType == t, t.color()) { filterErrType = t }
                }
            }
            FilterChipRowError(label = "Status", selectedColor = filterStatus?.color() ?: NeonCyan) {
                ErrorFilterChip("All",        filterStatus == null,                NeonCyan) { filterStatus = null }
                ErrorFilterChip("Pending",    filterStatus == ErrorStatus.PENDING, NeonRed)  { filterStatus = ErrorStatus.PENDING }
                ErrorFilterChip("Understood", filterStatus == ErrorStatus.UNDERSTOOD, NeonGold) { filterStatus = ErrorStatus.UNDERSTOOD }
                ErrorFilterChip("Mastered",   filterStatus == ErrorStatus.MASTERED, NeonGreen) { filterStatus = ErrorStatus.MASTERED }
            }

            Spacer(Modifier.height(4.dp))

            // ── Result count ─────────────────────────────────────────────────
            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = NeonRed.copy(0.4f), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No errors found", color = GlassWhite.copy(0.5f), fontSize = 15.sp)
                        if (all.isEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Tap + to log your first mistake", color = NeonCyan.copy(0.5f), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Text(
                    "${displayed.size} error${if (displayed.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan.copy(0.45f),
                    modifier = Modifier.padding(start = 18.dp, bottom = 4.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayed, key = { it.id }) { entry ->
                        ErrorEntryCard(
                            entry     = entry,
                            expanded  = expandedId == entry.id,
                            onClick   = { expandedId = if (expandedId == entry.id) null else entry.id },
                            onEdit    = { editTarget = it; showAddDialog = true },
                            onDelete  = { vm.delete(it) },
                            onStatus  = { vm.setStatus(entry.id, it) },
                            onRevised = { vm.markRevised(entry.id) }
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditErrorDialog(
            existing  = editTarget,
            onSave    = { vm.save(it); showAddDialog = false; editTarget = null },
            onDismiss = { showAddDialog = false; editTarget = null }
        )
    }
}

// ─── Stat Chip ────────────────────────────────────────────────────────────────

@Composable
private fun ErrorStatChip(label: String, count: Int, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(0.12f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, color = color.copy(0.75f), fontSize = 10.sp)
        }
    }
}

// ─── Search Bar ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorSearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(GlassSurface, RoundedCornerShape(12.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = GlassWhite, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search errors, chapters, sources…", color = GlassWhite.copy(0.35f), fontSize = 13.sp)
                inner()
            }
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Clear, null, tint = GlassWhite.copy(0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Filter Chip Row ──────────────────────────────────────────────────────────

@Composable
private fun FilterChipRowError(
    label: String,
    selectedColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label:", color = GlassWhite.copy(0.45f), fontSize = 10.sp, modifier = Modifier.width(42.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            item { Row { content() } }
        }
    }
}

@Composable
private fun ErrorFilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .background(
                if (selected) color.copy(0.22f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (selected) color else color.copy(0.25f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (selected) color else GlassWhite.copy(0.55f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Error Entry Card ─────────────────────────────────────────────────────────

@Composable
private fun ErrorEntryCard(
    entry: ErrorEntry,
    expanded: Boolean,
    onClick: () -> Unit,
    onEdit: (ErrorEntry) -> Unit,
    onDelete: (ErrorEntry) -> Unit,
    onStatus: (ErrorStatus) -> Unit,
    onRevised: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        entry.subject.errorColor().copy(0.08f),
                        CardGradientEnd.copy(0.9f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (expanded) entry.subject.errorColor().copy(0.5f) else GlassBorder.copy(0.3f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Left subject color bar
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(4.dp)
                .background(entry.subject.errorColor(), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
        )

        Column(modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.questionNo.isNotBlank()) {
                    Text(
                        "Q.${entry.questionNo}",
                        color = NeonGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                // Error type badge
                Box(
                    modifier = Modifier
                        .background(entry.errorType.color().copy(0.18f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, entry.errorType.color().copy(0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(entry.errorType.label(), color = entry.errorType.color(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                // Status badge
                Box(
                    modifier = Modifier
                        .background(entry.status.color().copy(0.18f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, entry.status.color().copy(0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(entry.status.label(), color = entry.status.color(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Description
            Spacer(Modifier.height(6.dp))
            Text(
                entry.description.ifBlank { "(No description)" },
                color = GlassWhite.copy(0.9f),
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Meta row
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.chapter.isNotBlank()) {
                    Icon(Icons.Default.MenuBook, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(entry.chapter, color = NeonCyan.copy(0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.width(6.dp))
                Text(entry.subject.name.take(3), color = entry.subject.errorColor(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (entry.sourceName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Source, null, tint = NeonPurple.copy(0.6f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(entry.sourceName, color = GlassWhite.copy(0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text("· ${entry.sourceType.label()}", color = GlassWhite.copy(0.35f), fontSize = 9.sp)
                }
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = GlassBorder.copy(0.25f))
                    Spacer(Modifier.height(8.dp))

                    if (entry.myAnswer.isNotBlank()) {
                        ErrorDetailField("My Answer", entry.myAnswer, NeonRed)
                    }
                    if (entry.correctAnswer.isNotBlank()) {
                        ErrorDetailField("Correct Answer", entry.correctAnswer, NeonGreen)
                    }
                    if (entry.explanation.isNotBlank()) {
                        ErrorDetailField("Explanation", entry.explanation, NeonCyan)
                    }
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            entry.tags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .padding(end = 5.dp)
                                        .background(NeonPurple.copy(0.15f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, NeonPurple.copy(0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("#$tag", color = NeonPurple, fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    // Revision info
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Replay, null, tint = NeonTeal.copy(0.6f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Revised ${entry.revisionCount}×  •  Last: ${fmtMs(entry.lastRevised)}", color = NeonTeal.copy(0.7f), fontSize = 10.sp)
                    }

                    Spacer(Modifier.height(10.dp))
                    Divider(color = GlassBorder.copy(0.2f))
                    Spacer(Modifier.height(8.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Status change
                        if (entry.status != ErrorStatus.UNDERSTOOD) {
                            ErrorActionBtn("Understood", Icons.Default.Check, NeonGold)  { onStatus(ErrorStatus.UNDERSTOOD) }
                        }
                        if (entry.status != ErrorStatus.MASTERED) {
                            ErrorActionBtn("Mastered", Icons.Default.Star, NeonGreen) { onStatus(ErrorStatus.MASTERED) }
                        }
                        if (entry.status != ErrorStatus.PENDING) {
                            ErrorActionBtn("Pending", Icons.Default.HourglassEmpty, NeonRed) { onStatus(ErrorStatus.PENDING) }
                        }
                        ErrorActionBtn("Revised", Icons.Default.Replay, NeonTeal)  { onRevised() }
                        ErrorActionBtn("Edit",    Icons.Default.Edit, NeonCyan)    { onEdit(entry) }
                        ErrorActionBtn("Delete",  Icons.Default.Delete, NeonRed.copy(0.8f)) { showDeleteConfirm = true }
                    }
                }
            }

            if (!expanded) {
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.Default.ExpandMore, null, tint = GlassWhite.copy(0.25f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = CardGradientEnd,
            title = { Text("Delete Error?", color = NeonRed) },
            text  = { Text("This error entry will be permanently deleted.", color = GlassWhite.copy(0.8f)) },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); showDeleteConfirm = false }) {
                    Text("Delete", color = NeonRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = NeonCyan) }
            }
        )
    }
}

@Composable
private fun ErrorDetailField(label: String, value: String, color: Color) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(label, color = color.copy(0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(0.07f), RoundedCornerShape(8.dp))
                .border(0.5.dp, color.copy(0.2f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(value, color = GlassWhite.copy(0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorActionBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(color.copy(0.12f), RoundedCornerShape(8.dp))
            .border(0.5.dp, color.copy(0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Add / Edit Error Dialog ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditErrorDialog(
    existing: ErrorEntry?,
    onSave: (ErrorEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var questionNo    by remember { mutableStateOf(existing?.questionNo ?: "") }
    var description   by remember { mutableStateOf(existing?.description ?: "") }
    var errorType     by remember { mutableStateOf(existing?.errorType ?: ErrorType.CONCEPT_MISTAKE) }
    var subject       by remember { mutableStateOf(existing?.subject ?: Subject.PHYSICS) }
    var chapter       by remember { mutableStateOf(existing?.chapter ?: "") }
    var sourceType    by remember { mutableStateOf(existing?.sourceType ?: ErrorSource.SELF_STUDY) }
    var sourceName    by remember { mutableStateOf(existing?.sourceName ?: "") }
    var myAnswer      by remember { mutableStateOf(existing?.myAnswer ?: "") }
    var correctAnswer by remember { mutableStateOf(existing?.correctAnswer ?: "") }
    var explanation   by remember { mutableStateOf(existing?.explanation ?: "") }
    var status        by remember { mutableStateOf(existing?.status ?: ErrorStatus.PENDING) }
    var tagsText      by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy.copy(0.97f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(NeonRed.copy(0.15f), NeonOrange.copy(0.1f)))
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (existing == null) Icons.Default.Add else Icons.Default.Edit,
                            null, tint = NeonRed, modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (existing == null) "Log New Error" else "Edit Error",
                            color = GlassWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = GlassWhite.copy(0.6f))
                        }
                    }
                }

                // Form
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Subject picker
                    item {
                        DialogSectionLabel("Subject")
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
                                    Text(s.name.take(4), color = if (sel) s.errorColor() else GlassWhite.copy(0.6f), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    // Error Type picker
                    item {
                        DialogSectionLabel("Error Type")
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.height(136.dp),
                            userScrollEnabled = false
                        ) {
                            gridItems(ErrorType.values().size) { i ->
                                val t = ErrorType.values()[i]
                                val sel = errorType == t
                                Box(
                                    modifier = Modifier
                                        .background(if (sel) t.color().copy(0.2f) else GlassSurface, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (sel) t.color() else GlassBorder.copy(0.3f), RoundedCornerShape(8.dp))
                                        .clickable { errorType = t }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(t.label(), color = if (sel) t.color() else GlassWhite.copy(0.65f), fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    // Question No
                    item {
                        DialogSectionLabel("Question No. (optional)")
                        ErrorFormField(questionNo, { questionNo = it }, "e.g. 45")
                    }

                    // Description
                    item {
                        DialogSectionLabel("What went wrong / Question topic *")
                        ErrorFormFieldMulti(description, { description = it }, "Describe the error or question topic...", 3)
                    }

                    // Chapter
                    item {
                        DialogSectionLabel("Chapter")
                        ErrorFormField(chapter, { chapter = it }, "e.g. Ray Optics")
                    }

                    // Source
                    item {
                        DialogSectionLabel("Source Type")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ErrorSource.values().size) { i ->
                                val s = ErrorSource.values()[i]
                                val sel = sourceType == s
                                Box(
                                    modifier = Modifier
                                        .background(if (sel) NeonPurple.copy(0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (sel) NeonPurple else GlassBorder.copy(0.3f), RoundedCornerShape(8.dp))
                                        .clickable { sourceType = s }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(s.label(), color = if (sel) NeonPurple else GlassWhite.copy(0.55f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    item {
                        DialogSectionLabel("Source Name")
                        ErrorFormField(sourceName, { sourceName = it }, "e.g. NEET 2023 PYQ, Allen Mock Test 5")
                    }

                    // Answers
                    item {
                        DialogSectionLabel("My Answer (what I wrote)")
                        ErrorFormFieldMulti(myAnswer, { myAnswer = it }, "Write your answer...", 2)
                    }
                    item {
                        DialogSectionLabel("Correct Answer")
                        ErrorFormFieldMulti(correctAnswer, { correctAnswer = it }, "Write correct answer...", 2)
                    }
                    item {
                        DialogSectionLabel("Explanation / Concept to Remember")
                        ErrorFormFieldMulti(explanation, { explanation = it }, "Why is this correct? Key concept...", 3)
                    }

                    // Tags
                    item {
                        DialogSectionLabel("Tags (comma-separated)")
                        ErrorFormField(tagsText, { tagsText = it }, "e.g. optics, refraction, lens")
                    }

                    // Status
                    item {
                        DialogSectionLabel("Status")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ErrorStatus.values().forEach { s ->
                                val sel = status == s
                                Box(
                                    modifier = Modifier
                                        .background(if (sel) s.color().copy(0.22f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (sel) s.color() else GlassBorder, RoundedCornerShape(8.dp))
                                        .clickable { status = s }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(s.label(), color = if (sel) s.color() else GlassWhite.copy(0.6f), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Save Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepNavy)
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (description.isBlank()) return@Button
                            val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val entry = ErrorEntry(
                                id            = existing?.id ?: UUID.randomUUID().toString(),
                                questionNo    = questionNo.trim(),
                                description   = description.trim(),
                                errorType     = errorType,
                                subject       = subject,
                                chapter       = chapter.trim(),
                                sourceType    = sourceType,
                                sourceName    = sourceName.trim(),
                                myAnswer      = myAnswer.trim(),
                                correctAnswer = correctAnswer.trim(),
                                explanation   = explanation.trim(),
                                status        = status,
                                tags          = tags,
                                imageUri      = existing?.imageUri ?: "",
                                revisionCount = existing?.revisionCount ?: 0,
                                lastRevised   = existing?.lastRevised ?: 0L,
                                createdAt     = existing?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(entry)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                        shape = RoundedCornerShape(12.dp),
                        enabled = description.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (existing == null) "Log Error" else "Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogSectionLabel(text: String) {
    Text(text, color = NeonCyan.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ErrorFormField(value: String, onChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = GlassWhite, fontSize = 13.sp),
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSurface, RoundedCornerShape(10.dp))
            .border(1.dp, GlassBorder.copy(0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = GlassWhite.copy(0.3f), fontSize = 13.sp)
            inner()
        }
    )
}

@Composable
private fun ErrorFormFieldMulti(value: String, onChange: (String) -> Unit, hint: String, minLines: Int) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = GlassWhite, fontSize = 13.sp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = (minLines * 24 + 20).dp)
            .background(GlassSurface, RoundedCornerShape(10.dp))
            .border(1.dp, GlassBorder.copy(0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = GlassWhite.copy(0.3f), fontSize = 13.sp)
            inner()
        }
    )
}
