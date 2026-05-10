package com.neet.tracker.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
                // Glow orb
                Box(modifier = Modifier.size(180.dp).align(Alignment.TopStart).offset((-35).dp, (-35).dp)
                    .background(Brush.radialGradient(listOf(accentColor.copy(0.14f * glowR), Color.Transparent)), CircleShape))
                Box(modifier = Modifier.size(120.dp).align(Alignment.BottomEnd).offset(25.dp, 25.dp)
                    .background(Brush.radialGradient(listOf(accentColor.copy(0.08f * glowR), Color.Transparent)), CircleShape))

                // Scan line effect
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                    .offset(y = (scanLine * 1000).dp.coerceAtMost(600.dp))
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, accentColor.copy(0.15f), Color.Transparent))))

                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    // Header
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

// ─── Comprehensive Rich Text Toolbar ──────────────────────────────────────────

@Composable
fun RichTextToolbar(accentColor: Color = NeonCyan, onInsert: (String) -> Unit) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showSpecial by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 1 — Text formatting
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val tools = listOf(
                "𝗕" to "**",          "𝐼" to "_",           "U̲" to "__",         "S̶" to "~~",
                "H₁" to "# ",         "H₂" to "## ",         "H₃" to "### ",       "»" to "> ",
                "{ }" to "`",          "[ ]" to "```\n\n```"
            )
            items(tools) { (label, insert) ->
                ToolButton(label = label, color = accentColor) { onInsert(insert) }
            }
        }

        // Row 2 — Lists and bullets
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val bullets = listOf(
                "●" to "\n● ",   "▸" to "\n▸ ",   "▪" to "\n▪ ",   "○" to "\n○ ",
                "1." to "\n1. ", "2." to "\n2. ", "A." to "\nA. ", "i." to "\ni. ",
                "→" to "\n→ ",  "✓" to "\n✓ ",   "✗" to "\n✗ ",   "★" to "\n★ "
            )
            items(bullets) { (label, insert) ->
                ToolButton(label = label, color = accentColor) { onInsert(insert) }
            }
        }

        // Row 3 — Quick inserts
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val quick = listOf(
                "∴" to "∴ ", "∵" to "∵ ", "⟹" to "⟹ ", "⟺" to "⟺ ",
                "≠" to "≠",  "≈" to "≈",  "∞" to "∞",   "α" to "α",
                "β" to "β",  "γ" to "γ",  "Δ" to "Δ",   "σ" to "σ",
                "π" to "π",  "μ" to "μ",  "λ" to "λ"
            )
            items(quick) { (label, insert) ->
                ToolButton(label = label, color = NeonGold) { onInsert(insert) }
            }
        }

        // Row 4 — Highlight / special
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Emoji toggle
            ToolButton(label = "😊", color = NeonGold) { showEmojiPicker = !showEmojiPicker }
            // Highlight toggle
            ToolButton(label = "🖊", color = NeonGold) { showHighlights = !showHighlights }
            // Special symbols
            ToolButton(label = "Σ", color = NeonPurple) { showSpecial = !showSpecial }
            // Divider line
            ToolButton(label = "─", color = accentColor) { onInsert("\n─────────────────\n") }
            // Table
            ToolButton(label = "⊞", color = accentColor) { onInsert("\n| Col 1 | Col 2 | Col 3 |\n|-------|-------|-------|\n| Data  | Data  | Data  |\n") }
            // New line
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
                listOf(
                    HighlightYellow to "Yellow",
                    HighlightGreen  to "Green",
                    HighlightBlue   to "Blue",
                    HighlightPink   to "Pink",
                    HighlightOrange to "Orange"
                ).forEach { (color, name) ->
                    Box(
                        modifier = Modifier.size(30.dp)
                            .background(color, CircleShape)
                            .border(2.dp, Color.White.copy(0.3f), CircleShape)
                            .clickable { onInsert("=="); showHighlights = false },
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 34.dp, minHeight = 30.dp)
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
            modifier = Modifier
                .fillMaxWidth()
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
            Triple(Status.EXPECTED,  "Expected",  StatusExpected),
            Triple(Status.COMPLETED, "Completed", StatusCompleted),
            Triple(Status.REVISION,  "Revision",  StatusRevision),
            Triple(Status.CROSSED,   "Crossed",   StatusCross),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            statuses.forEach { (s, label, color) ->
                val selected = current == s
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
    NEETDialog(title = "Remarks", icon = Icons.Default.StickyNote2, accentColor = NeonGold, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RichTextToolbar(accentColor = NeonGold, onInsert = { text += it })
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                    .background(NeonGold.copy(0.04f), RoundedCornerShape(14.dp))
                    .border(1.dp, NeonGold.copy(0.3f), RoundedCornerShape(14.dp)).padding(12.dp)
            ) {
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

// ─── Tag Dialog ───────────────────────────────────────────────────────────────

val PRESET_TAGS = listOf(
    "Scheduled", "Completed", "Revision", "High Priority", "Low Priority",
    "Important", "Quick Read", "Tough", "Easy", "Reference",
    "Formula", "Theory", "Numericals", "Concept", "NCERT",
    "Must Revise", "Weak Topic", "Strong Topic", "Exam Ready", "Pending"
)

@Composable
fun TagDialog(currentTags: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selectedTags by remember { mutableStateOf(currentTags.toMutableList()) }
    var customTag by remember { mutableStateOf("") }

    NEETDialog(title = "Manage Tags", icon = Icons.Default.LocalOffer, accentColor = NeonPurple, onDismiss = onDismiss) {
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
                                selectedTags = if (selected) selectedTags.also { it.remove(tag) }
                                else selectedTags.also { it.add(tag) }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("# $tag", style = MaterialTheme.typography.labelSmall, color = if (selected) NeonPurple else Color.White.copy(0.6f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
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
                Box(modifier = Modifier.size(44.dp).background(NeonCyan.copy(0.15f), RoundedCornerShape(12.dp)).border(1.dp, NeonCyan.copy(0.4f), RoundedCornerShape(12.dp)).clickable {
                    if (customTag.isNotBlank()) { selectedTags = selectedTags.also { it.add(customTag.trim()) }; customTag = "" }
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = NeonCyan) }
            }

            if (selectedTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(selectedTags) { tag -> TagChip(tag = tag, selected = true, onRemove = { selectedTags = selectedTags.also { it.remove(tag) } }) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave(selectedTags.toList()); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) { Text("Apply", color = NeonPurple, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Completion Date Dialog ───────────────────────────────────────────────────

@Composable
fun CompletionDateDialog(dates: List<CompletionDate>, onSave: (List<CompletionDate>) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(dates.toMutableList()) }
    var newDate by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }

    NEETDialog(title = "Completion Dates", icon = Icons.Default.DateRange, accentColor = NeonGreen, onDismiss = onDismiss) {
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
                        CardIconButton(Icons.Default.Delete, NeonRed.copy(0.6f)) { list = list.also { it.remove(cd) } }
                    }
                }
                if (list.isEmpty()) { item { Text("No dates added yet", color = Color.White.copy(0.3f), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) } }
            }
            NeonDivider(NeonGreen.copy(0.5f))
            DialogTextField(value = newDate, onValueChange = { newDate = it }, label = "Date (DD/MM/YYYY)", icon = Icons.Default.CalendarToday, accentColor = NeonGreen)
            DialogTextField(value = newNote, onValueChange = { newNote = it }, label = "Note (optional)", icon = Icons.Default.Notes, accentColor = NeonGreen)
            Button(onClick = { if (newDate.isNotBlank()) { list = list.also { it.add(CompletionDate(newDate, newNote)) }; newDate = ""; newNote = "" } },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.15f)), border = BorderStroke(1.dp, NeonGreen.copy(0.5f))) {
                Text("+ Add Date", color = NeonGreen, fontWeight = FontWeight.Bold)
            }
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

// ─── Specification / Missing Notes Dialog ─────────────────────────────────────

@Composable
fun SpecificationDialog(title: String, content: String, onSave: (String) -> Unit, onDismiss: () -> Unit, accentColor: Color = NeonCyan) {
    var text by remember { mutableStateOf(content) }
    NEETDialog(title = title, icon = Icons.Default.Info, accentColor = accentColor, onDismiss = onDismiss) {
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

@Composable
fun MissingNotesDialog(missingNotes: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    SpecificationDialog("Missing Notes", missingNotes, onSave, onDismiss, NeonOrange)
}

// ─── Info Dialog ──────────────────────────────────────────────────────────────

@Composable
fun InfoDialog(info: String, onSave: (String) -> Unit, onDismiss: () -> Unit, accentColor: Color = NeonCyan) {
    SpecificationDialog("Info / Details", info, onSave, onDismiss, accentColor)
}
