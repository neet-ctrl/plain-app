package com.neet.tracker.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.alarm.AlarmScheduler
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.diaryEntryRoute
import com.neet.tracker.navigation.dateEventDetailRoute
import com.neet.tracker.navigation.fileViewerRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.DiaryViewModel
import com.neet.tracker.ui.viewmodels.DateEventViewModel
import androidx.compose.foundation.text.BasicTextField
import java.text.SimpleDateFormat
import java.util.*

// ─── Daily Diary ─────────────────────────────────────────────────────────────

@Composable
fun DailyDiaryScreen(navController: NavController, vm: DiaryViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var newDate by remember { mutableStateOf("") }
    var newNick by remember { mutableStateOf("") }

    val allTags = entries.flatMap { it.tags }.distinct()
    val filtered = entries.filter {
        (searchQuery.isBlank() || it.date.contains(searchQuery, true) || it.nickName.contains(searchQuery, true)) &&
        (selectedTag == null || it.tags.contains(selectedTag))
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, icon = Icons.Default.Create, color = NeonGold) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Daily Diary", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search diary entries...")
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("All") }) }
                        items(allTags) { tag -> FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag }, label = { Text("# $tag") }) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No diary entries yet.\nTap + to start writing.", Icons.Default.MenuBook)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { diary ->
                        DiaryCard(diary = diary,
                            onClick = { navController.navigate(diaryEntryRoute(diary.id)) },
                            onDelete = { vm.delete(diary) }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) {
        NEETDialog(title = "New Diary Entry", icon = Icons.Default.MenuBook, accentColor = NeonGold, onDismiss = { showAdd = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogTextField(value = newDate, onValueChange = { newDate = it }, label = "Date (DD/MM/YYYY)", icon = Icons.Default.CalendarToday, accentColor = NeonGold)
                DialogTextField(value = newNick, onValueChange = { newNick = it }, label = "Nickname (e.g. 'Breakthrough Day')", icon = Icons.Default.Title, accentColor = NeonGold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showAdd = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                    Button(onClick = { if (newDate.isNotBlank()) { vm.save(DailyDiary(date = newDate, nickName = newNick)); showAdd = false; newDate = ""; newNick = "" } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)), border = BorderStroke(1.dp, NeonGold.copy(0.6f))) { Text("Create", color = NeonGold, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun DiaryCard(diary: DailyDiary, onClick: () -> Unit, onDelete: () -> Unit) {
    GlassCard(onClick = onClick, glowColor = NeonGold, modifier = Modifier.aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ThreeDIconBox(icon = Icons.Default.AutoStories, tint = NeonGold, size = 44.dp, iconSize = 24.dp)
                Spacer(Modifier.height(8.dp))
                Text(diary.date, style = MaterialTheme.typography.headlineSmall, color = NeonGold, fontWeight = FontWeight.Bold)
                if (diary.nickName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(diary.nickName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f), maxLines = 2)
                }
                if (diary.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("# ${diary.tags.first()}", style = MaterialTheme.typography.labelSmall, color = NeonPurple)
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CardIconButton(Icons.Default.Edit, NeonGold.copy(0.7f), onClick)
                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.7f), onDelete)
                }
            }
        }
    }
}

// ─── Diary Entry (Full Editor) ────────────────────────────────────────────────

