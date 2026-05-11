package com.neet.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.FlashcardType
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.*

@Composable
fun FlashcardScreen(
    navController: NavController,
    vm: FlashcardViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()

    SpaceBackground {
        when (ui.phase) {
            FlashcardPhase.SETUP    -> FcSetupPhase(ui, vm, navController)
            FlashcardPhase.REVIEWING -> FcReviewPhase(ui, vm)
            FlashcardPhase.RESULTS  -> FcResultsPhase(ui, vm, navController)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SETUP PHASE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FcSetupPhase(
    ui: FlashcardUiState,
    vm: FlashcardViewModel,
    navController: NavController
) {
    val neetTerms  by vm.neetTermCount.collectAsState()
    val words      by vm.wordCount.collectAsState()
    val mnemonics  by vm.mnemonicCount.collectAsState()
    val due        by vm.dueCount.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "fc_setup")
    val nebulaAlpha by infiniteTransition.animateFloat(
        0.07f, 0.18f,
        infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), "nebula"
    )
    val glowPulse by infiniteTransition.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), "glow"
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.size(220.dp).align(Alignment.TopEnd).offset(40.dp, (-30).dp)
            .background(Brush.radialGradient(listOf(NeonPink.copy(nebulaAlpha), Color.Transparent)), CircleShape))
        Box(Modifier.size(160.dp).align(Alignment.BottomStart).offset((-30).dp, 30.dp)
            .background(Brush.radialGradient(listOf(NeonPurple.copy(nebulaAlpha * 0.7f), Color.Transparent)), CircleShape))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonPink.copy(0.4f))
                        .background(Brush.linearGradient(listOf(NeonPink.copy(0.28f), NeonPurple.copy(0.12f))), RoundedCornerShape(12.dp))
                        .border(1.dp, NeonPink.copy(0.45f), RoundedCornerShape(12.dp))
                        .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = NeonPink, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Flashcard Review", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("AI-powered spaced repetition engine", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.42f))
                }
                Box(
                    modifier = Modifier.size(40.dp)
                        .shadow(6.dp, CircleShape, spotColor = NeonPink.copy(glowPulse * 0.4f))
                        .background(Brush.radialGradient(listOf(NeonPink.copy(0.22f), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Quiz, null, tint = NeonPink.copy(glowPulse), modifier = Modifier.size(22.dp))
                }
            }

            FcSetupSection("SOURCE") {
                val sources = listOf(
                    FlashcardSource.NEET_TERMS  to (Icons.Default.Translate   to "$neetTerms terms"),
                    FlashcardSource.NON_NEET_WORDS to (Icons.Default.Language to "$words words"),
                    FlashcardSource.MNEMONICS   to (Icons.Default.Psychology  to "$mnemonics mnemonics"),
                    FlashcardSource.MIXED       to (Icons.Default.Shuffle     to "All combined"),
                    FlashcardSource.DUE_ONLY    to (Icons.Default.Schedule    to "$due due now")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sources) { (src, pair) ->
                        val (icon, count) = pair
                        val sel = ui.source == src
                        val color = src.color()
                        FcSourceCard(src.label, count, icon, color, sel) { vm.onSourceChange(src) }
                    }
                }
            }

            FcSetupSection("SESSION SIZE") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20, 50, -1).forEach { n ->
                        val sel = ui.sessionSize == n
                        FcChip(if (n == -1) "All" else "$n", sel, NeonCyan) { vm.onSizeChange(n) }
                    }
                }
            }

            FcSetupSection("SUBJECT FILTER") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ALL", "PHYSICS", "CHEMISTRY", "BOTANY", "ZOOLOGY", "GENERAL")) { subj ->
                        val sel = ui.subjectFilter == subj
                        FcChip(subj.capitalize(), sel, subjectColor(subj)) { vm.onSubjectChange(subj) }
                    }
                }
            }

            FcSetupSection("REVIEW MODE") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        FlashcardMode.FLIP_CARD       to (Icons.Default.FlipToBack    to "3D card flip"),
                        FlashcardMode.MULTIPLE_CHOICE to (Icons.Default.CheckBox      to "4-option MCQ"),
                        FlashcardMode.TYPE_ANSWER     to (Icons.Default.Keyboard      to "Type the term")
                    ).forEach { (mode, pair) ->
                        val (icon, desc) = pair
                        val sel = ui.mode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(if (sel) 10.dp else 2.dp, RoundedCornerShape(16.dp), spotColor = NeonPurple.copy(if (sel) 0.4f else 0f))
                                .background(
                                    if (sel) Brush.linearGradient(listOf(NeonPurple.copy(0.28f), NeonPink.copy(0.14f)))
                                    else Brush.linearGradient(listOf(Color.White.copy(0.05f), Color.White.copy(0.02f))),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, if (sel) NeonPurple.copy(0.65f) else Color.White.copy(0.10f), RoundedCornerShape(16.dp))
                                .clickable { vm.onModeChange(mode) }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(icon, null, tint = if (sel) NeonPurple else Color.White.copy(0.35f), modifier = Modifier.size(22.dp))
                                Text(mode.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Color.White.copy(0.50f), fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal, textAlign = TextAlign.Center)
                                Text(desc, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = if (sel) NeonPurple.copy(0.8f) else Color.White.copy(0.25f), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // ── Begin Button ─────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(20.dp), spotColor = NeonPink.copy(glowPulse * 0.6f))
                    .background(
                        Brush.linearGradient(listOf(NeonPink.copy(0.85f), NeonPurple.copy(0.80f), NeonPink.copy(0.70f))),
                        RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.4f), NeonPink.copy(0.6f))), RoundedCornerShape(20.dp))
                    .clickable { vm.startSession() }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Text("Begin Session", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  REVIEW PHASE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FcReviewPhase(ui: FlashcardUiState, vm: FlashcardViewModel) {
    val card = ui.sessionCards.getOrNull(ui.currentIndex) ?: return
    val progress = ui.currentIndex + 1
    val total = ui.sessionCards.size
    val accuracy = if (ui.sessionResults.isEmpty()) 100
                   else (ui.sessionResults.count { it.quality >= 2 } * 100 / ui.sessionResults.size)

    val infiniteTransition = rememberInfiniteTransition(label = "fc_review")
    val glowPulse by infiniteTransition.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), "glow"
    )
    val cardColor = subjectColor(card.subject)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Progress & Stats ─────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(Color.White.copy(0.07f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
                        .clickable { vm.goToSetup() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                }
                Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.08f))) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.toFloat() / total)
                            .background(Brush.horizontalGradient(listOf(NeonPink, NeonPurple)), RoundedCornerShape(4.dp))
                    )
                }
                Text("$progress / $total", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f), fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FcStatChip(Icons.Default.TrendingUp, "${accuracy}%", NeonGreen, "Accuracy")
                val streak = ui.sessionResults.takeLastWhile { it.quality >= 2 }.size
                FcStatChip(Icons.Default.LocalFireDepartment, "$streak", NeonOrange, "Streak")
                FcStatChip(Icons.Default.Quiz, card.frontLabel, cardColor, "Type")
            }
        }

        // ── Main Flip Card ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (ui.mode) {
                FlashcardMode.FLIP_CARD -> FcFlipCard(card, ui.isFlipped, cardColor, glowPulse) { vm.flipCard() }
                FlashcardMode.MULTIPLE_CHOICE -> FcMcqView(card, ui, cardColor, glowPulse, vm)
                FlashcardMode.TYPE_ANSWER -> FcTypeView(card, ui, cardColor, glowPulse, vm)
            }
        }

        // ── Hint & Memory Hook ───────────────────────────────────────────────
        AnimatedVisibility(visible = !ui.isFlipped || ui.mode != FlashcardMode.FLIP_CARD) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FcActionButton(
                        icon = Icons.Default.Lightbulb,
                        label = "Hint (${4 - ui.hintLevel} left)",
                        color = NeonGold,
                        modifier = Modifier.weight(1f),
                        enabled = ui.hintLevel < 4
                    ) { vm.requestHint() }
                    FcActionButton(
                        icon = Icons.Default.Memory,
                        label = "Memory Hook",
                        color = NeonTeal,
                        modifier = Modifier.weight(1f)
                    ) { vm.requestMemoryHook() }
                }

                AnimatedVisibility(visible = ui.hintVisible) {
                    FcInfoPanel(ui.hintContent, NeonGold, Icons.Default.Lightbulb) { vm.dismissHint() }
                }
                AnimatedVisibility(visible = ui.memoryHookVisible) {
                    FcInfoPanel(ui.memoryHook, NeonTeal, Icons.Default.Memory) { vm.dismissMemoryHook() }
                }
            }
        }

        // ── Rating Buttons (shown after flip in FLIP mode) ───────────────────
        AnimatedVisibility(
            visible = ui.isFlipped && ui.mode == FlashcardMode.FLIP_CARD,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
        ) {
            FcRatingButtons(vm)
        }

        // ── Reveal prompt ────────────────────────────────────────────────────
        AnimatedVisibility(visible = !ui.isFlipped && ui.mode == FlashcardMode.FLIP_CARD) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tap the card to reveal",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(0.30f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── 3D Flip Card ──────────────────────────────────────────────────────────────

