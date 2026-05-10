package com.neet.tracker.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.StudentProfile
import com.neet.tracker.navigation.*
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.ProfileViewModel

data class MainCard(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val accentColor: Color,
    val description: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavController, vm: ProfileViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val mainCards = listOf(
        MainCard("Assets Vault", Icons.Default.Inventory2, Routes.ASSETS, NeonCyan, "Books, Notes & Papers"),
        MainCard("Smart Planner", Icons.Default.CalendarMonth, Routes.PLANNER, NeonPurple, "Day, Week, Month & Year"),
        MainCard("Daily Diary", Icons.Default.MenuBook, Routes.DAILY_DIARY, NeonGold, "Personal journal entries"),
        MainCard("Event Log", Icons.Default.EventNote, Routes.DATE_EVENTS, NeonGreen, "Per-date event tracker"),
        MainCard("NEET Syllabus", Icons.Default.School, Routes.NEET_SYLLABUS, NeonOrange, "Official syllabus PDF"),
        MainCard("Lexicon", Icons.Default.AutoStories, Routes.DICTIONARY, NeonCyan, "NEET & English dictionary"),
        MainCard("Mnemonic Lab", Icons.Default.Psychology, Routes.MNEMONICS, NeonPurple, "Memory techniques"),
        MainCard("Universe Calendar", Icons.Default.Today, Routes.UNIVERSAL_CALENDAR, NeonGold, "All events at one glance"),
        MainCard("Diagrams Atlas", Icons.Default.AccountTree, Routes.DIAGRAMS, NeonGreen, "Botany & Zoology diagrams"),
        MainCard("Chapter Notes", Icons.Default.Article, Routes.CHAPTER_SHORT_NOTES, NeonCyan, "Short notes per chapter"),
        MainCard("Wasted Days", Icons.Default.Dangerous, NeonRed.toString(), NeonRed, "Track & recover lost days"),
        MainCard("NEET Sequence", Icons.Default.LinearScale, Routes.NEET_SEQUENCE, NeonPurple, "Chapter study sequence"),
        MainCard("Subject Notes", Icons.Default.LibraryBooks, Routes.SUBJECT_SHORT_NOTES, NeonGold, "Subject-wise PDF notes"),
        MainCard("Lack Points", Icons.Default.TrendingDown, Routes.LACK_POINTS, NeonRed, "Identify & fix weaknesses"),
    )

    // fix route for wasted days
    val fixedCards = mainCards.map {
        if (it.accentColor == NeonRed && it.title == "Wasted Days") it.copy(route = Routes.DAY_WASTE) else it
    }

    val filtered = fixedCards.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            HomeHeader(profile = profile, navController = navController)

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search modules...")
                Spacer(Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(filtered) { index, card ->
                        val delay = index * 40
                        val visible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(delay.toLong())
                            visible.value = true
                        }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(tween(400)) + scaleIn(tween(400, easing = EaseOutBack), initialScale = 0.7f)
                        ) {
                            HomeModuleCard(card = card, onClick = { navController.navigate(card.route) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeHeader(profile: StudentProfile?, navController: NavController) {
    val infiniteTransition = rememberInfiniteTransition(label = "header_glow")
    val glowOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "glow_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1F3C), Color.Transparent)
                )
            )
            .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Photo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { navController.navigate(Routes.PROFILE) }
            ) {
                // Animated ring
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(
                            2.dp,
                            Brush.sweepGradient(
                                colors = listOf(NeonCyan, NeonPurple, NeonGold, NeonCyan)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .align(Alignment.Center)
                        .background(
                            Brush.radialGradient(colors = listOf(Color(0xFF1A2744), CosmicBlue)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile?.photoUri?.isNotBlank() == true) {
                        coil.compose.AsyncImage(
                            model = profile.photoUri,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.Person, null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.name?.ifBlank { "NEET Student" } ?: "NEET Student",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                // Aim bar
                Row(
                    modifier = Modifier
                        .background(NeonGold.copy(0.12f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, NeonGold.copy(0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = NeonGold, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Target: ${profile?.targetScore ?: "700/720"} · MBBS Dream",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Notification / Settings area
            IconButton(onClick = { navController.navigate(Routes.PROFILE) }) {
                Icon(Icons.Default.ManageAccounts, null, tint = NeonCyan, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun HomeModuleCard(card: MainCard, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_glow_${card.title}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween((1500..3000).random(), easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        card.accentColor.copy(alpha = 0.12f),
                        Color(0xFF0A1628).copy(alpha = 0.97f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        card.accentColor.copy(alpha = glowAlpha + 0.1f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Corner glow
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(card.accentColor.copy(alpha = glowAlpha), Color.Transparent)
                    ),
                    RoundedCornerShape(22.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(card.accentColor.copy(0.15f), RoundedCornerShape(16.dp))
                    .border(1.dp, card.accentColor.copy(0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(card.icon, null, tint = card.accentColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.45f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun NeatSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String = "Search...") {
    NeetSearchBar(query = query, onQueryChange = onQueryChange, placeholder = placeholder)
}
