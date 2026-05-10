package com.neet.tracker.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.DialogTextField
import com.neet.tracker.ui.dialogs.NEETDialog
import com.neet.tracker.ui.dialogs.RichTextToolbar
import com.neet.tracker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val UV_PAGE_MARKS = listOf(
    "✅" to "Got It",
    "❓" to "Review",
    "⭐" to "Important",
    "❌" to "Skip",
    "🔥" to "Key Page",
    "💡" to "Insight"
)
private val UV_STATUS_MARKS = listOf("✅", "❌", "⏳", "🔄", "⭐", "❗", "❓", "💡", "🎯", "🔥")
private val UV_QUICK_EMOJIS = listOf(
    "😊","😔","💪","🔥","📚","✅","❌","🎯","🌟","💡","⚡","🧠","📝","🏆","😴",
    "🩺","⚗️","🔬","🌿","🐾","💊","🫀","🫁","🦷","👁️","🦴","🧬","📊","📈","📉",
    "🤔","💯","🎉","📖","⏰","🔔","📌","🔑","💎","🚀"
)

// ─── Universal File Viewer ─────────────────────────────────────────────────────

@Composable
fun FileViewerScreen(navController: NavController, fileUri: String, title: String) {
    val context = LocalContext.current
    val uri = remember(fileUri) { try { Uri.parse(fileUri) } catch (e: Exception) { null } }

    // Resolve the actual filename for content:// URIs that don't expose a MIME type directly
    val resolvedDisplayName = remember(fileUri) {
        var name = ""
        if (uri?.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: "" }
            } catch (_: Exception) {}
        }
        name
    }

    val mimeType = remember(fileUri, resolvedDisplayName) {
        if (uri != null) {
            // Primary: content resolver (works for most providers)
            context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
                ?: run {
                    // Fallback: derive MIME from the display name's extension
                    val ext = if (resolvedDisplayName.isNotBlank())
                        resolvedDisplayName.substringAfterLast('.', "").lowercase()
                    else
                        fileUri.substringAfterLast('.', "").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
                }
        } else ""
    }
    val extension = remember(fileUri, mimeType, resolvedDisplayName) {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.takeIf { it.isNotBlank() }
            ?: if (resolvedDisplayName.isNotBlank()) resolvedDisplayName.substringAfterLast('.', "").lowercase()
            else MimeTypeMap.getFileExtensionFromUrl(fileUri)?.lowercase()
                ?: fileUri.substringAfterLast('.', "").lowercase()
    }
    val isImage = mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val isPdf   = mimeType == "application/pdf" || extension == "pdf" || fileUri.contains("pdf", ignoreCase = true)
    // For any non-image file, always attempt PDF rendering. PDFs stored via content:// often
    // have no detectable extension or MIME type, so we try the renderer and fall back to
    // GenericFileView only when the renderer itself reports an error.
    val tryAsPdf = !isImage && uri != null

    val prefs   = remember { context.getSharedPreferences("uv_viewer", Context.MODE_PRIVATE) }
    val noteKey = remember(fileUri) { "note_${fileUri.hashCode()}" }

    // ── PDF state ──────────────────────────────────────────────────────────────
    val pdfPages  = remember { mutableStateListOf<Bitmap>() }
    var pdfLoading      by remember { mutableStateOf(false) }
    var totalPages      by remember { mutableStateOf(0) }
    var currentPage     by remember { mutableStateOf(0) }
    var pdfError        by remember { mutableStateOf(false) }
    var pdfErrorMessage by remember { mutableStateOf("") }
    var maxVisited  by remember { mutableStateOf(0) }
    var scale       by remember(currentPage) { mutableStateOf(1f) }
    var panOffset   by remember(currentPage) { mutableStateOf(Offset.Zero) }

    // ── UI toggles ─────────────────────────────────────────────────────────────
    var notesExpanded  by remember { mutableStateOf(false) }
    var showThumbs     by remember { mutableStateOf(false) }
    var focusMode      by remember { mutableStateOf(false) }
    var showBookmarks  by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpTarget     by remember { mutableStateOf("") }
    var bookmarkTick   by remember { mutableStateOf(0) }

    // ── Notes ──────────────────────────────────────────────────────────────────
    var notesText by remember(noteKey) { mutableStateOf(prefs.getString(noteKey, "") ?: "") }
    LaunchedEffect(notesText) { prefs.edit().putString(noteKey, notesText).apply() }

    // ── Per-page mark ──────────────────────────────────────────────────────────
    fun markKey(page: Int) = "mark_${fileUri.hashCode()}_$page"
    var currentMark by remember(currentPage, fileUri) {
        mutableStateOf(prefs.getString(markKey(currentPage), "") ?: "")
    }
    LaunchedEffect(currentMark) { prefs.edit().putString(markKey(currentPage), currentMark).apply() }

    // ── Bookmarks ──────────────────────────────────────────────────────────────
    fun bmKey(page: Int) = "bm_${fileUri.hashCode()}_$page"
    var isBookmarked by remember(currentPage, fileUri) {
        mutableStateOf(prefs.getBoolean(bmKey(currentPage), false))
    }
    LaunchedEffect(isBookmarked) {
        prefs.edit().putBoolean(bmKey(currentPage), isBookmarked).apply()
        bookmarkTick++
    }
    val bookmarkedPages = remember(bookmarkTick, totalPages) {
        (0 until totalPages).filter { prefs.getBoolean(bmKey(it), false) }
    }

    // ── Reading progress ───────────────────────────────────────────────────────
    LaunchedEffect(currentPage) { if (currentPage > maxVisited) maxVisited = currentPage }
    val progressFraction = if (totalPages > 0) (maxVisited + 1f) / totalPages else 0f

    // ── PDF loading ────────────────────────────────────────────────────────────
    LaunchedEffect(fileUri) {
        if (tryAsPdf && uri != null) {
            pdfLoading = true; pdfError = false; pdfErrorMessage = ""
            withContext(Dispatchers.IO) {
                try {
                    val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        val width = 1080
                        for (i in 0 until renderer.pageCount.coerceAtMost(100)) {
                            val page  = renderer.openPage(i)
                            val ratio = page.height.toFloat() / page.width.toFloat()
                            val bmp   = Bitmap.createBitmap(width, (width * ratio).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close(); pdfPages.add(bmp)
                        }
                        renderer.close(); pfd.close()
                    } else {
                        pdfError = true
                        pdfErrorMessage = "Content resolver returned null file descriptor.\n" +
                            "The app may not have permission to read this URI, or the file was deleted/moved."
                    }
                } catch (e: Exception) {
                    pdfError = true
                    pdfErrorMessage = "${e.javaClass.simpleName}: ${e.message ?: "No message"}"
                }
            }
            pdfLoading = false
        }
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar (hidden in focus mode) ─────────────────────────────────
            AnimatedVisibility(visible = !focusMode, enter = fadeIn(), exit = fadeOut()) {
                NEETTopBar(
                    title      = title,
                    breadcrumb = if (pdfPages.isNotEmpty() && totalPages > 0) "Page ${currentPage + 1} / $totalPages" else "File Viewer",
                    onBack     = { navController.popBackStack() },
                    actions    = {
                        if (pdfPages.isNotEmpty()) {
                            IconButton(onClick = { focusMode = true }) {
                                Icon(Icons.Default.Fullscreen, null,
                                    tint = Color.White.copy(0.55f), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showThumbs = !showThumbs }) {
                                Icon(Icons.Default.GridView, null,
                                    tint = if (showThumbs) NeonCyan else Color.White.copy(0.55f),
                                    modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { isBookmarked = !isBookmarked }) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    null,
                                    tint = if (isBookmarked) NeonGold else Color.White.copy(0.55f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (bookmarkedPages.isNotEmpty()) {
                                IconButton(onClick = { showBookmarks = !showBookmarks }) {
                                    Icon(Icons.Default.BookmarkAdded, null,
                                        tint = if (showBookmarks) NeonGold else Color.White.copy(0.55f),
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { showJumpDialog = true }) {
                                Icon(Icons.Default.FindInPage, null,
                                    tint = NeonCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        // Notes
                        val noteGlow by animateFloatAsState(if (notesExpanded) 1f else 0f, tween(250), label = "note")
                        IconButton(onClick = { notesExpanded = !notesExpanded }) {
                            Box(
                                modifier = Modifier.size(34.dp)
                                    .background(NeonGold.copy(0.22f * noteGlow), RoundedCornerShape(10.dp))
                                    .border(BorderStroke((noteGlow).dp, NeonGold.copy(0.6f * noteGlow)), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (notesExpanded) Icons.Default.EditNote else Icons.Default.NoteAdd,
                                    null,
                                    tint = if (notesExpanded) NeonGold else Color.White.copy(0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        // Share
                        IconButton(onClick = {
                            val mime = if (isPdf) "application/pdf" else if (isImage) "image/*" else "*/*"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try { context.startActivity(Intent.createChooser(intent, "Share $title")) } catch (_: Exception) {}
                        }) { Icon(Icons.Default.Share, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                        // Open externally
                        IconButton(onClick = {
                            val mime = if (uri != null) context.contentResolver.getType(uri) ?: "*/*" else "*/*"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try { context.startActivity(Intent.createChooser(intent, "Open with")) } catch (_: Exception) {}
                        }) { Icon(Icons.Default.OpenInNew, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                    }
                )
            }

            // ── Focus mode exit bar ────────────────────────────────────────────
            AnimatedVisibility(visible = focusMode, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(NeonCyan.copy(0.12f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fullscreen, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Focus Mode  •  Tap page to exit",
                            style = MaterialTheme.typography.labelSmall, color = NeonCyan,
                            modifier = Modifier.weight(1f).clickable { focusMode = false }
                        )
                        if (totalPages > 0)
                            Text("${currentPage + 1}/$totalPages",
                                style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(0.7f))
                    }
                }
            }

            // ── Reading progress bar ───────────────────────────────────────────
            if (pdfPages.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(0.07f))) {
                    val animProg by animateFloatAsState(progressFraction, tween(600), label = "prog")
                    Box(
                        modifier = Modifier.fillMaxWidth(animProg).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(NeonCyan, NeonPurple.copy(0.75f))))
                    )
                }
            }

            // ── Bookmarks panel ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showBookmarks && bookmarkedPages.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut()
            ) {
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
                        IconButton(onClick = { showBookmarks = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(14.dp))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(bookmarkedPages) { page ->
                            val isCur = page == currentPage
                            Box(
                                modifier = Modifier
                                    .background(if (isCur) NeonGold.copy(0.3f) else NeonGold.copy(0.1f), RoundedCornerShape(8.dp))
                                    .border(if (isCur) 1.dp else 0.5.dp, if (isCur) NeonGold else NeonGold.copy(0.3f), RoundedCornerShape(8.dp))
                                    .clickable { currentPage = page; scale = 1f; panOffset = Offset.Zero; showBookmarks = false }
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

            // ── Main content ───────────────────────────────────────────────────
            Box(
                modifier = Modifier.weight(1f)
                    .clickable(enabled = focusMode) { focusMode = false }
            ) {
                when {
                    uri == null || fileUri.isBlank() -> FileErrorView()
                    isImage -> ImageViewer(uri = uri, title = title)
                    // tryAsPdf = all non-image files — we always attempt PDF rendering first.
                    // If the renderer fails (pdfError), fall back to GenericFileView so the
                    // user can still open the file externally.
                    tryAsPdf -> {
                        when {
                            pdfLoading -> UvLoadingView()
                            pdfPages.isNotEmpty() -> UvPdfPage(
                                bitmap    = pdfPages[currentPage.coerceIn(0, pdfPages.lastIndex)],
                                scale     = scale,
                                panOffset = panOffset,
                                onTransform = { s, p ->
                                    scale     = (scale * s).coerceIn(0.5f, 6f)
                                    panOffset = Offset(panOffset.x + p.x, panOffset.y + p.y)
                                }
                            )
                            pdfError -> FileFailureLog(
                                uri = uri, title = title,
                                detectedMime = mimeType, detectedExt = extension,
                                resolvedName = resolvedDisplayName, rawUri = fileUri,
                                errorMessage = pdfErrorMessage, context = context
                            )
                            else -> UvLoadingView()
                        }
                    }
                    else -> FileFailureLog(
                        uri = uri, title = title,
                        detectedMime = mimeType, detectedExt = extension,
                        resolvedName = resolvedDisplayName, rawUri = fileUri,
                        errorMessage = "URI scheme not supported for in-app viewing.", context = context
                    )
                }

                // Bookmark ribbon
                if (isBookmarked && pdfPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                            .background(NeonGold.copy(0.9f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp, topEnd = 6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("⭐", fontSize = 11.sp)
                            Text("Bookmarked", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1A1000), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Current page mark badge
                if (currentMark.isNotEmpty() && pdfPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 8.dp)
                            .background(Color.Black.copy(0.65f), RoundedCornerShape(10.dp))
                            .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text(currentMark, fontSize = 16.sp) }
                }

                // Zoom indicator
                if (scale != 1f && pdfPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(top = if (isBookmarked) 44.dp else 8.dp, end = 8.dp)
                            .background(Color.Black.copy(0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NeonCyan) }
                }
            }

            // ── Per-page mark strip ────────────────────────────────────────────
            AnimatedVisibility(
                visible = pdfPages.isNotEmpty() && !focusMode,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Column {
                    NeonDivider(NeonCyan.copy(0.15f))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            Text("Mark:", style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.4f), modifier = Modifier.padding(top = 7.dp))
                        }
                        items(UV_PAGE_MARKS) { (emoji, label) ->
                            val selected = currentMark == emoji
                            Box(
                                modifier = Modifier
                                    .shadow(if (selected) 4.dp else 0.dp, RoundedCornerShape(8.dp), spotColor = NeonCyan.copy(0.3f))
                                    .background(if (selected) NeonCyan.copy(0.2f) else Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                                    .border(if (selected) 1.dp else 0.5.dp, if (selected) NeonCyan.copy(0.7f) else Color.White.copy(0.15f), RoundedCornerShape(8.dp))
                                    .clickable { currentMark = if (currentMark == emoji) "" else emoji }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(emoji, fontSize = 13.sp)
                                    Text(label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) NeonCyan else Color.White.copy(0.4f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Thumbnail strip ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showThumbs && pdfPages.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit  = slideOutVertically { it } + fadeOut()
            ) {
                UvThumbStrip(pdfPages, currentPage) {
                    currentPage = it; scale = 1f; panOffset = Offset.Zero
                }
            }

            // ── Page nav bar ───────────────────────────────────────────────────
            if (pdfPages.isNotEmpty() && !focusMode) {
                UvPageNavBar(
                    current = currentPage,
                    total   = totalPages,
                    onPrev  = { if (currentPage > 0) { currentPage--; scale = 1f; panOffset = Offset.Zero } },
                    onNext  = { if (currentPage < pdfPages.lastIndex) { currentPage++; scale = 1f; panOffset = Offset.Zero } }
                )
            }

            // ── Annotation panel ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = notesExpanded,
                enter   = slideInVertically { it } + fadeIn(tween(250)),
                exit    = slideOutVertically { it } + fadeOut(tween(200))
            ) {
                AnnotationPanel(text = notesText, onTextChange = { notesText = it })
            }
        }

        // ── Jump to page dialog ────────────────────────────────────────────────
        if (showJumpDialog) {
            NEETDialog(
                title = "Jump to Page", icon = Icons.Default.FindInPage,
                accentColor = NeonCyan, onDismiss = { showJumpDialog = false; jumpTarget = "" }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Total: $totalPages pages",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                    DialogTextField(
                        value = jumpTarget, onValueChange = { jumpTarget = it },
                        label = "Page number (1–$totalPages)", icon = Icons.Default.Tag, accentColor = NeonCyan
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
                                    currentPage = pg - 1; scale = 1f; panOffset = Offset.Zero
                                    showJumpDialog = false; jumpTarget = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)),
                            border = BorderStroke(1.dp, NeonCyan.copy(0.6f))
                        ) { Text("Go", color = NeonCyan, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ─── Annotation Panel ─────────────────────────────────────────────────────────

@Composable
private fun AnnotationPanel(text: String, onTextChange: (String) -> Unit) {
    var showEmojiPanel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), spotColor = NeonGold.copy(0.35f))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D1929), Color(0xFF070F1C))),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            )
            .border(
                BorderStroke(1.dp, Brush.horizontalGradient(listOf(NeonGold.copy(0.4f), NeonCyan.copy(0.25f), NeonPurple.copy(0.4f)))),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(36.dp, 4.dp).background(Color.White.copy(0.2f), RoundedCornerShape(2.dp)))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp)
                    .background(NeonGold.copy(0.15f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, NeonGold.copy(0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.EditNote, null, tint = NeonGold, modifier = Modifier.size(16.dp)) }
            Text("My Annotations & Notes", style = MaterialTheme.typography.labelLarge,
                color = NeonGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (text.isNotBlank()) {
                Text("${text.length} ch", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.3f))
                IconButton(onClick = { onTextChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }

        NeonDivider(NeonGold.copy(0.25f))
        Spacer(Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(UV_STATUS_MARKS) { mark ->
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(8.dp))
                        .clickable { onTextChange(text + mark) },
                    contentAlignment = Alignment.Center
                ) { Text(mark, fontSize = 16.sp) }
            }
            item {
                Box(modifier = Modifier.height(34.dp).width(1.dp).background(Color.White.copy(0.1f)))
            }
            item {
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(if (showEmojiPanel) NeonGold.copy(0.2f) else Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, if (showEmojiPanel) NeonGold.copy(0.5f) else Color.White.copy(0.12f), RoundedCornerShape(8.dp))
                        .clickable { showEmojiPanel = !showEmojiPanel },
                    contentAlignment = Alignment.Center
                ) { Text("😊", fontSize = 16.sp) }
            }
        }

        AnimatedVisibility(visible = showEmojiPanel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(UV_QUICK_EMOJIS) { emoji ->
                    Box(
                        modifier = Modifier.size(36.dp)
                            .background(Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                            .clickable { onTextChange(text + emoji); showEmojiPanel = false },
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 18.sp) }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            RichTextToolbar(accentColor = NeonGold, onInsert = { onTextChange(text + it) })
        }
        Spacer(Modifier.height(8.dp))

        BasicTextField(
            value    = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp, max = 220.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.035f))
                .border(0.5.dp, NeonGold.copy(0.25f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            textStyle = TextStyle(color = Color.White.copy(0.9f), fontSize = 14.sp, lineHeight = 22.sp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text("Write annotations, key points, thoughts about this file…\nUse the toolbar above for rich formatting ✏️",
                        style = TextStyle(color = Color.White.copy(0.2f), fontSize = 13.sp, lineHeight = 20.sp))
                }
                inner()
            }
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ─── Universal PDF Page (single page, pinch-to-zoom + pan) ────────────────────

@Composable
private fun UvPdfPage(
    bitmap: Bitmap,
    scale: Float,
    panOffset: Offset,
    onTransform: (scaleChange: Float, panChange: Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080F))
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
                .shadow(12.dp, RoundedCornerShape(8.dp), spotColor = NeonCyan.copy(0.15f))
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = panOffset.x; translationY = panOffset.y
                }
        )
        // Pinch hint (only at 1x)
        if (scale == 1f) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    .background(Color.Black.copy(0.55f), RoundedCornerShape(14.dp))
                    .border(0.5.dp, NeonCyan.copy(0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.ZoomIn, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(13.dp))
                    Text("Pinch to zoom  ·  Drag to pan",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.45f))
                }
            }
        }
    }
}

// ─── Page Navigation Bar ──────────────────────────────────────────────────────

@Composable
private fun UvPageNavBar(current: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val canPrev = current > 0
    val canNext = current < total - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF070F1C).copy(0.96f))))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(if (canPrev) 6.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = NeonCyan.copy(0.3f))
                .background(if (canPrev) NeonCyan.copy(0.14f) else Color.White.copy(0.04f), RoundedCornerShape(12.dp))
                .border(1.dp, if (canPrev) NeonCyan.copy(0.5f) else Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                .clickable(enabled = canPrev, onClick = onPrev)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.ChevronLeft, null,
                    tint = if (canPrev) NeonCyan else Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
                Text("Prev", style = MaterialTheme.typography.labelMedium,
                    color = if (canPrev) NeonCyan else Color.White.copy(0.2f), fontWeight = FontWeight.SemiBold)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonCyan.copy(0.3f))
                    .background(Brush.linearGradient(listOf(NeonCyan.copy(0.22f), NeonCyan.copy(0.08f))), RoundedCornerShape(12.dp))
                    .border(1.dp, NeonCyan.copy(0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("${current + 1}", style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan, fontWeight = FontWeight.ExtraBold)
            }
            Text("of $total", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(if (canNext) 6.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = NeonCyan.copy(0.3f))
                .background(if (canNext) NeonCyan.copy(0.14f) else Color.White.copy(0.04f), RoundedCornerShape(12.dp))
                .border(1.dp, if (canNext) NeonCyan.copy(0.5f) else Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                .clickable(enabled = canNext, onClick = onNext)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Next", style = MaterialTheme.typography.labelMedium,
                    color = if (canNext) NeonCyan else Color.White.copy(0.2f), fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null,
                    tint = if (canNext) NeonCyan else Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Thumbnail Strip ──────────────────────────────────────────────────────────

@Composable
private fun UvThumbStrip(pages: List<Bitmap>, currentPage: Int, onPageClick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) {
        runCatching { listState.animateScrollToItem(currentPage.coerceIn(0, pages.lastIndex)) }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF070D18).copy(0.96f))
            .border(BorderStroke(0.5.dp, NeonCyan.copy(0.2f)))
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.GridView, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(11.dp))
            Text("Page Thumbnails", style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(0.6f))
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
                            .shadow(if (selected) 8.dp else 2.dp, RoundedCornerShape(6.dp),
                                spotColor = NeonCyan.copy(if (selected) 0.5f else 0.1f))
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                if (selected) 1.5.dp else 0.5.dp,
                                if (selected) NeonCyan else Color.White.copy(0.15f),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
                    }
                    Text("${index + 1}", fontSize = 9.sp,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) NeonCyan else Color.White.copy(0.35f),
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ─── Loading View ─────────────────────────────────────────────────────────────

@Composable
private fun UvLoadingView() {
    val inf = rememberInfiniteTransition(label = "uv_load")
    val pulse by inf.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(90.dp)
                    .shadow(20.dp, CircleShape, spotColor = NeonCyan.copy(pulse * 0.5f))
                    .background(Brush.radialGradient(listOf(NeonCyan.copy(0.18f), Color.Transparent)), CircleShape)
                    .border(2.dp, Brush.sweepGradient(listOf(NeonCyan, NeonPurple.copy(0.7f), NeonCyan)), CircleShape),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = NeonCyan, strokeWidth = 3.dp, modifier = Modifier.size(44.dp)) }
            Text("Rendering PDF pages…", color = Color.White.copy(0.55f), style = MaterialTheme.typography.bodySmall)
            Text("This may take a moment for large files", color = Color.White.copy(0.3f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─── Error View ───────────────────────────────────────────────────────────────

@Composable
private fun UvErrorView(uri: Uri, context: Context) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(glowColor = NeonRed, modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ThreeDIconBox(icon = Icons.Default.BrokenImage, tint = NeonRed, size = 70.dp, iconSize = 38.dp)
                Text("Cannot Load File", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("The file may be missing, encrypted, or incompatible.\nTry opening it in an external app.",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), textAlign = TextAlign.Center)
                Button(
                    onClick = {
                        val mime = context.contentResolver.getType(uri) ?: "application/pdf"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(0.18f)),
                    border = BorderStroke(1.dp, NeonOrange.copy(0.65f))
                ) {
                    Icon(Icons.Default.OpenInNew, null, tint = NeonOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Externally", color = NeonOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Image Viewer (pinch-to-zoom + pan) ───────────────────────────────────────

@Composable
private fun ImageViewer(uri: Uri, title: String) {
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showHint by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2500); showHint = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.85f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale  = (scale * zoom).coerceIn(0.5f, 6f)
                    offset = Offset(offset.x + pan.x * scale, offset.y + pan.y * scale)
                    showHint = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model              = uri,
            contentDescription = title,
            modifier           = Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            }
        )
        AnimatedVisibility(
            visible = showHint,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(0.65f), RoundedCornerShape(20.dp))
                    .border(0.5.dp, NeonCyan.copy(0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.ZoomIn, null, tint = NeonCyan.copy(0.7f), modifier = Modifier.size(14.dp))
                Text("Pinch to zoom  •  Drag to pan",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.6f))
            }
        }
        if (scale != 1f) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NeonGold) }
        }
    }
}