@Composable
private fun FcFlipCard(
    card: FlashcardCard,
    isFlipped: Boolean,
    cardColor: Color,
    glowPulse: Float,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "flip"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = cardColor.copy(glowPulse * 0.55f))
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            FcCardFront(card, cardColor, glowPulse)
        } else {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                FcCardBack(card, cardColor, glowPulse)
            }
        }
    }
}

@Composable
private fun FcCardFront(card: FlashcardCard, cardColor: Color, glowPulse: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF10183A), cardColor.copy(0.12f * glowPulse), Color(0xFF0A1028))
                )
            )
            .border(1.5.dp, Brush.linearGradient(listOf(cardColor.copy(0.7f * glowPulse), cardColor.copy(0.25f))), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Glow orbs
        Box(Modifier.size(120.dp).align(Alignment.TopEnd).offset(20.dp, (-20).dp)
            .background(Brush.radialGradient(listOf(cardColor.copy(0.12f * glowPulse), Color.Transparent)), CircleShape))
        Box(Modifier.size(80.dp).align(Alignment.BottomStart).offset((-15).dp, 15.dp)
            .background(Brush.radialGradient(listOf(cardColor.copy(0.08f), Color.Transparent)), CircleShape))

        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Label chip
            Box(
                modifier = Modifier
                    .background(cardColor.copy(0.18f), RoundedCornerShape(20.dp))
                    .border(0.5.dp, cardColor.copy(0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(card.frontLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = cardColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            // Front term
            Text(
                text = card.front,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )
            // Subject chip
            if (card.subject.isNotBlank() && card.subject != "GENERAL") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(cardColor, CircleShape))
                    Text(card.subject, style = MaterialTheme.typography.labelSmall, color = cardColor.copy(0.75f))
                    if (card.chapter.isNotBlank()) {
                        Text("· ${card.chapter.take(20)}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
                    }
                }
            }
            // Mastery indicator
            card.progress?.let { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val masteryColor = when {
                        p.intervalDays >= 21 -> NeonGreen
                        p.intervalDays >= 7  -> NeonGold
                        p.totalReviews == 0  -> Color.White.copy(0.3f)
                        else                 -> NeonOrange
                    }
                    Icon(Icons.Default.Star, null, tint = masteryColor, modifier = Modifier.size(12.dp))
                    Text(
                        if (p.totalReviews == 0) "New" else "×${p.totalReviews} · ${p.intervalDays}d",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = masteryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun FcCardBack(card: FlashcardCard, cardColor: Color, glowPulse: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D1830), cardColor.copy(0.18f * glowPulse), Color(0xFF0A1225))
                )
            )
            .border(1.5.dp, Brush.linearGradient(listOf(cardColor, cardColor.copy(0.4f))), RoundedCornerShape(28.dp))
    ) {
        Box(Modifier.size(100.dp).align(Alignment.BottomEnd).offset(20.dp, 20.dp)
            .background(Brush.radialGradient(listOf(cardColor.copy(0.14f * glowPulse), Color.Transparent)), CircleShape))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(cardColor.copy(0.22f), RoundedCornerShape(20.dp))
                    .border(0.5.dp, cardColor.copy(0.45f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("ANSWER", style = MaterialTheme.typography.labelSmall, color = cardColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            }

            Text(
                text = card.back,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp
            )

            if (card.backExtra.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(cardColor.copy(0.25f)))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = cardColor.copy(0.6f), modifier = Modifier.size(13.dp))
                    Text(card.backExtra, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.45f))
                }
            }

            if (card.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    card.tags.take(4).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(cardColor.copy(0.12f), RoundedCornerShape(8.dp))
                                .border(0.3.dp, cardColor.copy(0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("#$tag", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = cardColor.copy(0.75f))
                        }
                    }
                }
            }

            card.progress?.let { p ->
                val ai = FlashcardAI.predictMasteryDays(p)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = NeonPurple.copy(0.7f), modifier = Modifier.size(12.dp))
                    Text(ai, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = NeonPurple.copy(0.65f))
                }
            }
        }
    }
}

