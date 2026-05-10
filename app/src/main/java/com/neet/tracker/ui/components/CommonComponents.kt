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
        List(110) {
            Star(
                x      = Random.nextFloat(),
                y      = Random.nextFloat(),
                radius = Random.nextFloat() * 2.2f + 0.3f,
                alpha  = Random.nextFloat() * 0.8f + 0.15f,
                speed  = Random.nextFloat() * 1800f + 1200f
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
        initialValue = 0.07f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula1"
    )
    val nebula2 by infiniteTransition.animateFloat(
        initialValue = 0.05f, targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(7000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula2"
    )
    val nebula3 by infiniteTransition.animateFloat(
        initialValue = 0.04f, targetValue = 0.10f,
        animationSpec = infiniteRepeatable(tween(9000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "nebula3"
    )
    val gridAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f, targetValue = 0.09f,
        animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "grid_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0C1A38), Color(0xFF040913)),
                    center = Offset(320f, 180f),
                    radius = 1800f
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Nebula 1 — purple top-left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonPurple.copy(alpha = nebula1), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.18f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.12f, size.height * 0.18f)
            )
            // Nebula 2 — cyan bottom-right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = nebula2), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.78f),
                    radius = size.width * 0.45f
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.88f, size.height * 0.78f)
            )
            // Nebula 3 — gold mid
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonGold.copy(alpha = nebula3 * 0.6f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.width * 0.35f
                ),
                radius = size.width * 0.35f,
                center = Offset(size.width * 0.5f, size.height * 0.5f)
            )

            // Perspective grid — vertical lines
            val gridH = 72.dp.toPx()
            val gridV = 72.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = gridAlpha),
                    start = Offset(x, 0f),
                    end   = Offset(x, size.height),
                    strokeWidth = 0.6f
                )
                x += gridH
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = gridAlpha * 0.7f),
                    start = Offset(0f, y),
                    end   = Offset(size.width, y),
                    strokeWidth = 0.4f
                )
                y += gridV
            }

            // Twinkling stars
            stars.forEach { star ->
                val twinkle = (sin(starPhase * Math.PI.toFloat() * 2 + star.speed) * 0.45f + 0.55f).toFloat()
                // Core
                drawCircle(
                    color = Color.White.copy(alpha = star.alpha * twinkle),
                    radius = star.radius,
                    center = Offset(star.x * size.width, star.y * size.height)
                )
                // Glow halo on brighter stars
                if (star.radius > 1.5f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = star.alpha * twinkle * 0.3f), Color.Transparent),
                            center = Offset(star.x * size.width, star.y * size.height),
                            radius = star.radius * 3.5f
                        ),
                        radius = star.radius * 3.5f,
                        center = Offset(star.x * size.width, star.y * size.height)
                    )
                }
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
        initialValue = 0.20f, targetValue = 0.48f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.5f, 320f), label = "gc_scale")
    val shape = RoundedCornerShape(22.dp)

    val baseModifier = modifier
        .scale(scale)
        .let { if (elevation) it.shadow(16.dp, shape, spotColor = glowColor.copy(0.30f), ambientColor = glowColor.copy(0.12f)) else it }
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF111E3A).copy(alpha = 0.97f),
                    glowColor.copy(alpha = 0.07f),
                    Color(0xFF060E1C).copy(alpha = 0.99f)
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    glowColor.copy(alpha = glowAlpha),
                    Color.White.copy(alpha = 0.04f),
                    glowColor.copy(alpha = glowAlpha * 0.35f)
                )
            ),
            shape = shape
        )

    Box(modifier = baseModifier
        .let {
            if (onClick != null) it.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() } else it
        }
    ) {
        // ── Top-left light source highlight (3D lighting simulation)
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(0.10f), Color.Transparent)),
                    shape
                )
        )
        // ── Diagonal shine stripe (glass reflection)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(0f),
                            Color.White.copy(0.05f),
                            Color.White.copy(0.09f),
                            Color.White.copy(0.05f),
                            Color.White.copy(0f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(300f, 60f)
                    )
                )
        )
        // ── Bottom depth gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.25f)))
                )
        )
        Column(modifier = Modifier.fillMaxWidth(), content = content)
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
        initialValue = 0.18f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween((1800..2800).random(), easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.55f, 300f), label = "press_scale")
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .shadow(14.dp, shape, spotColor = statusGlow.copy(0.38f), ambientColor = statusGlow.copy(0.12f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF111E3A),
                        statusGlow.copy(0.14f),
                        Color(0xFF060D1B),
                        statusGlow.copy(0.06f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.24f),
                        statusGlow.copy(glowPulse + 0.12f),
                        Color.White.copy(0.04f),
                        statusGlow.copy(glowPulse * 0.28f)
                    )
                ),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // ── Top-left 3D light source
        Box(
            modifier = Modifier.size(100.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(Color.White.copy(0.13f), Color.Transparent)), shape)
        )
        // ── Colored corner glow orb
        Box(
            modifier = Modifier.size(96.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(statusGlow.copy(glowPulse * 0.85f), Color.Transparent)), shape)
        )
        // ── Diagonal shine (glass reflection)
        Box(
            modifier = Modifier.fillMaxWidth().height(70.dp).align(Alignment.TopCenter)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(0f),
                            Color.White.copy(0.06f),
                            Color.White.copy(0.11f),
                            Color.White.copy(0.04f),
                            Color.White.copy(0f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(250f, 70f)
                    )
                )
        )
        // ── Bottom-right accent glow
        Box(
            modifier = Modifier.size(60.dp).align(Alignment.BottomEnd)
                .background(Brush.radialGradient(listOf(statusGlow.copy(glowPulse * 0.4f), Color.Transparent)), shape)
        )
        // ── Bottom depth
        Box(
            modifier = Modifier.fillMaxWidth().height(45.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.30f))))
        )

        // Status dot top-right
        if (status != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd).padding(10.dp)
                    .size(11.dp)
                    .shadow(8.dp, CircleShape, spotColor = statusGlow)
                    .background(statusGlow, CircleShape)
            )
        }
        // Count badge top-left
        if (count >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart).padding(8.dp)
                    .background(statusGlow.copy(0.22f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, statusGlow.copy(0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = statusGlow, fontWeight = FontWeight.ExtraBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .let { if (bottomContent != null) it.padding(bottom = 38.dp) else it },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                ThreeDIconBox(icon = icon, tint = statusGlow, size = 50.dp, iconSize = 27.dp)
                Spacer(Modifier.height(9.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.ExtraBold
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
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, statusGlow.copy(0.35f), statusGlow.copy(0.35f), Color.Transparent)))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = bottomContent
                )
            }
        }
    }
}

