package com.neet.tracker.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neet.tracker.data.models.CompletionDate
import com.neet.tracker.data.models.Status
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Next-Level 3D Dialog Shell ───────────────────────────────────────────────

@Composable
fun NEETDialog(
    title: String,
    icon: ImageVector = Icons.Default.Info,
    accentColor: Color = NeonCyan,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val enter = rememberInfiniteTransition(label = "dlg")
        val glowR by enter.animateFloat(
            initialValue = 0.8f, targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), label = "glowR"
        )
        val scanLine by enter.animateFloat(
            initialValue = -0.05f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart), label = "scan"
        )

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(250)) + scaleIn(tween(300, easing = EaseOutBack), initialScale = 0.82f) + slideInVertically(tween(300, easing = EaseOutBack)) { it / 4 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.93f)
                    .shadow(32.dp, RoundedCornerShape(30.dp), spotColor = accentColor.copy(0.4f), ambientColor = accentColor.copy(0.15f))
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(colors = listOf(Color(0xFF0C1A32), Color(0xFF060C1A), Color(0xFF0A1526))))
                    .border(1.5.dp, Brush.linearGradient(colors = listOf(accentColor.copy(0.85f), accentColor.copy(0.2f), accentColor.copy(0.65f))), RoundedCornerShape(30.dp))
            ) {
                Box(modifier = Modifier.size(180.dp).align(Alignment.TopStart).offset((-35).dp, (-35).dp)
                    .background(Brush.radialGradient(listOf(accentColor.copy(0.14f * glowR), Color.Transparent)), CircleShape))
                Box(modifier = Modifier.size(120.dp).align(Alignment.BottomEnd).offset(25.dp, 25.dp)
                    .background(Brush.radialGradient(listOf(accentColor.copy(0.08f * glowR), Color.Transparent)), CircleShape))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                    .offset(y = (scanLine * 1000).dp.coerceAtMost(600.dp))
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, accentColor.copy(0.15f), Color.Transparent))))

                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(50.dp)
                                .shadow(8.dp, RoundedCornerShape(15.dp), spotColor = accentColor.copy(0.4f))
                                .background(Brush.radialGradient(listOf(accentColor.copy(0.25f), accentColor.copy(0.08f))), RoundedCornerShape(15.dp))
                                .border(1.dp, accentColor.copy(0.55f), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = accentColor, modifier = Modifier.size(26.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(
                            modifier = Modifier.size(34.dp)
                                .background(Color.White.copy(0.06f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    NeonDivider(accentColor)
                    Spacer(Modifier.height(18.dp))
                    content()
                }
            }
        }
    }
}

// ─── 3D View / Edit Toggle ────────────────────────────────────────────────────

@Composable
fun ViewEditToggle(
    isViewMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vet")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "vet_glow"
    )
    val activeColor = if (isViewMode) NeonPurple else NeonCyan
    val slideOffset by animateFloatAsState(if (isViewMode) 1f else 0f, spring(0.6f, 400f), label = "slide")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(50.dp), spotColor = activeColor.copy(glow * 0.4f))
            .clip(RoundedCornerShape(50.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0A1020), Color(0xFF080D1A))))
            .border(1.5.dp, Brush.linearGradient(listOf(activeColor.copy(glow * 0.8f), activeColor.copy(0.2f), activeColor.copy(glow * 0.5f))), RoundedCornerShape(50.dp))
            .clickable(onClick = onToggle)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Sliding pill indicator
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val halfWidth = maxWidth / 2
            Box(
                modifier = Modifier
                    .offset(x = halfWidth * slideOffset)
                    .width(halfWidth)
                    .height(36.dp)
                    .shadow(8.dp, RoundedCornerShape(50.dp), spotColor = activeColor.copy(0.5f))
                    .clip(RoundedCornerShape(50.dp))
                    .background(Brush.linearGradient(listOf(activeColor.copy(0.35f), activeColor.copy(0.15f))))
                    .border(1.dp, activeColor.copy(glow * 0.7f), RoundedCornerShape(50.dp))
            )
        }
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit side
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit,
                    null,
                    tint = if (!isViewMode) NeonCyan else Color.White.copy(0.35f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!isViewMode) NeonCyan else Color.White.copy(0.35f),
                    fontWeight = if (!isViewMode) FontWeight.ExtraBold else FontWeight.Normal
                )
            }
            // View side
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Visibility,
                    null,
                    tint = if (isViewMode) NeonPurple else Color.White.copy(0.35f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "View",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isViewMode) NeonPurple else Color.White.copy(0.35f),
                    fontWeight = if (isViewMode) FontWeight.ExtraBold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── View Mode Text Display Box ───────────────────────────────────────────────