// ── MCQ View ──────────────────────────────────────────────────────────────────

@Composable
private fun FcMcqView(
    card: FlashcardCard,
    ui: FlashcardUiState,
    cardColor: Color,
    glowPulse: Float,
    vm: FlashcardViewModel
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mcq")
    val igp by infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), "igp")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // Term display card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = cardColor.copy(igp * 0.4f))
                .background(Brush.linearGradient(listOf(Color(0xFF10183A), cardColor.copy(0.12f), Color(0xFF0A1028))), RoundedCornerShape(24.dp))
                .border(1.5.dp, Brush.linearGradient(listOf(cardColor.copy(igp * 0.7f), cardColor.copy(0.25f))), RoundedCornerShape(24.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.background(cardColor.copy(0.18f), RoundedCornerShape(20.dp)).border(0.5.dp, cardColor.copy(0.4f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(card.frontLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = cardColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Text(card.front, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (card.subject.isNotBlank() && card.subject != "GENERAL") {
                    Text(card.subject, style = MaterialTheme.typography.labelSmall, color = cardColor.copy(0.7f))
                }
            }
        }

        // MCQ Options
        if (ui.mcqSelected == null) {
            Text("Choose the correct definition:", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.45f), modifier = Modifier.padding(horizontal = 4.dp))
            ui.mcqOptions.forEachIndexed { i, opt ->
                FcMcqOption(
                    letter = ('A' + i).toString(),
                    text = opt,
                    state = null,
                    color = cardColor
                ) { vm.submitMcqAnswer(opt) }
            }
        } else {
            // Show answer feedback
            ui.mcqOptions.forEachIndexed { i, opt ->
                val isCorrect = opt == card.back
                val isSelected = opt == ui.mcqSelected
                val state = when {
                    isCorrect -> "correct"
                    isSelected -> "wrong"
                    else -> "neutral"
                }
                FcMcqOption(letter = ('A' + i).toString(), text = opt, state = state, color = cardColor) {}
            }
            Spacer(Modifier.height(4.dp))
            val autoQuality = if (ui.mcqSelected == card.back) 2 else 0
            FcRatingButtons(vm, compact = true, autoQuality = autoQuality)
        }
    }
}

@Composable
private fun FcMcqOption(
    letter: String,
    text: String,
    state: String?,    // null=unselected, "correct", "wrong", "neutral"
    color: Color,
    onClick: () -> Unit
) {
    val bg = when (state) {
        "correct" -> NeonGreen.copy(0.18f)
        "wrong"   -> NeonRed.copy(0.18f)
        else      -> Color.White.copy(0.05f)
    }
    val border = when (state) {
        "correct" -> NeonGreen.copy(0.7f)
        "wrong"   -> NeonRed.copy(0.6f)
        else      -> Color.White.copy(0.10f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (state != null) 6.dp else 0.dp, RoundedCornerShape(14.dp), spotColor = border.copy(0.2f))
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = state == null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(color.copy(0.15f), CircleShape).border(0.5.dp, color.copy(0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (state == "correct") NeonGreen else if (state == "wrong") NeonRed else Color.White.copy(0.8f),
            fontWeight = if (state == "correct") FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (state == "correct") Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
        if (state == "wrong")   Icon(Icons.Default.Cancel,      null, tint = NeonRed,   modifier = Modifier.size(20.dp))
    }
}

// ── Type Answer View ──────────────────────────────────────────────────────────

@Composable
private fun FcTypeView(
    card: FlashcardCard,
    ui: FlashcardUiState,
    cardColor: Color,
    glowPulse: Float,
    vm: FlashcardViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // Definition displayed — type the term
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = cardColor.copy(0.35f))
                .background(Brush.linearGradient(listOf(Color(0xFF0D1830), cardColor.copy(0.10f))), RoundedCornerShape(24.dp))
                .border(1.dp, cardColor.copy(glowPulse * 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.background(cardColor.copy(0.18f), RoundedCornerShape(20.dp)).border(0.5.dp, cardColor.copy(0.4f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("WHAT IS THE TERM?", style = MaterialTheme.typography.labelSmall, color = cardColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Text(card.back, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.85f))
            }
        }

        // Input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (!ui.typeChecked) 10.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = cardColor.copy(0.35f))
                .background(Brush.linearGradient(listOf(Color(0xFF0F1830), Color(0xFF090F22))), RoundedCornerShape(16.dp))
                .border(
                    1.5.dp,
                    if (ui.typeCorrect == true) NeonGreen.copy(0.8f)
                    else if (ui.typeCorrect == false) NeonRed.copy(0.7f)
                    else cardColor.copy(glowPulse * 0.5f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Edit, null, tint = cardColor.copy(0.5f), modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = ui.typeInput,
                    onValueChange = vm::onTypeInputChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    cursorBrush = SolidColor(cardColor),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.checkTypeAnswer() }),
                    enabled = !ui.typeChecked,
                    decorationBox = { inner ->
                        if (ui.typeInput.isEmpty()) Text("Type the term here…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.25f))
                        inner()
                    }
                )
                if (ui.typeCorrect == true) Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                if (ui.typeCorrect == false) Icon(Icons.Default.Cancel, null, tint = NeonRed, modifier = Modifier.size(20.dp))
            }
        }

        if (!ui.typeChecked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(cardColor.copy(0.22f), cardColor.copy(0.12f))), RoundedCornerShape(14.dp))
                    .border(1.dp, cardColor.copy(0.5f), RoundedCornerShape(14.dp))
                    .clickable { vm.checkTypeAnswer() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Check Answer", style = MaterialTheme.typography.labelLarge, color = cardColor, fontWeight = FontWeight.ExtraBold)
            }
        } else {
            // Reveal correct answer if wrong
            if (ui.typeCorrect == false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonGreen.copy(0.10f), RoundedCornerShape(12.dp))
                        .border(1.dp, NeonGreen.copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Text("Correct: ${card.front}", style = MaterialTheme.typography.labelMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val autoQuality = if (ui.typeCorrect == true) 2 else 0
            FcRatingButtons(vm, compact = false, autoQuality = autoQuality)
        }
    }
}

// ── Rating Buttons ────────────────────────────────────────────────────────────

@Composable
private fun FcRatingButtons(
    vm: FlashcardViewModel,
    compact: Boolean = false,
    autoQuality: Int? = null
) {
    val ratings = listOf(
        Triple(0, "Again", NeonRed),
        Triple(1, "Hard",  NeonOrange),
        Triple(2, "Good",  NeonGreen),
        Triple(3, "Easy",  NeonCyan)
    )
    val intervals = listOf("< 1d", "1d", "3d", "7d+")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ratings.forEachIndexed { i, (quality, label, color) ->
            val isHighlighted = autoQuality == quality
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(if (isHighlighted) 12.dp else 4.dp, RoundedCornerShape(14.dp), spotColor = color.copy(if (isHighlighted) 0.55f else 0.2f))
                    .background(
                        if (isHighlighted) Brush.linearGradient(listOf(color.copy(0.35f), color.copy(0.18f)))
                        else Brush.linearGradient(listOf(color.copy(0.12f), color.copy(0.05f))),
                        RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, if (isHighlighted) color.copy(0.8f) else color.copy(0.30f), RoundedCornerShape(14.dp))
                    .clickable { vm.submitRating(quality) }
                    .padding(vertical = if (compact) 10.dp else 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.ExtraBold)
                    if (!compact) {
                        Text(intervals[i], style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color.copy(0.6f))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  RESULTS PHASE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FcResultsPhase(
    ui: FlashcardUiState,
    vm: FlashcardViewModel,
    navController: NavController
) {
    val results = ui.sessionResults
    val total = results.size
    val easy   = results.count { it.quality == 3 }
    val good   = results.count { it.quality == 2 }
    val hard   = results.count { it.quality == 1 }
    val missed = results.count { it.quality == 0 }
    val correct = easy + good
    val accuracy = if (total > 0) (correct * 100 / total) else 0
    val avgMs = if (total > 0) results.sumOf { it.responseTimeMs } / total else 0
    val elapsed = ((System.currentTimeMillis() - ui.sessionStartTime) / 1000).let { s -> "${s / 60}m ${s % 60}s" }

    val infiniteTransition = rememberInfiniteTransition(label = "fc_results")
    val glowPulse by infiniteTransition.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = NeonPink.copy(glowPulse * 0.5f))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF121C3A), NeonPink.copy(0.14f * glowPulse), Color(0xFF090F25))),
                    RoundedCornerShape(28.dp)
                )
                .border(1.5.dp, Brush.linearGradient(listOf(NeonPink.copy(glowPulse * 0.8f), NeonPurple.copy(0.4f))), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(70.dp)
                        .shadow(20.dp, CircleShape, spotColor = NeonPink.copy(glowPulse * 0.6f))
                        .background(Brush.radialGradient(listOf(NeonPink.copy(0.3f), NeonPurple.copy(0.15f), Color.Transparent)), CircleShape)
                        .border(1.5.dp, Brush.sweepGradient(listOf(NeonPink.copy(glowPulse * 0.9f), NeonPurple.copy(0.5f), NeonPink.copy(0.4f))), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(when {
                        accuracy >= 90 -> "🏆"
                        accuracy >= 70 -> "⭐"
                        accuracy >= 50 -> "💪"
                        else           -> "📖"
                    }, fontSize = 30.sp)
                }
                Text("Session Complete!", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FcResultStat("$total", "Cards", NeonCyan)
                    FcResultStat("$accuracy%", "Accuracy", if (accuracy >= 70) NeonGreen else NeonOrange)
                    FcResultStat(elapsed, "Time", NeonGold)
                    FcResultStat("${avgMs / 1000}s", "Avg/Card", NeonPurple)
                }
            }
        }

        // ── Breakdown ────────────────────────────────────────────────────────
        FcSetupSection("RESULT BREAKDOWN") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FcBreakdownRow("Easy", easy, total, NeonCyan, Icons.Default.CheckCircle)
                FcBreakdownRow("Good", good, total, NeonGreen, Icons.Default.ThumbUp)
                FcBreakdownRow("Hard", hard, total, NeonOrange, Icons.Default.Warning)
                FcBreakdownRow("Missed", missed, total, NeonRed, Icons.Default.Refresh)
            }
        }

        // ── AI Insights ──────────────────────────────────────────────────────
        FcSetupSection("AI INSIGHTS") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val insight1 = when {
                    accuracy >= 90 -> "Exceptional! Your memory retention is excellent. Next review in 7+ days."
                    accuracy >= 70 -> "Good progress! Focus on the ${missed + hard} harder cards in your next session."
                    accuracy >= 50 -> "Keep going! Consistent daily reviews will rapidly improve your recall."
                    else           -> "These cards need more attention. Try shorter sessions more frequently."
                }
                val insight2 = "Average response time: ${avgMs / 1000}s per card — " +
                    if (avgMs < 5000) "great recall speed!" else "try to reduce thinking time for mastery."
                val insight3 = "${ui.nextDueCount} cards due tomorrow — " +
                    if (ui.nextDueCount > 0) "schedule a quick ${(ui.nextDueCount * 30 / 60)}min session." else "no cards scheduled for tomorrow."

                listOf(insight1, insight2, insight3).forEach { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeonPurple.copy(0.08f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, NeonPurple.copy(0.22f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = NeonPurple.copy(0.7f), modifier = Modifier.size(14.dp))
                        Text(msg, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                    }
                }
            }
        }

        // ── Actions ──────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White.copy(0.07f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(0.14f), RoundedCornerShape(16.dp))
                    .clickable { navController.popBackStack() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                    Text("Done", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(0.7f), fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = NeonPink.copy(0.45f))
                    .background(Brush.linearGradient(listOf(NeonPink.copy(0.8f), NeonPurple.copy(0.75f))), RoundedCornerShape(16.dp))
                    .border(1.dp, NeonPink.copy(0.5f), RoundedCornerShape(16.dp))
                    .clickable { vm.startSession() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Replay, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Review Again", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SHARED HELPER COMPOSABLES
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FcSetupSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f), letterSpacing = 1.4.sp)
            Box(modifier = Modifier.weight(1f).height(0.5.dp).background(Color.White.copy(0.10f)))
        }
        content()
    }
}