@Composable
fun DiaryEntryScreen(navController: NavController, diaryId: String, vm: DiaryViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsState()
    val diary = entries.find { it.id == diaryId }
    var content by remember(diary) { mutableStateOf(diary?.content ?: "") }
    var showTagDialog by remember { mutableStateOf(false) }

    val emojiTools = listOf("😊", "😔", "💪", "🔥", "📚", "✅", "❌", "🎯", "🌟", "💡", "⚡", "🧠", "📝", "🏆", "😴")

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(
                title = diary?.date ?: "Diary",
                breadcrumb = "Home / Diary",
                onBack = {
                    diary?.let { vm.save(it.copy(content = content)) }
                    navController.popBackStack()
                },
                actions = {
                    IconButton(onClick = { showTagDialog = true }) { Icon(Icons.Default.LocalOffer, null, tint = NeonPurple) }
                    IconButton(onClick = { diary?.let { vm.save(it.copy(content = content)) } }) { Icon(Icons.Default.Save, null, tint = NeonGold) }
                }
            )

            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                GlassCard(glowColor = NeonGold) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.AutoAwesome, null, tint = NeonGold, modifier = Modifier.size(20.dp))
                            Text(diary?.date ?: "", style = MaterialTheme.typography.headlineMedium, color = NeonGold, fontWeight = FontWeight.ExtraBold)
                        }
                        if (diary?.nickName?.isNotBlank() == true) {
                            Text(diary.nickName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(emojiTools) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(NeonGold.copy(0.1f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, NeonGold.copy(0.3f), RoundedCornerShape(10.dp))
                                .clickable { content += emoji },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 18.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                RichTextToolbar(accentColor = NeonGold, onInsert = { content += it })
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonGold.copy(0.04f))
                        .border(1.dp, NeonGold.copy(0.2f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = ExoFont, lineHeight = 24.sp),
                        decorationBox = { inner ->
                            if (content.isEmpty()) Text("Start writing your thoughts, experiences, what you learned today, how you feel...", color = Color.White.copy(0.25f), fontSize = 15.sp, fontFamily = ExoFont, lineHeight = 24.sp)
                            inner()
                        }
                    )
                }
            }
        }
    }
    if (showTagDialog) {
        TagDialog(diary?.tags ?: emptyList(), onSave = { newTags -> diary?.let { vm.save(it.copy(tags = newTags)) } }, onDismiss = { showTagDialog = false })
    }
}

// ─── Date Events ─────────────────────────────────────────────────────────────