@Composable
fun ViewModeBox(
    content: String,
    accentColor: Color,
    emptyHint: String = "Nothing added yet",
    minHeight: Dp = 80.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = accentColor.copy(0.2f))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accentColor.copy(0.08f), Color(0xFF06091A), accentColor.copy(0.04f))))
            .border(1.dp, accentColor.copy(0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Top-left shine
        Box(modifier = Modifier.size(50.dp).align(Alignment.TopStart)
            .background(Brush.radialGradient(listOf(Color.White.copy(0.06f), Color.Transparent)), RoundedCornerShape(16.dp)))
        if (content.isBlank()) {
            Text(emptyHint, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.3f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        } else {
            Text(content, style = MaterialTheme.typography.bodyMedium, color = Color.White, lineHeight = 22.sp, fontFamily = ExoFont)
        }
    }
}

// ─── 3D Modern Date Picker Button ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeetDatePickerButton(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    accentColor: Color = NeonCyan,
    label: String = "Select Date",
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val initialMillis = remember(selectedDate) {
        try {
            if (selectedDate.isNotBlank()) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(selectedDate)?.time
                    ?: System.currentTimeMillis()
            } else System.currentTimeMillis()
        } catch (e: Exception) { System.currentTimeMillis() }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    val infiniteTransition = rememberInfiniteTransition(label = "datebtn_$label")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "datebtn_glow"
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CalendarMonth, null, tint = accentColor.copy(0.8f), modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.9f), fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = accentColor.copy(glowAlpha * 0.8f), ambientColor = accentColor.copy(0.08f))
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(accentColor.copy(0.15f), Color(0xFF080F1F), accentColor.copy(0.07f))))
                .border(1.5.dp, Brush.linearGradient(listOf(accentColor.copy(glowAlpha + 0.1f), accentColor.copy(0.15f), accentColor.copy(glowAlpha * 0.6f))), RoundedCornerShape(18.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(modifier = Modifier.size(60.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(Color.White.copy(0.07f), Color.Transparent)), RoundedCornerShape(18.dp)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = accentColor.copy(0.5f))
                        .background(Brush.linearGradient(listOf(accentColor.copy(0.30f), accentColor.copy(0.08f))), RoundedCornerShape(14.dp))
                        .border(1.dp, accentColor.copy(glowAlpha + 0.2f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (selectedDate.isNotBlank()) {
                        Text(selectedDate, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Text("Tap to pick date", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.35f))
                    }
                    Text("Tap to open calendar", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.55f))
                }
                Box(
                    modifier = Modifier.size(28.dp)
                        .background(accentColor.copy(0.15f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, accentColor.copy(0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = accentColor.copy(0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        localSdf.timeZone = TimeZone.getTimeZone("UTC")
                        onDateSelected(localSdf.format(Date(millis)))
                    }
                    showPicker = false
                }) {
                    Text("Select", color = accentColor, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel", color = Color.White.copy(0.6f)) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF0C1A32), titleContentColor = accentColor,
                headlineContentColor = Color.White, weekdayContentColor = Color.White.copy(0.5f),
                subheadContentColor = Color.White.copy(0.7f), navigationContentColor = accentColor,
                yearContentColor = Color.White, currentYearContentColor = accentColor,
                selectedYearContentColor = Color.Black, selectedYearContainerColor = accentColor,
                dayContentColor = Color.White.copy(0.8f), selectedDayContentColor = Color.Black,
                selectedDayContainerColor = accentColor, todayContentColor = accentColor,
                todayDateBorderColor = accentColor, dividerColor = accentColor.copy(0.2f)
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF0C1A32), titleContentColor = accentColor,
                    headlineContentColor = Color.White, weekdayContentColor = Color.White.copy(0.5f),
                    subheadContentColor = Color.White.copy(0.7f), navigationContentColor = accentColor,
                    yearContentColor = Color.White, currentYearContentColor = accentColor,
                    selectedYearContentColor = Color.Black, selectedYearContainerColor = accentColor,
                    dayContentColor = Color.White.copy(0.8f), selectedDayContentColor = Color.Black,
                    selectedDayContainerColor = accentColor, todayContentColor = accentColor,
                    todayDateBorderColor = accentColor, dividerColor = accentColor.copy(0.2f)
                )
            )
        }
    }
}

