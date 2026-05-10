package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Offset
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
import java.util.Calendar

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
        MainCard("Assets Vault",       Icons.Default.Inventory2,       Routes.ASSETS,              NeonCyan,   "Books · Notes · Papers"),
        MainCard("Smart Planner",      Icons.Default.CalendarMonth,    Routes.PLANNER,             NeonPurple, "Day · Week · Month · Year"),
        MainCard("Daily Diary",        Icons.Default.MenuBook,         Routes.DAILY_DIARY,         NeonGold,   "Personal journal entries"),
        MainCard("Event Log",          Icons.Default.EventNote,        Routes.DATE_EVENTS,         NeonGreen,  "Per-date event tracker"),
        MainCard("NEET Syllabus",      Icons.Default.School,           Routes.NEET_SYLLABUS,       NeonOrange, "Official syllabus PDF"),
        MainCard("Lexicon",            Icons.Default.AutoStories,      Routes.DICTIONARY,          NeonCyan,   "NEET & English dictionary"),
        MainCard("Mnemonic Lab",       Icons.Default.Psychology,       Routes.MNEMONICS,           NeonPurple, "Memory techniques"),
        MainCard("Universe Calendar",  Icons.Default.Today,            Routes.UNIVERSAL_CALENDAR,  NeonGold,   "All events at one glance"),
        MainCard("Diagrams Atlas",     Icons.Default.AccountTree,      Routes.DIAGRAMS,            NeonGreen,  "Botany & Zoology diagrams"),
        MainCard("Chapter Notes",      Icons.Default.Article,          Routes.CHAPTER_SHORT_NOTES, NeonCyan,   "Short notes per chapter"),
        MainCard("Wasted Days",        Icons.Default.Dangerous,        Routes.DAY_WASTE,           NeonRed,    "Track & recover lost days"),
        MainCard("NEET Sequence",      Icons.Default.LinearScale,      Routes.NEET_SEQUENCE,       NeonPurple, "Chapter study sequence"),
        MainCard("Subject Notes",      Icons.Default.LibraryBooks,     Routes.SUBJECT_SHORT_NOTES, NeonGold,   "Subject-wise PDF notes"),
        MainCard("Lack Points",        Icons.Default.TrendingDown,     Routes.LACK_POINTS,         NeonRed,    "Identify & fix weaknesses"),
    )

    val filtered = mainCards.filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeHeader(profile = profile, navController = navController)

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                NeatSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "Search 14 modules...")
                Spacer(Modifier.height(10.dp))

                AnimatedVisibility(visible = searchQuery.isBlank()) {
                    Text(
                        text = "14 Modules · Your NEET Command Center",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan.copy(0.45f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(filtered) { index, card ->
                        val delay = index * 45
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(delay.toLong())
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(380)) + scaleIn(tween(420, easing = EaseOutBack), initialScale = 0.65f) + slideInVertically(tween(380, easing = EaseOutBack)) { it / 3 }
                        ) {
                            HomeModuleCard(card = card, onClick = { navController.navigate(card.route) })
                        }
                    }
                }
            }
        }
    }
}

// ─── Next-Level 3D Header ─────────────────────────────────────────────────────

