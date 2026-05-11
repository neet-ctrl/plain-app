package com.neet.tracker.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.neet.tracker.alarm.AlarmScheduler
import com.neet.tracker.data.models.DateEvent
import com.neet.tracker.data.models.PlannerEvent
import com.neet.tracker.data.models.StudentProfile
import com.neet.tracker.navigation.*
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.DateEventViewModel
import com.neet.tracker.ui.viewmodels.HomeCountViewModel
import com.neet.tracker.ui.viewmodels.PlannerViewModel
import com.neet.tracker.ui.viewmodels.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.*

data class MainCard(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val accentColor: Color,
    val description: String
)

// ─── Aggregated alarm item for Universal Reminder Card ───────────────────────

data class UniversalAlarmItem(
    val id: String,
    val label: String,
    val subLabel: String,
    val alarmTime: Long,
    val accentColor: Color,
    val icon: ImageVector,
    val onCancelAlarm: (() -> Unit)? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: ProfileViewModel = hiltViewModel(),
    countsVm: HomeCountViewModel = hiltViewModel(),
    dateEventVm: DateEventViewModel = hiltViewModel(),
    plannerVm: PlannerViewModel = hiltViewModel()
) {
    val context      = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val profile by vm.profile.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // ── All-Files storage permission state ────────────────────────────────────
    fun checkStoragePermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true

    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasStoragePermission = checkStoragePermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val assetsCount      by countsVm.assetsCount.collectAsState()
    val diaryCount       by countsVm.diaryCount.collectAsState()
    val eventCount       by countsVm.eventCount.collectAsState()
    val dictCount        by countsVm.dictCount.collectAsState()
    val mnemonicCount    by countsVm.mnemonicCount.collectAsState()
    val diagramCount     by countsVm.diagramCount.collectAsState()
    val chapterNoteCount by countsVm.chapterNoteCount.collectAsState()
    val dayWasteCount    by countsVm.dayWasteCount.collectAsState()
    val sequenceCount    by countsVm.sequenceCount.collectAsState()
    val lackCount        by countsVm.lackCount.collectAsState()

    // ── Aggregate ALL events with alarms ──────────────────────────────────────
    val allDateEvents  by dateEventVm.allEvents.collectAsState()
    val dayEntries     by plannerVm.dayEntries.collectAsState()
    val weekEntries    by plannerVm.weekEntries.collectAsState()
    val monthEntries   by plannerVm.monthEntries.collectAsState()
    val yearEntries    by plannerVm.yearEntries.collectAsState()

    val alarmItems: List<UniversalAlarmItem> = remember(allDateEvents, dayEntries, weekEntries, monthEntries, yearEntries) {
        val items = mutableListOf<UniversalAlarmItem>()
        // Date Events
        allDateEvents.filter { it.alarmTime > 0L }.forEach { ev ->
            items.add(UniversalAlarmItem(
                id = ev.id,
                label = ev.name.ifBlank { "Unnamed Event" },
                subLabel = ev.date,
                alarmTime = ev.alarmTime,
                accentColor = NeonGreen,
                icon = Icons.Default.EventNote,
                onCancelAlarm = {
                    AlarmScheduler.cancelAlarm(context, ev.id.hashCode())
                    dateEventVm.save(ev.copy(alarmTime = 0L, alarmLabel = ""))
                }
            ))
        }
        // Planner Events (all types)
        dayEntries.forEach { entry ->
            entry.events.filter { it.alarmTime > 0L }.forEach { ev ->
                items.add(UniversalAlarmItem(
                    id = ev.id,
                    label = ev.name.ifBlank { "Day Event" },
                    subLabel = "Day • ${entry.date}",
                    alarmTime = ev.alarmTime,
                    accentColor = NeonCyan,
                    icon = Icons.Default.Today,
                    onCancelAlarm = {
                        AlarmScheduler.cancelAlarm(context, ev.id.hashCode())
                        val newEvents = entry.events.map { if (it.id == ev.id) it.copy(alarmTime = 0L, alarmLabel = "") else it }
                        plannerVm.saveDay(entry.copy(events = newEvents))
                    }
                ))
            }
        }
        weekEntries.forEach { entry ->
            entry.events.filter { it.alarmTime > 0L }.forEach { ev ->
                items.add(UniversalAlarmItem(
                    id = ev.id,
                    label = ev.name.ifBlank { "Week Event" },
                    subLabel = "Week • ${entry.weekLabel}",
                    alarmTime = ev.alarmTime,
                    accentColor = NeonPurple,
                    icon = Icons.Default.ViewWeek,
                    onCancelAlarm = {
                        AlarmScheduler.cancelAlarm(context, ev.id.hashCode())
                        val newEvents = entry.events.map { if (it.id == ev.id) it.copy(alarmTime = 0L, alarmLabel = "") else it }
                        plannerVm.saveWeek(entry.copy(events = newEvents))
                    }
                ))
            }
        }
        monthEntries.forEach { entry ->
            entry.events.filter { it.alarmTime > 0L }.forEach { ev ->
                items.add(UniversalAlarmItem(
                    id = ev.id,
                    label = ev.name.ifBlank { "Month Event" },
                    subLabel = "Month • ${entry.month}",
                    alarmTime = ev.alarmTime,
                    accentColor = NeonGold,
                    icon = Icons.Default.CalendarViewMonth,
                    onCancelAlarm = {
                        AlarmScheduler.cancelAlarm(context, ev.id.hashCode())
                        val newEvents = entry.events.map { if (it.id == ev.id) it.copy(alarmTime = 0L, alarmLabel = "") else it }
                        plannerVm.saveMonth(entry.copy(events = newEvents))
                    }
                ))
            }
        }
        yearEntries.forEach { entry ->
            entry.events.filter { it.alarmTime > 0L }.forEach { ev ->
                items.add(UniversalAlarmItem(
                    id = ev.id,
                    label = ev.name.ifBlank { "Year Event" },
                    subLabel = "Year • ${entry.yearSession}",
                    alarmTime = ev.alarmTime,
                    accentColor = NeonGreen,
                    icon = Icons.Default.CalendarToday,
                    onCancelAlarm = {
                        AlarmScheduler.cancelAlarm(context, ev.id.hashCode())
                        val newEvents = entry.events.map { if (it.id == ev.id) it.copy(alarmTime = 0L, alarmLabel = "") else it }
                        plannerVm.saveYear(entry.copy(events = newEvents))
                    }
                ))
            }
        }
        items.sortedBy { it.alarmTime }
    }

    val countMap = remember(assetsCount, diaryCount, eventCount, dictCount, mnemonicCount,
        diagramCount, chapterNoteCount, dayWasteCount, sequenceCount, lackCount) {
        mapOf(
            Routes.ASSETS              to assetsCount,
            Routes.DAILY_DIARY         to diaryCount,
            Routes.DATE_EVENTS         to eventCount,
            Routes.DICTIONARY          to dictCount,
            Routes.MNEMONICS           to mnemonicCount,
            Routes.DIAGRAMS            to diagramCount,
            Routes.CHAPTER_SHORT_NOTES to chapterNoteCount,
            Routes.DAY_WASTE           to dayWasteCount,
            Routes.NEET_SEQUENCE       to sequenceCount,
            Routes.LACK_POINTS         to lackCount,
        )
    }

    val mainCards = listOf(
        MainCard("Assets Vault",       Icons.Default.Inventory2,        Routes.ASSETS,              NeonCyan,   "Books · Notes · Papers"),
        MainCard("Smart Planner",      Icons.Default.CalendarMonth,     Routes.PLANNER,             NeonPurple, "Day · Week · Month · Year"),
        MainCard("Daily Diary",        Icons.Default.AutoStories,       Routes.DAILY_DIARY,         NeonGold,   "Personal journal entries"),
        MainCard("Event Log",          Icons.Default.EventNote,         Routes.DATE_EVENTS,         NeonGreen,  "Per-date event tracker"),
        MainCard("NEET Syllabus",      Icons.Default.School,            Routes.NEET_SYLLABUS,       NeonOrange, "Official syllabus PDF"),
        MainCard("Lexicon",            Icons.Default.Translate,         Routes.DICTIONARY,          NeonCyan,   "NEET & English dictionary"),
        MainCard("Mnemonic Lab",       Icons.Default.Psychology,        Routes.MNEMONICS,           NeonPurple, "Memory techniques"),
        MainCard("Universe Calendar",  Icons.Default.DateRange,         Routes.UNIVERSAL_CALENDAR,  NeonGold,   "All events at one glance"),
        MainCard("Diagrams Atlas",     Icons.Default.AccountTree,       Routes.DIAGRAMS,            NeonGreen,  "Botany & Zoology diagrams"),
        MainCard("Chapter Notes",      Icons.Default.Article,           Routes.CHAPTER_SHORT_NOTES, NeonCyan,   "Short notes per chapter"),
        MainCard("Wasted Days",        Icons.Default.Dangerous,         Routes.DAY_WASTE,           NeonRed,    "Track & recover lost days"),
        MainCard("NEET Sequence",      Icons.Default.Timeline,          Routes.NEET_SEQUENCE,       NeonPurple, "Chapter study sequence"),
        MainCard("Subject Notes",      Icons.Default.LibraryBooks,      Routes.SUBJECT_SHORT_NOTES, NeonGold,   "Subject-wise PDF notes"),
        MainCard("Lack Points",        Icons.Default.TrendingDown,      Routes.LACK_POINTS,         NeonRed,    "Identify & fix weaknesses"),
        MainCard("Error Notebook",     Icons.Default.ErrorOutline,      Routes.ERROR_NOTEBOOK,      NeonOrange, "Track & fix mistakes"),
        MainCard("Revision Scheduler", Icons.Default.AutoGraph,         Routes.REVISION_SCHEDULER,  NeonTeal,   "Spaced revision planner"),
        MainCard("Flashcard Review",   Icons.Default.Quiz,              Routes.FLASHCARD_REVIEW,    NeonPink,   "AI-powered flip card study"),
        MainCard("Performance Stats", Icons.Default.Analytics,          Routes.PERFORMANCE_ANALYTICS, NeonIndigo, "Score trends · AI insights"),
    )

    val filtered = mainCards.filter {
        searchQuery.isBlank() ||
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.description.contains(searchQuery, ignoreCase = true)
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeHeader(profile = profile, navController = navController)

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search 16 modules...")
                Spacer(Modifier.height(10.dp))

                // ─── Universal Reminder Card ─────────────────────────────────
                AnimatedVisibility(
                    visible = alarmItems.isNotEmpty() && searchQuery.isBlank(),
                    enter = expandVertically(tween(400, easing = EaseOutBack)) + fadeIn(tween(350)),
                    exit  = shrinkVertically(tween(300)) + fadeOut(tween(200))
                ) {
                    UniversalReminderCard(
                        items = alarmItems,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                AnimatedVisibility(visible = searchQuery.isBlank()) {
                    Text(
                        text = "16 Modules · Your NEET Command Center",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan.copy(0.45f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }

                // Storage permission banner
                AnimatedVisibility(
                    visible = !hasStoragePermission && searchQuery.isBlank(),
                    enter = expandVertically() + fadeIn(tween(350)),
                    exit  = shrinkVertically() + fadeOut(tween(250))
                ) {
                    StoragePermissionBanner(
                        modifier = Modifier.padding(bottom = 12.dp),
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                // Routes that have already played their entrance animation — survives
                // recompositions and scroll-up/down without replaying the animation.
                val seenRoutes = remember { mutableSetOf<String>() }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(
                        items       = filtered,
                        key         = { _, card -> card.route },   // preserves remember state across scrolling
                        contentType = { _, _ -> "module_card" }
                    ) { index, card ->
                        // Stagger only on the very first appearance; never replay on scroll-up.
                        val alreadySeen = card.route in seenRoutes
                        val delay = if (!alreadySeen && index < 6) index * 45L else 0L
                        var visible by remember { mutableStateOf(alreadySeen || delay == 0L) }
                        LaunchedEffect(card.route) {
                            if (!visible) {
                                if (delay > 0L) kotlinx.coroutines.delay(delay)
                                visible = true
                                seenRoutes.add(card.route)
                            }
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400)) + scaleIn(tween(440, easing = EaseOutBack), initialScale = 0.60f) + slideInVertically(tween(400, easing = EaseOutBack)) { it / 3 }
                        ) {
                            HomeModuleCard(
                                card    = card,
                                count   = countMap[card.route],
                                onClick = { navController.navigate(card.route) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Universal Reminder Card ──────────────────────────────────────────────────

@Composable
fun UniversalReminderCard(items: List<UniversalAlarmItem>, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "urc")
    val bellRing by infiniteTransition.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(
            tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(1800)
        ), label = "bell_ring"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow_pulse"
    )

    var expanded by remember { mutableStateOf(true) }
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(22.dp), spotColor = NeonGold.copy(glowPulse * 0.45f), ambientColor = NeonGold.copy(0.1f))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A1400), Color(0xFF0C0E1A), Color(0xFF120F00))
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(NeonGold.copy(glowPulse * 0.9f), NeonOrange.copy(0.3f), NeonGold.copy(glowPulse * 0.5f))
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        // Background glow orbs
        Box(modifier = Modifier.size(140.dp).align(Alignment.TopStart).offset((-20).dp, (-20).dp)
            .background(Brush.radialGradient(listOf(NeonGold.copy(0.08f * glowPulse), Color.Transparent)), CircleShape))
        Box(modifier = Modifier.size(90.dp).align(Alignment.BottomEnd).offset(15.dp, 15.dp)
            .background(Brush.radialGradient(listOf(NeonOrange.copy(0.06f * glowPulse), Color.Transparent)), CircleShape))

        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                // Animated bell icon
                Box(
                    modifier = Modifier.size(42.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = NeonGold.copy(0.6f))
                        .background(
                            Brush.linearGradient(listOf(NeonGold.copy(0.30f), NeonOrange.copy(0.15f))),
                            RoundedCornerShape(14.dp)
                        )
                        .border(1.dp, NeonGold.copy(glowPulse * 0.8f), RoundedCornerShape(14.dp))
                        .graphicsLayer { rotationZ = bellRing },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = NeonGold, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Active Reminders",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeonGold,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${items.size} alarm${if (items.size != 1) "s" else ""} set across all modules",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.55f)
                    )
                }
                // Expand/collapse
                Box(
                    modifier = Modifier.size(30.dp)
                        .background(NeonGold.copy(0.12f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, NeonGold.copy(0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = NeonGold.copy(0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(320, easing = EaseOutBack)) + fadeIn(tween(280)),
                exit  = shrinkVertically(tween(250)) + fadeOut(tween(200))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonDivider(NeonGold.copy(0.3f))
                    Spacer(Modifier.height(4.dp))
                    items.forEach { item ->
                        UniversalReminderRow(item = item, sdf = sdf, glowPulse = glowPulse)
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversalReminderRow(item: UniversalAlarmItem, sdf: SimpleDateFormat, glowPulse: Float) {
    val now = System.currentTimeMillis()
    val isPast = item.alarmTime < now
    val isImminentHour = !isPast && (item.alarmTime - now) < 3_600_000L

    val rowColor = when {
        isPast       -> NeonRed.copy(0.8f)
        isImminentHour -> NeonOrange
        else         -> item.accentColor
    }

    val rowGlow by rememberInfiniteTransition(label = "row_${item.id}").animateFloat(
        initialValue = 0.15f, targetValue = if (isImminentHour) 0.6f else 0.35f,
        animationSpec = infiniteRepeatable(tween(if (isImminentHour) 700 else 2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "row_glow"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isImminentHour) 8.dp else 0.dp, RoundedCornerShape(14.dp), spotColor = rowColor.copy(rowGlow))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(rowColor.copy(0.12f), Color(0xFF080D1A), rowColor.copy(0.06f)))
            )
            .border(1.dp, rowColor.copy(rowGlow + 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Source icon
        Box(
            modifier = Modifier.size(34.dp)
                .background(rowColor.copy(0.2f), RoundedCornerShape(10.dp))
                .border(0.5.dp, rowColor.copy(0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = rowColor, modifier = Modifier.size(18.dp))
        }

        // Labels
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.45f),
                    maxLines = 1
                )
                Box(modifier = Modifier.size(3.dp).background(Color.White.copy(0.2f), CircleShape))
                Text(
                    sdf.format(Date(item.alarmTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = rowColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            if (isPast) {
                Text("⚠ Alarm time passed", style = MaterialTheme.typography.labelSmall, color = NeonRed.copy(0.85f), fontWeight = FontWeight.Bold)
            } else if (isImminentHour) {
                Text("🔔 Coming up within 1 hour!", style = MaterialTheme.typography.labelSmall, color = NeonOrange, fontWeight = FontWeight.Bold)
            }
        }

        // Bell animated icon
        Box(
            modifier = Modifier.size(32.dp)
                .background(rowColor.copy(0.15f), RoundedCornerShape(10.dp))
                .border(0.5.dp, rowColor.copy(0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AlarmOn,
                null,
                tint = rowColor,
                modifier = Modifier.size(18.dp)
            )
        }

        // Cancel alarm button
        if (item.onCancelAlarm != null) {
            IconButton(
                onClick = { item.onCancelAlarm.invoke() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.AlarmOff, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── 3D Home Header ───────────────────────────────────────────────────────────

@Composable
fun HomeHeader(profile: StudentProfile?, navController: NavController) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_rot"
    )
    val aimGlow by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "aim_glow"
    )
    val headerNebula by infiniteTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "header_nebula"
    )

    val greetingHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        greetingHour < 5  -> "Up Late?"
        greetingHour < 12 -> "Good Morning"
        greetingHour < 17 -> "Good Afternoon"
        greetingHour < 21 -> "Good Evening"
        else              -> "Good Night"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF07112A), Color(0xFF050E1E), Color(0xFF040B16).copy(0f))
                )
            )
    ) {
        Box(
            modifier = Modifier.size(260.dp).align(Alignment.TopEnd).offset(50.dp, (-30).dp)
                .background(Brush.radialGradient(listOf(NeonPurple.copy(headerNebula * 0.8f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier.size(180.dp).align(Alignment.TopStart).offset((-40).dp, 10.dp)
                .background(Brush.radialGradient(listOf(NeonCyan.copy(headerNebula * 0.5f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(0.30f), NeonPurple.copy(0.20f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {

                Box(modifier = Modifier.size(78.dp).clickable { navController.navigate(Routes.PROFILE) }, contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { rotationZ = ringRotation }
                            .border(
                                2.5.dp,
                                Brush.sweepGradient(
                                    0f    to NeonCyan,
                                    0.3f  to NeonPurple,
                                    0.6f  to NeonGold,
                                    0.85f to Color.Transparent,
                                    1f    to NeonCyan
                                ),
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier.size(70.dp)
                            .shadow(16.dp, CircleShape, spotColor = NeonCyan.copy(0.45f))
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF1A2A44), CosmicBlue)),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Brush.sweepGradient(listOf(Color.White.copy(0.25f), NeonCyan.copy(0.40f), Color.White.copy(0.10f))),
                                CircleShape
                            )
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile?.photoUri?.isNotBlank() == true) {
                            coil.compose.AsyncImage(
                                model = profile.photoUri,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(Brush.radialGradient(listOf(NeonCyan.copy(0.22f), Color.Transparent)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = NeonCyan, modifier = Modifier.size(36.dp))
                            }
                        }
                        Box(
                            modifier = Modifier.size(28.dp).align(Alignment.TopStart)
                                .background(Brush.radialGradient(listOf(Color.White.copy(0.18f), Color.Transparent)), CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier.size(22.dp).align(Alignment.BottomEnd).offset(2.dp, 2.dp)
                            .shadow(6.dp, CircleShape, spotColor = NeonCyan.copy(0.4f))
                            .background(
                                Brush.linearGradient(listOf(NeonCyan.copy(0.9f), NeonCyan)),
                                CircleShape
                            )
                            .border(2.dp, DeepNavy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = DeepNavy, modifier = Modifier.size(11.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "$greeting,",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(0.45f)
                    )
                    Text(
                        text = profile?.name?.ifBlank { "NEET Aspirant" } ?: "NEET Aspirant",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Row(
                        modifier = Modifier
                            .shadow(10.dp, RoundedCornerShape(12.dp), spotColor = NeonGold.copy(aimGlow * 0.55f))
                            .background(
                                Brush.linearGradient(listOf(NeonGold.copy(0.20f), NeonOrange.copy(0.10f))),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(Color.White.copy(0.20f), NeonGold.copy(aimGlow * 0.75f), NeonOrange.copy(aimGlow * 0.35f))),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, tint = NeonGold, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Target: ${profile?.targetScore ?: "700/720"} · Future MBBS Doctor 🩺",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Box(
                    modifier = Modifier.size(42.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = NeonCyan.copy(0.20f))
                        .background(
                            Brush.linearGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.03f))),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            0.5.dp,
                            Brush.linearGradient(listOf(Color.White.copy(0.22f), NeonCyan.copy(0.25f))),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { navController.navigate(Routes.PROFILE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ManageAccounts, null, tint = NeonCyan.copy(0.85f), modifier = Modifier.size(22.dp))
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = NeonCyan.copy(0.12f))
                    .background(
                        Brush.linearGradient(listOf(NeonCyan.copy(0.07f), NeonPurple.copy(0.07f))),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        0.5.dp,
                        Brush.linearGradient(listOf(Color.White.copy(0.18f), NeonCyan.copy(0.28f), NeonPurple.copy(0.18f), Color.White.copy(0.10f))),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        modifier = Modifier.size(22.dp)
                            .background(NeonCyan.copy(0.12f), RoundedCornerShape(7.dp))
                            .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = NeonCyan.copy(0.8f), modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = "Every hour of study today is a step towards that white coat.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.55f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // ── Global Search Button ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = NeonPurple.copy(0.30f))
                    .background(
                        Brush.linearGradient(
                            listOf(NeonPurple.copy(0.14f), NeonCyan.copy(0.08f), NeonPurple.copy(0.10f))
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(NeonPurple.copy(0.45f), NeonCyan.copy(0.22f), NeonPurple.copy(0.28f))
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { navController.navigate(Routes.GLOBAL_SEARCH) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp)
                            .background(
                                Brush.linearGradient(listOf(NeonPurple.copy(0.30f), NeonCyan.copy(0.15f))),
                                RoundedCornerShape(10.dp)
                            )
                            .border(0.5.dp, NeonPurple.copy(0.50f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Search, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Search Everything",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Mnemonics · PYQ · Errors · Dictionary & more",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.40f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = NeonPurple.copy(0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Maximized 3D Module Card ─────────────────────────────────────────────────

@Composable
fun HomeModuleCard(card: MainCard, count: Int? = null, onClick: () -> Unit) {
    // Durations fixed once per composition — NOT inside animationSpec where they'd
    // be re-evaluated on every recomposition and constantly restart the transition.
    val glowDuration  = remember { (1800..2900).random() }
    val shineDuration = remember { (4000..6000).random() }

    val infiniteTransition = rememberInfiniteTransition(label = "hmc_${card.title}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f, targetValue = 0.36f,
        animationSpec = infiniteRepeatable(tween(glowDuration, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(shineDuration, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "shine"
    )
    // Badge pulse must be declared unconditionally (Compose: no hooks inside ifs).
    val badgeTransition = rememberInfiniteTransition(label = "badge_${card.title}")
    val badgePulse by badgeTransition.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "badge_pulse"
    )
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.52f, 320f), label = "scale")
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .shadow(
                16.dp, shape,
                spotColor    = card.accentColor.copy(0.38f),
                ambientColor = card.accentColor.copy(0.10f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF131F3C),
                        card.accentColor.copy(glowAlpha * 1.1f),
                        Color(0xFF070E1D),
                        card.accentColor.copy(glowAlpha * 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.30f),
                        card.accentColor.copy(glowAlpha + 0.12f),
                        Color.White.copy(0.05f),
                        card.accentColor.copy(glowAlpha * 0.22f)
                    )
                ),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier.size(110.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(Color.White.copy(0.12f), Color.Transparent)), shape)
        )
        Box(
            modifier = Modifier.size(100.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(card.accentColor.copy(glowAlpha * 1.1f), Color.Transparent)), shape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { translationX = shineOffset * 80f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(0f),
                            Color.White.copy(0.03f),
                            Color.White.copy(0.10f),
                            Color.White.copy(0.14f),
                            Color.White.copy(0.10f),
                            Color.White.copy(0.03f),
                            Color.White.copy(0f)
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(200f, 90f)
                    )
                )
        )
        Box(
            modifier = Modifier.size(70.dp).align(Alignment.BottomEnd)
                .background(Brush.radialGradient(listOf(card.accentColor.copy(glowAlpha * 0.50f), Color.Transparent)), shape)
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(50.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.32f))))
        )

        if (count != null && count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = card.accentColor.copy(badgePulse * 0.6f))
                    .background(
                        Brush.linearGradient(listOf(card.accentColor.copy(0.85f), card.accentColor.copy(0.55f))),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 999) "999+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ThreeDIconBox(
                icon     = card.icon,
                tint     = card.accentColor,
                size     = 56.dp,
                iconSize = 29.dp
            )
            Spacer(Modifier.height(11.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2
            )
            Spacer(Modifier.height(3.dp))
            Text(
                card.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.42f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// ─── Storage Permission Banner ────────────────────────────────────────────────

@Composable
private fun StoragePermissionBanner(modifier: Modifier = Modifier, onAllow: () -> Unit) {
    val context = LocalContext.current
    val pulse by rememberInfiniteTransition(label = "spb_pulse").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "spb_glow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(18.dp), spotColor = NeonOrange.copy(pulse * 0.5f))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1A0E00), Color(0xFF120A00), Color(0xFF0E0A00))),
                RoundedCornerShape(18.dp)
            )
            .border(
                BorderStroke(1.dp, Brush.horizontalGradient(listOf(NeonOrange.copy(0.8f), NeonGold.copy(0.5f), NeonOrange.copy(0.4f)))),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(10.dp, RoundedCornerShape(12.dp), spotColor = NeonOrange.copy(pulse * 0.6f))
                        .background(NeonOrange.copy(0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, NeonOrange.copy(0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = NeonOrange, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Manage All Files Access",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonOrange,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Required to open any PDF or file directly from storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.5f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonOrange.copy(0.07f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, NeonOrange.copy(0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, null, tint = NeonGold.copy(0.7f), modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Text(
                    "Without this permission, some PDFs and files attached to your notes may fail to open inside the app. " +
                    "Tap Allow Access, then enable \"Allow access to manage all files\" for NEET Tracker.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.55f),
                    lineHeight = 17.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(0.18f)),
                    border = BorderStroke(1.dp, NeonOrange.copy(0.75f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Allow Access", color = NeonOrange, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier,
                    border = BorderStroke(1.dp, Color.White.copy(0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(0.4f))
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
