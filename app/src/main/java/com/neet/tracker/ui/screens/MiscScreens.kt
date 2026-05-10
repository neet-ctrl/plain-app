package com.neet.tracker.ui.screens

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.fileViewerRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.*

// ─── NEET Syllabus ────────────────────────────────────────────────────────────

@Composable
fun NEETSyllabusScreen(navController: NavController, vm: SyllabusViewModel = hiltViewModel()) {
    val syllabus by vm.syllabus.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                vm.save(NEETSyllabus(fileUri = localPath ?: u.toString()))
            }
        }
    }
    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "NEET Syllabus", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GlassCard(glowColor = NeonOrange) {
                    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ThreeDIconBox(icon = Icons.Default.School, tint = NeonOrange, size = 80.dp, iconSize = 44.dp)
                        Text("NEET Syllabus", style = MaterialTheme.typography.displaySmall, color = NeonOrange, fontWeight = FontWeight.ExtraBold)
                        if (syllabus?.fileUri?.isNotBlank() == true) {
                            Button(onClick = { navController.navigate(fileViewerRoute(syllabus!!.fileUri, "NEET Syllabus")) }, colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(0.2f)), border = BorderStroke(1.dp, NeonGreen.copy(0.6f)), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.FileOpen, null, tint = NeonGreen); Spacer(Modifier.width(8.dp)); Text("View Syllabus PDF", color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { vm.save(syllabus!!.copy(fileUri = "")) }, colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.15f)), border = BorderStroke(1.dp, NeonRed.copy(0.5f)), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Close, null, tint = NeonRed); Spacer(Modifier.width(8.dp)); Text("Remove PDF", color = NeonRed, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("No syllabus uploaded yet", color = Color.White.copy(0.4f))
                        }
                        Button(onClick = { launcher.launch(arrayOf("application/pdf")) }, colors = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(0.2f)), border = BorderStroke(1.dp, NeonOrange.copy(0.6f)), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.UploadFile, null, tint = NeonOrange); Spacer(Modifier.width(8.dp)); Text(if (syllabus?.fileUri?.isNotBlank() == true) "Replace PDF" else "Upload Syllabus PDF", color = NeonOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─── Dictionary - NEET ───────────────────────────────────────────────────────

@Composable
fun DictionaryNeetScreen(navController: NavController, vm: DictionaryViewModel = hiltViewModel()) {
    val terms by vm.neetTerms.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadTarget by remember { mutableStateOf<DictionaryNeet?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadTarget
        uploadTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { t -> vm.saveNeet(t.copy(fileUri = localPath ?: u.toString())) }
            }
        }
    }

    val allTags = terms.flatMap { it.tags }.distinct()
    val filtered = terms.filter {
        (searchQuery.isBlank() || it.term.contains(searchQuery, true) || it.definition.contains(searchQuery, true)) &&
        (selectedTag == null || it.tags.contains(selectedTag)) &&
        (selectedSubject == null || it.subject == selectedSubject)
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonCyan) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "NEET Lexicon", breadcrumb = "Home / Dictionary", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search terms...")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = selectedSubject == null, onClick = { selectedSubject = null }, label = { Text("All Subjects") }) }
                    items(Subject.values().toList()) { s -> FilterChip(selected = selectedSubject == s, onClick = { selectedSubject = if (selectedSubject == s) null else s }, label = { Text(s.name) }) }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) EmptyState("No terms yet. Tap + to add.", Icons.Default.AutoStories)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    itemsIndexed(filtered) { _, term ->
                        DictionaryTermCard(
                            term = term,
                            onDelete = { vm.deleteNeet(term) },
                            onViewFile = if (term.fileUri.isNotBlank()) { { navController.navigate(fileViewerRoute(term.fileUri, term.term)) } } else null,
                            onUploadFile = { uploadTarget = term; fileLauncher.launch(arrayOf("*/*")) },
                            onRemoveFile = if (term.fileUri.isNotBlank()) { { vm.saveNeet(term.copy(fileUri = "")) } } else null
                        )
                    }
                }
            }
        }
    }
    if (showAdd) AddNeetTermDialog(serialNo = terms.size + 1, onSave = { vm.saveNeet(it); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun DictionaryTermCard(
    term: DictionaryNeet,
    onDelete: () -> Unit,
    onViewFile: (() -> Unit)? = null,
    onUploadFile: (() -> Unit)? = null,
    onRemoveFile: (() -> Unit)? = null
) {
    GlassCard(glowColor = NeonCyan, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${term.serialNo}.", style = MaterialTheme.typography.labelLarge, color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                Text(term.term, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp)) }
            }
            Text(term.definition, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (term.chapter.isNotBlank()) Text("Ch: ${term.chapter}", style = MaterialTheme.typography.labelSmall, color = NeonPurple)
                Text(term.subject.name, style = MaterialTheme.typography.labelSmall, color = NeonGold)
            }
            if (term.tags.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(term.tags) { t -> TagChip(t) } }
            if (onViewFile != null || onUploadFile != null) {
                NeonDivider(NeonCyan.copy(0.2f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (term.fileUri.isNotBlank() && onViewFile != null) {
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
                            Icon(Icons.Default.UploadFile, tint = NeonCyan.copy(0.5f), modifier = Modifier.size(16.dp), contentDescription = "Replace file")
                        }
                        if (onRemoveFile != null) {
                            IconButton(onClick = onRemoveFile, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, tint = NeonRed.copy(0.75f), modifier = Modifier.size(16.dp), contentDescription = "Remove file")
                            }
                        }
                    } else if (onUploadFile != null) {
                        OutlinedButton(
                            onClick = onUploadFile,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NeonCyan.copy(0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
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

@Composable
fun AddNeetTermDialog(serialNo: Int, onSave: (DictionaryNeet) -> Unit, onDismiss: () -> Unit) {
    var term by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Subject.GENERAL) }

    NEETDialog(title = "Add NEET Term", icon = Icons.Default.Science, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = term, onValueChange = { term = it }, label = "Term / Word", icon = Icons.Default.Abc, accentColor = NeonCyan)
            DialogTextField(value = definition, onValueChange = { definition = it }, label = "Definition", icon = Icons.Default.Info, accentColor = NeonCyan, multiline = true)
            DialogTextField(value = chapter, onValueChange = { chapter = it }, label = "Chapter", icon = Icons.Default.MenuBook, accentColor = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.values().forEach { s ->
                    FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s.name, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.2f), selectedLabelColor = NeonCyan))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (term.isNotBlank()) onSave(DictionaryNeet(term = term, definition = definition, chapter = chapter, subject = subject, serialNo = serialNo)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) { Text("Add", color = NeonCyan, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Dictionary - Non-NEET ────────────────────────────────────────────────────

@Composable
fun DictionaryNonNeetScreen(navController: NavController, vm: DictionaryViewModel = hiltViewModel()) {
    val words by vm.nonNeetTerms.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = words.filter { searchQuery.isBlank() || it.word.contains(searchQuery, true) || it.meaning.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonPurple) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Word Bank", breadcrumb = "Home / Dictionary", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search words...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No words yet. Tap + to add.", Icons.Default.Translate)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    itemsIndexed(filtered) { _, w ->
                        GlassCard(glowColor = NeonPurple, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(w.word, style = MaterialTheme.typography.headlineSmall, color = NeonPurple, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { vm.deleteNonNeet(w) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp)) }
                                }
                                Text(w.meaning, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
                                if (w.example.isNotBlank()) Text("\"${w.example}\"", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        var word by remember { mutableStateOf("") }
        var meaning by remember { mutableStateOf("") }
        var example by remember { mutableStateOf("") }
        NEETDialog(title = "Add Word", icon = Icons.Default.Translate, accentColor = NeonPurple, onDismiss = { showAdd = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogTextField(value = word, onValueChange = { word = it }, label = "Word", icon = Icons.Default.Abc, accentColor = NeonPurple)
                DialogTextField(value = meaning, onValueChange = { meaning = it }, label = "Meaning", icon = Icons.Default.Info, accentColor = NeonPurple, multiline = true)
                DialogTextField(value = example, onValueChange = { example = it }, label = "Example Sentence", icon = Icons.Default.FormatQuote, accentColor = NeonPurple)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showAdd = false }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                    Button(onClick = { if (word.isNotBlank()) { vm.saveNonNeet(DictionaryNonNeet(word = word, meaning = meaning, example = example)); showAdd = false } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) { Text("Add", color = NeonPurple, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─── Mnemonics ────────────────────────────────────────────────────────────────

@Composable
fun MnemonicsScreen(navController: NavController, vm: MnemonicViewModel = hiltViewModel()) {
    val mnemonics by vm.mnemonics.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = mnemonics.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.chapter.contains(searchQuery, true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadMnemTarget by remember { mutableStateOf<Mnemonic?>(null) }
    val mnemFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadMnemTarget
        uploadMnemTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { t -> vm.save(t.copy(fileUri = localPath ?: u.toString())) }
            }
        }
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonPurple) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Mnemonic Lab", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search mnemonics...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No mnemonics yet. Tap + to add.", Icons.Default.Psychology)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { m ->
                        GlassCard(glowColor = NeonPurple, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ThreeDIconBox(icon = Icons.Default.Psychology, tint = NeonPurple, size = 48.dp, iconSize = 28.dp)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(m.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(m.chapter, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                            Text(m.subject.name, style = MaterialTheme.typography.labelSmall, color = NeonGold)
                                        }
                                        if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), maxLines = 2)
                                        if (m.tags.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(m.tags) { t -> TagChip(t) } }
                                    }
                                    IconButton(onClick = { vm.delete(m) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp)) }
                                }
                                NeonDivider(NeonPurple.copy(0.2f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (m.fileUri.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { navController.navigate(fileViewerRoute(m.fileUri, m.name)) },
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, NeonGreen.copy(0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("View File", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { uploadMnemTarget = m; mnemFileLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.UploadFile, tint = NeonPurple.copy(0.5f), modifier = Modifier.size(16.dp), contentDescription = "Replace file")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { uploadMnemTarget = m; mnemFileLauncher.launch(arrayOf("*/*")) },
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, NeonPurple.copy(0.4f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple),
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
            }
        }
    }
    if (showAdd) AddMnemonicDialog(onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
}

@Composable
fun AddMnemonicDialog(onSave: (Mnemonic) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Subject.GENERAL) }
    var description by remember { mutableStateOf("") }
    NEETDialog(title = "Add Mnemonic", icon = Icons.Default.Psychology, accentColor = NeonPurple, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Mnemonic Name", icon = Icons.Default.Label, accentColor = NeonPurple)
            DialogTextField(value = chapter, onValueChange = { chapter = it }, label = "Chapter", icon = Icons.Default.MenuBook, accentColor = NeonPurple)
            DialogTextField(value = description, onValueChange = { description = it }, label = "Description / Technique", icon = Icons.Default.Notes, accentColor = NeonPurple, multiline = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Subject.values().forEach { s -> FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s.name, fontSize = 10.sp) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (name.isNotBlank()) onSave(Mnemonic(name = name, chapter = chapter, subject = subject, description = description)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) { Text("Add", color = NeonPurple, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Day Waste ────────────────────────────────────────────────────────────────

@Composable
fun DayWasteScreen(navController: NavController, vm: DayWasteViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showWastePercent by remember { mutableStateOf<DayWaste?>(null) }
    var showReason by remember { mutableStateOf<DayWaste?>(null) }
    var showTip by remember { mutableStateOf<DayWaste?>(null) }
    var uploadSourceTarget by remember { mutableStateOf<DayWaste?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadSourceTarget
        uploadSourceTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { d -> vm.save(d.copy(sourceUri = localPath ?: u.toString())) }
            }
        }
    }

    val filtered = entries.filter { searchQuery.isBlank() || it.date.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonRed) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Wasted Days", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search wasted days...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No wasted days logged. Keep it that way! 💪", Icons.Default.EmojiEvents)
                else LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { d ->
                        GlassCard(glowColor = NeonRed, modifier = Modifier.aspectRatio(0.9f)) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    ThreeDIconBox(icon = Icons.Default.Dangerous, tint = NeonRed, size = 40.dp, iconSize = 22.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(d.date, style = MaterialTheme.typography.headlineSmall, color = NeonRed, fontWeight = FontWeight.Bold)
                                    Text("${d.wastePercentage}% wasted", style = MaterialTheme.typography.bodySmall, color = NeonRed.copy(0.7f))
                                }
                                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                                    HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                                    Row(modifier = Modifier.fillMaxWidth().padding(2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        CardIconButton(Icons.Default.Percent, NeonRed.copy(0.7f)) { showWastePercent = d }
                                        CardIconButton(Icons.Default.Warning, NeonOrange.copy(0.7f)) { showReason = d }
                                        CardIconButton(Icons.Default.Lightbulb, NeonGold.copy(0.7f)) { showTip = d }
                                        CardIconButton(
                                            if (d.sourceUri.isNotBlank()) Icons.Default.AttachFile else Icons.Default.UploadFile,
                                            if (d.sourceUri.isNotBlank()) NeonCyan.copy(0.8f) else NeonCyan.copy(0.4f)
                                        ) {
                                            if (d.sourceUri.isNotBlank()) navController.navigate(fileViewerRoute(d.sourceUri, "Source: ${d.date}"))
                                            else { uploadSourceTarget = d; sourceLauncher.launch(arrayOf("*/*")) }
                                        }
                                        if (d.sourceUri.isNotBlank()) {
                                            CardIconButton(Icons.Default.UploadFile, NeonCyan.copy(0.4f)) { uploadSourceTarget = d; sourceLauncher.launch(arrayOf("*/*")) }
                                        }
                                        CardIconButton(Icons.Default.Delete, NeonRed.copy(0.4f)) { vm.delete(d) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddDayWasteDialog(onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
    showWastePercent?.let { d ->
        var pct by remember { mutableStateOf(d.wastePercentage.toString()) }
        var isViewMode by remember { mutableStateOf(false) }
        NEETDialog(title = "Waste Percentage", icon = Icons.Default.Percent, accentColor = NeonRed, onDismiss = { showWastePercent = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ViewEditToggle(isViewMode = isViewMode, onToggle = { isViewMode = !isViewMode })
                AnimatedContent(targetState = isViewMode, transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                }, label = "wpct_content") { viewMode ->
                    if (viewMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = NeonRed.copy(0.3f))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(NeonRed.copy(0.15f), Color(0xFF080F1F), NeonRed.copy(0.08f))))
                                    .border(1.dp, NeonRed.copy(0.4f), RoundedCornerShape(20.dp))
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Percent, null, tint = NeonRed, modifier = Modifier.size(30.dp))
                                    Text(
                                        if (pct.isBlank()) "—" else "$pct%",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = NeonRed,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Button(onClick = { showWastePercent = null }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.15f)), border = BorderStroke(1.dp, NeonRed.copy(0.4f))) {
                                Icon(Icons.Default.Close, null, tint = NeonRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Close", color = NeonRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DialogTextField(value = pct, onValueChange = { pct = it }, label = "Waste % (0-100)", icon = Icons.Default.Percent, accentColor = NeonRed)
                            Button(onClick = { vm.save(d.copy(wastePercentage = pct.toIntOrNull() ?: 0)); showWastePercent = null },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.2f)), border = BorderStroke(1.dp, NeonRed.copy(0.6f))) {
                                Text("Save", color = NeonRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    showReason?.let { d -> RemarkDialog(d.reason, onSave = { vm.save(d.copy(reason = it)); showReason = null }, onDismiss = { showReason = null }) }
    showTip?.let { d -> SpecificationDialog("Recovery Tip", d.recoverTip, onSave = { vm.save(d.copy(recoverTip = it)); showTip = null }, onDismiss = { showTip = null }, accentColor = NeonGold) }
}

@Composable
fun AddDayWasteDialog(onSave: (DayWaste) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf("") }
    var pct by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    NEETDialog(title = "Log Wasted Day", icon = Icons.Default.Dangerous, accentColor = NeonRed, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NeetDatePickerButton(
                selectedDate = date,
                onDateSelected = { date = it },
                accentColor = NeonRed,
                label = "Date of Wasted Day"
            )
            DialogTextField(value = pct, onValueChange = { pct = it }, label = "Waste % (0-100)", icon = Icons.Default.Percent, accentColor = NeonRed)
            DialogTextField(value = reason, onValueChange = { reason = it }, label = "Reason of Waste", icon = Icons.Default.Warning, accentColor = NeonRed, multiline = true)
            DialogTextField(value = tip, onValueChange = { tip = it }, label = "Recovery Tip", icon = Icons.Default.Lightbulb, accentColor = NeonGold, multiline = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (date.isNotBlank()) onSave(DayWaste(date = date, wastePercentage = pct.toIntOrNull() ?: 0, reason = reason, recoverTip = tip)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.2f)), border = BorderStroke(1.dp, NeonRed.copy(0.6f))) { Text("Log", color = NeonRed, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Lack Points ─────────────────────────────────────────────────────────────

@Composable
fun LackPointsScreen(navController: NavController, vm: LackPointViewModel = hiltViewModel()) {
    val points by vm.points.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf<LackPoint?>(null) }
    val filtered = points.filter { searchQuery.isBlank() || it.point.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }, color = NeonRed) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Lack Points", breadcrumb = "Home", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search lack points...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No lack points yet. Add them to grow.", Icons.Default.TrendingDown)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filtered) { p ->
                        val sg = when (p.status) { Status.COMPLETED -> StatusCompleted; Status.EXPECTED -> StatusExpected; Status.CROSSED -> StatusCross; else -> StatusRevision }
                        GlassCard(glowColor = sg, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(p.status, modifier = Modifier)
                                    Spacer(Modifier.weight(1f))
                                    CardIconButton(Icons.Default.ToggleOn, sg) { showStatus = p }
                                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.6f)) { vm.delete(p) }
                                }
                                Text(p.point, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                if (p.solution.isNotBlank()) {
                                    NeonDivider(sg)
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Lightbulb, null, tint = NeonGold, modifier = Modifier.size(14.dp))
                                        Text("Solution: ${p.solution}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddLackPointDialog(onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
    showStatus?.let { p -> StatusSelectorDialog(p.status, onSelect = { vm.save(p.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
}

@Composable
fun AddLackPointDialog(onSave: (LackPoint) -> Unit, onDismiss: () -> Unit) {
    var point by remember { mutableStateOf("") }
    var solution by remember { mutableStateOf("") }
    NEETDialog(title = "Add Lack Point", icon = Icons.Default.TrendingDown, accentColor = NeonRed, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogTextField(value = point, onValueChange = { point = it }, label = "Identify the Lack / Weakness", icon = Icons.Default.TrendingDown, accentColor = NeonRed, multiline = true)
            DialogTextField(value = solution, onValueChange = { solution = it }, label = "How will you solve it?", icon = Icons.Default.Lightbulb, accentColor = NeonGold, multiline = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (point.isNotBlank()) onSave(LackPoint(point = point, solution = solution)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(0.2f)), border = BorderStroke(1.dp, NeonRed.copy(0.6f))) { Text("Add", color = NeonRed, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
