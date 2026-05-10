package com.neet.tracker.ui.screens

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
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
    var filesTargetId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadQPTarget
        uploadQPTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { t -> vm.save(t.copy(questionPaperUri = localPath ?: u.toString())) }
            }
        }
    }
    val solLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadSolTarget
        uploadSolTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { t -> vm.save(t.copy(solutionUri = localPath ?: u.toString())) }
            }
        }
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
                    items(filtered, key = { it.id }) { t ->
                        val statusGlow = statusColor(t.status)
                        val hasQP = t.questionPaperUri.isNotBlank()
                        val hasSol = t.solutionUri.isNotBlank()
                        GlassCard(glowColor = statusGlow, modifier = Modifier.aspectRatio(0.75f)) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                        .padding(bottom = 70.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { filesTargetId = t.id },
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(t.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
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
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (hasQP) Box(modifier = Modifier.size(8.dp).background(NeonGreen, CircleShape))
                                        if (hasSol) Box(modifier = Modifier.size(8.dp).background(NeonOrange, CircleShape))
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
                                        CardIconButton(Icons.Default.Edit, NeonCyan.copy(0.5f)) { showUrl = t }
                                        CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f)) { vm.delete(t) }
                                    }
                                    HorizontalDivider(color = Color.White.copy(0.06f), thickness = 0.5.dp)
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        CardIconButton(
                                            Icons.Default.OpenInBrowser,
                                            if (t.url.isNotBlank()) NeonCyan.copy(0.85f) else Color.White.copy(0.18f)
                                        ) {
                                            if (t.url.isNotBlank()) {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(t.url))
                                                context.startActivity(intent)
                                            }
                                        }
                                        CardIconButton(
                                            Icons.Default.PictureAsPdf,
                                            if (hasQP) NeonGreen.copy(0.85f) else Color.White.copy(0.18f)
                                        ) {
                                            if (hasQP) navController.navigate(fileViewerRoute(t.questionPaperUri, "${t.name} QP"))
                                        }
                                        CardIconButton(
                                            Icons.Default.FileOpen,
                                            if (hasSol) NeonOrange.copy(0.85f) else Color.White.copy(0.18f)
                                        ) {
                                            if (hasSol) navController.navigate(fileViewerRoute(t.solutionUri, "${t.name} Solution"))
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
    filesTargetId?.let { id ->
        val liveTest = filtered.firstOrNull { it.id == id }
        if (liveTest != null) {
            TestFilesDialog(
                test = liveTest,
                onDismiss = { filesTargetId = null },
                onUploadQP = { uploadQPTarget = liveTest; qpLauncher.launch(arrayOf("*/*")) },
                onUploadSol = { uploadSolTarget = liveTest; solLauncher.launch(arrayOf("*/*")) },
                onRemoveQP = { vm.save(liveTest.copy(questionPaperUri = "")) },
                onRemoveSol = { vm.save(liveTest.copy(solutionUri = "")) },
                onViewQP = { navController.navigate(fileViewerRoute(liveTest.questionPaperUri, "${liveTest.name} QP")); filesTargetId = null },
                onViewSol = { navController.navigate(fileViewerRoute(liveTest.solutionUri, "${liveTest.name} Solution")); filesTargetId = null }
            )
        }
    }
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadQPTarget
        uploadQPTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { p -> vm.save(p.copy(questionPaperUri = localPath ?: u.toString())) }
            }
        }
    }
    val solLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = uploadSolTarget
        uploadSolTarget = null
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                target?.let { p -> vm.save(p.copy(solutionUri = localPath ?: u.toString())) }
            }
        }
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
                                    if (p.questionPaperUri.isNotBlank()) NeonGreen.copy(0.85f) else NeonCyan.copy(0.4f)
                                ) {
                                    if (p.questionPaperUri.isNotBlank()) navController.navigate(fileViewerRoute(p.questionPaperUri, "${p.name} QP"))
                                    else { uploadQPTarget = p; qpLauncher.launch(arrayOf("*/*")) }
                                }
                                if (p.questionPaperUri.isNotBlank()) {
                                    CardIconButton(Icons.Default.Close, NeonRed.copy(0.65f)) { vm.save(p.copy(questionPaperUri = "")) }
                                }
                                CardIconButton(
                                    if (p.solutionUri.isNotBlank()) Icons.Default.FileOpen else Icons.Default.NoteAdd,
                                    if (p.solutionUri.isNotBlank()) NeonOrange.copy(0.85f) else NeonCyan.copy(0.4f)
                                ) {
                                    if (p.solutionUri.isNotBlank()) navController.navigate(fileViewerRoute(p.solutionUri, "${p.name} Solution"))
                                    else { uploadSolTarget = p; solLauncher.launch(arrayOf("*/*")) }
                                }
                                if (p.solutionUri.isNotBlank()) {
                                    CardIconButton(Icons.Default.Close, NeonRed.copy(0.65f)) { vm.save(p.copy(solutionUri = "")) }
                                }
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

// ─── Test Paper Files Dialog ───────────────────────────────────────────────────

@Composable
fun TestFilesDialog(
    test: TestPaper,
    onDismiss: () -> Unit,
    onUploadQP: () -> Unit,
    onUploadSol: () -> Unit,
    onRemoveQP: () -> Unit,
    onRemoveSol: () -> Unit,
    onViewQP: () -> Unit,
    onViewSol: () -> Unit
) {
    NEETDialog(
        title = test.name,
        icon = Icons.Default.Assignment,
        accentColor = NeonCyan,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TestFileCard(
                    label = "Question Paper",
                    uploadedIcon = Icons.Default.PictureAsPdf,
                    emptyIcon = Icons.Default.UploadFile,
                    color = NeonGreen,
                    isUploaded = test.questionPaperUri.isNotBlank(),
                    onTap = { if (test.questionPaperUri.isNotBlank()) onViewQP() else onUploadQP() },
                    onRemove = onRemoveQP,
                    modifier = Modifier.weight(1f)
                )
                TestFileCard(
                    label = "Solution",
                    uploadedIcon = Icons.Default.FileOpen,
                    emptyIcon = Icons.Default.NoteAdd,
                    color = NeonOrange,
                    isUploaded = test.solutionUri.isNotBlank(),
                    onTap = { if (test.solutionUri.isNotBlank()) onViewSol() else onUploadSol() },
                    onRemove = onRemoveSol,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color.White.copy(0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(0.7f))
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
fun TestFileCard(
    label: String,
    uploadedIcon: ImageVector,
    emptyIcon: ImageVector,
    color: Color,
    isUploaded: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isUploaded) color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                .border(
                    width = 1.dp,
                    color = if (isUploaded) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = if (isUploaded) uploadedIcon else emptyIcon,
                    contentDescription = null,
                    tint = if (isUploaded) color else Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(46.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUploaded) color else Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isUploaded) "Tap to open" else "Tap to upload",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUploaded) color.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.25f),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (isUploaded) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .background(NeonRed, CircleShape)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
