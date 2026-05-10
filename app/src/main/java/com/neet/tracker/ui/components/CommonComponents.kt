package com.neet.tracker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.neet.tracker.data.models.Status
import com.neet.tracker.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─── Animated Star Particle System ───────────────────────────────────────────

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float, val speed: Float)

@Composable
fun SpaceBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val stars = remember {
        List(80) {
            Star(
                x      = Random.nextFloat(),
                y      = Random.nextFloat(),
                radius = Random.nextFloat() * 1.8f + 0.4f,
                alpha  = Random.nextFloat() * 0.7f + 0.15f,
                speed  = Random.nextFloat() * 1500f + 1500f
            )
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val starPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "star_phase"
    )
    val nebula1 by infiniteTransition.animateFloat(
        initialValue = 0.06f, targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula1"
    )
    val nebula2 by infiniteTransition.animateFloat(
        initialValue = 0.04f, targetValue = 0.10f,
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A1530), Color(0xFF050B16)),
                    center = Offset(300f, 200f),
                    radius = 1600f
                )
            )
    ) {
        // Nebula glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonPurple.copy(alpha = nebula1), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.2f),
                    radius = size.width * 0.45f
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.15f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = nebula2), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.75f),
                    radius = size.width * 0.4f
                ),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.85f, size.height * 0.75f)
            )
            // Grid lines
            val gridSpacing = 88.dp.toPx()
            val lineColor = Color(0x08FFFFFF)
            var x = 0f
            while (x < size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 0.5f); x += gridSpacing }
            var y = 0f
            while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 0.5f); y += gridSpacing }
            // Stars
            stars.forEach { star ->
                val twinkle = (sin(starPhase * Math.PI.toFloat() * 2 + star.speed) * 0.4f + 0.6f).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = star.alpha * twinkle),
                    radius = star.radius,
                    center = Offset(star.x * size.width, star.y * size.height)
                )
            }
        }
        content()
    }
}

// ─── 3D Glass Card ────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glowColor: Color = NeonCyan,
    elevation: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val scale = remember { Animatable(1f) }

    val baseModifier = modifier
        .let { if (elevation) it.shadow(12.dp, RoundedCornerShape(22.dp), spotColor = glowColor.copy(0.25f), ambientColor = glowColor.copy(0.1f)) else it }
        .clip(RoundedCornerShape(22.dp))
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0E1E38).copy(alpha = 0.96f),
                    glowColor.copy(alpha = 0.06f),
                    Color(0xFF060E1C).copy(alpha = 0.98f)
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = glowAlpha),
                    Color.White.copy(alpha = 0.06f),
                    glowColor.copy(alpha = glowAlpha * 0.4f),
                    Color.White.copy(alpha = 0.03f)
                )
            ),
            shape = RoundedCornerShape(22.dp)
        )
        .scale(scale.value)

    if (onClick != null) {
        Column(
            modifier = baseModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            },
            content = content
        )
    } else {
        Column(modifier = baseModifier, content = content)
    }
}

// ─── 3D NEETCard (rounded square) ────────────────────────────────────────────

@Composable
fun NEETCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String = "",
    count: Int = -1,
    icon: ImageVector? = null,
    status: Status? = null,
    glowColor: Color = NeonCyan,
    onClick: () -> Unit,
    bottomContent: (@Composable RowScope.() -> Unit)? = null
) {
    val statusGlow = when (status) {
        Status.COMPLETED -> StatusCompleted
        Status.EXPECTED  -> StatusExpected
        Status.REVISION  -> StatusRevision
        Status.CROSSED   -> StatusCross
        null             -> glowColor
    }
    val infiniteTransition = rememberInfiniteTransition(label = "neet_card")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween((1800..2800).random(), easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.6f, 300f), label = "press_scale")

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .shadow(10.dp, RoundedCornerShape(22.dp), spotColor = statusGlow.copy(0.3f), ambientColor = statusGlow.copy(0.08f))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        statusGlow.copy(0.12f),
                        Color(0xFF080F20),
                        statusGlow.copy(0.05f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(statusGlow.copy(glowPulse + 0.1f), Color.White.copy(0.04f), statusGlow.copy(glowPulse * 0.3f))),
                RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Corner radial glow
        Box(
            modifier = Modifier
                .size(90.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(listOf(statusGlow.copy(glowPulse * 0.7f), Color.Transparent)),
                    RoundedCornerShape(22.dp)
                )
        )

        // Status dot top-right
        if (status != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(10.dp)
                    .shadow(6.dp, CircleShape, spotColor = statusGlow)
                    .background(statusGlow, CircleShape)
            )
        }

        // Count badge top-left
        if (count >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(statusGlow.copy(0.22f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, statusGlow.copy(0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = statusGlow, fontWeight = FontWeight.ExtraBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .let { if (bottomContent != null) it.padding(bottom = 36.dp) else it },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(statusGlow.copy(0.16f), RoundedCornerShape(14.dp))
                        .border(1.dp, statusGlow.copy(0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = statusGlow, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.55f),
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        // Bottom strip
        if (bottomContent != null) {
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(0.5.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, statusGlow.copy(0.25f), Color.Transparent)))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = bottomContent
                )
            }
        }
    }
}

