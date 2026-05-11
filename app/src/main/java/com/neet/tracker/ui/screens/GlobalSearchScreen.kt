package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.*

@Composable
fun GlobalSearchScreen(
    navController: NavController,
    vm: GlobalSearchViewModel = hiltViewModel()
) {
    val query       by vm.query.collectAsState()
    val allResults  by vm.results.collectAsState()
    var filterCat   by remember { mutableStateOf<SearchCategory?>(null) }
    val focusRequester = remember { FocusRequester() }

    val displayResults = remember(allResults, filterCat) {
        if (filterCat == null) allResults else allResults.filter { it.category == filterCat }
    }
    val grouped = remember(displayResults) { displayResults.groupBy { it.category } }

    val infiniteTransition = rememberInfiniteTransition(label = "gs")
    val nebulaAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(3500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula"
    )
    val scanLine by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "scan"
    )

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Sticky Search Header ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A1528), Color(0xFF060F22), Color(0xFF040C1A).copy(0.97f))
                        )
                    )
            ) {
                // Nebula glow orbs
                Box(
                    modifier = Modifier.size(200.dp).align(Alignment.TopEnd).offset(40.dp, (-20).dp)
                        .background(Brush.radialGradient(listOf(NeonPurple.copy(nebulaAlpha), Color.Transparent)), CircleShape)
                )
                Box(
                    modifier = Modifier.size(150.dp).align(Alignment.TopStart).offset((-30).dp, 10.dp)
                        .background(Brush.radialGradient(listOf(NeonCyan.copy(nebulaAlpha * 0.7f), Color.Transparent)), CircleShape)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Title row ────────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonPurple.copy(0.4f))
                                .background(
                                    Brush.linearGradient(listOf(NeonPurple.copy(0.30f), NeonCyan.copy(0.12f))),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, NeonPurple.copy(0.45f), RoundedCornerShape(12.dp))
                                .clickable { navController.popBackStack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowBack, null, tint = NeonPurple, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Global Search",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Across all your study modules",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.40f)
                            )
                        }
                        AnimatedVisibility(
                            visible = allResults.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .shadow(4.dp, RoundedCornerShape(10.dp), spotColor = NeonCyan.copy(0.35f))
                                    .background(NeonCyan.copy(0.12f), RoundedCornerShape(10.dp))
                                    .border(0.5.dp, NeonCyan.copy(0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${allResults.size} found",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // ── Search bar ───────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                if (query.isNotBlank()) 14.dp else 6.dp,
                                RoundedCornerShape(18.dp),
                                spotColor = if (query.isNotBlank()) NeonPurple.copy(0.45f) else Color.Transparent
                            )
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF141D38), Color(0xFF0D162A))),
                                RoundedCornerShape(18.dp)
                            )
                            .border(
                                1.5.dp,
                                Brush.linearGradient(
                                    if (query.isNotBlank())
                                        listOf(NeonPurple.copy(0.85f), NeonCyan.copy(0.55f), NeonPurple.copy(0.40f))
                                    else
                                        listOf(Color.White.copy(0.14f), NeonPurple.copy(0.20f), Color.White.copy(0.08f))
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Animated scan line when active
                        if (query.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                NeonPurple.copy(0.45f * scanLine),
                                                NeonCyan.copy(0.30f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                null,
                                tint = if (query.isNotBlank()) NeonPurple else Color.White.copy(0.30f),
                                modifier = Modifier.size(22.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        "Search mnemonics, PYQ, errors, notes…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(0.28f)
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = vm::onQueryChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                    cursorBrush = SolidColor(NeonPurple),
                                    singleLine = true
                                )
                            }
                            AnimatedVisibility(
                                visible = query.isNotBlank(),
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.White.copy(0.10f), CircleShape)
                                        .clickable { vm.clearQuery() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White.copy(0.70f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // ── Category filter chips ────────────────────────────────
                    AnimatedVisibility(visible = allResults.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 8.dp)
                        ) {
                            // "All" chip
                            item {
                                val sel = filterCat == null
                                GsChip(
                                    label = "All (${allResults.size})",
                                    selected = sel,
                                    color = NeonCyan
                                ) { filterCat = null }
                            }
                            // Per-category chips
                            val cats = allResults.map { it.category }.distinct().sortedBy { it.ordinal }
                            items(cats) { cat ->
                                val sel = filterCat == cat
                                val count = allResults.count { it.category == cat }
                                GsChip(
                                    label = "${cat.label} ($count)",
                                    selected = sel,
                                    color = cat.accentColor(),
                                    dot = true
                                ) { filterCat = if (sel) null else cat }
                            }
                        }
                    }
                }

                // Bottom glow line
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, NeonPurple.copy(0.45f), NeonCyan.copy(0.30f), Color.Transparent)
                            )
                        )
                )
            }

            // ── Results Body ──────────────────────────────────────────────────
            when {
                query.length < 2 -> GsEmptyState()
                grouped.isEmpty() -> GsNoResultsState(query)
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        grouped.entries.sortedBy { it.key.ordinal }.forEach { (cat, items) ->
                            item(key = "hdr_${cat.name}") {
                                GsSectionHeader(cat)
                            }
                            items(items = items, key = { it.id }) { result ->
                                GsResultCard(
                                    result = result,
                                    query = query,
                                    onClick = { navController.navigate(result.route) }
                                )
                            }
                            item(key = "spacer_${cat.name}") {
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Section Header ────────────────────────────────────────────────────────────

@Composable
private fun GsSectionHeader(cat: SearchCategory) {
    val color = cat.accentColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp)
                .background(color.copy(0.16f), RoundedCornerShape(8.dp))
                .border(0.5.dp, color.copy(0.35f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(cat.gsIcon(), null, tint = color, modifier = Modifier.size(15.dp))
        }
        Text(
            text = cat.label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )
        Box(
            modifier = Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(listOf(color.copy(0.30f), Color.Transparent)))
        )
    }
}

// ─── Result Card ───────────────────────────────────────────────────────────────

@Composable
private fun GsResultCard(result: SearchResult, query: String, onClick: () -> Unit) {
    val color = result.category.accentColor()

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(vertical = 3.dp)
            .shadow(5.dp, RoundedCornerShape(14.dp), spotColor = color.copy(0.14f))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F1830), color.copy(0.07f), Color(0xFF09101E))
                )
            )
            .border(
                0.5.dp,
                Brush.linearGradient(listOf(color.copy(0.28f), Color.White.copy(0.04f), Color.Transparent)),
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(54.dp)
                .background(
                    Brush.verticalGradient(listOf(color, color.copy(0.25f))),
                    RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                )
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Category icon
            Box(
                modifier = Modifier.size(36.dp)
                    .background(color.copy(0.14f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, color.copy(0.30f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(result.category.gsIcon(), null, tint = color, modifier = Modifier.size(18.dp))
            }
            // Title + subtitle
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = gsHighlight(result.title, query, color),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.subtitle.isNotBlank()) {
                    Text(
                        text = result.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.42f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Category badge + chevron
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(color.copy(0.14f), RoundedCornerShape(6.dp))
                        .border(0.3.dp, color.copy(0.32f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = result.category.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = Color.White.copy(0.22f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Filter Chip ──────────────────────────────────────────────────────────────

@Composable
private fun GsChip(
    label: String,
    selected: Boolean,
    color: Color,
    dot: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .shadow(
                if (selected) 6.dp else 0.dp,
                RoundedCornerShape(20.dp),
                spotColor = color.copy(0.4f)
            )
            .background(
                if (selected)
                    Brush.linearGradient(listOf(color.copy(0.26f), color.copy(0.10f)))
                else
                    Brush.linearGradient(listOf(Color.White.copy(0.06f), Color.White.copy(0.02f))),
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (selected) color.copy(0.65f) else Color.White.copy(0.10f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dot) {
                Box(
                    modifier = Modifier.size(6.dp)
                        .background(if (selected) color else Color.White.copy(0.25f), CircleShape)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else Color.White.copy(0.50f),
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
            )
        }
    }
}

// ─── Empty State (no query entered) ────────────────────────────────────────────

@Composable
private fun GsEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "gs_empty")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    val moduleGrid = listOf(
        "Mnemonics"     to NeonPurple,
        "PYQ Chapters"  to NeonGold,
        "NEET Terms"    to NeonCyan,
        "Error Notes"   to NeonRed,
        "Revision Plan" to NeonTeal,
        "Diagrams"      to NeonGreen,
        "Books"         to NeonOrange,
        "Diary"         to NeonPink,
        "Lack Points"   to NeonIndigo,
        "NEET Sequence" to NeonGold,
        "Test Papers"   to NeonOrange,
        "Day Waste"     to NeonRed
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Central animated icon
        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(22.dp, CircleShape, spotColor = NeonPurple.copy(pulse * 0.55f))
                .background(
                    Brush.radialGradient(
                        listOf(NeonPurple.copy(0.22f), NeonCyan.copy(0.08f), Color.Transparent)
                    ),
                    CircleShape
                )
                .border(
                    1.dp,
                    Brush.sweepGradient(
                        listOf(
                            NeonPurple.copy(pulse * 0.85f),
                            NeonCyan.copy(0.35f),
                            NeonPurple.copy(0.30f)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = NeonPurple.copy(0.55f + 0.45f * pulse),
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Search Everything",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Type at least 2 characters to search\nacross all your study content instantly",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(0.45f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // What you can search grid
        Text(
            "SEARCHABLE MODULES",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.30f),
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(14.dp))

        moduleGrid.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, color) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(color.copy(0.09f), RoundedCornerShape(10.dp))
                            .border(0.5.dp, color.copy(0.22f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 4.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = color.copy(0.85f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.size < 3) {
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))

        // Example searches
        Text(
            "TRY SEARCHING",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.30f),
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("enzyme", "NEET 2022", "meiosis", "optics").forEach { hint ->
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(0.06f), RoundedCornerShape(20.dp))
                        .border(0.5.dp, Color.White.copy(0.14f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "\"$hint\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.45f)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── No Results State ──────────────────────────────────────────────────────────

@Composable
private fun GsNoResultsState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            null,
            tint = NeonRed.copy(0.45f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "No results found",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Nothing matched \"$query\" across any module.\nTry a shorter word or check spelling.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(0.42f),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Highlight matched text ────────────────────────────────────────────────────

private fun gsHighlight(text: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return buildAnnotatedString { append(text) }
    val idx = text.lowercase().indexOf(query.lowercase())
    if (idx == -1) return buildAnnotatedString { append(text) }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = Color.White)) { append(text.substring(0, idx)) }
        withStyle(
            SpanStyle(
                color = color,
                fontWeight = FontWeight.ExtraBold,
                background = color.copy(0.14f)
            )
        ) { append(text.substring(idx, idx + query.length)) }
        withStyle(SpanStyle(color = Color.White)) { append(text.substring(idx + query.length)) }
    }
}

// ─── Category metadata (no @Composable needed — static values) ─────────────────

private fun SearchCategory.accentColor(): Color = when (this) {
    SearchCategory.NOTEBOOK_CHAPTER -> NeonCyan
    SearchCategory.BOOK             -> NeonPurple
    SearchCategory.PYQ              -> NeonGold
    SearchCategory.TEST             -> NeonOrange
    SearchCategory.SAMPLE           -> NeonOrange
    SearchCategory.PW               -> NeonPink
    SearchCategory.DIARY            -> NeonTeal
    SearchCategory.EVENT            -> NeonGreen
    SearchCategory.DICT_NEET        -> NeonCyan
    SearchCategory.DICT_WORD        -> NeonIndigo
    SearchCategory.MNEMONIC         -> NeonPurple
    SearchCategory.DIAGRAM          -> NeonGreen
    SearchCategory.CHAPTER_NOTE     -> NeonTeal
    SearchCategory.DAY_WASTE        -> NeonRed
    SearchCategory.NEET_SEQUENCE    -> NeonGold
    SearchCategory.LACK_POINT       -> NeonOrange
    SearchCategory.ERROR            -> NeonRed
    SearchCategory.REVISION         -> NeonCyan
}

private fun SearchCategory.gsIcon(): ImageVector = when (this) {
    SearchCategory.NOTEBOOK_CHAPTER -> Icons.Default.Book
    SearchCategory.BOOK             -> Icons.Default.LibraryBooks
    SearchCategory.PYQ              -> Icons.Default.History
    SearchCategory.TEST             -> Icons.Default.Assignment
    SearchCategory.SAMPLE           -> Icons.Default.Article
    SearchCategory.PW               -> Icons.Default.Groups
    SearchCategory.DIARY            -> Icons.Default.AutoStories
    SearchCategory.EVENT            -> Icons.Default.EventNote
    SearchCategory.DICT_NEET        -> Icons.Default.Translate
    SearchCategory.DICT_WORD        -> Icons.Default.Translate
    SearchCategory.MNEMONIC         -> Icons.Default.Psychology
    SearchCategory.DIAGRAM          -> Icons.Default.AccountTree
    SearchCategory.CHAPTER_NOTE     -> Icons.Default.StickyNote2
    SearchCategory.DAY_WASTE        -> Icons.Default.Dangerous
    SearchCategory.NEET_SEQUENCE    -> Icons.Default.Timeline
    SearchCategory.LACK_POINT       -> Icons.Default.TrendingDown
    SearchCategory.ERROR            -> Icons.Default.ErrorOutline
    SearchCategory.REVISION         -> Icons.Default.AutoGraph
}
