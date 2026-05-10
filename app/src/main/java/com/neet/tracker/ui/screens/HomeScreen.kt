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
import com.neet.tracker.ui.viewmodels.HomeCountViewModel
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
fun HomeScreen(
    navController: NavController,
    vm: ProfileViewModel = hiltViewModel(),
    countsVm: HomeCountViewModel = hiltViewModel()
) {
    val profile by vm.profile.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val assetsCount      by countsVm.assetsCount.collectAsState()
    val diaryCount       by countsVm.diaryCount.collectAsState()
    val eventCount       by countsVm.eventCount.collectAsState()
    val dictCount        by countsVm.dictCount.collectAsState()
    val mnemonicCount    by countsVm.mnemonicCount.collectAsState()
    val diagramCount     by countsVm.diagramCount.collectAsState()
    val chapterNoteCount by countsVm.chapterNoteCount.collectAsState()
    val dayWasteCount    by countsVm.dayWasteCount.collectAsState()
    val sequenceCount    by countsVm.sequenceCount.collectAsState()
    val lackCount        by countsVm.lackCount.collectAsState()

    // Route → live item count (null = no badge shown)
    val countMap = remember(assetsCount, diaryCount, eventCount, dictCount, mnemonicCount,
        diagramCount, chapterNoteCount, dayWasteCount, sequenceCount, lackCount) {
        mapOf(
            Routes.ASSETS              to assetsCount,
            Routes.DAILY_DIARY         to diaryCount,
            Routes.DATE_EVENTS         to eventCount,
            Routes.DICTIONARY          to dictCount,
            Routes.MNEMONICS           to mnemonicCount,
            Routes.DIAGRAMS            to diagramCount,
            Routes.CHAPTER_SHORT_NOTES to chapterNoteCount,
            Routes.DAY_WASTE           to dayWasteCount,
            Routes.NEET_SEQUENCE       to sequenceCount,
            Routes.LACK_POINTS         to lackCount,
        )
    }

    // Fully thematic icons — each card's icon perfectly matches its subject
    val mainCards = listOf(
        MainCard("Assets Vault",       Icons.Default.Inventory2,        Routes.ASSETS,              NeonCyan,   "Books · Notes · Papers"),
        MainCard("Smart Planner",      Icons.Default.CalendarMonth,     Routes.PLANNER,             NeonPurple, "Day · Week · Month · Year"),
        MainCard("Daily Diary",        Icons.Default.AutoStories,       Routes.DAILY_DIARY,         NeonGold,   "Personal journal entries"),
        MainCard("Event Log",          Icons.Default.EventNote,         Routes.DATE_EVENTS,         NeonGreen,  "Per-date event tracker"),
        MainCard("NEET Syllabus",      Icons.Default.School,            Routes.NEET_SYLLABUS,       NeonOrange, "Official syllabus PDF"),
        MainCard("Lexicon",            Icons.Default.Translate,         Routes.DICTIONARY,          NeonCyan,   "NEET & English dictionary"),
        MainCard("Mnemonic Lab",       Icons.Default.Psychology,        Routes.MNEMONICS,           NeonPurple, "Memory techniques"),
        MainCard("Universe Calendar",  Icons.Default.DateRange,         Routes.UNIVERSAL_CALENDAR,  NeonGold,   "All events at one glance"),
        MainCard("Diagrams Atlas",     Icons.Default.AccountTree,       Routes.DIAGRAMS,            NeonGreen,  "Botany & Zoology diagrams"),
        MainCard("Chapter Notes",      Icons.Default.Article,           Routes.CHAPTER_SHORT_NOTES, NeonCyan,   "Short notes per chapter"),
        MainCard("Wasted Days",        Icons.Default.Dangerous,         Routes.DAY_WASTE,           NeonRed,    "Track & recover lost days"),
        MainCard("NEET Sequence",      Icons.Default.Timeline,          Routes.NEET_SEQUENCE,       NeonPurple, "Chapter study sequence"),
        MainCard("Subject Notes",      Icons.Default.LibraryBooks,      Routes.SUBJECT_SHORT_NOTES, NeonGold,   "Subject-wise PDF notes"),
        MainCard("Lack Points",        Icons.Default.TrendingDown,      Routes.LACK_POINTS,         NeonRed,    "Identify & fix weaknesses"),
    )

    val filtered = mainCards.filter {
        searchQuery.isBlank() ||
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.description.contains(searchQuery, ignoreCase = true)
    }

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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                            enter = fadeIn(tween(400)) + scaleIn(tween(440, easing = EaseOutBack), initialScale = 0.60f) + slideInVertically(tween(400, easing = EaseOutBack)) { it / 3 }
                        ) {
                            HomeModuleCard(
                                card    = card,
                                count   = countMap[card.route],
                                onClick = { navController.navigate(card.route) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 3D Home Header ───────────────────────────────────────────────────────────

@Composable
fun HomeHeader(profile: StudentProfile?, navController: NavController) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_rot"
    )
    val aimGlow by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "aim_glow"
    )
    val headerNebula by infiniteTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "header_nebula"
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF07112A), Color(0xFF050E1E), Color(0xFF040B16).copy(0f))
                )
            )
    ) {
        // Nebula glow background on header
        Box(
            modifier = Modifier.size(260.dp).align(Alignment.TopEnd).offset(50.dp, (-30).dp)
                .background(Brush.radialGradient(listOf(NeonPurple.copy(headerNebula * 0.8f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier.size(180.dp).align(Alignment.TopStart).offset((-40).dp, 10.dp)
                .background(Brush.radialGradient(listOf(NeonCyan.copy(headerNebula * 0.5f), Color.Transparent)), CircleShape)
        )

        // Horizontal top shine line
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(0.30f), NeonPurple.copy(0.20f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {

                // ── 3D Profile Photo ──────────────────────────────────────────
                Box(modifier = Modifier.size(78.dp).clickable { navController.navigate(Routes.PROFILE) }, contentAlignment = Alignment.Center) {
                    // Outer animated sweep ring
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { rotationZ = ringRotation }
                            .border(
                                2.5.dp,
                                Brush.sweepGradient(
                                    0f    to NeonCyan,
                                    0.3f  to NeonPurple,
                                    0.6f  to NeonGold,
                                    0.85f to Color.Transparent,
                                    1f    to NeonCyan
                                ),
                                CircleShape
                            )
                    )
                    // Inner ring
                    Box(
                        modifier = Modifier.size(70.dp)
                            .shadow(16.dp, CircleShape, spotColor = NeonCyan.copy(0.45f))
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF1A2A44), CosmicBlue)),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Brush.sweepGradient(listOf(Color.White.copy(0.25f), NeonCyan.copy(0.40f), Color.White.copy(0.10f))),
                                CircleShape
                            )
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
                                    .background(Brush.radialGradient(listOf(NeonCyan.copy(0.22f), Color.Transparent)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = NeonCyan, modifier = Modifier.size(36.dp))
                            }
                        }
                        // Top-left shine
                        Box(
                            modifier = Modifier.size(28.dp).align(Alignment.TopStart)
                                .background(Brush.radialGradient(listOf(Color.White.copy(0.18f), Color.Transparent)), CircleShape)
                        )
                    }
                    // Camera edit badge
                    Box(
                        modifier = Modifier.size(22.dp).align(Alignment.BottomEnd).offset(2.dp, 2.dp)
                            .shadow(6.dp, CircleShape, spotColor = NeonCyan.copy(0.4f))
                            .background(
                                Brush.linearGradient(listOf(NeonCyan.copy(0.9f), NeonCyan)),
                                CircleShape
                            )
                            .border(2.dp, DeepNavy, CircleShape),
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
                    // Target badge
                    Row(
                        modifier = Modifier
                            .shadow(10.dp, RoundedCornerShape(12.dp), spotColor = NeonGold.copy(aimGlow * 0.55f))
                            .background(
                                Brush.linearGradient(listOf(NeonGold.copy(0.20f), NeonOrange.copy(0.10f))),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(Color.White.copy(0.20f), NeonGold.copy(aimGlow * 0.75f), NeonOrange.copy(aimGlow * 0.35f))),
                                RoundedCornerShape(12.dp)
                            )
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
                    modifier = Modifier.size(42.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = NeonCyan.copy(0.20f))
                        .background(
                            Brush.linearGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.03f))),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            0.5.dp,
                            Brush.linearGradient(listOf(Color.White.copy(0.22f), NeonCyan.copy(0.25f))),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { navController.navigate(Routes.PROFILE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ManageAccounts, null, tint = NeonCyan.copy(0.85f), modifier = Modifier.size(22.dp))
                }
            }

            // ── Motivational strip ────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = NeonCyan.copy(0.12f))
                    .background(
                        Brush.linearGradient(listOf(NeonCyan.copy(0.07f), NeonPurple.copy(0.07f))),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        0.5.dp,
                        Brush.linearGradient(listOf(Color.White.copy(0.18f), NeonCyan.copy(0.28f), NeonPurple.copy(0.18f), Color.White.copy(0.10f))),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        modifier = Modifier.size(22.dp)
                            .background(NeonCyan.copy(0.12f), RoundedCornerShape(7.dp))
                            .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = NeonCyan.copy(0.8f), modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = "Every hour of study today is a step towards that white coat.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.55f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ─── Maximized 3D Module Card ─────────────────────────────────────────────────

@Composable
fun HomeModuleCard(card: MainCard, count: Int? = null, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "hmc_${card.title}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f, targetValue = 0.36f,
        animationSpec = infiniteRepeatable(tween((1800..2900).random(), easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((4000..6000).random(), easing = EaseInOutSine), RepeatMode.Reverse),
        label = "shine"
    )
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.52f, 320f), label = "scale")
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            // Deep 3D shadow with colored glow
            .shadow(
                16.dp, shape,
                spotColor    = card.accentColor.copy(0.38f),
                ambientColor = card.accentColor.copy(0.10f)
            )
            .clip(shape)
            // Multi-layer background for deep 3D feel
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF131F3C),
                        card.accentColor.copy(glowAlpha * 1.1f),
                        Color(0xFF070E1D),
                        card.accentColor.copy(glowAlpha * 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            // Gradient border — bright top-left, dim bottom-right (3D lighting)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.30f),
                        card.accentColor.copy(glowAlpha + 0.12f),
                        Color.White.copy(0.05f),
                        card.accentColor.copy(glowAlpha * 0.22f)
                    )
                ),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // ── Layer 1: Top-left white light source (3D depth illusion)
        Box(
            modifier = Modifier.size(110.dp).align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(0.12f), Color.Transparent)),
                    shape
                )
        )
        // ── Layer 2: Colored corner glow orb
        Box(
            modifier = Modifier.size(100.dp).align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(listOf(card.accentColor.copy(glowAlpha * 1.1f), Color.Transparent)),
                    shape
                )
        )
        // ── Layer 3: Animated diagonal shine stripe (glass reflection)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { translationX = shineOffset * 80f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(0f),
                            Color.White.copy(0.03f),
                            Color.White.copy(0.10f),
                            Color.White.copy(0.14f),
                            Color.White.copy(0.10f),
                            Color.White.copy(0.03f),
                            Color.White.copy(0f)
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(200f, 90f)
                    )
                )
        )
        // ── Layer 4: Bottom-right accent glow
        Box(
            modifier = Modifier.size(70.dp).align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(listOf(card.accentColor.copy(glowAlpha * 0.50f), Color.Transparent)),
                    shape
                )
        )
        // ── Layer 5: Bottom depth shadow (3D floor shadow)
        Box(
            modifier = Modifier.fillMaxWidth().height(50.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.32f))))
        )

        // ── Count badge (top-right corner) ────────────────────────────────────
        if (count != null && count > 0) {
            val badgePulse by rememberInfiniteTransition(label = "badge_${card.title}").animateFloat(
                initialValue = 0.75f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "badge_pulse"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = card.accentColor.copy(badgePulse * 0.6f))
                    .background(
                        Brush.linearGradient(listOf(card.accentColor.copy(0.85f), card.accentColor.copy(0.55f))),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 999) "999+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            }
        }

        // ── Main content
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3D icon box (raised, lit, reflected)
            ThreeDIconBox(
                icon     = card.icon,
                tint     = card.accentColor,
                size     = 56.dp,
                iconSize = 29.dp
            )
            Spacer(Modifier.height(11.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2
            )
            Spacer(Modifier.height(3.dp))
            Text(
                card.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.42f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
