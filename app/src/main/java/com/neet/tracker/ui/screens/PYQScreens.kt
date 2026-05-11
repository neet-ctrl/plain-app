package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
import com.neet.tracker.navigation.*
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.PYQViewModel

@Composable
fun PYQChapterwiseScreen(navController: NavController, vm: PYQViewModel = hiltViewModel()) {
    val sources by vm.chapterwiseSources.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = sources.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Chapter-wise PYQs", breadcrumb = "Home / Assets / PYQ", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search sources...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No sources yet. Tap + to add.", Icons.Default.Archive)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { src ->
                        NEETCard(title = src.name, icon = Icons.Default.MenuBook, glowColor = NeonCyan,
                            onClick = { navController.navigate(pyqChapterwiseDetailRoute(src.id, src.name)) },
                            bottomContent = {
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.7f)) { vm.deleteSource(src) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        SimpleAddDialog("Add PYQ Source", "Source Name (e.g. Aakash Module)", NeonCyan, Icons.Default.Archive,
            onSave = { vm.saveSource(PYQSource(name = it, type = "CHAPTERWISE")); showAdd = false },
            onDismiss = { showAdd = false })
    }
}

@Composable
fun PYQChapterwiseDetailScreen(navController: NavController, sourceId: String, sourceName: String, vm: PYQViewModel = hiltViewModel()) {
    val chaptersFlow = remember(sourceId) { vm.chaptersFor(sourceId) }
    val chapters by chaptersFlow.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf<PYQChapter?>(null) }
    var showDates by remember { mutableStateOf<PYQChapter?>(null) }
    var showWrong by remember { mutableStateOf<PYQChapter?>(null) }
    var showRemark by remember { mutableStateOf<PYQChapter?>(null) }
    val filtered = chapters.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = sourceName, breadcrumb = "Home / Assets / PYQ / Chapterwise", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search chapters...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No chapters yet. Tap + to add.", Icons.Default.MenuBook)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { ch ->
                        val statusGlow = statusColor(ch.status)
                        NEETCard(
                            title = ch.name, glowColor = statusGlow, status = ch.status,
                            onClick = {},
                            bottomContent = {
                                CardIconButton(Icons.Default.ToggleOn, statusGlow) { showStatus = ch }
                                CardIconButton(Icons.Default.DateRange, NeonGreen.copy(0.7f)) { showDates = ch }
                                CardIconButton(Icons.Default.ErrorOutline, NeonRed.copy(0.7f)) { showWrong = ch }
                                CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.7f)) { showRemark = ch }
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.4f)) { vm.deleteChapter(ch) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) ChapterSimpleAddDialog("Add Chapter", NeonCyan, Icons.Default.MenuBook,
        onSave = { vm.saveChapter(PYQChapter(sourceId = sourceId, name = it)); showAdd = false },
        onDismiss = { showAdd = false })
    showStatus?.let { ch -> StatusSelectorDialog(ch.status, onSelect = { vm.saveChapter(ch.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showDates?.let { ch -> CompletionDateDialog(ch.completionDates, onSave = { vm.saveChapter(ch.copy(completionDates = it)); showDates = null }, onDismiss = { showDates = null }) }
    showWrong?.let { ch -> WrongQuestionsDialog(ch.wrongQuestions, onSave = { vm.saveChapter(ch.copy(wrongQuestions = it)); showWrong = null }, onDismiss = { showWrong = null }) }
    showRemark?.let { ch -> RemarkDialog(ch.remark, onSave = { vm.saveChapter(ch.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
}

@Composable
fun PYQYearwiseScreen(navController: NavController, vm: PYQViewModel = hiltViewModel()) {
    val sources by vm.yearwiseSources.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val filtered = sources.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Year-wise PYQs", breadcrumb = "Home / Assets / PYQ", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search sources...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No sources yet. Tap + to add.", Icons.Default.Archive)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { src ->
                        NEETCard(title = src.name, icon = Icons.Default.CalendarViewMonth, glowColor = NeonPurple,
                            onClick = { navController.navigate(pyqYearwiseDetailRoute(src.id, src.name)) },
                            bottomContent = { CardIconButton(Icons.Default.Delete, NeonRed.copy(0.7f)) { vm.deleteSource(src) } }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) SimpleAddDialog("Add Book", "Book Name", NeonPurple, Icons.Default.LibraryBooks,
        onSave = { vm.saveSource(PYQSource(name = it, type = "YEARWISE")); showAdd = false },
        onDismiss = { showAdd = false })
}

@Composable
fun PYQYearwiseDetailScreen(navController: NavController, bookId: String, bookName: String, vm: PYQViewModel = hiltViewModel()) {
    val yearsFlow = remember(bookId) { vm.yearsFor(bookId) }
    val years by yearsFlow.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf<PYQYear?>(null) }
    var showDates by remember { mutableStateOf<PYQYear?>(null) }
    var showWrong by remember { mutableStateOf<PYQYear?>(null) }
    var showRemark by remember { mutableStateOf<PYQYear?>(null) }
    val filtered = years.filter { searchQuery.isBlank() || it.year.contains(searchQuery, true) }

    SpaceBackground(floatingActionButton = { NeonFAB(onClick = { showAdd = true }) }) {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = bookName, breadcrumb = "Home / Assets / PYQ / Yearwise", onBack = { navController.popBackStack() })
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search years...")
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) EmptyState("No years yet. Tap + to add.", Icons.Default.CalendarMonth)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filtered) { yr ->
                        val statusGlow = statusColor(yr.status)
                        NEETCard(title = yr.year, icon = Icons.Default.CalendarToday, glowColor = statusGlow, status = yr.status, onClick = {},
                            bottomContent = {
                                CardIconButton(Icons.Default.ToggleOn, statusGlow) { showStatus = yr }
                                CardIconButton(Icons.Default.ErrorOutline, NeonRed.copy(0.7f)) { showWrong = yr }
                                CardIconButton(Icons.Default.DateRange, NeonGreen.copy(0.7f)) { showDates = yr }
                                CardIconButton(Icons.Default.StickyNote2, NeonGold.copy(0.7f)) { showRemark = yr }
                                CardIconButton(Icons.Default.Delete, NeonRed.copy(0.4f)) { vm.deleteYear(yr) }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showAdd) SimpleAddDialog("Add Year", "Year (e.g. 2023)", NeonPurple, Icons.Default.CalendarToday,
        onSave = { vm.saveYear(PYQYear(bookId = bookId, year = it)); showAdd = false },
        onDismiss = { showAdd = false })
    showStatus?.let { yr -> StatusSelectorDialog(yr.status, onSelect = { vm.saveYear(yr.copy(status = it)); showStatus = null }, onDismiss = { showStatus = null }) }
    showDates?.let { yr -> CompletionDateDialog(yr.completionDates, onSave = { vm.saveYear(yr.copy(completionDates = it)); showDates = null }, onDismiss = { showDates = null }) }
    showWrong?.let { yr -> WrongQuestionsDialog(yr.wrongQuestions, onSave = { vm.saveYear(yr.copy(wrongQuestions = it)); showWrong = null }, onDismiss = { showWrong = null }) }
    showRemark?.let { yr -> RemarkDialog(yr.remark, onSave = { vm.saveYear(yr.copy(remark = it)); showRemark = null }, onDismiss = { showRemark = null }) }
}

fun statusColor(status: Status) = when (status) {
    Status.COMPLETED -> StatusCompleted
    Status.EXPECTED -> StatusExpected
    Status.REVISION -> StatusRevision
    Status.CROSSED -> StatusCross
}