// ─── Card Icon Button ─────────────────────────────────────────────────────────

@Composable
fun CardIconButton(icon: ImageVector, tint: Color = NeonCyan.copy(0.8f), onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.8f else 1f, spring(0.5f, 400f), label = "icon_press")
    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(scale)
            .background(tint.copy(0.08f), RoundedCornerShape(8.dp))
            .border(0.5.dp, tint.copy(0.25f), RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

// ─── Status Badge ─────────────────────────────────────────────────────────────

@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier) {
    val (color, label, icon) = when (status) {
        Status.COMPLETED -> Triple(StatusCompleted, "Done",     Icons.Default.CheckCircle)
        Status.EXPECTED  -> Triple(StatusExpected,  "Expected", Icons.Default.RadioButtonUnchecked)
        Status.REVISION  -> Triple(StatusRevision,  "Revision", Icons.Default.Refresh)
        Status.CROSSED   -> Triple(StatusCross,     "Crossed",  Icons.Default.Cancel)
    }
    Row(
        modifier = modifier
            .background(color.copy(0.15f), RoundedCornerShape(12.dp))
            .border(0.5.dp, color.copy(0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ─── Top App Bar ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NEETTopBar(
    title: String,
    breadcrumb: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF060E1E).copy(0.98f), Color.Transparent)
                )
            )
    ) {
        TopAppBar(
            title = {
                Column {
                    if (breadcrumb.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            breadcrumb.split(" / ").forEachIndexed { i, crumb ->
                                if (i > 0) Text("›", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.3f))
                                Text(crumb, style = MaterialTheme.typography.labelSmall, color = if (i == breadcrumb.split(" / ").lastIndex) NeonCyan.copy(0.9f) else Color.White.copy(0.4f))
                            }
                        }
                    }
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold)
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                            .background(NeonCyan.copy(0.1f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBackIosNew, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
        )
    }
}

// ─── 3D Search Bar ────────────────────────────────────────────────────────────

@Composable
fun NeatSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search...") {
    val focused = remember { mutableStateOf(false) }
    val borderAlpha by animateFloatAsState(if (focused.value) 0.7f else 0.25f, label = "focus_border")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = NeonCyan.copy(0.15f))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors = listOf(Color(0xFF0E1E38).copy(0.97f), Color(0xFF070E1C).copy(0.99f))))
            .border(1.dp, NeonCyan.copy(borderAlpha), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Search, null, tint = NeonCyan.copy(0.7f), modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
            decorationBox = { inner ->
                if (query.isEmpty()) Text(placeholder, color = Color.White.copy(0.3f), fontSize = 14.sp, fontFamily = ExoFont)
                inner()
            }
        )
        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.45f), modifier = Modifier.size(18.dp).clickable { onQueryChange("") })
        }
    }
}

// ─── Tag Chip ─────────────────────────────────────────────────────────────────

@Composable
fun TagChip(tag: String, selected: Boolean = false, onRemove: (() -> Unit)? = null) {
    val color = if (selected) NeonCyan else NeonPurple
    Row(
        modifier = Modifier
            .background(color.copy(if (selected) 0.22f else 0.1f), RoundedCornerShape(20.dp))
            .border(0.5.dp, color.copy(0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("# $tag", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        if (onRemove != null) {
            Icon(Icons.Default.Close, null, tint = color, modifier = Modifier.size(11.dp).clickable { onRemove() })
        }
    }
}

// ─── Neon Divider ─────────────────────────────────────────────────────────────

@Composable
fun NeonDivider(color: Color = NeonCyan, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(1.dp)
            .background(Brush.horizontalGradient(colors = listOf(Color.Transparent, color.copy(0.5f), color, color.copy(0.5f), Color.Transparent)))
    )
}

// ─── Animated Neon FAB ────────────────────────────────────────────────────────

@Composable
fun NeonFAB(onClick: () -> Unit, icon: ImageVector = Icons.Default.Add, color: Color = NeonCyan) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseOut), RepeatMode.Restart),
        label = "fab_pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseOut), RepeatMode.Restart),
        label = "fab_pulse_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(56.dp).scale(pulseScale).background(color.copy(pulseAlpha), CircleShape))
        FloatingActionButton(
            onClick = onClick,
            containerColor = Color.Transparent,
            contentColor = color,
            modifier = Modifier
                .shadow(16.dp, CircleShape, spotColor = color.copy(0.5f))
                .background(Brush.radialGradient(listOf(color.copy(0.35f), color.copy(0.1f))), CircleShape)
                .border(1.dp, color.copy(0.7f), CircleShape)
        ) {
            Icon(icon, null, tint = color)
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(message: String, icon: ImageVector = Icons.Outlined.FolderOff) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(96.dp)
                .background(NeonCyan.copy(0.07f), CircleShape)
                .border(1.dp, NeonCyan.copy(0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = NeonCyan.copy(0.4f), modifier = Modifier.size(48.dp))
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.4f), textAlign = TextAlign.Center)
    }
}

