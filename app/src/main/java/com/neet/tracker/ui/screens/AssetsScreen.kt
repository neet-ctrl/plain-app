package com.neet.tracker.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.Subject
import com.neet.tracker.data.models.SubjectShortNote
import com.neet.tracker.navigation.Routes
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.SubjectNoteViewModel

@Composable
fun AssetsScreen(navController: NavController) {
    // Thematic icons: each card's icon perfectly represents its content
    val assetCards = listOf(
        Triple("Notebook Vault",  Icons.Default.Book,            Routes.NOTEBOOKS)     to NeonCyan,
        Triple("Book Library",   Icons.Default.LibraryBooks,     Routes.BOOKS)         to NeonPurple,
        Triple("PYQ Archive",    Icons.Default.History,          Routes.PYQ)           to NeonGold,
        Triple("Test Papers",    Icons.Default.Assignment,       Routes.TEST_PAPERS)   to NeonGreen,
        Triple("Sample Papers",  Icons.Default.ContentCopy,      Routes.SAMPLE_PAPERS) to NeonOrange,
        Triple("PW Batches",     Icons.Default.Groups,           Routes.PW_BATCHES)    to NeonCyan,
    )

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(
                title = "Assets Vault",
                breadcrumb = "Home",
                onBack = { navController.popBackStack() }
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                itemsIndexed(assetCards) { index, (info, color) ->
                    val visible = remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 80L)
                        visible.value = true
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(420)) + scaleIn(tween(440, easing = EaseOutBack), 0.70f)
                    ) {
                        NEETCard(
                            title = info.first,
                            icon = info.second,
                            glowColor = color,
                            onClick = { navController.navigate(info.third) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PYQScreen(navController: NavController) {
    val cards = listOf(
        Triple("Chapter-wise PYQs", Icons.Default.MenuBook,        Routes.PYQ_CHAPTERWISE) to NeonCyan,
        Triple("Year-wise PYQs",    Icons.Default.CalendarViewMonth, Routes.PYQ_YEARWISE) to NeonPurple,
    )

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "PYQ Archive", breadcrumb = "Home / Assets", onBack = { navController.popBackStack() })

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(cards) { (info, color) ->
                    NEETCard(title = info.first, icon = info.second, glowColor = color, onClick = { navController.navigate(info.third) })
                }
            }
        }
    }
}

@Composable
fun TestPapersScreen(navController: NavController) {
    val cards = listOf(
        Triple("Online Tests",  Icons.Default.Computer,   Routes.ONLINE_TESTS)  to NeonGreen,
        Triple("Offline Tests", Icons.Default.OfflineBolt, Routes.OFFLINE_TESTS) to NeonOrange,
    )

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Test Papers", breadcrumb = "Home / Assets", onBack = { navController.popBackStack() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(cards) { (info, color) ->
                    NEETCard(title = info.first, icon = info.second, glowColor = color, onClick = { navController.navigate(info.third) })
                }
            }
        }
    }
}

@Composable
fun DictionaryScreen(navController: NavController) {
    val cards = listOf(
        Triple("NEET Lexicon", Icons.Default.Science,   Routes.DICTIONARY_NEET)     to NeonCyan,
        Triple("Word Bank",    Icons.Default.Translate, Routes.DICTIONARY_NON_NEET) to NeonPurple,
    )

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Lexicon", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(cards) { (info, color) ->
                    NEETCard(title = info.first, icon = info.second, glowColor = color, onClick = { navController.navigate(info.third) })
                }
            }
        }
    }
}

@Composable
fun DiagramsScreen(navController: NavController) {
    // Thematic: Park = Botany (plant/tree), Pets = Zoology (animals)
    val subjects = listOf(
        Triple("Botany Diagrams",  Icons.Default.Park, "BOTANY")  to NeonGreen,
        Triple("Zoology Diagrams", Icons.Default.Pets, "ZOOLOGY") to NeonOrange,
    )

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Diagrams Atlas", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(subjects) { (info, color) ->
                    NEETCard(
                        title = info.first,
                        icon = info.second,
                        glowColor = color,
                        onClick = { navController.navigate(com.neet.tracker.navigation.diagramsSubjectRoute(info.third)) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterShortNotesScreen(navController: NavController) {
    // Thematic icons: ElectricBolt=Physics, Science=Chemistry, Park=Botany, Pets=Zoology
    val subjects = listOf(
        Triple("Physics Notes",   Icons.Default.ElectricBolt, "PHYSICS")   to NeonCyan,
        Triple("Chemistry Notes", Icons.Default.Science,      "CHEMISTRY") to NeonPurple,
        Triple("Botany Notes",    Icons.Default.Park,         "BOTANY")    to NeonGreen,
        Triple("Zoology Notes",   Icons.Default.Pets,         "ZOOLOGY")   to NeonOrange,
    )

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Chapter Notes", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(subjects) { (info, color) ->
                    NEETCard(
                        title = info.first,
                        icon = info.second,
                        glowColor = color,
                        onClick = { navController.navigate(com.neet.tracker.navigation.chapterShortNotesSubjectRoute(info.third)) }
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectShortNotesScreen(navController: NavController) {
    // Thematic icons for each subject
    val subjects = listOf(
        Triple("Physics",   Icons.Default.ElectricBolt, "PHYSICS")   to NeonCyan,
        Triple("Chemistry", Icons.Default.Science,      "CHEMISTRY") to NeonPurple,
        Triple("Botany",    Icons.Default.Park,         "BOTANY")    to NeonGreen,
        Triple("Zoology",   Icons.Default.Pets,         "ZOOLOGY")   to NeonOrange,
    )

    val vm: SubjectNoteViewModel = hiltViewModel()
    var uploadingSubject by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            uploadingSubject?.let { subj ->
                val subjectEnum = try { Subject.valueOf(subj) } catch (e: Exception) { Subject.GENERAL }
                vm.save(SubjectShortNote(subject = subjectEnum, fileUri = u.toString()))
            }
        }
        uploadingSubject = null
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(title = "Subject Notes", breadcrumb = "Home", onBack = { navController.popBackStack() })
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(subjects) { (info, color) ->
                    val note by vm.noteFor(info.third).collectAsState()
                    val hasPdf = note?.fileUri?.isNotBlank() == true
                    NEETCard(
                        title = info.first,
                        subtitle = if (hasPdf) "Tap to view PDF" else "Tap to upload PDF",
                        icon = if (hasPdf) Icons.Default.PictureAsPdf else info.second,
                        glowColor = color,
                        onClick = {
                            if (hasPdf) {
                                navController.navigate(com.neet.tracker.navigation.subjectNoteViewerRoute(info.third, note!!.fileUri, "${info.first} Notes"))
                            } else {
                                uploadingSubject = info.third
                                launcher.launch("application/pdf")
                            }
                        },
                        bottomContent = {
                            CardIconButton(Icons.Default.UploadFile, color.copy(0.7f)) {
                                uploadingSubject = info.third
                                launcher.launch("application/pdf")
                            }
                            if (hasPdf) {
                                CardIconButton(Icons.Default.FileOpen, color.copy(0.7f)) {
                                    navController.navigate(com.neet.tracker.navigation.subjectNoteViewerRoute(info.third, note!!.fileUri, "${info.first} Notes"))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
