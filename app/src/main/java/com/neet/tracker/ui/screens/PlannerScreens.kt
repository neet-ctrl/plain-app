package com.neet.tracker.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.alarm.AlarmScheduler
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.*
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PlannerScreen(navController: NavController) {
    val cards = listOf(
        Triple("Day Planner", Icons.Default.Today, Routes.DAY_PLANNER) to NeonCyan,
        Triple("Week Planner", Icons.Default.ViewWeek, Routes.WEEK_PLANNER) to NeonPurple,
        Triple("Month Planner", Icons.Default.CalendarViewMonth, Routes.MONTH_PLANNER) to NeonGold,
        Triple("Year Planner", Icons.Default.CalendarToday, Routes.YEAR_PLANNER) to NeonGreen,
    )
    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Smart Planner", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(cards) { (info, color) ->
                    NEETCard(title = info.first, icon = info.second, glowColor = color, onClick = { navController.navigate(info.third) })
                }
            }
        }
    }
}

// ─── Day Planner ─────────────────────────────────────────────────────────────

@Composable
fun DayPlannerScreen(navController: NavController, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.dayEntries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var newDate by remember { mutableStateOf("") }

    val filtered = entries.filter { searchQuery.isBlank() || it.date.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Day Planner", breadcrumb = "Home / Planner", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search dates...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No days planned yet. Tap + to add.", Icons.Default.Today)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { entry ->
                        NEETCard(
                            title = entry.date,
                            subtitle = "${entry.events.size} events",
                            icon = Icons.Default.Today,
                            glowColor = NeonCyan,
                            onClick = { navController.navigate(dayPlannerDetailRoute(entry.id)) }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) {
        NEETDialog(title = "New Day Entry", icon = Icons.Default.Today, accentColor = NeonCyan, onDismiss = { showAdd = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NeetDatePickerButton(
                    selectedDate = newDate,
                    onDateSelected = { newDate = it },
                    accentColor = NeonCyan,
                    label = "Select Day Date"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showAdd = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                    Button(onClick = {
                        if (newDate.isNotBlank()) { vm.saveDay(DayPlannerEntry(date = newDate)); showAdd = false; newDate = "" }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) { Text("Add", color = NeonCyan, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun DayPlannerDetailScreen(navController: NavController, entryId: String, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.dayEntries.collectAsState()
    val entry = entries.find { it.id == entryId }
    var events by remember(entry) { mutableStateOf(entry?.events ?: emptyList()) }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = entry?.date ?: "Day Plan", breadcrumb = "Home / Planner / Day", onBack = { navController.popBackStack() })
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(events) { i, event ->
                    PlannerEventCard(
                        event = event,
                        index = i + 1,
                        accentColor = NeonCyan,
                        onUpdate = { updated ->
                            val newList = events.toMutableList().also { it[i] = updated }
                            events = newList
                            entry?.let { vm.saveDay(it.copy(events = newList)) }
                        },
                        onDelete = {
                            val newList = events.toMutableList().also { it.removeAt(i) }
                            events = newList
                            entry?.let { vm.saveDay(it.copy(events = newList)) }
                        }
                    )
                }
                item {
                    Button(
                        onClick = {
                            val newList = events + PlannerEvent()
                            events = newList
                            entry?.let { vm.saveDay(it.copy(events = newList)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.12f)),
                        border = BorderStroke(1.dp, NeonCyan.copy(0.4f))
                    ) {
                        Icon(Icons.Default.Add, null, tint = NeonCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Event", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PlannerEventCard(event: PlannerEvent, index: Int, accentColor: Color, onUpdate: (PlannerEvent) -> Unit, onDelete: () -> Unit, timingPickerType: String = "TIME_RANGE") {
    val context = LocalContext.current
    var name by remember(event) { mutableStateOf(event.name) }
    var notes by remember(event) { mutableStateOf(event.notes) }
    var timing by remember(event) { mutableStateOf(event.timingRange) }
    var remark by remember(event) { mutableStateOf(event.remark) }
    var selectedStatus by remember(event) { mutableStateOf(event.status) }
    var alarmTime by remember(event) { mutableStateOf(event.alarmTime) }
    var alarmLabel by remember(event) { mutableStateOf(event.alarmLabel) }
    var isViewMode by remember(event) { mutableStateOf(event.name.isNotBlank()) }

    val alarmDisplayText = if (alarmTime > 0L) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(alarmTime))
    } else ""

    val cardGlow = when (selectedStatus) { "COMPLETED" -> StatusCompleted; "CROSSED" -> StatusCross; else -> accentColor }

    GlassCard(glowColor = cardGlow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Header (always visible) ───────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp)
                        .background(accentColor.copy(0.2f), androidx.compose.foundation.shape.CircleShape)
                        .border(1.dp, accentColor.copy(0.5f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$index", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (name.isNotBlank()) name else "Event $index",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (alarmTime > 0L) {
                    Icon(Icons.Default.AlarmOn, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                // Compact status badge
                val sc = when (selectedStatus) { "COMPLETED" -> StatusCompleted; "CROSSED" -> StatusCross; else -> Color.White.copy(0.3f) }
                Box(
                    modifier = Modifier
                        .background(sc.copy(0.15f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, sc.copy(0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        when (selectedStatus) { "COMPLETED" -> "✓"; "CROSSED" -> "✗"; else -> "○" },
                        style = MaterialTheme.typography.labelSmall, color = sc, fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(16.dp))
                }
            }

            NeonDivider(accentColor)

            // ── View / Edit Toggle ─────────────────────────────────────────────
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            // ── Content switches based on mode ─────────────────────────────────
            AnimatedContent(
                targetState = isViewMode,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "planner_card_$index"
            ) { viewMode ->
                if (viewMode) {
                    // ── View Mode ──────────────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (name.isNotBlank())   PlannerViewField("Event Name", name,   Icons.Default.Event,       accentColor)
                        if (notes.isNotBlank())  PlannerViewField("Notes",      notes,  Icons.Default.Notes,       accentColor)
                        if (timing.isNotBlank()) PlannerViewField(
                            when (timingPickerType) {
                                "DATE_RANGE"  -> "Date Range"
                                "MONTH_RANGE" -> "Month Range"
                                else          -> "Time Range"
                            },
                            timing,
                            when (timingPickerType) {
                                "DATE_RANGE", "MONTH_RANGE" -> Icons.Default.DateRange
                                else                        -> Icons.Default.Schedule
                            },
                            accentColor
                        )
                        if (remark.isNotBlank()) PlannerViewField("Remark",     remark, Icons.Default.StickyNote2, accentColor)

                        if (alarmTime > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(NeonGold.copy(0.08f), RoundedCornerShape(10.dp))
                                    .border(0.5.dp, NeonGold.copy(0.3f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AlarmOn, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                                Column {
                                    Text("Alarm Set", style = MaterialTheme.typography.labelSmall, color = NeonGold.copy(0.7f))
                                    Text(alarmDisplayText, style = MaterialTheme.typography.bodySmall, color = NeonGold, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Status pill
                        val statusColor = when (selectedStatus) { "COMPLETED" -> StatusCompleted; "CROSSED" -> StatusCross; else -> accentColor.copy(0.5f) }
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(0.18f), RoundedCornerShape(12.dp))
                                .border(1.dp, statusColor.copy(0.55f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                when (selectedStatus) { "COMPLETED" -> "✓  Done"; "CROSSED" -> "✗  Missed"; else -> "○  Pending" },
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                } else {
                    // ── Edit Mode ──────────────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogTextField(value = name, onValueChange = { name = it }, label = "Event Name", icon = Icons.Default.Event, accentColor = accentColor)
                        DialogTextField(value = notes, onValueChange = { notes = it }, label = "Notes / Description", icon = Icons.Default.Notes, accentColor = accentColor, multiline = true)
                        when (timingPickerType) {
                            "DATE_RANGE"  -> NeetDateRangePickerButton(value = timing, onValueChange = { timing = it }, accentColor = accentColor, label = "Event Date Range")
                            "MONTH_RANGE" -> NeetMonthRangePickerButton(value = timing, onValueChange = { timing = it }, accentColor = accentColor, label = "Event Month Range")
                            else          -> NeetTimeRangePickerButton(value = timing, onValueChange = { timing = it }, accentColor = accentColor, label = "Event Time Range")
                        }
                        DialogTextField(value = remark, onValueChange = { remark = it }, label = "Remark", icon = Icons.Default.StickyNote2, accentColor = accentColor)

                        NeonDivider(NeonGold.copy(0.3f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Alarm, null, tint = NeonGold, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reminder / Alarm", style = MaterialTheme.typography.labelMedium, color = NeonGold, fontWeight = FontWeight.SemiBold)
                                if (alarmDisplayText.isNotEmpty()) Text(alarmDisplayText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                                else Text("No alarm set", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
                            }
                            if (alarmTime > 0L) {
                                IconButton(onClick = {
                                    AlarmScheduler.cancelAlarm(context, event.id.hashCode())
                                    alarmTime = 0L; alarmLabel = ""
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.AlarmOff, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = {
                                val now = Calendar.getInstance()
                                TimePickerDialog(context, { _, hour, minute ->
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                                    }
                                    alarmTime = cal.timeInMillis
                                    alarmLabel = name.ifBlank { "Event $index" }
                                    AlarmScheduler.scheduleAlarm(context, event.id.hashCode(), cal.timeInMillis, "NEET Reminder", alarmLabel.ifBlank { "Time for your scheduled event!" })
                                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.AddAlarm, null, tint = NeonGold, modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusTickCrossButton("✓ Done", selectedStatus == "COMPLETED", StatusCompleted) {
                                selectedStatus = if (selectedStatus == "COMPLETED") "EXPECTED" else "COMPLETED"
                            }
                            StatusTickCrossButton("✗ Missed", selectedStatus == "CROSSED", StatusCross) {
                                selectedStatus = if (selectedStatus == "CROSSED") "EXPECTED" else "CROSSED"
                            }
                        }

                        NeonDivider(accentColor.copy(0.2f))
                        Button(
                            onClick = {
                                onUpdate(
                                    event.copy(
                                        name = name,
                                        notes = notes,
                                        timingRange = timing,
                                        remark = remark,
                                        status = selectedStatus,
                                        alarmTime = alarmTime,
                                        alarmLabel = alarmLabel
                                    )
                                )
                                isViewMode = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.2f)),
                            border = BorderStroke(1.dp, accentColor.copy(0.7f))
                        ) {
                            Icon(Icons.Default.Save, null, tint = accentColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Event", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerViewField(label: String, value: String, icon: ImageVector, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(accentColor.copy(0.07f), RoundedCornerShape(10.dp))
            .border(0.5.dp, accentColor.copy(0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = accentColor.copy(0.8f), modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.7f), fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, lineHeight = 18.sp)
        }
    }
}

@Composable
fun StatusTickCrossButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(0.2f) else Color.White.copy(0.04f))
            .border(1.dp, if (selected) color else Color.White.copy(0.1f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) color else Color.White.copy(0.5f), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

// ─── Week Planner ─────────────────────────────────────────────────────────────

@Composable
fun WeekPlannerScreen(navController: NavController, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.weekEntries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var newWeek by remember { mutableStateOf("") }
    var newRangeStart by remember { mutableStateOf("") }
    var newRangeEnd by remember { mutableStateOf("") }
    val filtered = entries.filter { searchQuery.isBlank() || it.weekLabel.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Week Planner", breadcrumb = "Home / Planner", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search weeks...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No weeks planned yet. Tap + to add.", Icons.Default.ViewWeek)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { entry ->
                        NEETCard(title = entry.weekLabel, subtitle = entry.dateRange, icon = Icons.Default.ViewWeek, glowColor = NeonPurple, onClick = { navController.navigate(weekPlannerDetailRoute(entry.id)) })
                    }
                }
            }
        }
    }
    if (showAdd) {
        NEETDialog(title = "New Week Entry", icon = Icons.Default.ViewWeek, accentColor = NeonPurple, onDismiss = { showAdd = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogTextField(value = newWeek, onValueChange = { newWeek = it }, label = "Week Label (e.g. 3rd Week of May)", icon = Icons.Default.ViewWeek, accentColor = NeonPurple)
                Text("Week Date Range", style = MaterialTheme.typography.labelSmall, color = NeonPurple.copy(0.9f), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeetDatePickerButton(
                        selectedDate = newRangeStart,
                        onDateSelected = { newRangeStart = it },
                        accentColor = NeonPurple,
                        label = "Start Date",
                        modifier = Modifier.weight(1f)
                    )
                    NeetDatePickerButton(
                        selectedDate = newRangeEnd,
                        onDateSelected = { newRangeEnd = it },
                        accentColor = NeonPurple,
                        label = "End Date",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showAdd = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (newWeek.isNotBlank()) {
                                val range = if (newRangeStart.isNotBlank() && newRangeEnd.isNotBlank()) "$newRangeStart – $newRangeEnd" else if (newRangeStart.isNotBlank()) newRangeStart else ""
                                vm.saveWeek(WeekPlannerEntry(weekLabel = newWeek, dateRange = range))
                                showAdd = false
                            }
                        },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) { Text("Add", color = NeonPurple, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun WeekPlannerDetailScreen(navController: NavController, weekId: String, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.weekEntries.collectAsState()
    val entry = entries.find { it.id == weekId }
    var events by remember(entry) { mutableStateOf(entry?.events ?: emptyList()) }
    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = entry?.weekLabel ?: "Week Plan", breadcrumb = "Home / Planner / Week", onBack = { navController.popBackStack() })
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(events) { i, event ->
                    PlannerEventCard(event = event, index = i + 1, accentColor = NeonPurple, timingPickerType = "DATE_RANGE",
                        onUpdate = { updated -> val nl = events.toMutableList().also { it[i] = updated }; events = nl; entry?.let { vm.saveWeek(it.copy(events = nl)) } },
                        onDelete = { val nl = events.toMutableList().also { it.removeAt(i) }; events = nl; entry?.let { vm.saveWeek(it.copy(events = nl)) } }
                    )
                }
                item {
                    Button(onClick = { val nl = events + PlannerEvent(); events = nl; entry?.let { vm.saveWeek(it.copy(events = nl)) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.12f)), border = BorderStroke(1.dp, NeonPurple.copy(0.4f))) {
                        Icon(Icons.Default.Add, null, tint = NeonPurple); Spacer(Modifier.width(8.dp)); Text("Add Event", color = NeonPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Month Planner ────────────────────────────────────────────────────────────

@Composable
fun MonthPlannerScreen(navController: NavController, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.monthEntries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var newMonth by remember { mutableStateOf("") }
    val filtered = entries.filter { searchQuery.isBlank() || it.month.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Month Planner", breadcrumb = "Home / Planner", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search months...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No months planned yet.", Icons.Default.CalendarViewMonth)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { e -> NEETCard(title = e.month, subtitle = "${e.events.size} events", icon = Icons.Default.CalendarViewMonth, glowColor = NeonGold, onClick = { navController.navigate(monthPlannerDetailRoute(e.id)) }) }
                }
            }
        }
    }
    if (showAdd) SimpleAddDialog("New Month Entry", "Month (e.g. May 2026)", NeonGold, Icons.Default.CalendarViewMonth, onSave = { vm.saveMonth(MonthPlannerEntry(month = it)); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun MonthPlannerDetailScreen(navController: NavController, monthId: String, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.monthEntries.collectAsState()
    val entry = entries.find { it.id == monthId }
    var events by remember(entry) { mutableStateOf(entry?.events ?: emptyList()) }
    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = entry?.month ?: "Month Plan", breadcrumb = "Home / Planner / Month", onBack = { navController.popBackStack() })
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(events) { i, event ->
                    PlannerEventCard(event = event, index = i + 1, accentColor = NeonGold, timingPickerType = "DATE_RANGE",
                        onUpdate = { updated -> val nl = events.toMutableList().also { it[i] = updated }; events = nl; entry?.let { vm.saveMonth(it.copy(events = nl)) } },
                        onDelete = { val nl = events.toMutableList().also { it.removeAt(i) }; events = nl; entry?.let { vm.saveMonth(it.copy(events = nl)) } }
                    )
                }
                item { Button(onClick = { val nl = events + PlannerEvent(); events = nl; entry?.let { vm.saveMonth(it.copy(events = nl)) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.12f)), border = BorderStroke(1.dp, NeonGold.copy(0.4f))) { Icon(Icons.Default.Add, null, tint = NeonGold); Spacer(Modifier.width(8.dp)); Text("Add Event", color = NeonGold, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

// ─── Year Planner ─────────────────────────────────────────────────────────────

@Composable
fun YearPlannerScreen(navController: NavController, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.yearEntries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = entries.filter { searchQuery.isBlank() || it.yearSession.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Year Planner", breadcrumb = "Home / Planner", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search year sessions...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No year sessions yet.", Icons.Default.CalendarToday)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { e -> NEETCard(title = e.yearSession, subtitle = "${e.events.size} events", icon = Icons.Default.CalendarToday, glowColor = NeonGreen, onClick = { navController.navigate(yearPlannerDetailRoute(e.id)) }) }
                }
            }
        }
    }
    if (showAdd) SimpleAddDialog("New Year Session", "Session (e.g. 2026-2027)", NeonGreen, Icons.Default.CalendarToday, onSave = { vm.saveYear(YearPlannerEntry(yearSession = it)); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun YearPlannerDetailScreen(navController: NavController, yearId: String, vm: PlannerViewModel = hiltViewModel()) {
    val entries by vm.yearEntries.collectAsState()
    val entry = entries.find { it.id == yearId }
    var events by remember(entry) { mutableStateOf(entry?.events ?: emptyList()) }
    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = entry?.yearSession ?: "Year Plan", breadcrumb = "Home / Planner / Year", onBack = { navController.popBackStack() })
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(events) { i, event ->
                    PlannerEventCard(event = event, index = i + 1, accentColor = NeonGreen, timingPickerType = "MONTH_RANGE",
                        onUpdate = { updated -> val nl = events.toMutableList().also { it[i] = updated }; events = nl; entry?.let { vm.saveYear(it.copy(events = nl)) } },
                        onDelete = { val nl = events.toMutableList().also { it.removeAt(i) }; events = nl; entry?.let { vm.saveYear(it.copy(events = nl)) } }
                    )
                }
                item { Button(onClick = { val nl = events + PlannerEvent(); events = nl; entry?.let { vm.saveYear(it.copy(events = nl)) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.12f)), border = BorderStroke(1.dp, NeonGreen.copy(0.4f))) { Icon(Icons.Default.Add, null, tint = NeonGreen); Spacer(Modifier.width(8.dp)); Text("Add Event", color = NeonGreen, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}