// ─── 3D Modern Time Picker Button ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeetTimePickerButton(
    selectedTime: String,
    onTimeSelected: (String) -> Unit,
    accentColor: Color = NeonCyan,
    label: String = "Select Time",
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val initHour = remember(selectedTime) {
        try {
            if (selectedTime.isNotBlank()) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(selectedTime) ?: Date()
                cal.get(Calendar.HOUR_OF_DAY)
            } else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        } catch (e: Exception) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    }
    val initMin = remember(selectedTime) {
        try {
            if (selectedTime.isNotBlank()) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(selectedTime) ?: Date()
                cal.get(Calendar.MINUTE)
            } else Calendar.getInstance().get(Calendar.MINUTE)
        } catch (e: Exception) { Calendar.getInstance().get(Calendar.MINUTE) }
    }

    val timePickerState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin, is24Hour = false)

    val infiniteTransition = rememberInfiniteTransition(label = "timebtn_$label")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f, targetValue = 0.50f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "timebtn_glow"
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.AccessTime, null, tint = accentColor.copy(0.8f), modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.9f), fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = accentColor.copy(glowAlpha * 0.7f))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(accentColor.copy(0.12f), Color(0xFF080F1F), accentColor.copy(0.06f))))
                .border(1.5.dp, Brush.linearGradient(listOf(accentColor.copy(glowAlpha + 0.1f), accentColor.copy(0.15f), accentColor.copy(glowAlpha * 0.6f))), RoundedCornerShape(16.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = accentColor.copy(0.5f))
                        .background(Brush.radialGradient(listOf(accentColor.copy(0.28f), accentColor.copy(0.07f))), RoundedCornerShape(12.dp))
                        .border(1.dp, accentColor.copy(0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccessTime, null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (selectedTime.isNotBlank()) {
                        Text(selectedTime, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Text("Tap to pick time", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.35f))
                    }
                }
                Icon(Icons.Default.KeyboardArrowDown, null, tint = accentColor.copy(0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val dlgGlow by rememberInfiniteTransition(label = "dlg_time").animateFloat(
                initialValue = 0.7f, targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), label = "dlg_glow"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .shadow(32.dp, RoundedCornerShape(30.dp), spotColor = accentColor.copy(0.45f))
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0C1A32), Color(0xFF060C1A), Color(0xFF0A1526))))
                    .border(1.5.dp, Brush.linearGradient(listOf(accentColor.copy(0.8f), accentColor.copy(0.2f), accentColor.copy(0.6f))), RoundedCornerShape(30.dp))
                    .padding(24.dp)
            ) {
                Box(modifier = Modifier.size(150.dp).align(Alignment.TopStart).offset((-30).dp, (-30).dp)
                    .background(Brush.radialGradient(listOf(accentColor.copy(0.12f * dlgGlow), Color.Transparent)), CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(40.dp).background(accentColor.copy(0.18f), RoundedCornerShape(12.dp)).border(1.dp, accentColor.copy(0.5f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccessTime, null, tint = accentColor, modifier = Modifier.size(22.dp))
                        }
                        Text(label, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                    NeonDivider(accentColor.copy(0.4f))
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = accentColor.copy(0.12f),
                            clockDialSelectedContentColor = Color.Black,
                            clockDialUnselectedContentColor = Color.White,
                            selectorColor = accentColor,
                            containerColor = Color.Transparent,
                            periodSelectorBorderColor = accentColor.copy(0.5f),
                            clockDialSelectorHandleContainerColor = accentColor,
                            timeSelectorSelectedContainerColor = accentColor.copy(0.3f),
                            timeSelectorUnselectedContainerColor = Color.White.copy(0.06f),
                            timeSelectorSelectedContentColor = accentColor,
                            timeSelectorUnselectedContentColor = Color.White.copy(0.7f),
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showPicker = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                        Button(
                            onClick = {
                                val h = timePickerState.hour
                                val m = timePickerState.minute
                                val amPm = if (h < 12) "AM" else "PM"
                                val displayH = if (h == 0) 12 else if (h > 12) h - 12 else h
                                onTimeSelected("${displayH.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')} $amPm")
                                showPicker = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.2f)),
                            border = BorderStroke(1.dp, accentColor.copy(0.6f))
                        ) { Text("Set Time", color = accentColor, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
    }
}

// ─── 3D Time Range Picker (Start → End) ───────────────────────────────────────

@Composable
fun NeetTimeRangePickerButton(
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color = NeonCyan,
    label: String = "Time Range",
    modifier: Modifier = Modifier
) {
    val parts = remember(value) {
        if (value.contains("–") || value.contains(" - ")) {
            value.split(Regex("\\s*[–-]\\s*"), limit = 2)
        } else listOf(value, "")
    }
    var startTime by remember(value) { mutableStateOf(parts.getOrElse(0) { "" }.trim()) }
    var endTime   by remember(value) { mutableStateOf(parts.getOrElse(1) { "" }.trim()) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Schedule, null, tint = accentColor.copy(0.8f), modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.9f), fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeetTimePickerButton(
                selectedTime = startTime,
                onTimeSelected = {
                    startTime = it
                    onValueChange(if (endTime.isNotBlank()) "$it – $endTime" else it)
                },
                accentColor = accentColor, label = "Start", modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("→", color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            NeetTimePickerButton(
                selectedTime = endTime,
                onTimeSelected = {
                    endTime = it
                    onValueChange(if (startTime.isNotBlank()) "$startTime – $it" else it)
                },
                accentColor = accentColor, label = "End", modifier = Modifier.weight(1f)
            )
        }
        if (startTime.isNotBlank() && endTime.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(accentColor.copy(0.08f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, accentColor.copy(0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$startTime – $endTime", style = MaterialTheme.typography.labelLarge, color = accentColor, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ─── Comprehensive Rich Text Toolbar ──────────────────────────────────────────

@Composable
fun RichTextToolbar(accentColor: Color = NeonCyan, onInsert: (String) -> Unit) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val tools = listOf("𝗕" to "**", "𝐼" to "_", "U̲" to "__", "S̶" to "~~", "H₁" to "# ", "H₂" to "## ", "H₃" to "### ", "»" to "> ", "{ }" to "`", "[ ]" to "```\n\n```")
            items(tools) { (label, insert) -> ToolButton(label = label, color = accentColor) { onInsert(insert) } }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val bullets = listOf("●" to "\n● ", "▸" to "\n▸ ", "▪" to "\n▪ ", "○" to "\n○ ", "1." to "\n1. ", "2." to "\n2. ", "A." to "\nA. ", "i." to "\ni. ", "→" to "\n→ ", "✓" to "\n✓ ", "✗" to "\n✗ ", "★" to "\n★ ")
            items(bullets) { (label, insert) -> ToolButton(label = label, color = accentColor) { onInsert(insert) } }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val quick = listOf("∴" to "∴ ", "∵" to "∵ ", "⟹" to "⟹ ", "⟺" to "⟺ ", "≠" to "≠", "≈" to "≈", "∞" to "∞", "α" to "α", "β" to "β", "γ" to "γ", "Δ" to "Δ", "σ" to "σ", "π" to "π", "μ" to "μ", "λ" to "λ")
            items(quick) { (label, insert) -> ToolButton(label = label, color = NeonGold) { onInsert(insert) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton(label = "😊", color = NeonGold) { showEmojiPicker = !showEmojiPicker }
            ToolButton(label = "🖊", color = NeonGold) { showHighlights = !showHighlights }
            ToolButton(label = "─", color = accentColor) { onInsert("\n─────────────────\n") }
            ToolButton(label = "⊞", color = accentColor) { onInsert("\n| Col 1 | Col 2 | Col 3 |\n|-------|-------|-------|\n| Data  | Data  | Data  |\n") }
            ToolButton(label = "↵", color = accentColor) { onInsert("\n") }
        }
        AnimatedVisibility(visible = showEmojiPicker) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                val emojis = listOf("😊","😔","💪","🔥","📚","✅","❌","🎯","🌟","💡","⚡","🧠","📝","🏆","😴","🩺","⚗️","🔬","🌿","🐾","💊","🫀","🫁","🦷","👁️","🦴","🧬","📊","📈","📉")
                items(emojis) { emoji ->
                    Text(emoji, fontSize = 22.sp, modifier = Modifier
                        .background(Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                        .clickable { onInsert(emoji); showEmojiPicker = false }
                        .padding(6.dp))
                }
            }
        }
        AnimatedVisibility(visible = showHighlights) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf(HighlightYellow to "Yellow", HighlightGreen to "Green", HighlightBlue to "Blue", HighlightPink to "Pink", HighlightOrange to "Orange").forEach { (color, _) ->
                    Box(modifier = Modifier.size(30.dp).background(color, CircleShape).border(2.dp, Color.White.copy(0.3f), CircleShape).clickable { onInsert("=="); showHighlights = false }) {}
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 30.dp)
            .background(color.copy(0.1f), RoundedCornerShape(8.dp))
            .border(0.5.dp, color.copy(0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// ─── Dialog Text Field ────────────────────────────────────────────────────────

@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    accentColor: Color = NeonCyan,
    multiline: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val borderAlpha by animateFloatAsState(if (focused) 0.75f else 0.25f, label = "field_border")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = accentColor.copy(0.7f), modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.8f), fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .let { if (multiline) it.heightIn(min = 60.dp, max = 140.dp) else it }
                .background(accentColor.copy(0.04f), RoundedCornerShape(12.dp))
                .border(1.dp, accentColor.copy(borderAlpha), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = !multiline,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(label, color = Color.White.copy(0.25f), fontSize = 14.sp, fontFamily = ExoFont)
                    inner()
                }
            )
        }
    }
}

// ─── Simple Add Dialog ────────────────────────────────────────────────────────

@Composable
fun SimpleAddDialog(
    title: String,
    label: String,
    accentColor: Color,
    icon: ImageVector,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    NEETDialog(title = title, icon = icon, accentColor = accentColor, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = text, onValueChange = { text = it }, label = label, icon = icon, accentColor = accentColor)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (text.isNotBlank()) onSave(text) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.2f)), border = BorderStroke(1.dp, accentColor.copy(0.6f))) {
                    Text("Add", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Status Selector Dialog ───────────────────────────────────────────────────

@Composable
fun StatusSelectorDialog(current: Status, onSelect: (Status) -> Unit, onDismiss: () -> Unit) {
    NEETDialog(title = "Set Status", icon = Icons.Default.ToggleOn, accentColor = NeonPurple, onDismiss = onDismiss) {
        val statuses = listOf(
            Triple(Status.EXPECTED, "Expected", StatusExpected),
            Triple(Status.COMPLETED, "Completed", StatusCompleted),
            Triple(Status.REVISION, "Revision", StatusRevision),
            Triple(Status.CROSSED, "Crossed", StatusCross),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            statuses.forEach { (s, label, color) ->
                val selected = current == s
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .shadow(if (selected) 8.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = color.copy(0.4f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) color.copy(0.18f) else Color.White.copy(0.03f))
                        .border(if (selected) 1.5.dp else 0.5.dp, if (selected) color else Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                        .clickable { onSelect(s); onDismiss() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val icon = when (s) {
                        Status.EXPECTED  -> Icons.Default.RadioButtonUnchecked
                        Status.COMPLETED -> Icons.Default.CheckCircle
                        Status.REVISION  -> Icons.Default.Refresh
                        Status.CROSSED   -> Icons.Default.Cancel
                    }
                    Box(modifier = Modifier.size(40.dp).background(color.copy(0.18f), CircleShape).border(1.dp, color.copy(0.55f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
                    Spacer(Modifier.weight(1f))
                    if (selected) Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─── Remark Dialog ────────────────────────────────────────────────────────────

@Composable
fun RemarkDialog(remark: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(remark) }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = "Remarks", icon = Icons.Default.StickyNote2, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "remark_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ViewModeBox(content = text, accentColor = NeonGold, emptyHint = "No remarks added yet", minHeight = 100.dp)
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.15f)), border = BorderStroke(1.dp, NeonGold.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = NeonGold, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RichTextToolbar(accentColor = NeonGold, onInsert = { text += it })
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                            .background(NeonGold.copy(0.04f), RoundedCornerShape(14.dp))
                            .border(1.dp, NeonGold.copy(0.3f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                            BasicTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                                decorationBox = { inner -> if (text.isEmpty()) Text("Add your remarks here...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont); inner() })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)), border = BorderStroke(1.dp, NeonGold.copy(0.6f))) { Text("Save", color = NeonGold, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Tag Dialog ───────────────────────────────────────────────────────────────

val PRESET_TAGS = listOf(
    "Scheduled", "Completed", "Revision", "High Priority", "Low Priority",
    "Important", "Quick Read", "Tough", "Easy", "Reference",
    "Formula", "Theory", "Numericals", "Concept", "NCERT",
    "Must Revise", "Weak Topic", "Strong Topic", "Exam Ready", "Pending"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDialog(currentTags: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selectedTags by remember { mutableStateOf(currentTags.toList()) }
    var customTag by remember { mutableStateOf("") }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = "Manage Tags", icon = Icons.Default.LocalOffer, accentColor = NeonPurple, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "tag_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selectedTags.isEmpty()) {
                            ViewModeBox(content = "", accentColor = NeonPurple, emptyHint = "No tags selected", minHeight = 60.dp)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedTags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = NeonPurple.copy(0.3f))
                                            .background(NeonPurple.copy(0.22f), RoundedCornerShape(20.dp))
                                            .border(0.5.dp, NeonPurple.copy(0.6f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text("# $tag", style = MaterialTheme.typography.labelMedium, color = NeonPurple, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.15f)), border = BorderStroke(1.dp, NeonPurple.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = NeonPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Preset Tags", style = MaterialTheme.typography.labelLarge, color = NeonPurple, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PRESET_TAGS.forEach { tag ->
                                val selected = selectedTags.contains(tag)
                                Box(
                                    modifier = Modifier
                                        .let { if (selected) it.shadow(6.dp, RoundedCornerShape(20.dp), spotColor = NeonPurple.copy(0.4f)) else it }
                                        .background(if (selected) NeonPurple.copy(0.25f) else Color.White.copy(0.05f), RoundedCornerShape(20.dp))
                                        .border(0.5.dp, if (selected) NeonPurple.copy(0.7f) else Color.White.copy(0.12f), RoundedCornerShape(20.dp))
                                        .clickable {
                                            // Fixed: create new list to trigger recomposition
                                            selectedTags = if (selected) {
                                                selectedTags.filter { it != tag }
                                            } else {
                                                selectedTags + tag
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("# $tag", style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) NeonPurple else Color.White.copy(0.6f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        NeonDivider(NeonPurple.copy(0.4f))
                        Text("Custom Tag", style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f).background(Color.White.copy(0.04f), RoundedCornerShape(12.dp)).border(1.dp, NeonCyan.copy(0.3f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                BasicTextField(value = customTag, onValueChange = { customTag = it }, singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                                    decorationBox = { inner -> if (customTag.isEmpty()) Text("Type custom tag...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont); inner() })
                            }
                            Box(modifier = Modifier.size(44.dp).background(NeonCyan.copy(0.15f), RoundedCornerShape(12.dp)).border(1.dp, NeonCyan.copy(0.4f), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (customTag.isNotBlank()) {
                                        selectedTags = selectedTags + customTag.trim()
                                        customTag = ""
                                    }
                                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = NeonCyan) }
                        }

                        if (selectedTags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(selectedTags) { tag ->
                                    TagChip(tag = tag, selected = true, onRemove = { selectedTags = selectedTags.filter { it != tag } })
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(selectedTags); onDismiss() }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) {
                                Text("Apply", color = NeonPurple, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Completion Date Dialog ───────────────────────────────────────────────────

@Composable
fun CompletionDateDialog(dates: List<CompletionDate>, onSave: (List<CompletionDate>) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(dates.toList()) }
    var newDate by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = "Completion Dates", icon = Icons.Default.DateRange, accentColor = NeonGreen, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "cdate_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (list.isEmpty()) {
                            ViewModeBox(content = "", accentColor = NeonGreen, emptyHint = "No completion dates yet", minHeight = 60.dp)
                        } else {
                            list.forEach { cd ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(NeonGreen.copy(0.08f), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, NeonGreen.copy(0.3f), RoundedCornerShape(12.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(32.dp).background(NeonGreen.copy(0.18f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.CalendarToday, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(cd.date, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.ExtraBold)
                                        if (cd.note.isNotBlank()) Text("(${cd.note})", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                                    }
                                }
                            }
                        }
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.15f)), border = BorderStroke(1.dp, NeonGreen.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = NeonGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(list) { cd ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(NeonGreen.copy(0.08f), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, NeonGreen.copy(0.3f), RoundedCornerShape(12.dp)).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(32.dp).background(NeonGreen.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.CalendarToday, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cd.date, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.ExtraBold)
                                        if (cd.note.isNotBlank()) Text("(${cd.note})", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                                    }
                                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.6f)) { list = list.filter { it != cd } }
                                }
                            }
                            if (list.isEmpty()) { item { Text("No dates added yet", color = Color.White.copy(0.3f), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) } }
                        }
                        NeonDivider(NeonGreen.copy(0.5f))
                        NeetDatePickerButton(selectedDate = newDate, onDateSelected = { newDate = it }, accentColor = NeonGreen, label = "Pick Completion Date")
                        DialogTextField(value = newNote, onValueChange = { newNote = it }, label = "Note (optional)", icon = Icons.Default.Notes, accentColor = NeonGreen)
                        Button(
                            onClick = { if (newDate.isNotBlank()) { list = list + CompletionDate(newDate, newNote); newDate = ""; newNote = "" } },
                            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.15f)), border = BorderStroke(1.dp, NeonGreen.copy(0.5f))) {
                            Text("+ Add Date", color = NeonGreen, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(list); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.2f)), border = BorderStroke(1.dp, NeonGreen.copy(0.6f))) { Text("Save", color = NeonGreen, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Wrong Questions Dialog ───────────────────────────────────────────────────

@Composable
fun WrongQuestionsDialog(wrongQuestions: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(wrongQuestions) }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = "Wrong Questions", icon = Icons.Default.ErrorOutline, accentColor = NeonRed, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "wq_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ViewModeBox(content = text, accentColor = NeonRed, emptyHint = "No wrong questions logged yet", minHeight = 80.dp)
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.15f)), border = BorderStroke(1.dp, NeonRed.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = NeonRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = NeonRed, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Enter question numbers (comma or newline separated)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                            .background(NeonRed.copy(0.05f), RoundedCornerShape(14.dp)).border(1.dp, NeonRed.copy(0.3f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                            BasicTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                                decorationBox = { inner -> if (text.isEmpty()) Text("e.g. Q12, Q34, Q56...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont); inner() })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.2f)), border = BorderStroke(1.dp, NeonRed.copy(0.6f))) { Text("Save", color = NeonRed, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Specification / Missing Notes / Info Dialog ───────────────────────────────

@Composable
fun SpecificationDialog(title: String, content: String, onSave: (String) -> Unit, onDismiss: () -> Unit, accentColor: Color = NeonCyan) {
    var text by remember { mutableStateOf(content) }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = title, icon = Icons.Default.Info, accentColor = accentColor, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "spec_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ViewModeBox(content = text, accentColor = accentColor, emptyHint = "Nothing added yet", minHeight = 100.dp)
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.15f)), border = BorderStroke(1.dp, accentColor.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = accentColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RichTextToolbar(accentColor = accentColor, onInsert = { text += it })
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
                            .background(accentColor.copy(0.04f), RoundedCornerShape(14.dp)).border(1.dp, accentColor.copy(0.25f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                            BasicTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                                decorationBox = { inner -> if (text.isEmpty()) Text("Add content here...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont); inner() })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.2f)), border = BorderStroke(1.dp, accentColor.copy(0.6f))) { Text("Save", color = accentColor, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissingNotesDialog(missingNotes: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    SpecificationDialog("Missing Notes", missingNotes, onSave, onDismiss, NeonOrange)
}

@Composable
fun InfoDialog(info: String, onSave: (String) -> Unit, onDismiss: () -> Unit, accentColor: Color = NeonCyan) {
    SpecificationDialog("Info / Details", info, onSave, onDismiss, accentColor)
}

// ─── Prefix Date Dialog (3D Calendar Picker) ──────────────────────────────────

@Composable
fun PrefixDateDialog(currentDate: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(currentDate) }
    NEETDialog(title = "Set Date / Schedule", icon = Icons.Default.Schedule, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NeetDatePickerButton(selectedDate = date, onDateSelected = { date = it }, accentColor = NeonGold, label = "Schedule Date")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(date); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)), border = BorderStroke(1.dp, NeonGold.copy(0.6f))) {
                    Text("Set Date", color = NeonGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Topics Asked Dialog ──────────────────────────────────────────────────────

@Composable
fun TopicsDialog(currentTopics: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var topics by remember { mutableStateOf(currentTopics) }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = "Topics Asked", icon = Icons.Default.Topic, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "topics_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ViewModeBox(content = topics, accentColor = NeonCyan, emptyHint = "No topics listed yet", minHeight = 80.dp)
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.15f)), border = BorderStroke(1.dp, NeonCyan.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("List the topics that appeared in this test/paper", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                            .background(NeonCyan.copy(0.04f), RoundedCornerShape(14.dp)).border(1.dp, NeonCyan.copy(0.3f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = topics, onValueChange = { topics = it }, modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                                decorationBox = { inner ->
                                    if (topics.isEmpty()) Text("e.g. Genetics, Cell Division, Photosynthesis...", color = Color.White.copy(0.3f), fontSize = 14.sp)
                                    inner()
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(topics); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) { Text("Save", color = NeonCyan, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Marks Obtained Dialog ────────────────────────────────────────────────────

@Composable
fun MarksDialog(currentMarks: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var marks by remember { mutableStateOf(currentMarks) }
    var isViewMode by remember { mutableStateOf(false) }

    NEETDialog(title = "Marks Obtained", icon = Icons.Default.Star, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })

            AnimatedContent(targetState = isViewMode, transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            }, label = "marks_content") { viewMode ->
                if (viewMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = NeonGold.copy(0.3f))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(NeonGold.copy(0.15f), Color(0xFF080F1F), NeonGold.copy(0.08f))))
                                .border(1.dp, NeonGold.copy(0.4f), RoundedCornerShape(20.dp))
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Star, null, tint = NeonGold, modifier = Modifier.size(32.dp))
                                Text(
                                    if (marks.isBlank()) "No marks recorded" else marks,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = if (marks.isBlank()) Color.White.copy(0.3f) else NeonGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.15f)), border = BorderStroke(1.dp, NeonGold.copy(0.4f))) {
                            Icon(Icons.Default.Close, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close", color = NeonGold, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        DialogTextField(value = marks, onValueChange = { marks = it }, label = "Marks / Score (e.g. 680/720)", icon = Icons.Default.Star, accentColor = NeonGold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                            Button(onClick = { onSave(marks); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)), border = BorderStroke(1.dp, NeonGold.copy(0.6f))) { Text("Save Marks", color = NeonGold, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── URL / Link Dialog ────────────────────────────────────────────────────────

@Composable
fun URLDialog(currentUrl: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(currentUrl) }
    NEETDialog(title = "Video / Link", icon = Icons.Default.Link, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = url, onValueChange = { url = it }, label = "YouTube or PDF URL", icon = Icons.Default.Link, accentColor = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(url); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) { Text("Save Link", color = NeonCyan, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
