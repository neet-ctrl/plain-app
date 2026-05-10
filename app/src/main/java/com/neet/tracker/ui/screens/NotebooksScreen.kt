package com.neet.tracker.ui.screens

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.notebookChaptersRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.NotebookViewModel

@Composable
fun NotebooksScreen(navController: NavController, vm: NotebookViewModel = hiltViewModel()) {
    val notebooks by vm.notebooks.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Notebook?>(null) }

    val filtered = notebooks.filter { searchQuery.isBlank() || it.notebookNo.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAddDialog = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Notebook Vault", breadcrumb = "Home / Assets", onBack = { navController.popBackStack() })

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search notebooks...")
                Spacer(Modifier.height(12.dp))

                if (filtered.isEmpty()) {
                    EmptyState("No notebooks yet.\nTap + to add your first notebook.", Icons.Default.Book)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filtered) { nb ->
                            NotebookCard(
                                notebook = nb,
                                onClick = { navController.navigate(notebookChaptersRoute(nb.id, nb.notebookNo)) },
                                onEdit = { editTarget = nb },
                                onDelete = { vm.delete(nb) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        NotebookEditDialog(notebook = null, onSave = { vm.save(it); showAddDialog = false }, onDismiss = { showAddDialog = false })
    }
    editTarget?.let { nb ->
        NotebookEditDialog(notebook = nb, onSave = { vm.save(it); editTarget = null }, onDismiss = { editTarget = null })
    }
}

@Composable
fun NotebookCard(notebook: Notebook, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    GlassCard(onClick = onClick, glowColor = NeonCyan, modifier = Modifier.aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (notebook.photoUri.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = notebook.photoUri, contentDescription = "Notebook",
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    ThreeDIconBox(icon = Icons.Default.Book, tint = NeonCyan, size = 60.dp, iconSize = 32.dp)
                }
                Spacer(Modifier.height(10.dp))
                Text("NB ${notebook.notebookNo}", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardIconButton(Icons.Default.Edit, NeonCyan.copy(0.7f), onEdit)
                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.7f), onDelete)
                }
            }
        }
    }
}

@Composable
fun NotebookEditDialog(notebook: Notebook?, onSave: (Notebook) -> Unit, onDismiss: () -> Unit) {
    var nbNo by remember { mutableStateOf(notebook?.notebookNo ?: "") }
    var photoUri by remember { mutableStateOf(notebook?.photoUri ?: "") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { picked ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, picked)
                photoUri = localPath ?: picked.toString()
            }
        }
    }

    NEETDialog(title = if (notebook == null) "New Notebook" else "Edit Notebook", icon = Icons.Default.Book, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = nbNo, onValueChange = { nbNo = it }, label = "Notebook Number / Name", icon = Icons.Default.Numbers, accentColor = NeonCyan)
            Button(onClick = { photoLauncher.launch(arrayOf("image/*")) }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.12f)), border = BorderStroke(1.dp, NeonCyan.copy(0.4f)), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoCamera, null, tint = NeonCyan); Spacer(Modifier.width(8.dp))
                Text(if (photoUri.isBlank()) "Upload Cover Photo" else "Change Photo", color = NeonCyan)
            }
            if (photoUri.isNotBlank()) {
                coil.compose.AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).align(Alignment.CenterHorizontally))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = {
                    onSave((notebook ?: Notebook()).copy(notebookNo = nbNo, photoUri = photoUri))
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) {
                    Text("Save", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Notebook Chapters ────────────────────────────────────────────────────────

@Composable
fun NotebookChaptersScreen(navController: NavController, notebookId: String, notebookNo: String, vm: NotebookViewModel = hiltViewModel()) {
    val chapters by vm.chaptersFor(notebookId).collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<NotebookChapter?>(null) }

    val filtered = chapters.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "NB $notebookNo", breadcrumb = "Home / Assets / Notebooks", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters in NB $notebookNo...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    EmptyState("No chapters yet. Tap + to add.", Icons.Default.MenuBook)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filtered) { chapter ->
                            ChapterCard(
                                chapter = chapter,
                                onEdit = { editTarget = chapter },
                                onDelete = { vm.deleteChapter(chapter) },
                                onStatusChange = { vm.saveChapter(chapter.copy(status = it)) },
                                onSpecSave = { vm.saveChapter(chapter.copy(specifications = it)) },
                                onMissingSave = { vm.saveChapter(chapter.copy(missingNotes = it)) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ChapterEditDialog(chapter = null, notebookId = notebookId, onSave = { vm.saveChapter(it); showAdd = false }, onDismiss = { showAdd = false })
    }
    editTarget?.let { ch ->
        ChapterEditDialog(chapter = ch, notebookId = notebookId, onSave = { vm.saveChapter(it); editTarget = null }, onDismiss = { editTarget = null })
    }
}

@Composable
fun ChapterCard(chapter: NotebookChapter, onEdit: () -> Unit, onDelete: () -> Unit, onStatusChange: (Status) -> Unit, onSpecSave: (String) -> Unit = {}, onMissingSave: (String) -> Unit = {}) {
    var showSpec by remember { mutableStateOf(false) }
    var showMissing by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }

    val statusGlow = when (chapter.status) {
        Status.COMPLETED -> StatusCompleted
        Status.EXPECTED -> StatusExpected
        Status.REVISION -> StatusRevision
        Status.CROSSED -> StatusCross
    }

    GlassCard(glowColor = statusGlow, modifier = Modifier.aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(10.dp).background(statusGlow, CircleShape))
                Spacer(Modifier.height(8.dp))
                Text(chapter.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CardIconButton(Icons.Default.Info, NeonCyan.copy(0.7f)) { showSpec = true }
                    CardIconButton(Icons.Default.WarningAmber, NeonOrange.copy(0.7f)) { showMissing = true }
                    CardIconButton(Icons.Default.ToggleOn, statusGlow) { showStatus = true }
                    CardIconButton(Icons.Default.Edit, NeonPurple.copy(0.7f), onEdit)
                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f), onDelete)
                }
            }
        }
    }

    if (showSpec) {
        SpecificationDialog("Chapter Specifications", chapter.specifications, onSave = { onSpecSave(it); showSpec = false }, onDismiss = { showSpec = false })
    }
    if (showMissing) {
        MissingNotesDialog(chapter.missingNotes, onSave = { onMissingSave(it); showMissing = false }, onDismiss = { showMissing = false })
    }
    if (showStatus) {
        StatusSelectorDialog(chapter.status, onSelect = onStatusChange, onDismiss = { showStatus = false })
    }
}

@Composable
fun ChapterEditDialog(chapter: NotebookChapter?, notebookId: String, onSave: (NotebookChapter) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(chapter?.name ?: "") }
    NEETDialog(title = if (chapter == null) "New Chapter" else "Edit Chapter", icon = Icons.Default.MenuBook, accentColor = NeonCyan, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Chapter Name", icon = Icons.Default.Article, accentColor = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave((chapter ?: NotebookChapter(notebookId = notebookId)).copy(name = name)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)), border = BorderStroke(1.dp, NeonCyan.copy(0.6f))) {
                    Text("Save", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
