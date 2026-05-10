package com.neet.tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun OnlineTestsScreen(navController: NavController, vm: TestPaperViewModel = hiltViewModel()) {
    TestListScreen(navController, "Online Tests", "Home / Assets / Tests", NeonGreen, "ONLINE", vm)
}

@Composable
fun OfflineTestsScreen(navController: NavController, vm: TestPaperViewModel = hiltViewModel()) {
    TestListScreen(navController, "Offline Tests", "Home / Assets / Tests", NeonOrange, "OFFLINE", vm)
}

@Composable
fun TestListScreen(navController: NavController, title: String, breadcrumb: String, color: Color, type: String, vm: TestPaperViewModel) {
    val tests by (if (type == "ONLINE") vm.onlineTests else vm.offlineTests).collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    var showStatus by remember { mutableStateOf<TestPaper?>(null) }
    var showPrefixDate by remember { mutableStateOf<TestPaper?>(null) }
    var showTopics by remember { mutableStateOf<TestPaper?>(null) }
    var showWrong by remember { mutableStateOf<TestPaper?>(null) }
    var showRemark by remember { mutableStateOf<TestPaper?>(null) }
    var showTags by remember { mutableStateOf<TestPaper?>(null) }
    var showMarks by remember { mutableStateOf<TestPaper?>(null) }
    var showUrl by remember { mutableStateOf<TestPaper?>(null) }
    var editTarget by remember { mutableStateOf<TestPaper?>(null) }
    var uploadQPTarget by remember { mutableStateOf<TestPaper?>(null) }
    var uploadSolTarget by remember { mutableStateOf<TestPaper?>(null) }

    val qpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> uploadQPTarget?.let { t -> vm.save(t.copy(questionPaperUri = u.toString())) } }
        uploadQPTarget = null
    }
    val solLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> uploadSolTarget?.let { t -> vm.save(t.copy(solutionUri = u.toString())) } }
        uploadSolTarget = null
    }

    val allTags = tests.flatMap { it.tags }.distinct()
    val filtered = tests.filter {
        (searchQuery.isBlank() || it.name.contains(searchQuery, true)) &&
        (selectedTag == null || it.tags.contains(selectedTag))
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = title, breadcrumb = breadcrumb, onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search tests...")
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("All") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(0.2f), selectedLabelColor = color)) }
                        items(allTags) { tag -> FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag }, label = { Text("# $tag") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(0.2f), selectedLabelColor = NeonPurple)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) EmptyState("No tests yet. Tap + to add.", Icons.Default.Assignment)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { t ->
                        val statusGlow = statusColor(t.status)
                        GlassCard(glowColor = statusGlow, modifier = Modifier.aspectRatio(0.85f)) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(t.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Spacer(Modifier.height(6.dp))
                                    StatusBadge(t.status)
                                    if (t.marksObtained.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Star, null, tint = NeonGold, modifier = Modifier.size(12.dp))
                                            Text(t.marksObtained, style = MaterialTheme.typography.labelSmall, color = NeonGold)
                                        }
                                    }
                                    if (t.prefixDate.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Schedule, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(10.dp))
                                            Text(t.prefixDate, style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(0.6f))
                                        }
                                    }
                                    if (t.questionPaperUri.isNotBlank()) {
                                        TextButton(onClick = { navController.navigate(fileViewerRoute(t.questionPaperUri, "${t.name} QP")) }) {
                                            Text("View QP", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                                    HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        CardIconButton(Icons.Default.ToggleOn, statusGlow) { showStatus = t }
                                        CardIconButton(Icons.Default.Schedule, NeonGold.copy(0.7f)) { showPrefixDate = t }
                                        CardIconButton(Icons.Default.Topic, NeonCyan.copy(0.7f)) { showTopics = t }
                                        CardIconButton(Icons.Default.ErrorOutline, NeonRed.copy(0.7f)) { showWrong = t }
                                        CardIconButton(Icons.Default.Star, NeonGold.copy(0.7f)) { showMarks = t }
                                        CardIconButton(Icons.Default.LocalOffer, NeonPurple.copy(0.7f)) { showTags = t }
                                        CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.6f)) { showRemark = t }
                                        CardIconButton(Icons.Default.Link, NeonCyan.copy(0.5f)) { showUrl = t }
                                        CardIconButton(
                                            if (t.questionPaperUri.isNotBlank()) Icons.Default.PictureAsPdf else Icons.Default.UploadFile,
                                            if (t.questionPaperUri.isNotBlank()) NeonGreen.copy(0.8f) else NeonCyan.copy(0.4f)
                                        ) { uploadQPTarget = t; qpLauncher.launch("*/*") }
                                        CardIconButton(
                                            if (t.solutionUri.isNotBlank()) Icons.Default.FilePresent else Icons.Default.NoteAdd,
                                            if (t.solutionUri.isNotBlank()) NeonOrange.copy(0.8f) else NeonCyan.copy(0.4f)
                                        ) { uploadSolTarget = t; solLauncher.launch("*/*") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) SimpleAddDialog("Add ${if (type == "ONLINE") "Online" else "Offline"} Test", "Test Name", color, Icons.Default.Assignment,
        onSave = { vm.save(TestPaper(name = it, type = type)); showAdd = false }, onDismiss = { showAdd = false })
    showStatus?.let { t -> StatusSelectorDialog(t.status, onSelect = { vm.save(t.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showPrefixDate?.let { t -> PrefixDateDialog(t.prefixDate, onSave = { vm.save(t.copy(prefixDate = it)); showPrefixDate = null }, onDismiss = { showPrefixDate = null }) }
    showTopics?.let { t -> TopicsDialog(t.topicsAsked, onSave = { vm.save(t.copy(topicsAsked = it)); showTopics = null }, onDismiss = { showTopics = null }) }
    showWrong?.let { t -> WrongQuestionsDialog(t.wrongQuestions, onSave = { vm.save(t.copy(wrongQuestions = it)); showWrong = null }, onDismiss = { showWrong = null }) }
    showMarks?.let { t -> MarksDialog(t.marksObtained, onSave = { vm.save(t.copy(marksObtained = it)); showMarks = null }, onDismiss = { showMarks = null }) }
    showTags?.let { t -> TagDialog(t.tags, onSave = { vm.save(t.copy(tags = it)); showTags = null }, onDismiss = { showTags = null }) }
    showRemark?.let { t -> RemarkDialog(t.remark, onSave = { vm.save(t.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
    showUrl?.let { t -> URLDialog(t.url, onSave = { vm.save(t.copy(url = it)); showUrl = null }, onDismiss = { showUrl = null }) }
}

@Composable
fun SamplePapersScreen(navController: NavController, vm: SamplePaperViewModel = hiltViewModel()) {
    val papers by vm.papers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf<SamplePaper?>(null) }
    var showWrong by remember { mutableStateOf<SamplePaper?>(null) }
    var showTags by remember { mutableStateOf<SamplePaper?>(null) }
    var showRemark by remember { mutableStateOf<SamplePaper?>(null) }
    var showMarks by remember { mutableStateOf<SamplePaper?>(null) }
    var showPrefixDate by remember { mutableStateOf<SamplePaper?>(null) }
    var showUrl by remember { mutableStateOf<SamplePaper?>(null) }
    var uploadQPTarget by remember { mutableStateOf<SamplePaper?>(null) }
    var uploadSolTarget by remember { mutableStateOf<SamplePaper?>(null) }

    val qpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> uploadQPTarget?.let { p -> vm.save(p.copy(questionPaperUri = u.toString())) } }
        uploadQPTarget = null
    }
    val solLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> uploadSolTarget?.let { p -> vm.save(p.copy(solutionUri = u.toString())) } }
        uploadSolTarget = null
    }

    val allTags = papers.flatMap { it.tags }.distinct()
    val filtered = papers.filter {
        (searchQuery.isBlank() || it.name.contains(searchQuery, true)) &&
        (selectedTag == null || it.tags.contains(selectedTag))
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Sample Papers", breadcrumb = "Home / Assets", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search papers...")
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("All") }) }
                        items(allTags) { tag -> FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag }, label = { Text("# $tag") }) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) EmptyState("No sample papers yet.", Icons.Default.FileCopy)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { p ->
                        val sg = statusColor(p.status)
                        NEETCard(title = p.name, icon = Icons.Default.FileCopy, glowColor = sg, status = p.status, onClick = {},
                            bottomContent = {
                                CardIconButton(Icons.Default.ToggleOn, sg) { showStatus = p }
                                CardIconButton(Icons.Default.Schedule, NeonGold.copy(0.7f)) { showPrefixDate = p }
                                CardIconButton(Icons.Default.ErrorOutline, NeonRed.copy(0.7f)) { showWrong = p }
                                CardIconButton(Icons.Default.Star, NeonGold.copy(0.7f)) { showMarks = p }
                                CardIconButton(Icons.Default.LocalOffer, NeonPurple.copy(0.7f)) { showTags = p }
                                CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.6f)) { showRemark = p }
                                CardIconButton(Icons.Default.Link, NeonCyan.copy(0.5f)) { showUrl = p }
                                CardIconButton(
                                    if (p.questionPaperUri.isNotBlank()) Icons.Default.PictureAsPdf else Icons.Default.UploadFile,
                                    if (p.questionPaperUri.isNotBlank()) NeonGreen.copy(0.8f) else NeonCyan.copy(0.4f)
                                ) { uploadQPTarget = p; qpLauncher.launch("*/*") }
                                CardIconButton(
                                    if (p.solutionUri.isNotBlank()) Icons.Default.FilePresent else Icons.Default.NoteAdd,
                                    if (p.solutionUri.isNotBlank()) NeonOrange.copy(0.8f) else NeonCyan.copy(0.4f)
                                ) { uploadSolTarget = p; solLauncher.launch("*/*") }
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.4f)) { vm.delete(p) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) SimpleAddDialog("Add Sample Paper", "Paper Name", NeonOrange, Icons.Default.FileCopy, onSave = { vm.save(SamplePaper(name = it)); showAdd = false }, onDismiss = { showAdd = false })
    showStatus?.let { p -> StatusSelectorDialog(p.status, onSelect = { vm.save(p.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showPrefixDate?.let { p -> PrefixDateDialog(p.prefixDate, onSave = { vm.save(p.copy(prefixDate = it)); showPrefixDate = null }, onDismiss = { showPrefixDate = null }) }
    showWrong?.let { p -> WrongQuestionsDialog(p.wrongQuestions, onSave = { vm.save(p.copy(wrongQuestions = it)); showWrong = null }, onDismiss = { showWrong = null }) }
    showMarks?.let { p -> MarksDialog(p.marksObtained, onSave = { vm.save(p.copy(marksObtained = it)); showMarks = null }, onDismiss = { showMarks = null }) }
    showTags?.let { p -> TagDialog(p.tags, onSave = { vm.save(p.copy(tags = it)); showTags = null }, onDismiss = { showTags = null }) }
    showRemark?.let { p -> RemarkDialog(p.remark, onSave = { vm.save(p.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
    showUrl?.let { p -> URLDialog(p.url, onSave = { vm.save(p.copy(url = it)); showUrl = null }, onDismiss = { showUrl = null }) }
}
