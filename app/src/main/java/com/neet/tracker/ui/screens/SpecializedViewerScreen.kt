package com.neet.tracker.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.DialogTextField
import com.neet.tracker.ui.dialogs.NEETDialog
import com.neet.tracker.ui.dialogs.RichTextToolbar
import com.neet.tracker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SV_STATUS_MARKS = listOf("✅","❌","⏳","🔄","⭐","❗","❓","💡","🎯","🔥")
private val SV_QUICK_EMOJIS = listOf(
    "😊","😔","💪","🔥","📚","✅","❌","🎯","🌟","💡","⚡","🧠","📝","🏆","😴",
    "🩺","⚗️","🔬","🌿","🐾","💊","🫀","🫁","🦷","👁️","🦴","🧬","📊","📈","📉",
    "🤔","💯","🎉","📖","⏰","🔔","📌","🔑","💎","🚀"
)
private val SV_PAGE_MARKS = listOf(
    "✅" to "Got It",
    "❓" to "Review",
    "⭐" to "Important",
    "❌" to "Skip",
    "🔥" to "Key Page",
    "💡" to "Insight"
)

// ═══════════════════════════════════════════════════════════════════════════════
//  1 · DIAGRAM VIEWER  — per-page labels + global annotations
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun DiagramViewerScreen(
    navController: NavController,
    subject: String,
    fileUri: String,
    title: String
) {
    val context = LocalContext.current
    val uri = remember(fileUri) { runCatching { Uri.parse(fileUri) }.getOrNull() }
    val accentColor = if (subject == "BOTANY") NeonGreen else NeonOrange
    val subjectIcon = if (subject == "BOTANY") Icons.Default.Park else Icons.Default.Pets

    val prefs = remember { context.getSharedPreferences("diagram_viewer", Context.MODE_PRIVATE) }
    val pdfPages = remember { mutableStateListOf<Bitmap>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var totalPages by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showThumbs by remember { mutableStateOf(false) }
    var showLabelEditor by remember { mutableStateOf(false) }

    var scale by remember(currentPage) { mutableStateOf(1f) }
    var panOffset by remember(currentPage) { mutableStateOf(Offset.Zero) }

    val globalNoteKey = remember(fileUri) { "note_${fileUri.hashCode()}" }
    var globalNote by remember(globalNoteKey) { mutableStateOf(prefs.getString(globalNoteKey, "") ?: "") }
    LaunchedEffect(globalNote) { prefs.edit().putString(globalNoteKey, globalNote).apply() }

    val labelKey = remember(fileUri, currentPage) { "label_${fileUri.hashCode()}_$currentPage" }
    var pageLabel by remember(labelKey) { mutableStateOf(prefs.getString(labelKey, "") ?: "") }
    LaunchedEffect(pageLabel) { prefs.edit().putString(labelKey, pageLabel).apply() }

    LaunchedEffect(fileUri) {
        if (uri != null) {
            loading = true; error = false
            withContext(Dispatchers.IO) {
                try {
                    val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        for (i in 0 until renderer.pageCount.coerceAtMost(100)) {
                            val page = renderer.openPage(i)
                            val w = 1080; val h = (w * page.height.toFloat() / page.width).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close(); pdfPages.add(bmp)
                        }
                        renderer.close(); pfd.close()
                    } else error = true
                } catch (e: Exception) { error = true }
            }
            loading = false
        }
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            NEETTopBar(
                title = title,
                breadcrumb = if (pdfPages.isNotEmpty()) "$subject  ·  Diagram ${currentPage + 1}/${totalPages}" else "$subject Diagrams",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { showThumbs = !showThumbs }) {
                        Icon(Icons.Default.GridView, null,
                            tint = if (showThumbs) accentColor else Color.White.copy(0.55f),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showLabelEditor = !showLabelEditor }) {
                        Icon(Icons.Default.Label, null,
                            tint = if (showLabelEditor) NeonGold else Color.White.copy(0.55f),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showAnnotations = !showAnnotations }) {
                        Icon(Icons.Default.EditNote, null,
                            tint = if (showAnnotations) NeonGold else Color.White.copy(0.55f),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        uri?.let {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, it)
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
                        }
                    }) { Icon(Icons.Default.Share, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                }
            )

            // Subject banner
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(accentColor.copy(0.18f), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(subjectIcon, null, tint = accentColor, modifier = Modifier.size(16.dp))
                Text("$subject Diagrams Atlas", style = MaterialTheme.typography.labelMedium,
                    color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (pdfPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(accentColor.copy(0.15f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, accentColor.copy(0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("$totalPages pages", style = MaterialTheme.typography.labelSmall, color = accentColor) }
                }
            }

            // Main page content
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> SvLoadingView(accentColor)
                    error || uri == null -> SvErrorView(fileUri, uri, context, accentColor)
                    pdfPages.isNotEmpty() -> SvPdfPage(
                        bitmap = pdfPages[currentPage.coerceIn(0, pdfPages.lastIndex)],
                        scale = scale, panOffset = panOffset,
                        onTransform = { s, p -> scale = (scale * s).coerceIn(0.5f, 6f); panOffset = Offset(panOffset.x + p.x, panOffset.y + p.y) },
                        accentColor = accentColor
                    )
                    else -> SvLoadingView(accentColor)
                }
            }

            // Per-page label editor
            AnimatedVisibility(visible = showLabelEditor && pdfPages.isNotEmpty(),
                enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(accentColor.copy(0.07f))
                        .border(BorderStroke(0.5.dp, accentColor.copy(0.3f)))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Label, null, tint = NeonGold, modifier = Modifier.size(14.dp))
                        Text("Page ${currentPage + 1} Label / Caption",
                            style = MaterialTheme.typography.labelSmall, color = NeonGold, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (pageLabel.isNotBlank()) {
                            IconButton(onClick = { pageLabel = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    BasicTextField(
                        value = pageLabel, onValueChange = { pageLabel = it },
                        modifier = Modifier.fillMaxWidth()
                            .background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, accentColor.copy(0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        textStyle = TextStyle(color = Color.White.copy(0.85f), fontSize = 13.sp),
                        decorationBox = { inner ->
                            if (pageLabel.isEmpty()) Text("Add a label or caption for this diagram page…",
                                style = TextStyle(color = Color.White.copy(0.25f), fontSize = 12.sp))
                            inner()
                        }
                    )
                }
            }

            // Thumbnail strip
            AnimatedVisibility(visible = showThumbs && pdfPages.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                SvThumbStrip(pdfPages, currentPage, accentColor) { currentPage = it }
            }

            // Navigation bar
            if (pdfPages.isNotEmpty()) {
                SvPageNavBar(
                    current = currentPage, total = totalPages, accentColor = accentColor,
                    onPrev = { if (currentPage > 0) currentPage-- },
                    onNext = { if (currentPage < pdfPages.lastIndex) currentPage++ }
                )
            }

            // Annotation panel
            AnimatedVisibility(visible = showAnnotations,
                enter = slideInVertically { it } + fadeIn(tween(250)),
                exit = slideOutVertically { it } + fadeOut(tween(200))) {
                SvAnnotationPanel(text = globalNote, onTextChange = { globalNote = it }, accentColor = NeonGold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  2 · SHORT NOTE VIEWER  — reading mode, per-page marks, progress tracking
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ShortNoteViewerScreen(
    navController: NavController,
    subject: String,
    fileUri: String,
    title: String
) {
    val context = LocalContext.current
    val uri = remember(fileUri) { runCatching { Uri.parse(fileUri) }.getOrNull() }
    val accentColor = when (subject) {
        "PHYSICS"   -> NeonCyan
        "CHEMISTRY" -> NeonPurple
        "BOTANY"    -> NeonGreen
        else        -> NeonOrange
    }
    val subjectIcon = when (subject) {
        "PHYSICS"   -> Icons.Default.ElectricBolt
        "CHEMISTRY" -> Icons.Default.Science
        "BOTANY"    -> Icons.Default.Park
        else        -> Icons.Default.Pets
    }

    val prefs = remember { context.getSharedPreferences("shortnote_viewer", Context.MODE_PRIVATE) }
    val pdfPages = remember { mutableStateListOf<Bitmap>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var totalPages by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var maxVisited by remember { mutableStateOf(0) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showThumbs by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }

    var scale by remember(currentPage) { mutableStateOf(1f) }
    var panOffset by remember(currentPage) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentPage) { if (currentPage > maxVisited) maxVisited = currentPage }

    val globalNoteKey = remember(fileUri) { "note_${fileUri.hashCode()}" }
    var globalNote by remember(globalNoteKey) { mutableStateOf(prefs.getString(globalNoteKey, "") ?: "") }
    LaunchedEffect(globalNote) { prefs.edit().putString(globalNoteKey, globalNote).apply() }

    fun markKey(page: Int) = "mark_${fileUri.hashCode()}_$page"
    var currentMark by remember(currentPage, fileUri) { mutableStateOf(prefs.getString(markKey(currentPage), "") ?: "") }
    LaunchedEffect(currentMark) { prefs.edit().putString(markKey(currentPage), currentMark).apply() }

    val progressFraction = if (totalPages > 0) (maxVisited + 1f) / totalPages else 0f

    LaunchedEffect(fileUri) {
        if (uri != null) {
            loading = true; error = false
            withContext(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        for (i in 0 until renderer.pageCount.coerceAtMost(100)) {
                            val page = renderer.openPage(i)
                            val w = 1080; val h = (w * page.height.toFloat() / page.width).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close(); pdfPages.add(bmp)
                        }
                        renderer.close(); pfd.close()
                    } else error = true
                } catch (e: Exception) { error = true }
            }
            loading = false
        }
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            AnimatedVisibility(visible = !focusMode, enter = fadeIn(), exit = fadeOut()) {
                NEETTopBar(
                    title = title,
                    breadcrumb = if (pdfPages.isNotEmpty()) "$subject  ·  Page ${currentPage + 1}/$totalPages" else "$subject Short Notes",
                    onBack = { navController.popBackStack() },
                    actions = {
                        IconButton(onClick = { focusMode = true }) {
                            Icon(Icons.Default.FullscreenExit, null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showThumbs = !showThumbs }) {
                            Icon(Icons.Default.GridView, null,
                                tint = if (showThumbs) accentColor else Color.White.copy(0.55f), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showAnnotations = !showAnnotations }) {
                            Icon(Icons.Default.EditNote, null,
                                tint = if (showAnnotations) NeonGold else Color.White.copy(0.55f), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            uri?.let {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, it)
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
                            }
                        }) { Icon(Icons.Default.Share, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                    }
                )
            }

            // Focus mode exit bar
            AnimatedVisibility(visible = focusMode, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxWidth()
                    .background(accentColor.copy(0.12f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(subjectIcon, null, tint = accentColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Focus Mode  •  Tap to exit", style = MaterialTheme.typography.labelSmall, color = accentColor, modifier = Modifier.weight(1f).clickable { focusMode = false })
                        Text("${currentPage + 1}/$totalPages", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.7f))
                    }
                }
            }

            // Reading progress bar
            if (pdfPages.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(0.07f))) {
                    val animatedProgress by animateFloatAsState(progressFraction, tween(600), label = "progress")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(accentColor, accentColor.copy(0.6f))))
                    )
                }
            }

            // Subject info strip (non-focus mode)
            AnimatedVisibility(visible = !focusMode && pdfPages.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(accentColor.copy(0.14f), Color.Transparent)))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(subjectIcon, null, tint = accentColor, modifier = Modifier.size(14.dp))
                    Text(subject, style = MaterialTheme.typography.labelMedium, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val readCount = if (totalPages > 0) (maxVisited + 1) else 0
                    Text("$readCount/$totalPages read", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.7f))
                    if (currentMark.isNotEmpty()) {
                        Text(currentMark, fontSize = 16.sp)
                    }
                }
            }

            // Main page
            Box(modifier = Modifier.weight(1f).clickable(enabled = focusMode) { focusMode = false }) {
                when {
                    loading -> SvLoadingView(accentColor)
                    error || uri == null -> SvErrorView(fileUri, uri, context, accentColor)
                    pdfPages.isNotEmpty() -> SvPdfPage(
                        bitmap = pdfPages[currentPage.coerceIn(0, pdfPages.lastIndex)],
                        scale = scale, panOffset = panOffset,
                        onTransform = { s, p -> scale = (scale * s).coerceIn(0.5f, 6f); panOffset = Offset(panOffset.x + p.x, panOffset.y + p.y) },
                        accentColor = accentColor
                    )
                    else -> SvLoadingView(accentColor)
                }
            }

            // Per-page quick mark strip
            AnimatedVisibility(visible = !focusMode && pdfPages.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Column {
                    NeonDivider(accentColor.copy(0.2f))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            Text("Mark:", style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.4f), modifier = Modifier.padding(top = 6.dp))
                        }
                        items(SV_PAGE_MARKS) { (emoji, label) ->
                            val selected = currentMark == emoji
                            Box(
                                modifier = Modifier
                                    .shadow(if (selected) 6.dp else 0.dp, RoundedCornerShape(10.dp), spotColor = accentColor.copy(0.4f))
                                    .background(
                                        if (selected) accentColor.copy(0.2f) else Color.White.copy(0.05f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        if (selected) 1.dp else 0.5.dp,
                                        if (selected) accentColor else Color.White.copy(0.1f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { currentMark = if (currentMark == emoji) "" else emoji }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(emoji, fontSize = 14.sp)
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) accentColor else Color.White.copy(0.45f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Thumbnail strip
            AnimatedVisibility(visible = showThumbs && pdfPages.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                SvThumbStrip(pdfPages, currentPage, accentColor) { currentPage = it }
            }

            // Navigation bar
            if (pdfPages.isNotEmpty() && !focusMode) {
                SvPageNavBar(
                    current = currentPage, total = totalPages, accentColor = accentColor,
                    onPrev = { if (currentPage > 0) currentPage-- },
                    onNext = { if (currentPage < pdfPages.lastIndex) currentPage++ }
                )
            }

            // Annotation panel
            AnimatedVisibility(visible = showAnnotations,
                enter = slideInVertically { it } + fadeIn(tween(250)),
                exit = slideOutVertically { it } + fadeOut(tween(200))) {
                SvAnnotationPanel(text = globalNote, onTextChange = { globalNote = it }, accentColor = NeonGold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  3 · SUBJECT NOTE VIEWER  — bookmarks, jump-to-page, subject-wide notes
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SubjectNoteViewerScreen(
    navController: NavController,
    subject: String,
    fileUri: String,
    title: String
) {
    val context = LocalContext.current
    val uri = remember(fileUri) { runCatching { Uri.parse(fileUri) }.getOrNull() }
    val accentColor = when (subject) {
        "PHYSICS"   -> NeonCyan
        "CHEMISTRY" -> NeonPurple
        "BOTANY"    -> NeonGreen
        else        -> NeonOrange
    }
    val subjectIcon = when (subject) {
        "PHYSICS"   -> Icons.Default.ElectricBolt
        "CHEMISTRY" -> Icons.Default.Science
        "BOTANY"    -> Icons.Default.Park
        else        -> Icons.Default.Pets
    }

    val prefs = remember { context.getSharedPreferences("subjectnote_viewer", Context.MODE_PRIVATE) }
    val pdfPages = remember { mutableStateListOf<Bitmap>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var totalPages by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showThumbs by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf("") }
    var bookmarkTick by remember { mutableStateOf(0) }

    var scale by remember(currentPage) { mutableStateOf(1f) }
    var panOffset by remember(currentPage) { mutableStateOf(Offset.Zero) }

    val globalNoteKey = remember(fileUri) { "note_${fileUri.hashCode()}" }
    var globalNote by remember(globalNoteKey) { mutableStateOf(prefs.getString(globalNoteKey, "") ?: "") }
    LaunchedEffect(globalNote) { prefs.edit().putString(globalNoteKey, globalNote).apply() }

    fun bmKey(page: Int) = "bm_${fileUri.hashCode()}_$page"
    var isBookmarked by remember(currentPage, fileUri) { mutableStateOf(prefs.getBoolean(bmKey(currentPage), false)) }
    LaunchedEffect(isBookmarked) {
        prefs.edit().putBoolean(bmKey(currentPage), isBookmarked).apply()
        bookmarkTick++
    }

    val bookmarkedPages = remember(bookmarkTick, totalPages) {
        (0 until totalPages).filter { prefs.getBoolean(bmKey(it), false) }
    }

    LaunchedEffect(fileUri) {
        if (uri != null) {
            loading = true; error = false
            withContext(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        for (i in 0 until renderer.pageCount.coerceAtMost(100)) {
                            val page = renderer.openPage(i)
                            val w = 1080; val h = (w * page.height.toFloat() / page.width).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close(); pdfPages.add(bmp)
                        }
                        renderer.close(); pfd.close()
                    } else error = true
                } catch (e: Exception) { error = true }
            }
            loading = false
        }
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            NEETTopBar(
                title = title,
                breadcrumb = if (pdfPages.isNotEmpty()) "$subject Notes  ·  ${currentPage + 1}/$totalPages" else "$subject Notes",
                onBack = { navController.popBackStack() },
                actions = {
                    // Bookmark toggle
                    IconButton(onClick = { isBookmarked = !isBookmarked }) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            null, tint = if (isBookmarked) NeonGold else Color.White.copy(0.55f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Bookmarks panel
                    IconButton(onClick = { showBookmarks = !showBookmarks }) {
                        Icon(Icons.Default.BookmarkAdded, null,
                            tint = if (showBookmarks) NeonGold else Color.White.copy(0.55f), modifier = Modifier.size(20.dp))
                    }
                    // Jump to page
                    IconButton(onClick = { showJumpDialog = true }) {
                        Icon(Icons.Default.FindInPage, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                    // Thumbnails
                    IconButton(onClick = { showThumbs = !showThumbs }) {
                        Icon(Icons.Default.GridView, null,
                            tint = if (showThumbs) accentColor else Color.White.copy(0.55f), modifier = Modifier.size(20.dp))
                    }
                    // Annotations
                    IconButton(onClick = { showAnnotations = !showAnnotations }) {
                        Icon(Icons.Default.EditNote, null,
                            tint = if (showAnnotations) NeonGold else Color.White.copy(0.55f), modifier = Modifier.size(20.dp))
                    }
                    // Share
                    IconButton(onClick = {
                        uri?.let {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, it)
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
                        }
                    }) { Icon(Icons.Default.Share, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                }
            )

            // Subject header band
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(accentColor.copy(0.2f), accentColor.copy(0.04f), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThreeDIconBox(icon = subjectIcon, tint = accentColor, size = 34.dp, iconSize = 18.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(subject, style = MaterialTheme.typography.labelLarge, color = accentColor, fontWeight = FontWeight.ExtraBold)
                    Text("Complete Subject Notes", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.6f))
                }
                if (pdfPages.isNotEmpty()) {
                    val bmCount = bookmarkedPages.size
                    if (bmCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(NeonGold.copy(0.15f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, NeonGold.copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Bookmark, null, tint = NeonGold, modifier = Modifier.size(10.dp))
                                Text("$bmCount", style = MaterialTheme.typography.labelSmall, color = NeonGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (isBookmarked) {
                        Text("⭐", fontSize = 14.sp)
                    }
                }
            }

            // Bookmarks panel
            AnimatedVisibility(visible = showBookmarks && bookmarkedPages.isNotEmpty(),
                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(NeonGold.copy(0.06f))
                        .border(BorderStroke(0.5.dp, NeonGold.copy(0.25f)))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BookmarkAdded, null, tint = NeonGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bookmarked Pages", style = MaterialTheme.typography.labelMedium,
                            color = NeonGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(bookmarkedPages) { page ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (page == currentPage) NeonGold.copy(0.3f) else NeonGold.copy(0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        if (page == currentPage) 1.dp else 0.5.dp,
                                        if (page == currentPage) NeonGold else NeonGold.copy(0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { currentPage = page; showBookmarks = false }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Bookmark, null, tint = NeonGold, modifier = Modifier.size(10.dp))
                                    Text("Pg ${page + 1}", style = MaterialTheme.typography.labelSmall,
                                        color = NeonGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Main page content
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> SvLoadingView(accentColor)
                    error || uri == null -> SvErrorView(fileUri, uri, context, accentColor)
                    pdfPages.isNotEmpty() -> SvPdfPage(
                        bitmap = pdfPages[currentPage.coerceIn(0, pdfPages.lastIndex)],
                        scale = scale, panOffset = panOffset,
                        onTransform = { s, p -> scale = (scale * s).coerceIn(0.5f, 6f); panOffset = Offset(panOffset.x + p.x, panOffset.y + p.y) },
                        accentColor = accentColor
                    )
                    else -> SvLoadingView(accentColor)
                }
                // Bookmark ribbon on current page
                if (isBookmarked && pdfPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                            .background(NeonGold.copy(0.85f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp, topEnd = 6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("⭐ Bookmarked", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1A1000), fontWeight = FontWeight.Bold) }
                }
            }

            // Thumbnail strip
            AnimatedVisibility(visible = showThumbs && pdfPages.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                SvThumbStrip(pdfPages, currentPage, accentColor) { currentPage = it }
            }

            // Navigation bar
            if (pdfPages.isNotEmpty()) {
                SvPageNavBar(
                    current = currentPage, total = totalPages, accentColor = accentColor,
                    onPrev = { if (currentPage > 0) currentPage-- },
                    onNext = { if (currentPage < pdfPages.lastIndex) currentPage++ }
                )
            }

            // Annotation panel
            AnimatedVisibility(visible = showAnnotations,
                enter = slideInVertically { it } + fadeIn(tween(250)),
                exit = slideOutVertically { it } + fadeOut(tween(200))) {
                SvAnnotationPanel(text = globalNote, onTextChange = { globalNote = it }, accentColor = NeonGold)
            }
        }

        // Jump to page dialog
        if (showJumpDialog) {
            NEETDialog(
                title = "Jump to Page",
                icon = Icons.Default.FindInPage,
                accentColor = accentColor,
                onDismiss = { showJumpDialog = false; jumpTarget = "" }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Total: $totalPages pages", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                    DialogTextField(
                        value = jumpTarget, onValueChange = { jumpTarget = it },
                        label = "Page number (1–$totalPages)", icon = Icons.Default.Tag, accentColor = accentColor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showJumpDialog = false; jumpTarget = "" },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                val pg = jumpTarget.toIntOrNull()
                                if (pg != null && pg in 1..totalPages) {
                                    currentPage = pg - 1
                                    showJumpDialog = false; jumpTarget = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.2f)),
                            border = BorderStroke(1.dp, accentColor.copy(0.6f))
                        ) { Text("Go", color = accentColor, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SHARED COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SvLoadingView(color: Color) {
    val inf = rememberInfiniteTransition(label = "sv_load")
    val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "sv_pulse")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier.size(86.dp)
                    .shadow(18.dp, CircleShape, spotColor = color.copy(pulse * 0.5f))
                    .background(Brush.radialGradient(listOf(color.copy(0.16f), Color.Transparent)), CircleShape)
                    .border(2.dp, Brush.sweepGradient(listOf(color, color.copy(0.3f), color)), CircleShape),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = color, strokeWidth = 3.dp, modifier = Modifier.size(42.dp)) }
            Text("Loading PDF…", color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
            Text("Rendering pages in high quality", color = Color.White.copy(0.28f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SvErrorView(fileUri: String, uri: Uri?, context: Context, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(glowColor = NeonRed, modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ThreeDIconBox(icon = Icons.Default.BrokenImage, tint = NeonRed, size = 70.dp, iconSize = 38.dp)
                Text("Cannot Load File", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("The file may be missing, encrypted, or incompatible.\nTry opening it in an external app.",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), textAlign = TextAlign.Center)
                if (uri != null) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.18f)),
                        border = BorderStroke(1.dp, color.copy(0.65f))
                    ) {
                        Icon(Icons.Default.OpenInNew, null, tint = color)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Externally", color = color, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SvPdfPage(
    bitmap: Bitmap,
    scale: Float,
    panOffset: Offset,
    onTransform: (scaleChange: Float, panChange: Offset) -> Unit,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(8.dp), spotColor = accentColor.copy(0.2f))
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = panOffset.x; translationY = panOffset.y
                }
        )
        // Zoom level indicator
        if (scale != 1f) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                .background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = accentColor)
            }
        }
        // Pinch hint
        if (scale == 1f) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .background(Color.Black.copy(0.5f), RoundedCornerShape(12.dp))
                    .border(0.5.dp, accentColor.copy(0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.ZoomIn, null, tint = accentColor.copy(0.6f), modifier = Modifier.size(12.dp))
                    Text("Pinch to zoom  ·  Drag to pan", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.45f))
                }
            }
        }
    }
}

@Composable
private fun SvPageNavBar(current: Int, total: Int, accentColor: Color, onPrev: () -> Unit, onNext: () -> Unit) {
    val canPrev = current > 0
    val canNext = current < total - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF070F1C).copy(0.95f))))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Prev button
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(if (canPrev) 6.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = accentColor.copy(0.3f))
                .background(if (canPrev) accentColor.copy(0.15f) else Color.White.copy(0.04f), RoundedCornerShape(12.dp))
                .border(1.dp, if (canPrev) accentColor.copy(0.5f) else Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                .clickable(enabled = canPrev, onClick = onPrev)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.ChevronLeft, null, tint = if (canPrev) accentColor else Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
                Text("Prev", style = MaterialTheme.typography.labelMedium, color = if (canPrev) accentColor else Color.White.copy(0.2f), fontWeight = FontWeight.SemiBold)
            }
        }

        // Page counter badge
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = accentColor.copy(0.35f))
                    .background(Brush.linearGradient(listOf(accentColor.copy(0.22f), accentColor.copy(0.08f))), RoundedCornerShape(14.dp))
                    .border(1.dp, accentColor.copy(0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("${current + 1}", style = MaterialTheme.typography.headlineSmall, color = accentColor, fontWeight = FontWeight.ExtraBold)
            }
            Text("of $total", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
        }

        // Next button
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(if (canNext) 6.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = accentColor.copy(0.3f))
                .background(if (canNext) accentColor.copy(0.15f) else Color.White.copy(0.04f), RoundedCornerShape(12.dp))
                .border(1.dp, if (canNext) accentColor.copy(0.5f) else Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                .clickable(enabled = canNext, onClick = onNext)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Next", style = MaterialTheme.typography.labelMedium, color = if (canNext) accentColor else Color.White.copy(0.2f), fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null, tint = if (canNext) accentColor else Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SvThumbStrip(pages: List<Bitmap>, currentPage: Int, accentColor: Color, onPageClick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) {
        runCatching { listState.animateScrollToItem(currentPage.coerceIn(0, pages.lastIndex)) }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF070D18).copy(0.96f))
            .border(BorderStroke(0.5.dp, accentColor.copy(0.2f)))
            .padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(start = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.GridView, null, tint = accentColor.copy(0.6f), modifier = Modifier.size(11.dp))
            Text("Page Thumbnails", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(0.6f))
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
        ) {
            itemsIndexed(pages) { index, bmp ->
                val selected = index == currentPage
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.clickable { onPageClick(index) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .shadow(if (selected) 8.dp else 2.dp, RoundedCornerShape(6.dp), spotColor = accentColor.copy(if (selected) 0.5f else 0.1f))
                            .clip(RoundedCornerShape(6.dp))
                            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) accentColor else Color.White.copy(0.15f), RoundedCornerShape(6.dp))
                    ) {
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
                    }
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                        color = if (selected) accentColor else Color.White.copy(0.35f),
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun SvAnnotationPanel(text: String, onTextChange: (String) -> Unit, accentColor: Color) {
    var showEmojiPanel by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), spotColor = accentColor.copy(0.3f))
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1929), Color(0xFF070F1C))), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(accentColor.copy(0.4f), NeonCyan.copy(0.2f), NeonPurple.copy(0.3f)))), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
    ) {
        // Handle + header
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(34.dp, 4.dp).background(Color.White.copy(0.2f), RoundedCornerShape(2.dp)))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).background(accentColor.copy(0.14f), RoundedCornerShape(7.dp)).border(0.5.dp, accentColor.copy(0.4f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EditNote, null, tint = accentColor, modifier = Modifier.size(15.dp))
            }
            Text("My Annotations & Notes", style = MaterialTheme.typography.labelLarge, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (text.isNotBlank()) {
                Text("${text.length} ch", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.3f))
                IconButton(onClick = { onTextChange("") }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(14.dp))
                }
            }
        }
        NeonDivider(accentColor.copy(0.2f))
        Spacer(Modifier.height(4.dp))

        // Status marks
        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(SV_STATUS_MARKS) { mark ->
                Box(Modifier.size(32.dp).background(Color.White.copy(0.05f), RoundedCornerShape(7.dp)).border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(7.dp)).clickable { onTextChange(text + mark) }, contentAlignment = Alignment.Center) {
                    Text(mark, fontSize = 15.sp)
                }
            }
            item {
                Box(Modifier.size(32.dp)
                    .background(if (showEmojiPanel) accentColor.copy(0.18f) else Color.White.copy(0.05f), RoundedCornerShape(7.dp))
                    .border(0.5.dp, if (showEmojiPanel) accentColor.copy(0.5f) else Color.White.copy(0.1f), RoundedCornerShape(7.dp))
                    .clickable { showEmojiPanel = !showEmojiPanel }, contentAlignment = Alignment.Center) {
                    Text("😊", fontSize = 15.sp)
                }
            }
        }

        // Emoji picker
        AnimatedVisibility(visible = showEmojiPanel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(SV_QUICK_EMOJIS) { emoji ->
                    Box(Modifier.size(34.dp).background(Color.White.copy(0.05f), RoundedCornerShape(7.dp)).border(0.5.dp, Color.White.copy(0.08f), RoundedCornerShape(7.dp)).clickable { onTextChange(text + emoji); showEmojiPanel = false }, contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            RichTextToolbar(accentColor = accentColor, onInsert = { onTextChange(text + it) })
        }
        Spacer(Modifier.height(6.dp))

        // Notes text editor
        BasicTextField(
            value = text, onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.03f))
                .border(0.5.dp, accentColor.copy(0.22f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            textStyle = TextStyle(color = Color.White.copy(0.88f), fontSize = 14.sp, lineHeight = 22.sp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Write annotations, key points, labels, thoughts…\nUse the toolbar above for rich formatting ✏️",
                    style = TextStyle(color = Color.White.copy(0.18f), fontSize = 12.sp, lineHeight = 19.sp))
                inner()
            }
        )
        Spacer(Modifier.height(12.dp))
    }
}