@Composable
fun DateEventsScreen(navController: NavController, vm: DateEventViewModel = hiltViewModel()) {
    val events by vm.allEvents.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var newDate by remember { mutableStateOf("") }

    val dates = events.map { it.date }.distinct().filter { searchQuery.isBlank() || it.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonGreen) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Event Log", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search dates...")
                Spacer(Modifier.height(12.dp))
                if (dates.isEmpty()) EmptyState("No event dates yet. Tap + to add.", Icons.Default.EventNote)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(dates) { date ->
                        val count = events.count { it.date == date }
                        val hasAlarm = events.any { it.date == date && it.alarmTime > 0L }
                        NEETCard(
                            title = date,
                            subtitle = "$count events${if (hasAlarm) " • ⏰" else ""}",
                            icon = Icons.Default.EventNote,
                            glowColor = NeonGreen,
                            onClick = { navController.navigate(dateEventDetailRoute(date)) }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) {
        NEETDialog(title = "New Date", icon = Icons.Default.EventNote, accentColor = NeonGreen, onDismiss = { showAdd = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogTextField(value = newDate, onValueChange = { newDate = it }, label = "Date (DD/MM/YYYY)", icon = Icons.Default.CalendarToday, accentColor = NeonGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showAdd = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                    Button(onClick = { if (newDate.isNotBlank()) { vm.save(DateEvent(date = newDate)); showAdd = false; newDate = "" } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.2f)), border = BorderStroke(1.dp, NeonGreen.copy(0.6f))) { Text("Add", color = NeonGreen, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun DateEventDetailScreen(navController: NavController, date: String, vm: DateEventViewModel = hiltViewModel()) {
    val events by vm.eventsForDate(date).collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadEventTarget by remember { mutableStateOf<DateEvent?>(null) }
    val eventFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadEventTarget
        uploadEventTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { e -> vm.save(e.copy(fileUri = localPath ?: u.toString())) }
            }
        }
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonGreen) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = date, breadcrumb = "Home / Events", onBack = { navController.popBackStack() })
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                if (events.isEmpty()) {
                    item { EmptyState("No events for this date. Tap + to add.", Icons.Default.EventNote) }
                }
                itemsIndexed(events) { i, event ->
                    DateEventCard(
                        event = event,
                        index = i + 1,
                        onUpdate = { vm.save(it) },
                        onDelete = { vm.delete(event) },
                        onShiftToNextDate = {
                            val tomorrow = shiftDate(date)
                            vm.save(event.copy(id = java.util.UUID.randomUUID().toString(), date = tomorrow))
                        },
                        onViewFile = if (event.fileUri.isNotBlank()) { { navController.navigate(fileViewerRoute(event.fileUri, event.name.ifBlank { "Event ${i + 1}" })) } } else null,
                        onUploadFile = { uploadEventTarget = event; eventFileLauncher.launch(arrayOf("*/*")) }
                    )
                }
                item {
                    Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.12f)), border = BorderStroke(1.dp, NeonGreen.copy(0.4f))) {
                        Icon(Icons.Default.Add, null, tint = NeonGreen); Spacer(Modifier.width(8.dp)); Text("Add Event", color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showAdd) {
        AddDateEventDialog(date = date, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
    }
}

@Composable
fun DateEventCard(
    event: DateEvent,
    index: Int,
    onUpdate: (DateEvent) -> Unit,
    onDelete: () -> Unit,
    onShiftToNextDate: () -> Unit,
    onViewFile: (() -> Unit)? = null,
    onUploadFile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by remember(event) { mutableStateOf(event.name) }
    var detail by remember(event) { mutableStateOf(event.detail) }
    var remark by remember(event) { mutableStateOf(event.remark) }
    var status by remember(event) { mutableStateOf(event.status) }
    var crossReason by remember(event) { mutableStateOf(event.crossReason) }
    var showCrossInput by remember { mutableStateOf(status == "CROSSED") }
    var alarmTime by remember(event) { mutableStateOf(event.alarmTime) }
    var alarmLabel by remember(event) { mutableStateOf(event.alarmLabel) }

    val statusColor = when (status) { "COMPLETED" -> StatusCompleted; "CROSSED" -> StatusCross; else -> NeonGreen }
    val alarmDisplayText = if (alarmTime > 0L) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(alarmTime))
    } else ""

    GlassCard(glowColor = statusColor, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Event $index", style = MaterialTheme.typography.headlineSmall, color = statusColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (alarmTime > 0L) {
                    Icon(Icons.Default.AlarmOn, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(16.dp)) }
            }
            NeonDivider(statusColor)
            DialogTextField(value = name, onValueChange = { name = it; onUpdate(event.copy(name = name)) }, label = "Event Name", icon = Icons.Default.Event, accentColor = statusColor)
            DialogTextField(value = detail, onValueChange = { detail = it; onUpdate(event.copy(detail = detail)) }, label = "Details", icon = Icons.Default.Description, accentColor = statusColor, multiline = true)
            if (event.url.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Link, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                    Text(event.url, style = MaterialTheme.typography.labelSmall, color = NeonCyan, maxLines = 1)
                }
            }
            if (event.totalQuestions > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Quiz, null, tint = NeonPurple, modifier = Modifier.size(14.dp))
                    Text("${event.totalQuestions} questions", style = MaterialTheme.typography.labelSmall, color = NeonPurple)
                }
            }

            // ─── Alarm / Reminder Row ──────────────────────────────────────────
            NeonDivider(NeonGold.copy(0.3f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Alarm, null, tint = NeonGold, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reminder / Alarm", style = MaterialTheme.typography.labelMedium, color = NeonGold, fontWeight = FontWeight.SemiBold)
                    if (alarmDisplayText.isNotEmpty()) {
                        Text(alarmDisplayText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                    } else {
                        Text("No alarm set", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
                    }
                }
                if (alarmTime > 0L) {
                    IconButton(
                        onClick = {
                            AlarmScheduler.cancelAlarm(context, event.id.hashCode())
                            alarmTime = 0L
                            alarmLabel = ""
                            onUpdate(event.copy(alarmTime = 0L, alarmLabel = ""))
                        },
                        modifier = Modifier.size(32.dp)
                    ) { Icon(Icons.Default.AlarmOff, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(18.dp)) }
                }
                IconButton(
                    onClick = {
                        val now = Calendar.getInstance()
                        TimePickerDialog(context, { _, hour, minute ->
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            alarmTime = cal.timeInMillis
                            alarmLabel = name.ifBlank { "Event $index" }
                            AlarmScheduler.scheduleAlarm(context, event.id.hashCode(), cal.timeInMillis, "NEET Event Reminder", alarmLabel.ifBlank { "Scheduled event coming up!" })
                            onUpdate(event.copy(alarmTime = cal.timeInMillis, alarmLabel = alarmLabel))
                        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Default.AddAlarm, null, tint = NeonGold, modifier = Modifier.size(18.dp)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusTickCrossButton("✓ Done", status == "COMPLETED", StatusCompleted) {
                    status = if (status == "COMPLETED") "EXPECTED" else "COMPLETED"
                    showCrossInput = false
                    onUpdate(event.copy(status = status))
                }
                StatusTickCrossButton("✗ Missed", status == "CROSSED", StatusCross) {
                    status = if (status == "CROSSED") "EXPECTED" else "CROSSED"
                    showCrossInput = status == "CROSSED"
                    onUpdate(event.copy(status = status))
                }
            }
            if (showCrossInput) {
                DialogTextField(value = crossReason, onValueChange = { crossReason = it; onUpdate(event.copy(crossReason = crossReason)) }, label = "Reason for missing", icon = Icons.Default.ReportProblem, accentColor = StatusCross)
                Button(
                    onClick = onShiftToNextDate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(0.15f)),
                    border = BorderStroke(1.dp, NeonOrange.copy(0.5f))
                ) {
                    Icon(Icons.Default.ArrowForward, null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shift to Next Date", color = NeonOrange, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (onViewFile != null || onUploadFile != null) {
                NeonDivider(NeonGreen.copy(0.2f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (event.fileUri.isNotBlank() && onViewFile != null) {
                        OutlinedButton(
                            onClick = onViewFile,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NeonGreen.copy(0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("View File", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = onUploadFile ?: {}, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.UploadFile, tint = NeonGold.copy(0.5f), modifier = Modifier.size(16.dp), contentDescription = "Replace file")
                        }
                    } else if (onUploadFile != null) {
                        OutlinedButton(
                            onClick = onUploadFile,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NeonGold.copy(0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGold),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Attach File", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

fun shiftDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("/")
        if (parts.size == 3) {
            val d = parts[0].toInt() + 1
            "${d.toString().padStart(2, '0')}/${parts[1]}/${parts[2]}"
        } else dateStr
    } catch (e: Exception) { dateStr }
}

@Composable
fun AddDateEventDialog(date: String, onSave: (DateEvent) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var totalQ by remember { mutableStateOf("") }
    var timeRange by remember { mutableStateOf("") }
    var alarmTime by remember { mutableStateOf(0L) }
    val alarmText = if (alarmTime > 0L) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(alarmTime)) else "Set Alarm"

    NEETDialog(title = "Add Event for $date", icon = Icons.Default.EventNote, accentColor = NeonGreen, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Event Name", icon = Icons.Default.Event, accentColor = NeonGreen)
            DialogTextField(value = detail, onValueChange = { detail = it }, label = "Details", icon = Icons.Default.Description, accentColor = NeonGreen, multiline = true)
            DialogTextField(value = url, onValueChange = { url = it }, label = "URL (optional)", icon = Icons.Default.Link, accentColor = NeonGreen)
            DialogTextField(value = totalQ, onValueChange = { totalQ = it }, label = "No. of Questions (optional)", icon = Icons.Default.Quiz, accentColor = NeonGreen)
            DialogTextField(value = timeRange, onValueChange = { timeRange = it }, label = "Time Range (optional)", icon = Icons.Default.Schedule, accentColor = NeonGreen)

            // Alarm button
            OutlinedButton(
                onClick = {
                    val now = Calendar.getInstance()
                    TimePickerDialog(context, { _, hour, minute ->
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        alarmTime = cal.timeInMillis
                    }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
                },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, if (alarmTime > 0L) NeonGold else Color.White.copy(0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (alarmTime > 0L) NeonGold else Color.White.copy(0.7f))
            ) {
                Icon(if (alarmTime > 0L) Icons.Default.AlarmOn else Icons.Default.AddAlarm, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(alarmText)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val newEvent = DateEvent(
                                date = date, name = name, detail = detail, url = url,
                                totalQuestions = totalQ.toIntOrNull() ?: 0, timeRange = timeRange,
                                alarmTime = alarmTime, alarmLabel = name
                            )
                            if (alarmTime > 0L) {
                                AlarmScheduler.scheduleAlarm(context, newEvent.id.hashCode(), alarmTime, "NEET Event Reminder", name)
                            }
                            onSave(newEvent)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.2f)),
                    border = BorderStroke(1.dp, NeonGreen.copy(0.6f))
                ) { Text("Add", color = NeonGreen, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
