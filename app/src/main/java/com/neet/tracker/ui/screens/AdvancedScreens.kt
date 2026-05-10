package com.neet.tracker.ui.screens

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.fileViewerRoute
import com.neet.tracker.navigation.diagramViewerRoute
import com.neet.tracker.navigation.shortNoteViewerRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.*

// ─── Universal Calendar ────────────────────────────────────────────────────────

@Composable
fun UniversalCalendarScreen(navController: NavController) {
    val eventVm: DateEventViewModel = hiltViewModel()
    val diaryVm: DiaryViewModel = hiltViewModel()
    val dayPlannerVm: PlannerViewModel = hiltViewModel()

    val dateEvents    by eventVm.allEvents.collectAsState()
    val diaryEntries  by diaryVm.entries.collectAsState()
    val plannerEntries by dayPlannerVm.dayEntries.collectAsState()

    val allDates = (dateEvents.map { it.date } + diaryEntries.map { it.date } + plannerEntries.map { it.date }).distinct().sorted()

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Universe Calendar", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (allDates.isEmpty()) {
                    item { EmptyState("No events found in any module yet.\nAdd events, plans, or diary entries to see them here.", Icons.Default.Today) }
                }
                items(allDates) { date ->
                    val eventsForDate  = dateEvents.filter { it.date == date }
                    val diariesForDate = diaryEntries.filter { it.date == date }
                    val plansForDate   = plannerEntries.filter { it.date == date }
                    val total = eventsForDate.size + diariesForDate.size + plansForDate.size

                    GlassCard(glowColor = NeonCyan, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ThreeDIconBox(icon = Icons.Default.CalendarToday, tint = NeonCyan, size = 46.dp, iconSize = 24.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(date, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(NeonCyan.copy(0.15f), RoundedCornerShape(10.dp))
                                        .border(0.5.dp, NeonCyan.copy(0.4f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("$total items", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            NeonDivider(NeonCyan.copy(0.3f))
                            if (eventsForDate.isNotEmpty())  CalendarSection("Events",    NeonGreen, eventsForDate.map { it.name })
                            if (diariesForDate.isNotEmpty()) CalendarSection("Diary",     NeonGold,  diariesForDate.map { it.nickName.ifBlank { "Diary Entry" } })
                            if (plansForDate.isNotEmpty())   CalendarSection("Day Plan",  NeonCyan,  plansForDate.flatMap { it.events.map { e -> e.name } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarSection(label: String, color: Color, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(6.dp, 18.dp).background(color, RoundedCornerShape(3.dp)))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.ExtraBold)
        }
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(color.copy(0.06f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(5.dp).background(color, CircleShape))
                Text(item.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.75f))
            }
        }
    }
}

// ─── NEET Sequence — 3D Connected Rectangle Boxes ─────────────────────────────

@Composable
fun NeetSequenceScreen(navController: NavController, vm: NeetSequenceViewModel = hiltViewModel()) {
    val sequence  by vm.sequence.collectAsState()
    val sequencePdf by vm.sequencePdf.collectAsState()
    var showAdd   by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStatus  by remember { mutableStateOf<NeetSequence?>(null) }
    var showRemark  by remember { mutableStateOf<NeetSequence?>(null) }
    var showTags    by remember { mutableStateOf<NeetSequence?>(null) }
    var showPdfMenu by remember { mutableStateOf(false) }

    val filtered = sequence.filter { searchQuery.isBlank() || it.chapterName.contains(searchQuery, true) }

    // Summary stats
    val totalCount    = filtered.size
    val completedCount = filtered.count { it.status == Status.COMPLETED }
    val expectedCount  = filtered.count { it.status == Status.EXPECTED }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPdf = sequencePdf?.fileUri?.isNotBlank() == true

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                vm.saveSequencePdf(localPath ?: u.toString())
            }
        }
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonPurple) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(
                title = "NEET Sequence",
                breadcrumb = "Home",
                onBack = { navController.popBackStack() },
                actions = {
                    Box {
                        val infiniteTransition = rememberInfiniteTransition(label = "pdf_btn")
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = if (hasPdf) 0.55f else 0.25f,
                            targetValue  = if (hasPdf) 0.90f else 0.50f,
                            animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
                            label = "pdf_glow"
                        )
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .shadow(
                                    if (hasPdf) 12.dp else 6.dp,
                                    RoundedCornerShape(13.dp),
                                    spotColor = NeonPurple.copy(if (hasPdf) 0.6f else 0.3f)
                                )
                                .background(
                                    Brush.linearGradient(
                                        if (hasPdf)
                                            listOf(NeonPurple.copy(0.35f), NeonPurple.copy(0.12f))
                                        else
                                            listOf(NeonPurple.copy(0.16f), NeonPurple.copy(0.06f))
                                    ),
                                    RoundedCornerShape(13.dp)
                                )
                                .border(
                                    if (hasPdf) 1.dp else 0.5.dp,
                                    Brush.linearGradient(
                                        if (hasPdf)
                                            listOf(NeonPurple.copy(glowAlpha), Color.White.copy(0.30f), NeonPurple.copy(glowAlpha * 0.6f))
                                        else
                                            listOf(Color.White.copy(0.25f), NeonPurple.copy(0.40f))
                                    ),
                                    RoundedCornerShape(13.dp)
                                )
                                .clickable { showPdfMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (hasPdf) Icons.Default.PictureAsPdf else Icons.Default.UploadFile,
                                null,
                                tint = NeonPurple.copy(if (hasPdf) glowAlpha else 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPdfMenu,
                            onDismissRequest = { showPdfMenu = false },
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF0D1830), Color(0xFF060E20))),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(0.5.dp, NeonPurple.copy(0.35f), RoundedCornerShape(14.dp))
                        ) {
                            if (hasPdf) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.size(30.dp)
                                                    .background(NeonPurple.copy(0.15f), RoundedCornerShape(9.dp))
                                                    .border(0.5.dp, NeonPurple.copy(0.45f), RoundedCornerShape(9.dp)),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.PictureAsPdf, null, tint = NeonPurple, modifier = Modifier.size(15.dp)) }
                                            Text("View PDF", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = {
                                        showPdfMenu = false
                                        navController.navigate(fileViewerRoute(sequencePdf!!.fileUri, "NEET Sequence"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.size(30.dp)
                                                    .background(NeonCyan.copy(0.12f), RoundedCornerShape(9.dp))
                                                    .border(0.5.dp, NeonCyan.copy(0.4f), RoundedCornerShape(9.dp)),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.UploadFile, null, tint = NeonCyan, modifier = Modifier.size(15.dp)) }
                                            Text("Replace PDF", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = { showPdfMenu = false; pdfLauncher.launch(arrayOf("application/pdf")) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.size(30.dp)
                                                    .background(NeonRed.copy(0.12f), RoundedCornerShape(9.dp))
                                                    .border(0.5.dp, NeonRed.copy(0.4f), RoundedCornerShape(9.dp)),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.DeleteOutline, null, tint = NeonRed, modifier = Modifier.size(15.dp)) }
                                            Text("Remove PDF", color = NeonRed.copy(0.85f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = { showPdfMenu = false; vm.clearSequencePdf() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.size(30.dp)
                                                    .background(NeonPurple.copy(0.15f), RoundedCornerShape(9.dp))
                                                    .border(0.5.dp, NeonPurple.copy(0.45f), RoundedCornerShape(9.dp)),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.UploadFile, null, tint = NeonPurple, modifier = Modifier.size(15.dp)) }
                                            Text("Upload Sequence PDF", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = { showPdfMenu = false; pdfLauncher.launch(arrayOf("application/pdf")) }
                                )
                            }
                        }
                    }
                }
            )

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))

                // Stats row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SequenceStatChip("Total", "$totalCount", NeonCyan, Modifier.weight(1f))
                    SequenceStatChip("Done", "$completedCount", StatusCompleted, Modifier.weight(1f))
                    SequenceStatChip("Pending", "$expectedCount", StatusExpected, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))

                if (filtered.isEmpty()) {
                    EmptyState("Build your NEET study sequence.\nTap + to add chapters.", Icons.Default.LinearScale)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        itemsIndexed(filtered) { index, seq ->
                            Sequence3DItem(
                                seq      = seq,
                                index    = index,
                                isLast   = index == filtered.lastIndex,
                                onStatusClick = { showStatus = seq },
                                onRemarkClick = { showRemark = seq },
                                onTagClick    = { showTags   = seq },
                                onDelete      = { vm.delete(seq) }
                            )
                        }
                    }
                }
            }
        }

    }

    if (showAdd) AddSequenceDialog(
        nextSerial = (sequence.maxOfOrNull { it.serialNo } ?: 0) + 1,
        onSave = { vm.save(it); showAdd = false },
        onDismiss = { showAdd = false }
    )
    showStatus?.let { s -> StatusSelectorDialog(s.status, onSelect = { vm.save(s.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showRemark?.let { s -> RemarkDialog(s.remark, onSave  = { vm.save(s.copy(remark = it)); showRemark  = null }, onDismiss = { showRemark  = null }) }
    showTags?.let   { s -> TagDialog(s.tags,   onSave    = { vm.save(s.copy(tags   = it)); showTags    = null }, onDismiss = { showTags    = null }) }
}

@Composable
private fun SequenceStatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = color.copy(0.3f))
            .background(color.copy(0.1f), RoundedCornerShape(14.dp))
            .border(0.5.dp, color.copy(0.4f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(0.7f))
    }
}

// ─── 3D Connected Rectangle Box Item ──────────────────────────────────────────

@Composable
fun Sequence3DItem(
    seq: NeetSequence,
    index: Int,
    isLast: Boolean,
    onStatusClick: () -> Unit,
    onRemarkClick: () -> Unit,
    onTagClick:    () -> Unit,
    onDelete:      () -> Unit
) {
    val statusColor = when (seq.status) {
        Status.COMPLETED -> StatusCompleted
        Status.EXPECTED  -> StatusExpected
        Status.REVISION  -> StatusRevision
        Status.CROSSED   -> StatusCross
    }
    val subjColor = when (seq.subject) {
        Subject.PHYSICS   -> NeonCyan
        Subject.CHEMISTRY -> NeonPurple
        Subject.BOTANY    -> NeonGreen
        Subject.ZOOLOGY   -> NeonOrange
        else              -> NeonGold
    }

    val infiniteTransition = rememberInfiniteTransition(label = "seq_${seq.id}")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween((2000..3000).random(), easing = EaseInOutSine), RepeatMode.Reverse),
        label = "seq_glow"
    )

    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {

        // ── Left connector column ──────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
            // 3D serial number box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(10.dp, RoundedCornerShape(12.dp), spotColor = statusColor.copy(0.4f))
                    .background(
                        Brush.linearGradient(listOf(statusColor.copy(0.30f), statusColor.copy(0.08f))),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.5.dp, statusColor.copy(glowPulse + 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${seq.serialNo}",
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Connector line with animated glow
            if (!isLast) {
                Box(
                    modifier = Modifier.width(3.dp).height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(statusColor.copy(glowPulse * 1.2f), statusColor.copy(glowPulse * 0.3f), Color.Transparent)
                            )
                        )
                )
                // Connector dot
                Box(
                    modifier = Modifier.size(7.dp)
                        .background(statusColor.copy(glowPulse * 0.8f), CircleShape)
                        .border(1.dp, statusColor.copy(0.5f), CircleShape)
                )
                Box(modifier = Modifier.width(3.dp).height(14.dp).background(
                    Brush.verticalGradient(colors = listOf(statusColor.copy(glowPulse * 0.4f), Color.Transparent))
                ))
            }
        }

        Spacer(Modifier.width(10.dp))

        // ── 3D Rectangle Card ──────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).padding(bottom = if (!isLast) 4.dp else 0.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = statusColor.copy(0.22f), ambientColor = statusColor.copy(0.06f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(statusColor.copy(0.13f), Color(0xFF080F20), statusColor.copy(0.05f)),
                            start = Offset(0f, 0f),
                            end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(statusColor.copy(glowPulse + 0.05f), Color.White.copy(0.04f), statusColor.copy(glowPulse * 0.25f))),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { expanded = !expanded }
            ) {
                // Corner glow
                Box(modifier = Modifier.size(70.dp).align(Alignment.TopEnd)
                    .background(Brush.radialGradient(listOf(statusColor.copy(glowPulse * 0.35f), Color.Transparent)), RoundedCornerShape(18.dp)))

                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Header row — chapter + subject badge
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                seq.chapterName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = if (expanded) Int.MAX_VALUE else 2
                            )
                        }
                        // Subject badge
                        Box(
                            modifier = Modifier
                                .background(subjColor.copy(0.18f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, subjColor.copy(0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(seq.subject.name, style = MaterialTheme.typography.labelSmall, color = subjColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    // Status + Action row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(seq.status)
                        Spacer(Modifier.weight(1f))
                        // Expand/collapse
                        Box(
                            modifier = Modifier.size(28.dp)
                                .background(Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(8.dp))
                                .clickable { expanded = !expanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
                        }
                    }

                    // Expanded content
                    AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeonDivider(statusColor.copy(0.3f))

                            // Action buttons row
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Sequence3DActionButton("Status", Icons.Default.ToggleOn, statusColor, Modifier.weight(1f), onStatusClick)
                                Sequence3DActionButton("Remark", Icons.Default.StickyNote2, NeonGold, Modifier.weight(1f), onRemarkClick)
                                Sequence3DActionButton("Tags",   Icons.Default.LocalOffer,  NeonPurple, Modifier.weight(1f), onTagClick)
                                Sequence3DActionButton("Delete", Icons.Default.Delete,      NeonRed,    Modifier.weight(1f), onDelete)
                            }

                            // Remark
                            if (seq.remark.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(NeonGold.copy(0.07f), RoundedCornerShape(10.dp)).border(0.5.dp, NeonGold.copy(0.25f), RoundedCornerShape(10.dp)).padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.StickyNote2, null, tint = NeonGold.copy(0.7f), modifier = Modifier.size(14.dp))
                                    Text(seq.remark, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.65f))
                                }
                            }

                            // Tags
                            if (seq.tags.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(seq.tags) { t -> TagChip(t) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sequence3DActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(10.dp), spotColor = color.copy(0.25f))
            .background(color.copy(0.1f), RoundedCornerShape(10.dp))
            .border(0.5.dp, color.copy(0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
    }
}

@Composable
fun AddSequenceDialog(nextSerial: Int, onSave: (NeetSequence) -> Unit, onDismiss: () -> Unit) {
    var name    by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Subject.GENERAL) }
    NEETDialog(title = "Add to Sequence #$nextSerial", icon = Icons.Default.LinearScale, accentColor = NeonPurple, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Chapter Name", icon = Icons.Default.Article, accentColor = NeonPurple)
            Text("Select Subject", style = MaterialTheme.typography.labelLarge, color = NeonPurple, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Subject.values().toList()) { s ->
                    val sColor = when (s) { Subject.PHYSICS -> NeonCyan; Subject.CHEMISTRY -> NeonPurple; Subject.BOTANY -> NeonGreen; Subject.ZOOLOGY -> NeonOrange; else -> NeonGold }
                    val selected = subject == s
                    Box(
                        modifier = Modifier
                            .let { if (selected) it.shadow(6.dp, RoundedCornerShape(12.dp), spotColor = sColor.copy(0.4f)) else it }
                            .background(if (selected) sColor.copy(0.22f) else Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                            .border(if (selected) 1.dp else 0.5.dp, if (selected) sColor else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                            .clickable { subject = s }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(s.name, style = MaterialTheme.typography.labelMedium, color = if (selected) sColor else Color.White.copy(0.5f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (name.isNotBlank()) onSave(NeetSequence(serialNo = nextSerial, chapterName = name, subject = subject)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) { Text("Add", color = NeonPurple, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Diagrams Subject Screen ──────────────────────────────────────────────────

@Composable
fun DiagramsSubjectScreen(navController: NavController, subject: String, vm: DiagramViewModel = hiltViewModel()) {
    val diagrams by vm.diagramsFor(subject).collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val color = if (subject == "BOTANY") NeonGreen else NeonOrange
    val filtered = diagrams.filter { searchQuery.isBlank() || it.chapter.contains(searchQuery, true) }

    var uploadTarget by remember { mutableStateOf<Diagram?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadTarget
        uploadTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { d -> vm.save(d.copy(fileUri = localPath ?: u.toString())) }
            }
        }
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = color) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "$subject Diagrams", breadcrumb = "Home / Diagrams Atlas", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No diagrams yet. Tap + to add.", Icons.Default.AccountTree)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { d ->
                        NEETCard(
                            title = d.chapter,
                            icon  = if (d.fileUri.isNotBlank()) Icons.Default.PictureAsPdf else if (subject == "BOTANY") Icons.Default.Park else Icons.Default.Pets,
                            glowColor = color,
                            onClick = { if (d.fileUri.isNotBlank()) navController.navigate(diagramViewerRoute(subject, d.fileUri, d.chapter)) },
                            bottomContent = {
                                CardIconButton(
                                    if (d.fileUri.isNotBlank()) Icons.Default.PictureAsPdf else Icons.Default.UploadFile,
                                    if (d.fileUri.isNotBlank()) color.copy(0.85f) else color.copy(0.4f)
                                ) {
                                    if (d.fileUri.isNotBlank()) navController.navigate(diagramViewerRoute(subject, d.fileUri, d.chapter))
                                    else { uploadTarget = d; pdfLauncher.launch(arrayOf("application/pdf")) }
                                }
                                if (d.fileUri.isNotBlank()) {
                                    CardIconButton(Icons.Default.UploadFile, color.copy(0.4f)) { uploadTarget = d; pdfLauncher.launch(arrayOf("application/pdf")) }
                                    CardIconButton(Icons.Default.Close, NeonRed.copy(0.65f)) { vm.save(d.copy(fileUri = "")) }
                                }
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f)) { vm.delete(d) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) AddDiagramDialog(subject = subject, color = color, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun AddDiagramDialog(subject: String, color: Color, onSave: (Diagram) -> Unit, onDismiss: () -> Unit) {
    var chapter by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                fileUri = localPath ?: u.toString()
            }
        }
    }
    NEETDialog(title = "Add Diagram", icon = Icons.Default.AccountTree, accentColor = color, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = chapter, onValueChange = { chapter = it }, label = "Chapter Name", icon = Icons.Default.Article, accentColor = color)
            Button(onClick = { launcher.launch(arrayOf("application/pdf")) }, colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.12f)), border = BorderStroke(1.dp, color.copy(0.4f)), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, null, tint = color); Spacer(Modifier.width(8.dp))
                Text(if (fileUri.isBlank()) "Upload Diagram PDF" else "✓ PDF Uploaded", color = color)
            }
            if (fileUri.isNotBlank()) {
                OutlinedButton(onClick = { fileUri = "" }, border = BorderStroke(1.dp, NeonRed.copy(0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Remove PDF", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (chapter.isNotBlank()) onSave(Diagram(chapter = chapter, subject = subject, fileUri = fileUri)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.2f)), border = BorderStroke(1.dp, color.copy(0.6f))) { Text("Add", color = color, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Chapter Short Notes Subject ──────────────────────────────────────────────

@Composable
fun ChapterShortNotesSubjectScreen(navController: NavController, subject: String, vm: ShortNoteViewModel = hiltViewModel()) {
    val notes by vm.notesFor(subject).collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val color = when (subject) { "PHYSICS" -> NeonCyan; "CHEMISTRY" -> NeonPurple; "BOTANY" -> NeonGreen; else -> NeonOrange }
    val filtered = notes.filter { searchQuery.isBlank() || it.chapter.contains(searchQuery, true) }

    var uploadTarget by remember { mutableStateOf<ChapterShortNote?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadTarget
        uploadTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { n -> vm.save(n.copy(fileUri = localPath ?: u.toString())) }
            }
        }
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = color) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "$subject Notes", breadcrumb = "Home / Chapter Notes", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No short notes yet. Tap + to add.", Icons.Default.Article)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { n ->
                        NEETCard(
                            title = n.chapter,
                            icon  = if (n.fileUri.isNotBlank()) Icons.Default.PictureAsPdf else Icons.Default.Article,
                            glowColor = color,
                            onClick = { if (n.fileUri.isNotBlank()) navController.navigate(shortNoteViewerRoute(subject, n.fileUri, n.chapter)) },
                            bottomContent = {
                                CardIconButton(
                                    if (n.fileUri.isNotBlank()) Icons.Default.PictureAsPdf else Icons.Default.UploadFile,
                                    if (n.fileUri.isNotBlank()) color.copy(0.85f) else color.copy(0.4f)
                                ) {
                                    if (n.fileUri.isNotBlank()) navController.navigate(shortNoteViewerRoute(subject, n.fileUri, n.chapter))
                                    else { uploadTarget = n; pdfLauncher.launch(arrayOf("application/pdf")) }
                                }
                                if (n.fileUri.isNotBlank()) {
                                    CardIconButton(Icons.Default.UploadFile, color.copy(0.4f)) { uploadTarget = n; pdfLauncher.launch(arrayOf("application/pdf")) }
                                    CardIconButton(Icons.Default.Close, NeonRed.copy(0.65f)) { vm.save(n.copy(fileUri = "")) }
                                }
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f)) { vm.delete(n) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) AddChapterNoteDialog(subject = subject, color = color, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun AddChapterNoteDialog(subject: String, color: Color, onSave: (ChapterShortNote) -> Unit, onDismiss: () -> Unit) {
    var chapter by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf("") }
    val subjectEnum = try { Subject.valueOf(subject) } catch (e: Exception) { Subject.GENERAL }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                fileUri = localPath ?: u.toString()
            }
        }
    }
    NEETDialog(title = "Add Short Notes", icon = Icons.Default.Article, accentColor = color, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = chapter, onValueChange = { chapter = it }, label = "Chapter Name", icon = Icons.Default.MenuBook, accentColor = color)
            Button(onClick = { launcher.launch(arrayOf("application/pdf")) }, colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.12f)), border = BorderStroke(1.dp, color.copy(0.4f)), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, null, tint = color); Spacer(Modifier.width(8.dp))
                Text(if (fileUri.isBlank()) "Upload Notes PDF" else "✓ PDF Uploaded", color = color)
            }
            if (fileUri.isNotBlank()) {
                OutlinedButton(onClick = { fileUri = "" }, border = BorderStroke(1.dp, NeonRed.copy(0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Remove PDF", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (chapter.isNotBlank()) onSave(ChapterShortNote(chapter = chapter, subject = subjectEnum, fileUri = fileUri)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.2f)), border = BorderStroke(1.dp, color.copy(0.6f))) { Text("Add", color = color, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