@Composable
private fun FcSourceCard(label: String, count: String, icon: ImageVector, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .shadow(if (selected) 12.dp else 2.dp, RoundedCornerShape(18.dp), spotColor = color.copy(if (selected) 0.5f else 0f))
            .background(
                if (selected) Brush.linearGradient(listOf(color.copy(0.28f), color.copy(0.10f)))
                else Brush.linearGradient(listOf(Color.White.copy(0.05f), Color.White.copy(0.02f))),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, if (selected) color.copy(0.70f) else Color.White.copy(0.09f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(38.dp)
                    .background(color.copy(if (selected) 0.24f else 0.10f), RoundedCornerShape(12.dp))
                    .border(0.5.dp, color.copy(if (selected) 0.5f else 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (selected) color else Color.White.copy(0.40f), modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else Color.White.copy(0.55f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
            Text(count, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (selected) color else Color.White.copy(0.30f))
        }
    }
}

@Composable
private fun FcChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .shadow(if (selected) 6.dp else 0.dp, RoundedCornerShape(20.dp), spotColor = color.copy(0.4f))
            .background(
                if (selected) color.copy(0.22f) else Color.White.copy(0.05f),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, if (selected) color.copy(0.65f) else Color.White.copy(0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) color else Color.White.copy(0.50f), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

@Composable
private fun FcActionButton(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(14.dp), spotColor = color.copy(0.3f))
            .background(color.copy(if (enabled) 0.12f else 0.05f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(if (enabled) 0.35f else 0.12f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (enabled) color else color.copy(0.3f), modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) color else color.copy(0.35f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FcInfoPanel(content: String, color: Color, icon: ImageVector, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(0.30f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(content, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.80f), modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(20.dp).background(Color.White.copy(0.08f), CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun FcStatChip(icon: ImageVector, value: String, color: Color, label: String) {
    Row(
        modifier = Modifier
            .background(color.copy(0.10f), RoundedCornerShape(10.dp))
            .border(0.5.dp, color.copy(0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FcResultStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.42f))
    }
}

@Composable
private fun FcBreakdownRow(label: String, count: Int, total: Int, color: Color, icon: ImageVector) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f), modifier = Modifier.width(56.dp))
        Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.07f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color, RoundedCornerShape(4.dp)))
        }
        Text("$count", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
    }
}

// ── Pure helpers ──────────────────────────────────────────────────────────────

private fun subjectColor(subject: String): Color = when (subject.uppercase()) {
    "PHYSICS"   -> NeonCyan
    "CHEMISTRY" -> NeonOrange
    "BOTANY"    -> NeonGreen
    "ZOOLOGY"   -> NeonPurple
    else        -> NeonGold
}

private fun FlashcardSource.color(): Color = when (this) {
    FlashcardSource.NEET_TERMS     -> NeonCyan
    FlashcardSource.NON_NEET_WORDS -> NeonIndigo
    FlashcardSource.MNEMONICS      -> NeonPurple
    FlashcardSource.MIXED          -> NeonGold
    FlashcardSource.DUE_ONLY       -> NeonOrange
}

@Suppress("DEPRECATION")
private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