@Composable
fun HomeHeader(profile: StudentProfile?, navController: NavController) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_rot"
    )
    val aimGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "aim_glow"
    )

    val greetingHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        greetingHour < 5  -> "Up Late?"
        greetingHour < 12 -> "Good Morning"
        greetingHour < 17 -> "Good Afternoon"
        greetingHour < 21 -> "Good Evening"
        else              -> "Good Night"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF07112A), Color(0xFF040B16).copy(0f))))
    ) {
        // Header nebula
        Box(
            modifier = Modifier.size(200.dp).align(Alignment.TopEnd).offset(40.dp, (-20).dp)
                .background(Brush.radialGradient(listOf(NeonPurple.copy(0.12f), Color.Transparent)), CircleShape)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {

                // ── 3D Profile Photo ──────────────────────────────────────────
                Box(modifier = Modifier.size(74.dp).clickable { navController.navigate(Routes.PROFILE) }, contentAlignment = Alignment.Center) {
                    // Outer animated sweep ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = ringRotation }
                            .border(
                                2.5.dp,
                                Brush.sweepGradient(
                                    0f   to NeonCyan,
                                    0.3f to NeonPurple,
                                    0.6f to NeonGold,
                                    0.85f to Color.Transparent,
                                    1f   to NeonCyan
                                ),
                                CircleShape
                            )
                    )
                    // Static pulse ring
                    Box(
                        modifier = Modifier.size(66.dp)
                            .shadow(12.dp, CircleShape, spotColor = NeonCyan.copy(0.4f))
                            .background(Brush.radialGradient(listOf(Color(0xFF1A2A44), CosmicBlue)), CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile?.photoUri?.isNotBlank() == true) {
                            coil.compose.AsyncImage(
                                model = profile.photoUri,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(Brush.radialGradient(listOf(NeonCyan.copy(0.2f), Color.Transparent)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = NeonCyan, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    // Camera badge
                    Box(
                        modifier = Modifier.size(20.dp).align(Alignment.BottomEnd).offset(2.dp, 2.dp)
                            .background(NeonCyan, CircleShape).border(2.dp, DeepNavy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = DeepNavy, modifier = Modifier.size(11.dp))
                    }
                }

                // ── Name + Greeting ───────────────────────────────────────────
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "$greeting,",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(0.45f)
                    )
                    Text(
                        text = profile?.name?.ifBlank { "NEET Aspirant" } ?: "NEET Aspirant",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    // Aim badge
                    Row(
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonGold.copy(aimGlow * 0.5f))
                            .background(
                                Brush.horizontalGradient(listOf(NeonGold.copy(0.18f), NeonOrange.copy(0.10f))),
                                RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Brush.horizontalGradient(listOf(NeonGold.copy(aimGlow * 0.7f), NeonOrange.copy(aimGlow * 0.4f))), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, tint = NeonGold, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Target: ${profile?.targetScore ?: "700/720"} · Future MBBS Doctor 🩺",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Settings icon
                Box(
                    modifier = Modifier.size(40.dp)
                        .background(Color.White.copy(0.05f), RoundedCornerShape(13.dp))
                        .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(13.dp))
                        .clickable { navController.navigate(Routes.PROFILE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ManageAccounts, null, tint = NeonCyan.copy(0.8f), modifier = Modifier.size(22.dp))
                }
            }

            // ── Motivational strip ────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(NeonCyan.copy(0.06f), NeonPurple.copy(0.06f))), RoundedCornerShape(12.dp))
                    .border(0.5.dp, Brush.horizontalGradient(listOf(NeonCyan.copy(0.25f), NeonPurple.copy(0.2f))), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(13.dp))
                    Text(
                        text = "Every hour of study today is a step towards that white coat.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.5f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ─── 3D Module Card ───────────────────────────────────────────────────────────

@Composable
fun HomeModuleCard(card: MainCard, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "hmc_${card.title}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween((1800..2900).random(), easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.55f, 320f), label = "scale")

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .shadow(10.dp, RoundedCornerShape(22.dp), spotColor = card.accentColor.copy(0.28f), ambientColor = card.accentColor.copy(0.06f))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(card.accentColor.copy(glowAlpha * 0.9f), Color(0xFF060E1C), card.accentColor.copy(glowAlpha * 0.4f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, Brush.linearGradient(listOf(card.accentColor.copy(glowAlpha + 0.08f), Color.White.copy(0.03f), card.accentColor.copy(glowAlpha * 0.25f))), RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
    ) {
        // Corner glow orb
        Box(
            modifier = Modifier.size(88.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(card.accentColor.copy(glowAlpha * 1.1f), Color.Transparent)), RoundedCornerShape(22.dp))
        )
        // Bottom-right reverse glow
        Box(
            modifier = Modifier.size(56.dp).align(Alignment.BottomEnd)
                .background(Brush.radialGradient(listOf(card.accentColor.copy(glowAlpha * 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3D icon box
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = card.accentColor.copy(0.5f))
                    .background(
                        Brush.linearGradient(listOf(card.accentColor.copy(0.25f), card.accentColor.copy(0.08f))),
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, card.accentColor.copy(0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(card.icon, null, tint = card.accentColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(card.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            Text(card.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.42f), textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