// ─── 3D Icon Box (reusable thematic icon container) ──────────────────────────

@Composable
fun ThreeDIconBox(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 28.dp
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(12.dp, shape, spotColor = tint.copy(0.55f), ambientColor = tint.copy(0.20f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(0.32f),
                        tint.copy(0.12f),
                        tint.copy(0.22f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.35f),
                        tint.copy(0.60f),
                        tint.copy(0.25f)
                    )
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Top-left highlight (3D raised look)
        Box(
            modifier = Modifier.size(size * 0.6f).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(Color.White.copy(0.22f), Color.Transparent)), shape)
        )
        // Bottom-right shadow (3D depth)
        Box(
            modifier = Modifier.size(size * 0.5f).align(Alignment.BottomEnd)
                .background(Brush.radialGradient(listOf(Color.Black.copy(0.25f), Color.Transparent)), shape)
        )
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
        // Tiny top-right reflection dot
        Box(
            modifier = Modifier.size(6.dp).align(Alignment.TopEnd).offset((-4).dp, 4.dp)
                .background(Color.White.copy(0.45f), CircleShape)
        )
    }
}

// ─── Card Icon Button ─────────────────────────────────────────────────────────

@Composable
fun CardIconButton(icon: ImageVector, tint: Color = NeonCyan.copy(0.8f), onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.78f else 1f, spring(0.5f, 400f), label = "icon_press")
    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(scale)
            .shadow(4.dp, RoundedCornerShape(9.dp), spotColor = tint.copy(0.3f))
            .background(
                Brush.linearGradient(listOf(tint.copy(0.14f), tint.copy(0.06f))),
                RoundedCornerShape(9.dp)
            )
            .border(0.5.dp,
                Brush.linearGradient(listOf(Color.White.copy(0.18f), tint.copy(0.30f))),
                RoundedCornerShape(9.dp)
            )
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
            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = color.copy(0.3f))
            .background(
                Brush.linearGradient(listOf(color.copy(0.20f), color.copy(0.08f))),
                RoundedCornerShape(12.dp)
            )
            .border(0.5.dp,
                Brush.linearGradient(listOf(Color.White.copy(0.20f), color.copy(0.50f))),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
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
            .shadow(8.dp, RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07112A).copy(0.99f),
                        Color(0xFF050D1E).copy(0.96f),
                        Color.Transparent
                    )
                )
            )
    ) {
        // Top-left corner highlight
        Box(
            modifier = Modifier.size(200.dp).align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(listOf(NeonCyan.copy(0.07f), Color.Transparent)),
                    RoundedCornerShape(0.dp)
                )
        )
        // Shine stripe across top
        Box(
            modifier = Modifier.fillMaxWidth().height(1.5.dp).align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(0.14f), NeonCyan.copy(0.22f), Color.White.copy(0.14f), Color.Transparent)
                    )
                )
        )
        TopAppBar(
            title = {
                Column {
                    if (breadcrumb.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            breadcrumb.split(" / ").forEachIndexed { i, crumb ->
                                if (i > 0) Text("›", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.30f))
                                Text(crumb, style = MaterialTheme.typography.labelSmall, color = if (i == breadcrumb.split(" / ").lastIndex) NeonCyan.copy(0.95f) else Color.White.copy(0.40f))
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
                            .size(40.dp)
                            .shadow(6.dp, RoundedCornerShape(13.dp), spotColor = NeonCyan.copy(0.3f))
                            .background(
                                Brush.linearGradient(listOf(NeonCyan.copy(0.16f), NeonCyan.copy(0.06f))),
                                RoundedCornerShape(13.dp)
                            )
                            .border(
                                0.5.dp,
                                Brush.linearGradient(listOf(Color.White.copy(0.25f), NeonCyan.copy(0.40f))),
                                RoundedCornerShape(13.dp)
                            )
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBackIosNew, null, tint = NeonCyan, modifier = Modifier.size(17.dp))
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
    val borderAlpha by animateFloatAsState(if (focused.value) 0.80f else 0.28f, label = "focus_border")
    val glowAlpha by animateFloatAsState(if (focused.value) 0.25f else 0.08f, label = "focus_glow")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (focused.value) 10.dp else 5.dp, shape, spotColor = NeonCyan.copy(glowAlpha + 0.05f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF111F3C).copy(0.98f),
                        NeonCyan.copy(if (focused.value) 0.06f else 0.02f),
                        Color(0xFF070E1C).copy(0.99f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(0.22f), NeonCyan.copy(borderAlpha), Color.White.copy(0.06f))
                ),
                shape
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp)
                .background(NeonCyan.copy(0.12f), RoundedCornerShape(8.dp))
                .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Search, null, tint = NeonCyan.copy(0.9f), modifier = Modifier.size(16.dp))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = ExoFont),
            decorationBox = { inner ->
                if (query.isEmpty()) Text(placeholder, color = Color.White.copy(0.30f), fontSize = 14.sp, fontFamily = ExoFont)
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
            .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = color.copy(0.25f))
            .background(
                Brush.linearGradient(listOf(color.copy(if (selected) 0.25f else 0.12f), color.copy(if (selected) 0.12f else 0.06f))),
                RoundedCornerShape(20.dp)
            )
            .border(0.5.dp, Brush.linearGradient(listOf(Color.White.copy(0.2f), color.copy(0.5f))), RoundedCornerShape(20.dp))
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
        initialValue = 0.25f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseOut), RepeatMode.Restart),
        label = "fab_pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseOut), RepeatMode.Restart),
        label = "fab_pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "fab_glow"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        Box(modifier = Modifier.size(56.dp).scale(pulseScale).background(color.copy(pulseAlpha), CircleShape))
        // Secondary pulse
        Box(modifier = Modifier.size(56.dp).scale(pulseScale * 0.7f).background(color.copy(pulseAlpha * 0.5f), CircleShape))
        FloatingActionButton(
            onClick = onClick,
            containerColor = Color.Transparent,
            contentColor = color,
            modifier = Modifier
                .shadow(20.dp, CircleShape, spotColor = color.copy(glowAlpha))
                .background(
                    Brush.linearGradient(
                        listOf(color.copy(0.45f), color.copy(0.18f), color.copy(0.32f)),
                        start = Offset(0f, 0f),
                        end = Offset(100f, 100f)
                    ),
                    CircleShape
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.45f), color.copy(0.80f))),
                    CircleShape
                )
        ) {
            // Shine on FAB
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.size(28.dp).align(Alignment.TopStart).offset(4.dp, 4.dp)
                        .background(Brush.radialGradient(listOf(Color.White.copy(0.20f), Color.Transparent)), CircleShape)
                )
                Icon(icon, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(26.dp))
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(message: String, icon: ImageVector = Icons.Outlined.FolderOff) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(16.dp, CircleShape, spotColor = NeonCyan.copy(0.25f))
                .background(
                    Brush.radialGradient(listOf(NeonCyan.copy(0.16f), NeonCyan.copy(0.04f))),
                    CircleShape
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.22f), NeonCyan.copy(0.40f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(28.dp).align(Alignment.TopStart).offset(10.dp, 10.dp)
                    .background(Brush.radialGradient(listOf(Color.White.copy(0.20f), Color.Transparent)), CircleShape)
            )
            Icon(icon, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(40.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.35f),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, color: Color = NeonCyan) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp).height(22.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(0.5f), color, Color.Transparent)),
                    RoundedCornerShape(2.dp)
                )
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.ExtraBold)
    }
}
