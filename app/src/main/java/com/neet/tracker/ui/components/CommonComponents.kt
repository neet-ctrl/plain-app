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

// ─── Glowing background ───────────────────────────────────────────────────────

@Composable
fun SpaceBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1B3E), DeepNavy),
                    center = Offset(0.3f, 0.2f),
                    radius = 1200f
                )
            )
    ) {
        // Subtle grid lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 80.dp.toPx()
            val lineColor = Color(0x0AFFFFFF)
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 0.5f)
                x += gridSpacing
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 0.5f)
                y += gridSpacing
            }
        }
        content()
    }
}

// ─── Glass Card ──────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glowColor: Color = NeonCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )

    val baseModifier = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(
            Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.08f),
                    Color(0xFF0A1628).copy(alpha = 0.95f),
                    glowColor.copy(alpha = 0.04f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = glowAlpha),
                    Color.White.copy(alpha = 0.05f),
                    glowColor.copy(alpha = glowAlpha * 0.5f)
                )
            ),
            shape = RoundedCornerShape(20.dp)
        )

    if (onClick != null) {
        Column(
            modifier = baseModifier.clickable(onClick = onClick),
            content = content
        )
    } else {
        Column(modifier = baseModifier, content = content)
    }
}

// ─── NEETCard (main rounded-square card) ─────────────────────────────────────

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
        Status.EXPECTED -> StatusExpected
        Status.REVISION -> StatusRevision
        Status.CROSSED -> StatusCross
        null -> glowColor
    }

    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }

    GlassCard(
        modifier = modifier.aspectRatio(1f),
        onClick = onClick,
        glowColor = statusGlow
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Status glow dot top-right
            if (status != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(10.dp)
                        .background(statusGlow, CircleShape)
                        .shadow(8.dp, CircleShape, spotColor = statusGlow)
                )
            }

            // Count badge top-left
            if (count >= 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            glowColor.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = glowColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // Bottom strip with icons
            if (bottomContent != null) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 0.5.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        content = bottomContent
                    )
                }
            }
        }
    }
}

// ─── Icon Action Button ───────────────────────────────────────────────────────

@Composable
fun CardIconButton(
    icon: ImageVector,
    tint: Color = NeonCyan.copy(alpha = 0.8f),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ─── Status Badge ─────────────────────────────────────────────────────────────

@Composable
fun StatusBadge(status: Status, modifier: Modifier = Modifier) {
    val (color, label, icon) = when (status) {
        Status.COMPLETED -> Triple(StatusCompleted, "Done", Icons.Default.CheckCircle)
        Status.EXPECTED -> Triple(StatusExpected, "Expected", Icons.Default.RadioButtonUnchecked)
        Status.REVISION -> Triple(StatusRevision, "Revision", Icons.Default.Refresh)
        Status.CROSSED -> Triple(StatusCross, "Crossed", Icons.Default.Cancel)
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ─── Top App Bar with breadcrumb ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NEETTopBar(
    title: String,
    breadcrumb: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                if (breadcrumb.isNotBlank()) {
                    Text(
                        text = breadcrumb,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = NeonCyan
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = NeonCyan
        )
    )
}

// ─── 3D Search Bar ────────────────────────────────────────────────────────────

@Composable
fun NeetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.08f),
                        Color(0xFF0A1628).copy(alpha = 0.9f)
                    )
                )
            )
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = ExoFont
            ),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp, fontFamily = ExoFont)
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") }
            )
        }
    }
}

// ─── Tag Chip ─────────────────────────────────────────────────────────────────

@Composable
fun TagChip(tag: String, selected: Boolean = false, onRemove: (() -> Unit)? = null) {
    val color = if (selected) NeonCyan else NeonPurple
    Row(
        modifier = Modifier
            .background(color.copy(alpha = if (selected) 0.2f else 0.1f), RoundedCornerShape(20.dp))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "# $tag",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
        if (onRemove != null) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = color,
                modifier = Modifier.size(12.dp).clickable { onRemove() }
            )
        }
    }
}

// ─── Neon Divider ─────────────────────────────────────────────────────────────

@Composable
fun NeonDivider(color: Color = NeonCyan, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.6f),
                        color,
                        color.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
    )
}

// ─── FAB ──────────────────────────────────────────────────────────────────────

@Composable
fun NeonFAB(onClick: () -> Unit, icon: ImageVector = Icons.Default.Add, color: Color = NeonCyan) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color.Transparent,
        contentColor = color,
        modifier = Modifier
            .background(
                Brush.radialGradient(colors = listOf(color.copy(0.3f), color.copy(0.05f))),
                CircleShape
            )
            .border(1.dp, color.copy(0.6f), CircleShape)
    ) {
        Icon(icon, contentDescription = "Add", tint = color)
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
        Icon(icon, contentDescription = null, tint = NeonCyan.copy(0.3f), modifier = Modifier.size(64.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.4f), textAlign = TextAlign.Center)
    }
}
