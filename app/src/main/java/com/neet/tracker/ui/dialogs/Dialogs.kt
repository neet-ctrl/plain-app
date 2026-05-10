package com.neet.tracker.ui.dialogs

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

// ─── Base Dialog Shell ────────────────────────────────────────────────────────

@Composable
fun NEETDialog(
    title: String,
    icon: ImageVector = Icons.Default.Info,
    accentColor: Color = NeonCyan,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "dialog_glow")
        val glowRadius by infiniteTransition.animateFloat(
            initialValue = 0.8f, targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "glow"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0D1F3C), Color(0xFF060E1E), Color(0xFF0A1628))
                    )
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.8f),
                            accentColor.copy(alpha = 0.2f),
                            accentColor.copy(alpha = 0.6f)
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
        ) {
            // Glow orb behind dialog
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.TopStart)
                    .offset((-40).dp, (-40).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.12f * glowRadius),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(0.5f))
                    }
                }

                Spacer(Modifier.height(16.dp))
                NeonDivider(accentColor)
                Spacer(Modifier.height(16.dp))

                content()
            }
        }
    }
}

// ─── Status Selector Dialog ───────────────────────────────────────────────────

@Composable
fun StatusSelectorDialog(
    current: Status,
    onSelect: (Status) -> Unit,
    onDismiss: () -> Unit
) {
    NEETDialog(
        title = "Set Status",
        icon = Icons.Default.ToggleOn,
        accentColor = NeonPurple,
        onDismiss = onDismiss
    ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                        .border(
                            width = if (selected) 1.5.dp else 0.5.dp,
                            color = if (selected) color else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(s); onDismiss() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val icon = when (s) {
                        Status.EXPECTED -> Icons.Default.RadioButtonUnchecked
                        Status.COMPLETED -> Icons.Default.CheckCircle
                        Status.REVISION -> Icons.Default.Refresh
                        Status.CROSSED -> Icons.Default.Cancel
                    }
                    Box(
                        modifier = Modifier.size(36.dp)
                            .background(color.copy(0.15f), CircleShape)
                            .border(1.dp, color.copy(0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.weight(1f))
                    if (selected) Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─── Remark Dialog ────────────────────────────────────────────────────────────

@Composable
fun RemarkDialog(
    remark: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(remark) }
    NEETDialog(title = "Remarks", icon = Icons.Default.StickyNote2, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp)
                    .background(Color.White.copy(0.04f), RoundedCornerShape(12.dp))
                    .border(1.dp, NeonGold.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Add your remarks here...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont)
                        inner()
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Cancel") }
                Button(
                    onClick = { onSave(text); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)),
                    border = BorderStroke(1.dp, NeonGold.copy(0.6f))
                ) { Text("Save", color = NeonGold, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Tag Dialog ───────────────────────────────────────────────────────────────

val PRESET_TAGS = listOf(
    "Scheduled", "Completed", "Revision", "High Priority", "Low Priority",
    "Important", "Quick Read", "Tough", "Easy", "Reference",
    "Formula", "Theory", "Numericals", "Concept", "NCERT"
)

@Composable
fun TagDialog(
    currentTags: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTags by remember { mutableStateOf(currentTags.toMutableList()) }
    var customTag by remember { mutableStateOf("") }

    NEETDialog(title = "Add Tags", icon = Icons.Default.LocalOffer, accentColor = NeonPurple, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Preset Tags", style = MaterialTheme.typography.labelLarge, color = NeonPurple)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_TAGS.forEach { tag ->
                    val selected = selectedTags.contains(tag)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTags = if (selected) selectedTags.also { it.remove(tag) }
                            else selectedTags.also { it.add(tag) }
                        },
                        label = { Text(tag, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple.copy(0.25f),
                            selectedLabelColor = NeonPurple,
                            containerColor = Color.White.copy(0.05f),
                            labelColor = Color.White.copy(0.7f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = NeonPurple.copy(0.6f),
                            borderColor = Color.White.copy(0.1f)
                        )
                    )
                }
            }

            Text("Custom Tag", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(0.04f), RoundedCornerShape(10.dp))
                        .border(1.dp, NeonCyan.copy(0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = customTag,
                        onValueChange = { customTag = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                        decorationBox = { inner ->
                            if (customTag.isEmpty()) Text("Type custom tag...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont)
                            inner()
                        }
                    )
                }
                IconButton(
                    onClick = {
                        if (customTag.isNotBlank()) {
                            selectedTags = selectedTags.also { it.add(customTag.trim()) }
                            customTag = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(NeonCyan.copy(0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, NeonCyan.copy(0.4f), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Add, null, tint = NeonCyan)
                }
            }

            if (selectedTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(selectedTags) { tag ->
                        TagChip(tag = tag, selected = true, onRemove = {
                            selectedTags = selectedTags.also { it.remove(tag) }
                        })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Cancel") }
                Button(
                    onClick = { onSave(selectedTags.toList()); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)),
                    border = BorderStroke(1.dp, NeonPurple.copy(0.6f))
                ) { Text("Apply", color = NeonPurple, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Completion Date Dialog ───────────────────────────────────────────────────

@Composable
fun CompletionDateDialog(
    dates: List<CompletionDate>,
    onSave: (List<CompletionDate>) -> Unit,
    onDismiss: () -> Unit
) {
    var list by remember { mutableStateOf(dates.toMutableList()) }
    var newDate by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }

    NEETDialog(title = "Completion Dates", icon = Icons.Default.DateRange, accentColor = NeonGreen, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { cd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeonGreen.copy(0.08f), RoundedCornerShape(10.dp))
                            .border(0.5.dp, NeonGreen.copy(0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cd.date, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                            if (cd.note.isNotBlank()) Text("(${cd.note})", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                        }
                        IconButton(onClick = { list = list.also { it.remove(cd) } }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (list.isEmpty()) {
                    item { Text("No dates added yet", color = Color.White.copy(0.3f), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                }
            }

            NeonDivider(NeonGreen)
            Text("Add New Date", style = MaterialTheme.typography.labelLarge, color = NeonGreen)

            DialogTextField(value = newDate, onValueChange = { newDate = it }, label = "Date (DD/MM/YYYY)", icon = Icons.Default.CalendarToday, accentColor = NeonGreen)
            DialogTextField(value = newNote, onValueChange = { newNote = it }, label = "Note (optional)", icon = Icons.Default.Notes, accentColor = NeonGreen)

            Button(
                onClick = {
                    if (newDate.isNotBlank()) {
                        list = list.also { it.add(CompletionDate(newDate, newNote)) }
                        newDate = ""; newNote = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.15f)),
                border = BorderStroke(1.dp, NeonGreen.copy(0.5f))
            ) { Text("+ Add Date", color = NeonGreen, fontWeight = FontWeight.Bold) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(list.toList()); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.2f)), border = BorderStroke(1.dp, NeonGreen.copy(0.6f))) { Text("Save", color = NeonGreen, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Wrong Questions Dialog ───────────────────────────────────────────────────

@Composable
fun WrongQuestionsDialog(wrongQuestions: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(wrongQuestions) }
    NEETDialog(title = "Wrong Questions", icon = Icons.Default.ErrorOutline, accentColor = NeonRed, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Enter question numbers separated by commas or new lines", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp)
                    .background(NeonRed.copy(0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, NeonRed.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("e.g. Q12, Q34, Q56...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont)
                        inner()
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.2f)), border = BorderStroke(1.dp, NeonRed.copy(0.6f))) { Text("Save", color = NeonRed, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Info / Specification Dialog ─────────────────────────────────────────────

@Composable
fun SpecificationDialog(
    title: String,
    content: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color = NeonCyan
) {
    var text by remember { mutableStateOf(content) }
    NEETDialog(title = title, icon = Icons.Default.Info, accentColor = accentColor, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Rich text toolbar
            RichTextToolbar(accentColor = accentColor, onInsert = { text += it })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .background(accentColor.copy(0.04f), RoundedCornerShape(12.dp))
                    .border(1.dp, accentColor.copy(0.25f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Add content here...", color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont)
                        inner()
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.2f)), border = BorderStroke(1.dp, accentColor.copy(0.6f))) { Text("Save", color = accentColor, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Rich Text Toolbar ────────────────────────────────────────────────────────

@Composable
fun RichTextToolbar(accentColor: Color = NeonCyan, onInsert: (String) -> Unit) {
    val tools = listOf(
        "•" to "Bullet", "1." to "Number", "→" to "Arrow",
        "★" to "Star", "✓" to "Tick", "✗" to "Cross",
        "⚡" to "Flash", "📌" to "Pin", "🔬" to "Science",
        "💡" to "Idea", "\n▌" to "Section", "═══" to "Line"
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(tools) { (symbol, label) ->
            Box(
                modifier = Modifier
                    .background(accentColor.copy(0.1f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, accentColor.copy(0.3f), RoundedCornerShape(8.dp))
                    .clickable { onInsert("\n$symbol ") }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Missing Notes Dialog ─────────────────────────────────────────────────────

@Composable
fun MissingNotesDialog(
    notes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SpecificationDialog(
        title = "Missing Notes",
        content = notes,
        onSave = onSave,
        onDismiss = onDismiss,
        accentColor = NeonOrange
    )
}

// ─── URL Dialog ───────────────────────────────────────────────────────────────

@Composable
fun URLDialog(url: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(url) }
    NEETDialog(title = "Add URL", icon = Icons.Default.Link, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = text, onValueChange = { text = it }, label = "URL", icon = Icons.Default.Language, accentColor = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) { Text("Save", color = NeonCyan, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Prefix Date Dialog ───────────────────────────────────────────────────────

@Composable
fun PrefixDateDialog(date: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(date) }
    NEETDialog(title = "Scheduled / Prefix Date", icon = Icons.Default.Schedule, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set expected or completed date", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
            DialogTextField(value = text, onValueChange = { text = it }, label = "Date (DD/MM/YYYY or description)", icon = Icons.Default.CalendarMonth, accentColor = NeonGold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)), border = BorderStroke(1.dp, NeonGold.copy(0.6f))) { Text("Save", color = NeonGold, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Marks Dialog ─────────────────────────────────────────────────────────────

@Composable
fun MarksDialog(marks: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(marks) }
    NEETDialog(title = "Marks Obtained", icon = Icons.Default.Score, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = text, onValueChange = { text = it }, label = "Marks (e.g. 680/720)", icon = Icons.Default.Star, accentColor = NeonGold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.2f)), border = BorderStroke(1.dp, NeonGold.copy(0.6f))) { Text("Save", color = NeonGold, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Topics Asked Dialog ──────────────────────────────────────────────────────

@Composable
fun TopicsDialog(topics: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    SpecificationDialog(title = "Topics Asked", content = topics, onSave = onSave, onDismiss = onDismiss, accentColor = NeonPurple)
}

// ─── Shared input field ───────────────────────────────────────────────────────

@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    accentColor: Color = NeonCyan,
    multiline: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accentColor.copy(0.8f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor.copy(0.05f), RoundedCornerShape(10.dp))
                .border(1.dp, accentColor.copy(0.2f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = if (multiline) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = accentColor.copy(0.6f), modifier = Modifier.size(18.dp))
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
