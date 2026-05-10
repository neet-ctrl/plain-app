package com.neet.tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.fileViewerRoute
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

    val dateEvents by eventVm.allEvents.collectAsState()
    val diaryEntries by diaryVm.entries.collectAsState()
    val plannerEntries by dayPlannerVm.dayEntries.collectAsState()

    val allDates = (dateEvents.map { it.date } + diaryEntries.map { it.date } + plannerEntries.map { it.date }).distinct().sorted()
    val months = allDates.map { it.takeLast(7) }.distinct()

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Universe Calendar", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                if (allDates.isEmpty()) {
                    item { EmptyState("No events found in any module yet.\nAdd events, plans, or diary entries to see them here.", Icons.Default.Today) }
                }
                items(allDates) { date ->
                    val eventsForDate = dateEvents.filter { it.date == date }
                    val diariesForDate = diaryEntries.filter { it.date == date }
                    val plansForDate = plannerEntries.filter { it.date == date }

                    GlassCard(glowColor = NeonCyan, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(40.dp).background(NeonCyan.copy(0.15f), RoundedCornerShape(10.dp)).border(1.dp, NeonCyan.copy(0.4f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CalendarToday, null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                                }
                                Text(date, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.weight(1f))
                                val total = eventsForDate.size + diariesForDate.size + plansForDate.size
                                Box(modifier = Modifier.background(NeonCyan.copy(0.15f), RoundedCornerShape(8.dp)).border(0.5.dp, NeonCyan.copy(0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("$total items", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (eventsForDate.isNotEmpty()) {
                                CalendarSection("Events", NeonGreen, eventsForDate.map { it.name })
                            }
                            if (diariesForDate.isNotEmpty()) {
                                CalendarSection("Diary", NeonGold, diariesForDate.map { it.nickName.ifBlank { "Diary Entry" } })
                            }
                            if (plansForDate.isNotEmpty()) {
                                CalendarSection("Day Plan", NeonCyan, plansForDate.flatMap { it.events.map { e -> e.name } })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarSection(label: String, color: Color, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(5.dp).background(color, CircleShape))
                Text(item.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
            }
        }
    }
}

// ─── NEET Sequence ────────────────────────────────────────────────────────────

@Composable
fun NeetSequenceScreen(navController: NavController, vm: NeetSequenceViewModel = hiltViewModel()) {
    val sequence by vm.sequence.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStatus by remember { mutableStateOf<NeetSequence?>(null) }
    var showRemark by remember { mutableStateOf<NeetSequence?>(null) }
    var showTags by remember { mutableStateOf<NeetSequence?>(null) }

    val filtered = sequence.filter { searchQuery.isBlank() || it.chapterName.contains(searchQuery, true) }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "NEET Sequence", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))
                Text("${filtered.size} chapters in sequence", style = MaterialTheme.typography.bodySmall, color = NeonCyan.copy(0.6f))
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) EmptyState("Build your NEET study sequence.\nTap + to add chapters.", Icons.Default.LinearScale)
                else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                        itemsIndexed(filtered) { index, seq ->
                            SequenceItem(seq = seq, isLast = index == filtered.lastIndex,
                                onStatusClick = { showStatus = seq },
                                onRemarkClick = { showRemark = seq },
                                onTagClick = { showTags = seq },
                                onDelete = { vm.delete(seq) }
                            )
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) { NeonFAB(onClick = { showAdd = true }, color = NeonPurple) }
    }
    if (showAdd) AddSequenceDialog(nextSerial = (sequence.maxOfOrNull { it.serialNo } ?: 0) + 1, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
    showStatus?.let { s -> StatusSelectorDialog(s.status, onSelect = { vm.save(s.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showRemark?.let { s -> RemarkDialog(s.remark, onSave = { vm.save(s.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
    showTags?.let { s -> TagDialog(s.tags, onSave = { vm.save(s.copy(tags = it)); showTags = null }, onDismiss = { showTags = null }) }
}

@Composable
fun SequenceItem(seq: NeetSequence, isLast: Boolean, onStatusClick: () -> Unit, onRemarkClick: () -> Unit, onTagClick: () -> Unit, onDelete: () -> Unit) {
    val statusColor = when (seq.status) { Status.COMPLETED -> StatusCompleted; Status.EXPECTED -> StatusExpected; Status.REVISION -> StatusRevision; Status.CROSSED -> StatusCross }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        // Connector line
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(modifier = Modifier.size(32.dp).background(statusColor.copy(0.2f), CircleShape).border(2.dp, statusColor, CircleShape), contentAlignment = Alignment.Center) {
                Text("${seq.serialNo}", style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.ExtraBold)
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(24.dp).background(Brush.verticalGradient(colors = listOf(statusColor.copy(0.5f), Color.Transparent))))
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(statusColor.copy(0.08f))
            .border(0.5.dp, statusColor.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(seq.chapterName, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(seq.subject.name, style = MaterialTheme.typography.labelSmall, color = NeonGold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(seq.status)
                    Spacer(Modifier.weight(1f))
                    CardIconButton(Icons.Default.ToggleOn, statusColor, onStatusClick)
                    CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.7f), onRemarkClick)
                    CardIconButton(Icons.Default.LocalOffer, NeonPurple.copy(0.7f), onTagClick)
                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f), onDelete)
                }
                if (seq.tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(seq.tags) { t -> TagChip(t) } }
                }
            }
        }
    }
}

@Composable
fun AddSequenceDialog(nextSerial: Int, onSave: (NeetSequence) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Subject.GENERAL) }
    NEETDialog(title = "Add to Sequence #$nextSerial", icon = Icons.Default.LinearScale, accentColor = NeonPurple, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Chapter Name", icon = Icons.Default.Article, accentColor = NeonPurple)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Subject.values().forEach { s -> FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s.name, fontSize = 10.sp) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (name.isNotBlank()) onSave(NeetSequence(serialNo = nextSerial, chapterName = name, subject = subject)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) { Text("Add", color = NeonPurple, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Diagrams Subject Screen ───────────────────────────────────────────────────

@Composable
fun DiagramsSubjectScreen(navController: NavController, subject: String, vm: DiagramViewModel = hiltViewModel()) {
    val diagrams by vm.diagramsFor(subject).collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val color = if (subject == "BOTANY") NeonGreen else NeonOrange
    val filtered = diagrams.filter { searchQuery.isBlank() || it.chapter.contains(searchQuery, true) }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "$subject Diagrams", breadcrumb = "Home / Diagrams", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No diagrams yet. Tap + to add.", Icons.Default.AccountTree)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { d ->
                        NEETCard(title = d.chapter, icon = if (subject == "BOTANY") Icons.Default.Park else Icons.Default.Pets, glowColor = color,
                            onClick = { if (d.fileUri.isNotBlank()) navController.navigate(fileViewerRoute(d.fileUri, d.chapter)) },
                            bottomContent = {
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f)) { vm.delete(d) }
                            }
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) { NeonFAB(onClick = { showAdd = true }, color = color) }
    }
    if (showAdd) AddDiagramDialog(subject = subject, color = color, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun AddDiagramDialog(subject: String, color: Color, onSave: (Diagram) -> Unit, onDismiss: () -> Unit) {
    var chapter by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { fileUri = it.toString() } }
    NEETDialog(title = "Add Diagram", icon = Icons.Default.AccountTree, accentColor = color, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = chapter, onValueChange = { chapter = it }, label = "Chapter Name", icon = Icons.Default.Article, accentColor = color)
            Button(onClick = { launcher.launch("application/pdf") }, colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.12f)), border = BorderStroke(1.dp, color.copy(0.4f)), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, null, tint = color); Spacer(Modifier.width(8.dp)); Text(if (fileUri.isBlank()) "Upload Diagram PDF" else "✓ PDF Uploaded", color = color)
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

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "$subject Notes", breadcrumb = "Home / Chapter Notes", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No short notes yet. Tap + to add.", Icons.Default.Article)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { n ->
                        NEETCard(title = n.chapter, icon = Icons.Default.Article, glowColor = color,
                            onClick = { if (n.fileUri.isNotBlank()) navController.navigate(fileViewerRoute(n.fileUri, n.chapter)) },
                            bottomContent = { CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f)) { vm.delete(n) } }
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) { NeonFAB(onClick = { showAdd = true }, color = color) }
    }
    if (showAdd) AddChapterNoteDialog(subject = subject, color = color, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun AddChapterNoteDialog(subject: String, color: Color, onSave: (ChapterShortNote) -> Unit, onDismiss: () -> Unit) {
    var chapter by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf("") }
    val subjectEnum = try { Subject.valueOf(subject) } catch (e: Exception) { Subject.GENERAL }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { fileUri = it.toString() } }
    NEETDialog(title = "Add Short Notes", icon = Icons.Default.Article, accentColor = color, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = chapter, onValueChange = { chapter = it }, label = "Chapter Name", icon = Icons.Default.MenuBook, accentColor = color)
            Button(onClick = { launcher.launch("application/pdf") }, colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.12f)), border = BorderStroke(1.dp, color.copy(0.4f)), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, null, tint = color); Spacer(Modifier.width(8.dp)); Text(if (fileUri.isBlank()) "Upload Notes PDF" else "✓ PDF Uploaded", color = color)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (chapter.isNotBlank()) onSave(ChapterShortNote(chapter = chapter, subject = subjectEnum, fileUri = fileUri)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.2f)), border = BorderStroke(1.dp, color.copy(0.6f))) { Text("Add", color = color, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
