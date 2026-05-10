package com.neet.tracker.ui.screens

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
import com.neet.tracker.navigation.pwBatchTestsRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.PWBatchViewModel

@Composable
fun PWBatchesScreen(navController: NavController, vm: PWBatchViewModel = hiltViewModel()) {
    val batches by vm.batches.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf<PWBatch?>(null) }
    var showTags by remember { mutableStateOf<PWBatch?>(null) }
    var showRemark by remember { mutableStateOf<PWBatch?>(null) }
    val filtered = batches.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "PW Batches", breadcrumb = "Home / Assets", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search batches...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No batches yet. Tap + to add.", Icons.Default.Groups)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { b ->
                        val sg = statusColor(b.status)
                        NEETCard(title = b.name, icon = Icons.Default.Groups, glowColor = sg, status = b.status,
                            onClick = { navController.navigate(pwBatchTestsRoute(b.id, b.name)) },
                            bottomContent = {
                                CardIconButton(Icons.Default.ToggleOn, sg) { showStatus = b }
                                CardIconButton(Icons.Default.LocalOffer, NeonPurple.copy(0.7f)) { showTags = b }
                                CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.7f)) { showRemark = b }
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f)) { vm.deleteBatch(b) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) SimpleAddDialog("Add PW Batch", "Batch Name", NeonCyan, Icons.Default.Groups, onSave = { vm.saveBatch(PWBatch(name = it)); showAdd = false }, onDismiss = { showAdd = false })
    showStatus?.let { b -> StatusSelectorDialog(b.status, onSelect = { vm.saveBatch(b.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showTags?.let { b -> TagDialog(b.tags, onSave = { vm.saveBatch(b.copy(tags = it)); showTags = null }, onDismiss = { showTags = null }) }
    showRemark?.let { b -> RemarkDialog(b.remark, onSave = { vm.saveBatch(b.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
}

@Composable
fun PWBatchTestsScreen(navController: NavController, batchId: String, batchName: String, vm: PWBatchViewModel = hiltViewModel()) {
    val tests by vm.testsFor(batchId).collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showStatus by remember { mutableStateOf<PWTest?>(null) }
    var showPrefixDate by remember { mutableStateOf<PWTest?>(null) }
    var showTopics by remember { mutableStateOf<PWTest?>(null) }
    var showWrong by remember { mutableStateOf<PWTest?>(null) }
    var showRemark by remember { mutableStateOf<PWTest?>(null) }
    var showTags by remember { mutableStateOf<PWTest?>(null) }
    var showMarks by remember { mutableStateOf<PWTest?>(null) }
    var showUrl by remember { mutableStateOf<PWTest?>(null) }

    val allTags = tests.flatMap { it.tags }.distinct()
    val filtered = tests.filter {
        (searchQuery.isBlank() || it.name.contains(searchQuery, true)) &&
        (selectedTag == null || it.tags.contains(selectedTag))
    }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = batchName, breadcrumb = "Home / Assets / PW Batches", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search tests...")
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("All") }) }
                        items(allTags) { tag -> FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag }, label = { Text("# $tag") }) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) EmptyState("No tests in this batch yet.", Icons.Default.Assignment)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { t ->
                        val sg = statusColor(t.status)
                        GlassCard(glowColor = sg, modifier = Modifier.aspectRatio(0.9f)) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(t.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                                    if (t.subject.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(t.subject, style = MaterialTheme.typography.bodySmall, color = NeonCyan.copy(0.7f))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    StatusBadge(t.status)
                                    if (t.marksObtained.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Star, null, tint = NeonGold, modifier = Modifier.size(12.dp))
                                            Text(t.marksObtained, style = MaterialTheme.typography.labelSmall, color = NeonGold)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                                    HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        CardIconButton(Icons.Default.ToggleOn, sg) { showStatus = t }
                                        CardIconButton(Icons.Default.Schedule, NeonGold.copy(0.7f)) { showPrefixDate = t }
                                        CardIconButton(Icons.Default.Topic, NeonCyan.copy(0.7f)) { showTopics = t }
                                        CardIconButton(Icons.Default.ErrorOutline, NeonRed.copy(0.7f)) { showWrong = t }
                                        CardIconButton(Icons.Default.Star, NeonGold.copy(0.7f)) { showMarks = t }
                                        CardIconButton(Icons.Default.LocalOffer, NeonPurple.copy(0.7f)) { showTags = t }
                                        CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.6f)) { showRemark = t }
                                        CardIconButton(Icons.Default.Link, NeonCyan.copy(0.5f)) { showUrl = t }
                                        CardIconButton(Icons.Default.Delete, NeonRed.copy(0.4f)) { vm.deleteTest(t) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) PWTestAddDialog(batchId = batchId, onSave = { vm.saveTest(it); showAdd = false }, onDismiss = { showAdd = false })
    showStatus?.let { t -> StatusSelectorDialog(t.status, onSelect = { vm.saveTest(t.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showPrefixDate?.let { t -> PrefixDateDialog(t.prefixDate, onSave = { vm.saveTest(t.copy(prefixDate = it)); showPrefixDate = null }, onDismiss = { showPrefixDate = null }) }
    showTopics?.let { t -> TopicsDialog(t.topicsAsked, onSave = { vm.saveTest(t.copy(topicsAsked = it)); showTopics = null }, onDismiss = { showTopics = null }) }
    showWrong?.let { t -> WrongQuestionsDialog(t.wrongQuestions, onSave = { vm.saveTest(t.copy(wrongQuestions = it)); showWrong = null }, onDismiss = { showWrong = null }) }
    showMarks?.let { t -> MarksDialog(t.marksObtained, onSave = { vm.saveTest(t.copy(marksObtained = it)); showMarks = null }, onDismiss = { showMarks = null }) }
    showTags?.let { t -> TagDialog(t.tags, onSave = { vm.saveTest(t.copy(tags = it)); showTags = null }, onDismiss = { showTags = null }) }
    showRemark?.let { t -> RemarkDialog(t.remark, onSave = { vm.saveTest(t.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
    showUrl?.let { t -> URLDialog(t.url, onSave = { vm.saveTest(t.copy(url = it)); showUrl = null }, onDismiss = { showUrl = null }) }
}

@Composable
fun PWTestAddDialog(batchId: String, onSave: (PWTest) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    NEETDialog(title = "Add Test", icon = Icons.Default.Assignment, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Test Name", icon = Icons.Default.Assignment, accentColor = NeonCyan)
            DialogTextField(value = subject, onValueChange = { subject = it }, label = "Subject", icon = Icons.Default.Science, accentColor = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { if (name.isNotBlank()) onSave(PWTest(batchId = batchId, name = name, subject = subject)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) {
                    Text("Add", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
