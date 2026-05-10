package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.BookViewModel

@Composable
fun BooksScreen(navController: NavController, vm: BookViewModel = hiltViewModel()) {
    val books by vm.books.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Book?>(null) }
    var showTagDialog by remember { mutableStateOf<Book?>(null) }
    var showRemarkDialog by remember { mutableStateOf<Book?>(null) }
    var showStatusDialog by remember { mutableStateOf<Book?>(null) }
    var showInfoDialog by remember { mutableStateOf<Book?>(null) }

    val allTags = books.flatMap { it.tags }.distinct()
    val filtered = books.filter { book ->
        (searchQuery.isBlank() || book.name.contains(searchQuery, true)) &&
        (selectedTag == null || book.tags.contains(selectedTag))
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Book Library", breadcrumb = "Home / Assets", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search books...")
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text("All") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.2f), selectedLabelColor = NeonCyan)
                            )
                        }
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                label = { Text("# $tag") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(0.2f), selectedLabelColor = NeonPurple)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    EmptyState("No books yet. Tap + to add.", Icons.Default.LibraryBooks)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filtered) { book ->
                            BookCard(
                                book = book,
                                onStatusClick = { showStatusDialog = book },
                                onTagClick = { showTagDialog = book },
                                onRemarkClick = { showRemarkDialog = book },
                                onInfoClick = { showInfoDialog = book },
                                onEdit = { editTarget = book },
                                onDelete = { vm.delete(book) }
                            )
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
            NeonFAB(onClick = { showAdd = true })
        }
    }

    if (showAdd) BookEditDialog(book = null, onSave = { vm.save(it); showAdd = false }, onDismiss = { showAdd = false })
    editTarget?.let { b -> BookEditDialog(book = b, onSave = { vm.save(it); editTarget = null }, onDismiss = { editTarget = null }) }
    showTagDialog?.let { b -> TagDialog(b.tags, onSave = { vm.save(b.copy(tags = it)); showTagDialog = null }, onDismiss = { showTagDialog = null }) }
    showRemarkDialog?.let { b -> RemarkDialog(b.remark, onSave = { vm.save(b.copy(remark = it)); showRemarkDialog = null }, onDismiss = { showRemarkDialog = null }) }
    showStatusDialog?.let { b -> StatusSelectorDialog(b.status, onSelect = { vm.save(b.copy(status = it)); showStatusDialog = null }, onDismiss = { showStatusDialog = null }) }
    showInfoDialog?.let { b -> SpecificationDialog("Book Info", b.info, onSave = { vm.save(b.copy(info = it)); showInfoDialog = null }, onDismiss = { showInfoDialog = null }) }
}

@Composable
fun BookCard(book: Book, onStatusClick: () -> Unit, onTagClick: () -> Unit, onRemarkClick: () -> Unit, onInfoClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val statusGlow = when (book.status) {
        Status.COMPLETED -> StatusCompleted
        Status.EXPECTED -> StatusExpected
        Status.REVISION -> StatusRevision
        Status.CROSSED -> StatusCross
    }
    GlassCard(glowColor = statusGlow, modifier = Modifier.aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MenuBook, null, tint = statusGlow, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(book.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                StatusBadge(book.status)
                if (book.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(book.tags.take(2)) { tag -> TagChip(tag) }
                    }
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                HorizontalDivider(color = Color.White.copy(0.08f), thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CardIconButton(Icons.Default.Info, NeonCyan.copy(0.7f), onInfoClick)
                    CardIconButton(Icons.Default.ToggleOn, statusGlow, onStatusClick)
                    CardIconButton(Icons.Default.LocalOffer, NeonPurple.copy(0.7f), onTagClick)
                    CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.7f), onRemarkClick)
                    CardIconButton(Icons.Default.Edit, NeonCyan.copy(0.5f), onEdit)
                    CardIconButton(Icons.Default.Delete, NeonRed.copy(0.5f), onDelete)
                }
            }
        }
    }
}

@Composable
fun BookEditDialog(book: Book?, onSave: (Book) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(book?.name ?: "") }
    NEETDialog(title = if (book == null) "Add Book" else "Edit Book", icon = Icons.Default.LibraryBooks, accentColor = NeonPurple, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogTextField(value = name, onValueChange = { name = it }, label = "Book Name", icon = Icons.Default.Book, accentColor = NeonPurple)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel") }
                Button(onClick = { onSave((book ?: Book()).copy(name = name)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.2f)), border = BorderStroke(1.dp, NeonPurple.copy(0.6f))) {
                    Text("Save", color = NeonPurple, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