// ─── File Failure Log ─────────────────────────────────────────────────────────
// Shown when PDF rendering fails. Displays full diagnostics so the user knows
// exactly what format was detected and why in-app rendering could not proceed.

@Composable
private fun FileFailureLog(
    uri: Uri,
    title: String,
    detectedMime: String,
    detectedExt: String,
    resolvedName: String,
    rawUri: String,
    errorMessage: String,
    context: Context
) {
    var showRawUri by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val uriScheme    = uri.scheme ?: "unknown"
    val uriAuthority = uri.authority ?: "—"
    val mimeDisplay  = detectedMime.ifBlank { "Could not detect (content resolver returned null)" }
    val extDisplay   = detectedExt.ifBlank { "No extension found" }
    val nameDisplay  = resolvedName.ifBlank { "Not available (non-content:// URI or no DISPLAY_NAME column)" }

    // Friendly conclusion
    val conclusion = when {
        errorMessage.contains("PdfRenderer", ignoreCase = true) ||
        errorMessage.contains("IllegalArgument", ignoreCase = true) ||
        errorMessage.contains("parsererror", ignoreCase = true) ->
            "The file was opened but the PDF parser rejected it. " +
            "It is likely NOT a PDF — it may be a Word doc, image, or other format."
        errorMessage.contains("null file descriptor", ignoreCase = true) ||
        errorMessage.contains("Permission", ignoreCase = true) ->
            "The app could not get read access to this file. " +
            "This usually happens when the URI permission expired after an app restart. " +
            "Re-upload the file to fix it."
        errorMessage.contains("FileNotFoundException", ignoreCase = true) ||
        errorMessage.contains("No such file", ignoreCase = true) ->
            "The file no longer exists at the stored location. It may have been deleted or moved."
        uriScheme == "file" ->
            "Direct file:// URIs are blocked on Android 7+. Re-import the file through the picker."
        else -> "PDF rendering failed. The file may be a non-PDF format, corrupted, password-protected, or inaccessible."
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            GlassCard(glowColor = NeonOrange) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThreeDIconBox(icon = Icons.Default.BrokenImage, tint = NeonOrange, size = 52.dp, iconSize = 28.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cannot Render In-App", style = MaterialTheme.typography.titleMedium,
                                color = NeonOrange, fontWeight = FontWeight.ExtraBold)
                            Text(title, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                        }
                    }
                    NeonDivider(NeonOrange.copy(0.3f))
                    Text(conclusion, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.8f), lineHeight = 20.sp)
                }
            }
        }

        // Diagnostic Log
        item {
            GlassCard(glowColor = NeonCyan) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BugReport, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Text("Diagnostic Log", style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        if (copied) {
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(2000)
                                copied = false
                            }
                        }
                        val copyText = buildString {
                            appendLine("=== NEET Tracker File Diagnostic ===")
                            appendLine("File Title        : $title")
                            appendLine("Resolved Filename : $nameDisplay")
                            appendLine("Detected MIME     : $mimeDisplay")
                            appendLine("Detected Extension: $extDisplay")
                            appendLine("URI Scheme        : $uriScheme")
                            appendLine("URI Authority     : $uriAuthority")
                            appendLine()
                            appendLine("--- Error ---")
                            appendLine(errorMessage.ifBlank { "No error message captured." })
                            appendLine()
                            appendLine("--- Raw URI ---")
                            append(rawUri)
                        }
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(copyText))
                                copied = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, if (copied) NeonGreen.copy(0.7f) else NeonCyan.copy(0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (copied) NeonGreen else NeonCyan
                            )
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                null, modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (copied) "Copied ✓" else "Copy All",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    DiagRow("File Title",        title)
                    DiagRow("Resolved Filename", nameDisplay)
                    DiagRow("Detected MIME",     mimeDisplay)
                    DiagRow("Detected Extension",extDisplay)
                    DiagRow("URI Scheme",        uriScheme)
                    DiagRow("URI Authority",     uriAuthority)

                    Spacer(Modifier.height(8.dp))
                    NeonDivider(NeonCyan.copy(0.2f))
                    Spacer(Modifier.height(8.dp))

                    // Error block
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Error, null, tint = NeonRed, modifier = Modifier.size(14.dp))
                        Text("PDF Renderer Error", style = MaterialTheme.typography.labelMedium,
                            color = NeonRed, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color.Black.copy(0.45f), RoundedCornerShape(10.dp))
                            .border(0.5.dp, NeonRed.copy(0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            errorMessage.ifBlank { "No error message captured." },
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonRed.copy(0.9f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    // Raw URI toggle
                    Row(
                        modifier = Modifier.clickable { showRawUri = !showRawUri }
                            .fillMaxWidth()
                            .background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (showRawUri) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(16.dp)
                        )
                        Text("${if (showRawUri) "Hide" else "Show"} Raw URI",
                            style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(0.7f))
                    }
                    AnimatedVisibility(visible = showRawUri) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color.Black.copy(0.4f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, NeonCyan.copy(0.2f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(rawUri, style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.5f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 16.sp)
                        }
                    }
                }
            }
        }

        // Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val mime = context.contentResolver.getType(uri)
                            ?: if (detectedMime.isNotBlank()) detectedMime else "*/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try { context.startActivity(Intent.createChooser(intent, "Open with")) } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.18f)),
                    border = BorderStroke(1.dp, NeonCyan.copy(0.65f))
                ) {
                    Icon(Icons.Default.OpenInNew, null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Open in External App", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (detectedMime.isNotBlank()) detectedMime else "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try { context.startActivity(Intent.createChooser(shareIntent, "Share $title")) } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(0.7f))
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share File")
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = NeonCyan.copy(0.55f), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(0.85f),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            lineHeight = 18.sp)
        Spacer(Modifier.height(4.dp))
        NeonDivider(Color.White.copy(0.07f))
    }
}

// ─── File Error View ──────────────────────────────────────────────────────────

@Composable
private fun FileErrorView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ThreeDIconBox(icon = Icons.Default.BrokenImage, tint = NeonRed, size = 80.dp, iconSize = 44.dp)
            Text("File not found", style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold)
            Text("The file may have been moved or deleted.",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.3f))
        }
    }
}
