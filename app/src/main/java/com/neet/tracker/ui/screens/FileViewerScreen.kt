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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.neet.tracker.util.AnnotationManager
import com.neet.tracker.util.AnnotationStroke
import com.neet.tracker.util.AnnotationTextBox
import com.neet.tracker.util.AnnotationTool
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontStyle

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

private val ANNOT_COLORS_ARGB = listOf(
    android.graphics.Color.RED,
    android.graphics.Color.parseColor("#FF9800"),
    android.graphics.Color.parseColor("#FFEB3B"),
    android.graphics.Color.GREEN,
    android.graphics.Color.CYAN,
    android.graphics.Color.BLUE,
    android.graphics.Color.parseColor("#9C27B0"),
    android.graphics.Color.WHITE,
    android.graphics.Color.BLACK,
)
private val ANNOT_WIDTHS = listOf(3f to "XS", 6f to "S", 10f to "M", 16f to "L", 24f to "XL")
private val TEXTBOX_BG_OPTIONS = listOf(
    0 to "None",
    android.graphics.Color.argb(0xDD, 0x07, 0x0F, 0x1C) to "Dark",
    android.graphics.Color.argb(0xDD, 0xFF, 0xFF, 0xFF) to "White",
    android.graphics.Color.argb(0xCC, 0xFF, 0xEB, 0x3B) to "Yellow",
    android.graphics.Color.argb(0xCC, 0x1B, 0x3A, 0x2F) to "Forest",
)

// ─── Universal File Viewer ─────────────────────────────────────────────────────

@Composable
fun FileViewerScreen(navController: NavController, fileUri: String, title: String) {
    val context = LocalContext.current
    // Local file paths (copied to app-internal storage) start with "/".
    // Content:// URIs are used for backwards-compat with old uploads.
    val isLocalFile = remember(fileUri) { fileUri.startsWith("/") }
    val localFile   = remember(fileUri) { if (isLocalFile) File(fileUri) else null }
    val uri = remember(fileUri) {
        if (isLocalFile) null
        else try { Uri.parse(fileUri) } catch (e: Exception) { null }
    }

    // Resolve display name — for local files just use the filename.
    val resolvedDisplayName = remember(fileUri) {
        if (isLocalFile) return@remember File(fileUri).name
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
        if (isLocalFile) {
            val ext = File(fileUri).extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
        } else if (uri != null) {
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
        if (isLocalFile) File(fileUri).extension.lowercase()
        else {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.takeIf { it.isNotBlank() }
                ?: if (resolvedDisplayName.isNotBlank()) resolvedDisplayName.substringAfterLast('.', "").lowercase()
                else MimeTypeMap.getFileExtensionFromUrl(fileUri)?.lowercase()
                    ?: fileUri.substringAfterLast('.', "").lowercase()
        }
    }
    val isImage = mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    // Try PDF rendering for any non-image, whether we have a local File or a content:// URI.
    val tryAsPdf = !isImage && (uri != null || localFile != null)

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
    var bookmarkTick       by remember { mutableStateOf(0) }
    var isHorizontalScroll by remember { mutableStateOf(false) }
    var zoomEnabled        by remember { mutableStateOf(false) }

    // ── Annotation fullscreen + floating toolbar ────────────────────────────────
    var annotFullScreen      by remember { mutableStateOf(false) }
    var annotToolbarExpanded by remember { mutableStateOf(true) }
    var annotToolbarOffsetX  by remember { mutableFloatStateOf(16f) }
    var annotToolbarOffsetY  by remember { mutableFloatStateOf(300f) }
    var annotZoomEnabled     by remember { mutableStateOf(false) }

    // ── Annotation / Draw-on-PDF ───────────────────────────────────────────────
    val annoScope           = rememberCoroutineScope()
    var annotationMode      by remember { mutableStateOf(false) }
    var annotationTool      by remember { mutableStateOf(AnnotationTool.PEN) }
    var annotationColorArgb by remember { mutableIntStateOf(android.graphics.Color.RED) }
    var annotationWidthDp   by remember { mutableFloatStateOf(6f) }
    var allPageStrokes      by remember { mutableStateOf<Map<Int, List<AnnotationStroke>>>(emptyMap()) }
    var annoUndoStack       by remember { mutableStateOf<List<Map<Int, List<AnnotationStroke>>>>(emptyList()) }
    var annoRedoStack       by remember { mutableStateOf<List<Map<Int, List<AnnotationStroke>>>>(emptyList()) }
    var allPageTextBoxes    by remember { mutableStateOf<Map<Int, List<AnnotationTextBox>>>(emptyMap()) }
    var pendingTextPos      by remember { mutableStateOf<Pair<Float, Float>?>(null) }

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

    // ── Load saved annotations ─────────────────────────────────────────────────
    LaunchedEffect(fileUri) {
        if (tryAsPdf) {
            val loaded = AnnotationManager.load(context, fileUri)
            if (loaded.isNotEmpty()) allPageStrokes = loaded
        }
    }
    LaunchedEffect(fileUri) {
        if (tryAsPdf) {
            val loadedTexts = AnnotationManager.loadTextBoxes(context, fileUri)
            if (loadedTexts.isNotEmpty()) allPageTextBoxes = loadedTexts
        }
    }

    // ── PDF loading ────────────────────────────────────────────────────────────
    LaunchedEffect(fileUri) {
        if (tryAsPdf) {
            pdfLoading = true; pdfError = false; pdfErrorMessage = ""
            withContext(Dispatchers.IO) {
                try {
                    // Local files (copied to internal storage): open directly — no permissions needed.
                    // Content:// URIs (legacy uploads): use content resolver.
                    val pfd: ParcelFileDescriptor? = when {
                        localFile != null -> ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        uri != null       -> context.contentResolver.openFileDescriptor(uri, "r")
                        else              -> null
                    }
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
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar (hidden in focus mode) ─────────────────────────────────
            AnimatedVisibility(visible = !focusMode && !annotFullScreen, enter = fadeIn(), exit = fadeOut()) {
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
                            val mime = if (tryAsPdf) "application/pdf" else if (isImage) "image/*" else "*/*"
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
                        // Draw / annotate on PDF
                        // NOTE: animateFloatAsState must be called unconditionally (Compose rule).
                        val annotGlow by animateFloatAsState(
                            if (annotationMode) 1f else 0f, tween(250), label = "annot"
                        )
                        if (pdfPages.isNotEmpty()) {
                            IconButton(onClick = {
                                annotationMode = !annotationMode
                                if (annotationMode) {
                                    scale = 1f; panOffset = Offset.Zero
                                    focusMode = false; notesExpanded = false; showThumbs = false
                                    annotFullScreen = true
                                    annotToolbarExpanded = true
                                    annotZoomEnabled = false
                                } else {
                                    annotFullScreen = false
                                    annotZoomEnabled = false
                                }
                            }) {
                                Box(
                                    modifier = Modifier.size(34.dp)
                                        .background(NeonOrange.copy(0.22f * annotGlow), RoundedCornerShape(10.dp))
                                        .border(BorderStroke(maxOf(0.5f, annotGlow).dp, NeonOrange.copy(0.15f + 0.5f * annotGlow)), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Edit, null,
                                        tint = if (annotationMode) NeonOrange else Color.White.copy(0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
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
            if (pdfPages.isNotEmpty() && !annotFullScreen) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(0.07f))) {
                    val animProg by animateFloatAsState(progressFraction, tween(600), label = "prog")
                    Box(
                        modifier = Modifier.fillMaxWidth(animProg).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(NeonCyan, NeonPurple.copy(0.75f))))
                    )
                }
            }

            // ── Viewer controls strip (scroll direction + zoom) ────────────────
            AnimatedVisibility(visible = pdfPages.isNotEmpty() && !focusMode && !annotFullScreen, enter = fadeIn(), exit = fadeOut()) {
                UvViewerControls(
                    isHorizontalScroll = isHorizontalScroll,
                    onScrollToggle = {
                        isHorizontalScroll = !isHorizontalScroll
                        scale = 1f; panOffset = Offset.Zero
                    },
                    zoomEnabled = zoomEnabled,
                    onZoomToggle = {
                        zoomEnabled = !zoomEnabled
                        if (!zoomEnabled) { scale = 1f; panOffset = Offset.Zero }
                    }
                )
            }

            // ── Annotation toolbar (static strip — only shown when NOT in fullscreen mode) ──
            AnimatedVisibility(
                visible = annotationMode && pdfPages.isNotEmpty() && !annotFullScreen,
                enter = slideInVertically { -it } + fadeIn(tween(250)),
                exit  = slideOutVertically { -it } + fadeOut(tween(200))
            ) {
                PdfAnnotationToolbar(
                    tool          = annotationTool,
                    colorArgb     = annotationColorArgb,
                    widthDp       = annotationWidthDp,
                    canUndo       = annoUndoStack.isNotEmpty(),
                    canRedo       = annoRedoStack.isNotEmpty(),
                    onToolChange  = { annotationTool = it },
                    onColorChange = { annotationColorArgb = it },
                    onWidthChange = { annotationWidthDp = it },
                    onUndo = {
                        annoUndoStack.lastOrNull()?.let { snap ->
                            annoRedoStack = annoRedoStack + allPageStrokes
                            allPageStrokes = snap
                            annoUndoStack = annoUndoStack.dropLast(1)
                            annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                        }
                    },
                    onRedo = {
                        annoRedoStack.lastOrNull()?.let { snap ->
                            annoUndoStack = (annoUndoStack + allPageStrokes).takeLast(50)
                            allPageStrokes = snap
                            annoRedoStack = annoRedoStack.dropLast(1)
                            annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                        }
                    },
                    onClearPage = {
                        if ((allPageStrokes[currentPage] ?: emptyList()).isNotEmpty()) {
                            annoUndoStack = (annoUndoStack + allPageStrokes).takeLast(50)
                            annoRedoStack = emptyList()
                            allPageStrokes = allPageStrokes + (currentPage to emptyList())
                            annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                        }
                    },
                    onDone = {
                        annotationMode = false
                        annotFullScreen = false
                        annotZoomEnabled = false
                        annoScope.launch {
                            AnnotationManager.save(context, fileUri, allPageStrokes)
                            AnnotationManager.saveTextBoxes(context, fileUri, allPageTextBoxes)
                        }
                    }
                )
            }

            // ── Bookmarks panel ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showBookmarks && bookmarkedPages.isNotEmpty() && !annotFullScreen,
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
                    fileUri.isBlank() -> FileErrorView()
                    isImage -> {
                        // Local file image (e.g. copied photo) — render directly from File.
                        if (localFile != null) {
                            AsyncImage(
                                model              = localFile,
                                contentDescription = title,
                                modifier           = Modifier.fillMaxSize()
                            )
                        } else if (uri != null) {
                            ImageViewer(uri = uri, title = title)
                        } else {
                            FileErrorView()
                        }
                    }
                    tryAsPdf -> {
                        when {
                            pdfLoading -> UvLoadingView()
                            pdfPages.isNotEmpty() -> UvPdfPage(
                                bitmap          = pdfPages[currentPage.coerceIn(0, pdfPages.lastIndex)],
                                scale           = scale,
                                panOffset       = panOffset,
                                onTransform = { s, p ->
                                    scale     = (scale * s).coerceIn(0.5f, 6f)
                                    panOffset = Offset(panOffset.x + p.x, panOffset.y + p.y)
                                },
                                zoomEnabled        = zoomEnabled,
                                isHorizontalScroll = isHorizontalScroll,
                                onPrev = { if (currentPage > 0) { currentPage--; scale = 1f; panOffset = Offset.Zero } },
                                onNext = { if (currentPage < pdfPages.lastIndex) { currentPage++; scale = 1f; panOffset = Offset.Zero } },
                                annotationMode      = annotationMode,
                                annotationTool      = annotationTool,
                                annotationColorArgb = annotationColorArgb,
                                annotationWidthDp   = annotationWidthDp,
                                annotZoomEnabled    = annotZoomEnabled,
                                pageStrokes         = allPageStrokes[currentPage] ?: emptyList(),
                                onStrokeCommit = { stroke ->
                                    annoUndoStack  = (annoUndoStack + allPageStrokes).takeLast(50)
                                    annoRedoStack  = emptyList()
                                    val updated    = (allPageStrokes[currentPage] ?: emptyList()) + stroke
                                    allPageStrokes = allPageStrokes + (currentPage to updated)
                                    annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                                },
                                onEraseGestureStart = {
                                    // Push undo snapshot once per eraser drag gesture
                                    annoUndoStack = (annoUndoStack + allPageStrokes).takeLast(50)
                                    annoRedoStack = emptyList()
                                },
                                onStrokesErase = { removed ->
                                    val current    = allPageStrokes[currentPage] ?: emptyList()
                                    val removedSet = removed.toSet()
                                    val updated    = current.filter { it !in removedSet }
                                    allPageStrokes = allPageStrokes + (currentPage to updated)
                                    annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                                },
                                pageTextBoxes   = allPageTextBoxes[currentPage] ?: emptyList(),
                                onTextBoxPlace  = { xNorm, yNorm -> pendingTextPos = Pair(xNorm, yNorm) },
                                onTextBoxDelete = { id ->
                                    val cur = allPageTextBoxes[currentPage] ?: emptyList()
                                    val upd = cur.filter { it.id != id }
                                    allPageTextBoxes = allPageTextBoxes + (currentPage to upd)
                                    annoScope.launch { AnnotationManager.saveTextBoxes(context, fileUri, allPageTextBoxes) }
                                }
                            )
                            pdfError -> {
                                if (uri != null) {
                                    FileFailureLog(
                                        uri = uri, title = title,
                                        detectedMime = mimeType, detectedExt = extension,
                                        resolvedName = resolvedDisplayName, rawUri = fileUri,
                                        errorMessage = pdfErrorMessage, context = context
                                    )
                                } else {
                                    // Local file that couldn't be rendered as PDF (e.g. it's a
                                    // Word doc). Show simpler error with Open Externally button.
                                    LocalFileRenderError(
                                        localFile    = localFile,
                                        title        = title,
                                        errorMessage = pdfErrorMessage,
                                        context      = context
                                    )
                                }
                            }
                            else -> UvLoadingView()
                        }
                    }
                    else -> {
                        if (uri != null) {
                            FileFailureLog(
                                uri = uri, title = title,
                                detectedMime = mimeType, detectedExt = extension,
                                resolvedName = resolvedDisplayName, rawUri = fileUri,
                                errorMessage = "URI scheme not supported for in-app viewing.", context = context
                            )
                        } else {
                            FileErrorView()
                        }
                    }
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
                visible = pdfPages.isNotEmpty() && !focusMode && !annotFullScreen,
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
                visible = showThumbs && pdfPages.isNotEmpty() && !annotFullScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit  = slideOutVertically { it } + fadeOut()
            ) {
                UvThumbStrip(pdfPages, currentPage) {
                    currentPage = it; scale = 1f; panOffset = Offset.Zero
                }
            }

            // ── Page nav bar ───────────────────────────────────────────────────
            if (pdfPages.isNotEmpty() && !focusMode && !annotFullScreen) {
                UvPageNavBar(
                    current = currentPage,
                    total   = totalPages,
                    onPrev  = { if (currentPage > 0) { currentPage--; scale = 1f; panOffset = Offset.Zero } },
                    onNext  = { if (currentPage < pdfPages.lastIndex) { currentPage++; scale = 1f; panOffset = Offset.Zero } }
                )
            }

            // ── Annotation panel ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = notesExpanded && !annotFullScreen,
                enter   = slideInVertically { it } + fadeIn(tween(250)),
                exit    = slideOutVertically { it } + fadeOut(tween(200))
            ) {
                AnnotationPanel(text = notesText, onTextChange = { notesText = it })
            }
        }

        // ── Floating annotation toolbar (fullscreen draw mode) ─────────────────
        if (annotationMode && pdfPages.isNotEmpty()) {
            FloatingAnnotToolbar(
                expanded         = annotToolbarExpanded,
                offsetX          = annotToolbarOffsetX,
                offsetY          = annotToolbarOffsetY,
                onDrag           = { dx, dy ->
                    annotToolbarOffsetX = (annotToolbarOffsetX + dx).coerceAtLeast(0f)
                    annotToolbarOffsetY = (annotToolbarOffsetY + dy).coerceAtLeast(0f)
                },
                onToggleExpanded = { annotToolbarExpanded = !annotToolbarExpanded },
                tool             = annotationTool,
                colorArgb        = annotationColorArgb,
                widthDp          = annotationWidthDp,
                canUndo          = annoUndoStack.isNotEmpty(),
                canRedo          = annoRedoStack.isNotEmpty(),
                zoomEnabled      = annotZoomEnabled,
                onToolChange     = { annotationTool = it; if (it == AnnotationTool.TEXT) annotZoomEnabled = false },
                onColorChange    = { annotationColorArgb = it },
                onWidthChange    = { annotationWidthDp = it },
                onUndo = {
                    annoUndoStack.lastOrNull()?.let { snap ->
                        annoRedoStack  = annoRedoStack + allPageStrokes
                        allPageStrokes = snap
                        annoUndoStack  = annoUndoStack.dropLast(1)
                        annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                    }
                },
                onRedo = {
                    annoRedoStack.lastOrNull()?.let { snap ->
                        annoUndoStack  = (annoUndoStack + allPageStrokes).takeLast(50)
                        allPageStrokes = snap
                        annoRedoStack  = annoRedoStack.dropLast(1)
                        annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                    }
                },
                onClearPage = {
                    if ((allPageStrokes[currentPage] ?: emptyList()).isNotEmpty()) {
                        annoUndoStack  = (annoUndoStack + allPageStrokes).takeLast(50)
                        annoRedoStack  = emptyList()
                        allPageStrokes = allPageStrokes + (currentPage to emptyList())
                        annoScope.launch { AnnotationManager.save(context, fileUri, allPageStrokes) }
                    }
                },
                onZoomToggle = { annotZoomEnabled = !annotZoomEnabled },
                onDone = {
                    annotationMode  = false
                    annotFullScreen = false
                    annotZoomEnabled = false
                    annoScope.launch {
                        AnnotationManager.save(context, fileUri, allPageStrokes)
                        AnnotationManager.saveTextBoxes(context, fileUri, allPageTextBoxes)
                    }
                }
            )
        }

        } // end Box

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
        pendingTextPos?.let { (xNorm, yNorm) ->
            TextBoxEditorDialog(
                xNorm = xNorm,
                yNorm = yNorm,
                onSave = { tb ->
                    val cur = allPageTextBoxes[currentPage] ?: emptyList()
                    allPageTextBoxes = allPageTextBoxes + (currentPage to cur + tb)
                    annoScope.launch { AnnotationManager.saveTextBoxes(context, fileUri, allPageTextBoxes) }
                    pendingTextPos = null
                },
                onDismiss = { pendingTextPos = null }
            )
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

// ─── Universal PDF Page (single page, pinch-to-zoom + pan + annotation) ───────

@Composable
private fun UvPdfPage(
    bitmap: Bitmap,
    scale: Float,
    panOffset: Offset,
    onTransform: (scaleChange: Float, panChange: Offset) -> Unit,
    zoomEnabled: Boolean = false,
    isHorizontalScroll: Boolean = false,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    annotationMode: Boolean = false,
    annotationTool: AnnotationTool = AnnotationTool.PEN,
    annotationColorArgb: Int = android.graphics.Color.RED,
    annotationWidthDp: Float = 6f,
    annotZoomEnabled: Boolean = false,
    pageStrokes: List<AnnotationStroke> = emptyList(),
    onStrokeCommit: (AnnotationStroke) -> Unit = {},
    onEraseGestureStart: () -> Unit = {},
    onStrokesErase: (List<AnnotationStroke>) -> Unit = {},
    pageTextBoxes: List<AnnotationTextBox> = emptyList(),
    onTextBoxPlace: (Float, Float) -> Unit = { _, _ -> },
    onTextBoxDelete: (String) -> Unit = {},
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080F))
            .run {
                if (!annotationMode) {
                    if (zoomEnabled) {
                        pointerInput(zoomEnabled) {
                            detectTransformGestures { _, pan, zoom, _ -> onTransform(zoom, pan) }
                        }
                    } else {
                        pointerInput(isHorizontalScroll) {
                            var totalX = 0f
                            var totalY = 0f
                            detectDragGestures(
                                onDragStart = { totalX = 0f; totalY = 0f },
                                onDrag = { _, dragAmount ->
                                    totalX += dragAmount.x
                                    totalY += dragAmount.y
                                },
                                onDragEnd = {
                                    val threshold = 60f
                                    if (isHorizontalScroll) {
                                        when {
                                            totalX < -threshold -> onNext()
                                            totalX > threshold  -> onPrev()
                                        }
                                    } else {
                                        when {
                                            totalY < -threshold -> onNext()
                                            totalY > threshold  -> onPrev()
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else this
            },
        contentAlignment = Alignment.Center
    ) {
        val imageWidthPx  = with(density) { maxWidth.toPx() }
        val aspect        = bitmap.height.toFloat() / bitmap.width.toFloat()
        val imageHeightPx = imageWidthPx * aspect
        val imageHeightDp = with(density) { imageHeightPx.toDp() }

        // PDF page bitmap
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

        // Read-only stroke display when not in annotation mode
        if (!annotationMode && pageStrokes.isNotEmpty()) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(imageHeightDp).clip(RoundedCornerShape(8.dp))
            ) {
                for (stroke in pageStrokes) {
                    if (stroke.points.size < 2) continue
                    val swPx = with(density) { stroke.widthDp.dp.toPx() }
                    val path = Path()
                    stroke.points.forEachIndexed { idx, (px, py) ->
                        val sx = px * imageWidthPx; val sy = py * imageHeightPx
                        if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                    }
                    drawPath(path, Color(stroke.colorArgb),
                        style = DrawStyle(width = swPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (stroke.tool == "arrow" && stroke.points.size >= 2) {
                        val tipPt  = stroke.points.last()
                        val prevPt = stroke.points[(stroke.points.lastIndex - 1).coerceAtLeast(0)]
                        val tipX   = tipPt.first  * imageWidthPx
                        val tipY   = tipPt.second * imageHeightPx
                        val angle  = atan2(tipY - prevPt.second * imageHeightPx, tipX - prevPt.first * imageWidthPx)
                        val aLen   = swPx * 4.5f
                        val wing   = kotlin.math.PI.toFloat() * 0.72f
                        val arrowPath = Path().apply {
                            moveTo(tipX, tipY)
                            lineTo(tipX + aLen * cos(angle + wing), tipY + aLen * sin(angle + wing))
                            lineTo(tipX + aLen * cos(angle - wing), tipY + aLen * sin(angle - wing))
                            close()
                        }
                        drawPath(arrowPath, Color(stroke.colorArgb))
                    }
                }
            }
        }

        // Interactive annotation overlay (annotation mode only)
        if (annotationMode) {
            PdfAnnotationOverlay(
                modifier            = Modifier.fillMaxWidth().height(imageHeightDp).clip(RoundedCornerShape(8.dp)),
                imageWidthPx        = imageWidthPx,
                imageHeightPx       = imageHeightPx,
                strokes             = pageStrokes,
                tool                = annotationTool,
                colorArgb           = annotationColorArgb,
                strokeWidthDp       = annotationWidthDp,
                annotZoomEnabled    = annotZoomEnabled,
                onZoomTransform     = onTransform,
                onTextBoxPlace      = onTextBoxPlace,
                onStrokeCommit      = onStrokeCommit,
                onEraseGestureStart = onEraseGestureStart,
                onStrokesErase      = onStrokesErase,
            )
        }

        // Text box composable overlays (visible in both read and annotation mode)
        if (pageTextBoxes.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(imageHeightDp)) {
                pageTextBoxes.forEach { tb ->
                    val xDp = with(density) { (tb.xNorm * imageWidthPx).toDp() }
                    val yDp = with(density) { (tb.yNorm * imageHeightPx).toDp() }
                    Box(
                        modifier = Modifier
                            .offset(xDp, yDp)
                            .shadow(8.dp, RoundedCornerShape(8.dp), spotColor = Color(tb.colorArgb).copy(0.2f))
                            .background(
                                if (tb.bgArgb == 0) Color.Transparent else Color(tb.bgArgb),
                                RoundedCornerShape(8.dp)
                            )
                            .then(
                                if (tb.hasBorder) Modifier.border(1.dp, Color(tb.colorArgb).copy(0.75f), RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text       = tb.text,
                                color      = Color(tb.colorArgb),
                                fontSize   = tb.fontSizeSp.sp,
                                fontWeight = if (tb.isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle  = if (tb.isItalic) FontStyle.Italic else FontStyle.Normal,
                                lineHeight = (tb.fontSizeSp * 1.35f).sp
                            )
                            if (annotationMode) {
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(17.dp)
                                        .background(NeonRed.copy(0.88f), CircleShape)
                                        .border(1.dp, NeonRed, CircleShape)
                                        .clickable { onTextBoxDelete(tb.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hints
        if (!annotationMode) {
            if (scale == 1f) {
                val hintIcon = when {
                    zoomEnabled          -> Icons.Default.ZoomIn
                    isHorizontalScroll   -> Icons.Default.MoreHoriz
                    else                 -> Icons.Default.MoreVert
                }
                val hintText = when {
                    zoomEnabled        -> "Pinch to zoom  ·  Drag to pan"
                    isHorizontalScroll -> "Swipe ←  to next  ·  → to prev"
                    else               -> "Swipe ↑  to next  ·  ↓ to prev"
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                        .background(Color.Black.copy(0.55f), RoundedCornerShape(14.dp))
                        .border(0.5.dp, NeonCyan.copy(0.2f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(hintIcon, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(13.dp))
                        Text(hintText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.45f))
                    }
                }
            }
        } else {
            // Annotation mode indicator badge
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    .background(NeonOrange.copy(0.18f), RoundedCornerShape(14.dp))
                    .border(0.5.dp, NeonOrange.copy(0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Edit, null, tint = NeonOrange, modifier = Modifier.size(12.dp))
                    Text(
                        when (annotationTool) {
                            AnnotationTool.PEN         -> "Pen  ·  Draw anywhere on the page"
                            AnnotationTool.HIGHLIGHTER -> "Highlighter  ·  Swipe to highlight"
                            AnnotationTool.ERASER      -> "Eraser  ·  Touch strokes to erase"
                            AnnotationTool.ARROW       -> "Arrow  ·  Draw a curved labeling arrow"
                            AnnotationTool.TEXT        -> "Text  ·  Tap to place a text label"
                        },
                        style = MaterialTheme.typography.labelSmall, color = NeonOrange.copy(0.9f)
                    )
                }
            }
        }
    }
}

// ─── PDF Annotation Overlay ────────────────────────────────────────────────────

@Composable
private fun PdfAnnotationOverlay(
    modifier: Modifier,
    imageWidthPx: Float,
    imageHeightPx: Float,
    strokes: List<AnnotationStroke>,
    tool: AnnotationTool,
    colorArgb: Int,
    strokeWidthDp: Float,
    annotZoomEnabled: Boolean = false,
    onZoomTransform: (Float, Offset) -> Unit = { _, _ -> },
    onTextBoxPlace: (Float, Float) -> Unit = { _, _ -> },
    onStrokeCommit: (AnnotationStroke) -> Unit,
    onEraseGestureStart: () -> Unit,
    onStrokesErase: (List<AnnotationStroke>) -> Unit,
) {
    val density = LocalDensity.current
    val strokeWidthPx  = with(density) { strokeWidthDp.dp.toPx() }
    val eraserRadiusPx = with(density) { 26.dp.toPx() }

    val liveStrokes = remember { mutableStateListOf<AnnotationStroke>() }
    SideEffect { liveStrokes.clear(); liveStrokes.addAll(strokes) }

    var currentPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var eraserPos     by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    Canvas(
        modifier = modifier
            .pointerInput(tool, colorArgb, strokeWidthDp, annotZoomEnabled) {
                if (annotZoomEnabled) {
                    // ── Simultaneous zoom (2-finger) + draw (1-finger) ──────────
                    awaitPointerEventScope {
                        val drawPoints   = mutableListOf<Pair<Float, Float>>()
                        var eraseStarted = false
                        var wasMulti     = false
                        var prevCx = 0f; var prevCy = 0f; var prevDist = 0f
                        while (true) {
                            val event   = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            when {
                                pressed.isEmpty() -> {
                                    // All fingers up — commit any in-progress stroke
                                    if (drawPoints.size >= 2 && tool != AnnotationTool.ERASER) {
                                        val isH = tool == AnnotationTool.HIGHLIGHTER
                                        val fc  = Color(colorArgb).copy(alpha = if (isH) 0.38f else 1f)
                                        onStrokeCommit(AnnotationStroke(
                                            points    = drawPoints.toList(),
                                            colorArgb = fc.toArgb(),
                                            widthDp   = strokeWidthDp * if (isH) 3.5f else 1f,
                                            tool      = when (tool) { AnnotationTool.ARROW -> "arrow"; AnnotationTool.HIGHLIGHTER -> "highlight"; else -> "pen" }
                                        ))
                                    }
                                    drawPoints.clear()
                                    currentPoints = emptyList()
                                    eraserPos     = null
                                    eraseStarted  = false
                                    wasMulti      = false
                                    prevDist      = 0f
                                    event.changes.forEach { it.consume() }
                                }
                                pressed.size >= 2 -> {
                                    // Two-finger zoom/pan — commit partial stroke first
                                    if (drawPoints.size >= 2 && tool != AnnotationTool.ERASER) {
                                        val isH = tool == AnnotationTool.HIGHLIGHTER
                                        val fc  = Color(colorArgb).copy(alpha = if (isH) 0.38f else 1f)
                                        onStrokeCommit(AnnotationStroke(drawPoints.toList(), fc.toArgb(), strokeWidthDp * if (isH) 3.5f else 1f, when (tool) { AnnotationTool.ARROW -> "arrow"; AnnotationTool.HIGHLIGHTER -> "highlight"; else -> "pen" }))
                                        drawPoints.clear(); currentPoints = emptyList()
                                    }
                                    val p1  = pressed[0].position
                                    val p2  = pressed[1].position
                                    val cx  = (p1.x + p2.x) / 2f
                                    val cy  = (p1.y + p2.y) / 2f
                                    val ddx = p1.x - p2.x
                                    val ddy = p1.y - p2.y
                                    val dist = sqrt(ddx * ddx + ddy * ddy)
                                    if (wasMulti && prevDist > 0f) {
                                        onZoomTransform(dist / prevDist, Offset(cx - prevCx, cy - prevCy))
                                    }
                                    prevCx = cx; prevCy = cy; prevDist = dist
                                    wasMulti = true
                                    pressed.forEach { it.consume() }
                                }
                                else -> {
                                    // Single finger
                                    if (wasMulti) {
                                        wasMulti = false; prevDist = 0f
                                        pressed.forEach { it.consume() }
                                    } else {
                                        val c  = pressed[0]
                                        val nx = (c.position.x / imageWidthPx).coerceIn(0f, 1f)
                                        val ny = (c.position.y / imageHeightPx).coerceIn(0f, 1f)
                                        if (tool == AnnotationTool.ERASER) {
                                            eraserPos = Pair(nx, ny)
                                            val hit = liveStrokes.filter { s ->
                                                s.points.any { (px, py) ->
                                                    val ex = (px - nx) * imageWidthPx
                                                    val ey = (py - ny) * imageHeightPx
                                                    sqrt(ex * ex + ey * ey) < eraserRadiusPx
                                                }
                                            }
                                            if (hit.isNotEmpty()) {
                                                if (!eraseStarted) { onEraseGestureStart(); eraseStarted = true }
                                                onStrokesErase(hit)
                                            }
                                        } else {
                                            drawPoints.add(Pair(nx, ny))
                                            currentPoints = drawPoints.toList()
                                        }
                                        c.consume()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ── Original single-touch draw-only mode ────────────────────
                    if (tool == AnnotationTool.TEXT) {
                        detectTapGestures { offset ->
                            val nx = (offset.x / imageWidthPx).coerceIn(0f, 1f)
                            val ny = (offset.y / imageHeightPx).coerceIn(0f, 1f)
                            onTextBoxPlace(nx, ny)
                        }
                    } else {
                        var eraseStarted = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                val nx = (offset.x / imageWidthPx).coerceIn(0f, 1f)
                                val ny = (offset.y / imageHeightPx).coerceIn(0f, 1f)
                                if (tool == AnnotationTool.ERASER) {
                                    eraserPos    = Pair(nx, ny)
                                    eraseStarted = false
                                } else {
                                    currentPoints = listOf(Pair(nx, ny))
                                }
                            },
                            onDrag = { change, _ ->
                                val nx = (change.position.x / imageWidthPx).coerceIn(0f, 1f)
                                val ny = (change.position.y / imageHeightPx).coerceIn(0f, 1f)
                                if (tool == AnnotationTool.ERASER) {
                                    eraserPos = Pair(nx, ny)
                                    val hit = liveStrokes.filter { stroke ->
                                        stroke.points.any { (px, py) ->
                                            val dx = (px - nx) * imageWidthPx
                                            val dy = (py - ny) * imageHeightPx
                                            sqrt(dx * dx + dy * dy) < eraserRadiusPx
                                        }
                                    }
                                    if (hit.isNotEmpty()) {
                                        if (!eraseStarted) { onEraseGestureStart(); eraseStarted = true }
                                        onStrokesErase(hit)
                                    }
                                } else {
                                    currentPoints = currentPoints + Pair(nx, ny)
                                }
                            },
                            onDragEnd = {
                                if (tool != AnnotationTool.ERASER && currentPoints.size >= 2) {
                                    val isHighlight = tool == AnnotationTool.HIGHLIGHTER
                                    val finalColor  = Color(colorArgb).copy(alpha = if (isHighlight) 0.38f else 1f)
                                    onStrokeCommit(AnnotationStroke(
                                        points    = currentPoints,
                                        colorArgb = finalColor.toArgb(),
                                        widthDp   = strokeWidthDp * if (isHighlight) 3.5f else 1f,
                                        tool      = when (tool) { AnnotationTool.ARROW -> "arrow"; AnnotationTool.HIGHLIGHTER -> "highlight"; else -> "pen" }
                                    ))
                                }
                                currentPoints = emptyList()
                                eraserPos     = null
                                eraseStarted  = false
                            },
                            onDragCancel = {
                                currentPoints = emptyList(); eraserPos = null; eraseStarted = false
                            }
                        )
                    }
                }
            }
    ) {
        // Draw all committed strokes for this page
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val swPx = with(density) { stroke.widthDp.dp.toPx() }
            val path = Path()
            stroke.points.forEachIndexed { idx, (px, py) ->
                val sx = px * imageWidthPx; val sy = py * imageHeightPx
                if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            drawPath(path, Color(stroke.colorArgb),
                style = DrawStyle(width = swPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Filled arrowhead for arrow strokes
            if (stroke.tool == "arrow" && stroke.points.size >= 2) {
                val tipPt  = stroke.points.last()
                val prevPt = stroke.points[(stroke.points.lastIndex - 1).coerceAtLeast(0)]
                val tipX   = tipPt.first  * imageWidthPx
                val tipY   = tipPt.second * imageHeightPx
                val angle  = atan2(tipY - prevPt.second * imageHeightPx, tipX - prevPt.first * imageWidthPx)
                val aLen   = swPx * 4.5f
                val wing   = kotlin.math.PI.toFloat() * 0.72f
                val arrowPath = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(tipX + aLen * cos(angle + wing), tipY + aLen * sin(angle + wing))
                    lineTo(tipX + aLen * cos(angle - wing), tipY + aLen * sin(angle - wing))
                    close()
                }
                drawPath(arrowPath, Color(stroke.colorArgb))
            }
        }

        // Draw the in-progress stroke in real-time
        if (currentPoints.size >= 2) {
            val isHighlight = tool == AnnotationTool.HIGHLIGHTER
            val liveColor   = Color(colorArgb).copy(alpha = if (isHighlight) 0.38f else 1f)
            val liveWidth   = strokeWidthPx * if (isHighlight) 3.5f else 1f
            val path = Path()
            currentPoints.forEachIndexed { idx, (px, py) ->
                val sx = px * imageWidthPx; val sy = py * imageHeightPx
                if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            drawPath(path, liveColor,
                style = DrawStyle(width = liveWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Live arrowhead preview
            if (tool == AnnotationTool.ARROW && currentPoints.size >= 2) {
                val tipPt  = currentPoints.last()
                val prevPt = currentPoints[currentPoints.lastIndex - 1]
                val tipX   = tipPt.first  * imageWidthPx
                val tipY   = tipPt.second * imageHeightPx
                val angle  = atan2(tipY - prevPt.second * imageHeightPx, tipX - prevPt.first * imageWidthPx)
                val aLen   = strokeWidthPx * 4.5f
                val wing   = kotlin.math.PI.toFloat() * 0.72f
                val arrowPath = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(tipX + aLen * cos(angle + wing), tipY + aLen * sin(angle + wing))
                    lineTo(tipX + aLen * cos(angle - wing), tipY + aLen * sin(angle - wing))
                    close()
                }
                drawPath(arrowPath, liveColor)
            }
        }

        // Eraser circle cursor
        eraserPos?.let { (nx, ny) ->
            val cx = nx * imageWidthPx; val cy = ny * imageHeightPx
            drawCircle(Color.Black.copy(0.15f), eraserRadiusPx, Offset(cx, cy))
            drawCircle(Color.White.copy(0.65f), eraserRadiusPx, Offset(cx, cy),
                style = DrawStyle(width = with(density) { 2.dp.toPx() }))
            drawCircle(Color.White.copy(0.9f), with(density) { 3.dp.toPx() }, Offset(cx, cy))
        }
    }
}

// ─── Floating Annotation Toolbar (draggable, collapsible) ─────────────────────

@Composable
private fun FloatingAnnotToolbar(
    expanded: Boolean,
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float) -> Unit,
    onToggleExpanded: () -> Unit,
    tool: AnnotationTool,
    colorArgb: Int,
    widthDp: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    zoomEnabled: Boolean,
    onToolChange: (AnnotationTool) -> Unit,
    onColorChange: (Int) -> Unit,
    onWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onZoomToggle: () -> Unit,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
    ) {
        if (!expanded) {
            // ── Collapsed: floating pencil bubble ──────────────────────────────
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(14.dp, CircleShape, spotColor = NeonOrange.copy(0.55f))
                    .background(
                        Brush.radialGradient(listOf(NeonOrange.copy(0.38f), Color(0xFF0B1020))),
                        CircleShape
                    )
                    .border(1.5.dp, NeonOrange.copy(0.85f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
                    }
                    .clickable { onToggleExpanded() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, null, tint = NeonOrange, modifier = Modifier.size(22.dp))
            }
        } else {
            // ── Expanded: full floating panel ──────────────────────────────────
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 210.dp, max = 280.dp)
                    .shadow(22.dp, RoundedCornerShape(18.dp), spotColor = NeonOrange.copy(0.45f))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0E1A2A), Color(0xFF070F1C))),
                        RoundedCornerShape(18.dp)
                    )
                    .border(1.dp, NeonOrange.copy(0.55f), RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
            ) {
                // ── Drag handle + title + collapse ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
                        }
                        .background(NeonOrange.copy(0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.DragHandle, null,
                        tint = Color.White.copy(0.35f), modifier = Modifier.size(16.dp))
                    Icon(Icons.Default.Edit, null,
                        tint = NeonOrange.copy(0.9f), modifier = Modifier.size(13.dp))
                    Text("Draw Tools", style = MaterialTheme.typography.labelMedium,
                        color = NeonOrange, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ExpandLess, null,
                            tint = Color.White.copy(0.5f), modifier = Modifier.size(16.dp))
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NeonOrange.copy(0.3f)))

                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    // ── Row 1: Tool buttons ────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(AnnotationTool.PEN,         Icons.Default.Edit,  "Pen"),
                            Triple(AnnotationTool.HIGHLIGHTER, Icons.Default.Brush, "High."),
                            Triple(AnnotationTool.ERASER,      Icons.Default.Close, "Erase"),
                        ).forEach { (t, icon, label) ->
                            val sel = tool == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (sel) NeonOrange.copy(0.28f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                                    .border(if (sel) 1.dp else 0.5.dp, if (sel) NeonOrange.copy(0.85f) else Color.White.copy(0.15f), RoundedCornerShape(10.dp))
                                    .clickable { onToolChange(t) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(icon, null,
                                        tint = if (sel) NeonOrange else Color.White.copy(0.5f),
                                        modifier = Modifier.size(15.dp))
                                    Text(label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = if (sel) NeonOrange else Color.White.copy(0.4f),
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    // ── Row 2: Arrow + Text tools ──────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(AnnotationTool.ARROW, Icons.Default.ArrowForward, "Arrow"),
                            Triple(AnnotationTool.TEXT,  Icons.Default.TextFields,   "Text"),
                        ).forEach { (t, icon, label) ->
                            val sel = tool == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (sel) NeonCyan.copy(0.28f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                                    .border(if (sel) 1.dp else 0.5.dp, if (sel) NeonCyan.copy(0.85f) else Color.White.copy(0.15f), RoundedCornerShape(10.dp))
                                    .clickable { onToolChange(t) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(icon, null,
                                        tint = if (sel) NeonCyan else Color.White.copy(0.5f),
                                        modifier = Modifier.size(15.dp))
                                    Text(label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = if (sel) NeonCyan else Color.White.copy(0.4f),
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    // ── Row 3: Color chips ─────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ANNOT_COLORS_ARGB.forEach { argb ->
                            val sel = argb == colorArgb
                            Box(
                                modifier = Modifier
                                    .size(if (sel) 27.dp else 21.dp)
                                    .shadow(if (sel) 5.dp else 0.dp, CircleShape, spotColor = Color(argb).copy(0.65f))
                                    .border(if (sel) 2.dp else 0.5.dp, if (sel) Color.White else Color.White.copy(0.2f), CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .clickable { onColorChange(argb) }
                            )
                        }
                    }

                    // ── Row 3: Stroke widths ───────────────────────────────────
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ANNOT_WIDTHS.forEach { (w, label) ->
                            val sel = widthDp == w
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(if (sel) NeonOrange.copy(0.22f) else Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                                    .border(if (sel) 1.dp else 0.5.dp, if (sel) NeonOrange.copy(0.75f) else Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                                    .clickable { onWidthChange(w) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (sel) NeonOrange else Color.White.copy(0.45f),
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NeonOrange.copy(0.18f)))

                    // ── Row 4: Actions ─────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Undo, null,
                                tint = if (canUndo) NeonCyan else Color.White.copy(0.2f),
                                modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Redo, null,
                                tint = if (canRedo) NeonCyan else Color.White.copy(0.2f),
                                modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onClearPage, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteSweep, null,
                                tint = NeonRed.copy(0.7f), modifier = Modifier.size(16.dp))
                        }

                        Spacer(Modifier.weight(1f))

                        // Zoom toggle
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .shadow(if (zoomEnabled) 6.dp else 0.dp, RoundedCornerShape(8.dp), spotColor = NeonCyan.copy(0.4f))
                                .background(if (zoomEnabled) NeonCyan.copy(0.25f) else Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                                .border(1.dp, if (zoomEnabled) NeonCyan.copy(0.8f) else Color.White.copy(0.15f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onZoomToggle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ZoomIn, null,
                                tint = if (zoomEnabled) NeonCyan else Color.White.copy(0.45f),
                                modifier = Modifier.size(16.dp))
                        }

                        // Done
                        Box(
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(9.dp), spotColor = NeonGreen.copy(0.4f))
                                .background(NeonGreen.copy(0.2f), RoundedCornerShape(9.dp))
                                .border(1.dp, NeonGreen.copy(0.7f), RoundedCornerShape(9.dp))
                                .clickable(onClick = onDone)
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Check, null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                                Text("Done", style = MaterialTheme.typography.labelSmall,
                                    color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Zoom mode hint
                    if (zoomEnabled) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(NeonCyan.copy(0.1f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, NeonCyan.copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.ZoomIn, null, tint = NeonCyan, modifier = Modifier.size(11.dp))
                                Text("1 finger draws  ·  2 fingers zoom",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp, color = NeonCyan.copy(0.9f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── PDF Annotation Toolbar ────────────────────────────────────────────────────

@Composable
private fun PdfAnnotationToolbar(
    tool: AnnotationTool,
    colorArgb: Int,
    widthDp: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (AnnotationTool) -> Unit,
    onColorChange: (Int) -> Unit,
    onWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF070D1A))),
            )
            .border(BorderStroke(1.dp, NeonOrange.copy(0.45f)))
            .padding(vertical = 8.dp)
    ) {
        // ── Row 1: Tools  ·  Undo/Redo  ·  Clear  ·  Done ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple(AnnotationTool.PEN,         Icons.Default.Edit,    "Pen"),
                Triple(AnnotationTool.HIGHLIGHTER, Icons.Default.Brush,   "High."),
                Triple(AnnotationTool.ERASER,      Icons.Default.Close,   "Erase"),
            ).forEach { (t, icon, label) ->
                val sel = tool == t
                Box(
                    modifier = Modifier
                        .background(if (sel) NeonOrange.copy(0.25f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                        .border(if (sel) 1.dp else 0.5.dp, if (sel) NeonOrange.copy(0.85f) else Color.White.copy(0.15f), RoundedCornerShape(10.dp))
                        .clickable { onToolChange(t) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(icon, null, tint = if (sel) NeonOrange else Color.White.copy(0.5f), modifier = Modifier.size(15.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                            color = if (sel) NeonOrange else Color.White.copy(0.4f),
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Undo
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Undo, null, tint = if (canUndo) NeonCyan else Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
            }
            // Redo
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Redo, null, tint = if (canRedo) NeonCyan else Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
            }
            // Clear page
            IconButton(onClick = onClearPage, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteSweep, null, tint = NeonRed.copy(0.65f), modifier = Modifier.size(18.dp))
            }
            // Done
            Box(
                modifier = Modifier
                    .background(NeonGreen.copy(0.18f), RoundedCornerShape(10.dp))
                    .border(1.dp, NeonGreen.copy(0.65f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Check, null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                    Text("Done", style = MaterialTheme.typography.labelMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Row 1b: Arrow + Text tools ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple(AnnotationTool.ARROW, Icons.Default.ArrowForward, "Arrow"),
                Triple(AnnotationTool.TEXT,  Icons.Default.TextFields,   "Text"),
            ).forEach { (t, icon, label) ->
                val sel = tool == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (sel) NeonCyan.copy(0.25f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                        .border(if (sel) 1.dp else 0.5.dp, if (sel) NeonCyan.copy(0.85f) else Color.White.copy(0.15f), RoundedCornerShape(10.dp))
                        .clickable { onToolChange(t) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(icon, null, tint = if (sel) NeonCyan else Color.White.copy(0.5f), modifier = Modifier.size(15.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                            color = if (sel) NeonCyan else Color.White.copy(0.4f),
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(NeonOrange.copy(0.2f)))
        Spacer(Modifier.height(7.dp))

        // ── Row 2: Color chips  ·  Width buttons ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ANNOT_COLORS_ARGB.forEach { argb ->
                val sel = argb == colorArgb
                Box(
                    modifier = Modifier
                        .size(if (sel) 30.dp else 24.dp)
                        .shadow(if (sel) 6.dp else 0.dp, CircleShape, spotColor = Color(argb).copy(0.6f))
                        .border(if (sel) 2.5.dp else 0.8.dp, if (sel) Color.White else Color.White.copy(0.2f), CircleShape)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .clickable { onColorChange(argb) }
                )
            }

            Spacer(Modifier.weight(1f))

            ANNOT_WIDTHS.forEach { (w, label) ->
                val sel = widthDp == w
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (sel) NeonOrange.copy(0.22f) else Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                        .border(if (sel) 1.dp else 0.5.dp, if (sel) NeonOrange.copy(0.75f) else Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                        .clickable { onWidthChange(w) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                        color = if (sel) NeonOrange else Color.White.copy(0.45f),
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
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

// ─── Local File Render Error ──────────────────────────────────────────────────
// Shown when a locally-copied file cannot be rendered in-app (e.g. a Word doc).
// Uses FileProvider to offer "Open Externally" since the file is in internal storage.

@Composable
private fun LocalFileRenderError(
    localFile: File?,
    title: String,
    errorMessage: String,
    context: Context
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            GlassCard(glowColor = NeonOrange) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThreeDIconBox(icon = Icons.Default.InsertDriveFile, tint = NeonOrange, size = 52.dp, iconSize = 28.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cannot Preview In-App", style = MaterialTheme.typography.titleMedium,
                                color = NeonOrange, fontWeight = FontWeight.ExtraBold)
                            Text(title, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                        }
                    }
                    NeonDivider(NeonOrange.copy(0.3f))
                    Text(
                        "This file format cannot be displayed in-app. Use the button below to open it with another app on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.8f)
                    )
                }
            }
        }
        if (errorMessage.isNotBlank()) {
            item {
                GlassCard(glowColor = NeonRed.copy(0.5f)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Error, null, tint = NeonRed, modifier = Modifier.size(14.dp))
                            Text("Render Detail", style = MaterialTheme.typography.labelMedium, color = NeonRed)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color.Black.copy(0.45f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, NeonRed.copy(0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonRed.copy(0.9f),
                                lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
        item {
            localFile?.let { f ->
                Button(
                    onClick = {
                        try {
                            val fUri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", f
                            )
                            val mime = context.contentResolver.getType(fUri) ?: "*/*"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fUri, mime)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.18f)),
                    border = BorderStroke(1.dp, NeonCyan.copy(0.65f))
                ) {
                    Icon(Icons.Default.OpenInNew, null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Open in External App", color = NeonCyan, fontWeight = FontWeight.Bold)
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
    val isOldUploadPermissionError = (errorMessage.contains("Permission", ignoreCase = true) ||
        errorMessage.contains("SecurityException", ignoreCase = true)) &&
        uriAuthority.contains("media.documents", ignoreCase = true)

    val conclusion = when {
        isOldUploadPermissionError ->
            "ACTION REQUIRED: This file was uploaded using an older version of the app that did not " +
            "store permanent access to it. The stored link is now permanently broken.\n\n" +
            "Fix: Go back to the screen where you attached this file, tap the upload button, " +
            "and select the file again. All re-uploaded files will work correctly going forward."
        errorMessage.contains("PdfRenderer", ignoreCase = true) ||
        errorMessage.contains("IllegalArgument", ignoreCase = true) ||
        errorMessage.contains("parsererror", ignoreCase = true) ->
            "The file was opened but the PDF parser rejected it. " +
            "It is likely NOT a PDF — it may be a Word doc, image, or other format."
        errorMessage.contains("null file descriptor", ignoreCase = true) ||
        errorMessage.contains("Permission", ignoreCase = true) ->
            "The app lost read access to this file. Go back and re-upload it."
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

// ─── Viewer Controls Strip (Scroll Direction + Zoom Toggle) ──────────────────

@Composable
private fun UvViewerControls(
    isHorizontalScroll: Boolean,
    onScrollToggle: () -> Unit,
    zoomEnabled: Boolean,
    onZoomToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A1220), Color(0xFF060D18)))
            )
            .border(BorderStroke(0.5.dp, Brush.horizontalGradient(listOf(NeonCyan.copy(0.25f), NeonPurple.copy(0.2f), NeonGold.copy(0.2f)))))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        Text(
            "Scroll",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.35f),
            fontSize = 10.sp
        )

        // Horizontal toggle pill
        Box(
            modifier = Modifier
                .shadow(if (isHorizontalScroll) 6.dp else 0.dp, RoundedCornerShape(10.dp), spotColor = NeonCyan.copy(0.5f))
                .background(
                    if (isHorizontalScroll) Brush.linearGradient(listOf(NeonCyan.copy(0.28f), NeonCyan.copy(0.12f)))
                    else Brush.linearGradient(listOf(Color.White.copy(0.06f), Color.White.copy(0.03f))),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    1.dp,
                    if (isHorizontalScroll) NeonCyan.copy(0.75f) else Color.White.copy(0.1f),
                    RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onScrollToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.MoreHoriz, null,
                    tint = if (isHorizontalScroll) NeonCyan else Color.White.copy(0.35f),
                    modifier = Modifier.size(14.dp))
                Text("H-Scroll",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isHorizontalScroll) NeonCyan else Color.White.copy(0.35f),
                    fontWeight = if (isHorizontalScroll) FontWeight.ExtraBold else FontWeight.Normal)
            }
        }

        // Vertical toggle pill
        Box(
            modifier = Modifier
                .shadow(if (!isHorizontalScroll) 6.dp else 0.dp, RoundedCornerShape(10.dp), spotColor = NeonPurple.copy(0.5f))
                .background(
                    if (!isHorizontalScroll) Brush.linearGradient(listOf(NeonPurple.copy(0.28f), NeonPurple.copy(0.12f)))
                    else Brush.linearGradient(listOf(Color.White.copy(0.06f), Color.White.copy(0.03f))),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    1.dp,
                    if (!isHorizontalScroll) NeonPurple.copy(0.75f) else Color.White.copy(0.1f),
                    RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onScrollToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.MoreVert, null,
                    tint = if (!isHorizontalScroll) NeonPurple else Color.White.copy(0.35f),
                    modifier = Modifier.size(14.dp))
                Text("V-Scroll",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (!isHorizontalScroll) NeonPurple else Color.White.copy(0.35f),
                    fontWeight = if (!isHorizontalScroll) FontWeight.ExtraBold else FontWeight.Normal)
            }
        }

        Spacer(Modifier.weight(1f))

        // Divider
        Box(modifier = Modifier.width(0.5.dp).height(22.dp).background(Color.White.copy(0.12f)))

        Spacer(Modifier.width(4.dp))

        // Zoom label
        Text(
            "Zoom",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.35f),
            fontSize = 10.sp
        )

        // Zoom toggle pill
        Box(
            modifier = Modifier
                .shadow(if (zoomEnabled) 8.dp else 0.dp, RoundedCornerShape(10.dp), spotColor = NeonGold.copy(0.55f))
                .background(
                    if (zoomEnabled) Brush.linearGradient(listOf(NeonGold.copy(0.3f), NeonGold.copy(0.1f)))
                    else Brush.linearGradient(listOf(Color.White.copy(0.06f), Color.White.copy(0.03f))),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    1.dp,
                    if (zoomEnabled) NeonGold.copy(0.8f) else Color.White.copy(0.1f),
                    RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onZoomToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    if (zoomEnabled) Icons.Default.ZoomIn else Icons.Default.ZoomOut,
                    null,
                    tint = if (zoomEnabled) NeonGold else Color.White.copy(0.35f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    if (zoomEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (zoomEnabled) NeonGold else Color.White.copy(0.35f),
                    fontWeight = if (zoomEnabled) FontWeight.ExtraBold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Text Box Editor Dialog ────────────────────────────────────────────────────

@Composable
private fun TextBoxEditorDialog(
    xNorm: Float,
    yNorm: Float,
    onSave: (AnnotationTextBox) -> Unit,
    onDismiss: () -> Unit,
) {
    var text      by remember { mutableStateOf("") }
    var colorArgb by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var fontSize  by remember { mutableFloatStateOf(14f) }
    var isBold    by remember { mutableStateOf(false) }
    var isItalic  by remember { mutableStateOf(false) }
    var bgArgb    by remember { mutableIntStateOf(0) }
    var hasBorder by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF08122A),
        shape            = RoundedCornerShape(24.dp),
        title            = null,
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .shadow(10.dp, RoundedCornerShape(13.dp), spotColor = NeonCyan.copy(0.5f))
                            .background(Brush.linearGradient(listOf(NeonCyan.copy(0.28f), NeonCyan.copy(0.07f))), RoundedCornerShape(13.dp))
                            .border(1.dp, NeonCyan.copy(0.6f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.TextFields, null, tint = NeonCyan, modifier = Modifier.size(22.dp)) }
                    Column {
                        Text("Add Text Label", style = MaterialTheme.typography.headlineSmall, color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                        Text("Customise & place on PDF", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.38f))
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(0.35f), Color.Transparent))))

                // ── Text input ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = NeonCyan.copy(0.15f))
                        .background(Color.White.copy(0.04f), RoundedCornerShape(14.dp))
                        .border(1.dp, NeonCyan.copy(0.28f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    if (text.isEmpty()) {
                        Text("Enter label text…", color = Color.White.copy(0.22f), style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value         = text,
                        onValueChange = { text = it },
                        textStyle     = TextStyle(
                            color      = Color(colorArgb),
                            fontSize   = fontSize.sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle  = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = (fontSize * 1.4f).sp
                        ),
                        modifier  = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        maxLines  = 8
                    )
                }

                // ── Font size slider ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FormatSize, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(14.dp))
                        Text("Font Size", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .shadow(3.dp, RoundedCornerShape(7.dp), spotColor = NeonCyan.copy(0.3f))
                                .background(NeonCyan.copy(0.12f), RoundedCornerShape(7.dp))
                                .border(0.5.dp, NeonCyan.copy(0.4f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Slider(
                        value         = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange    = 8f..52f,
                        steps         = 43,
                        colors        = SliderDefaults.colors(
                            thumbColor         = NeonCyan,
                            activeTrackColor   = NeonCyan.copy(0.7f),
                            inactiveTrackColor = Color.White.copy(0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Text color ────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Text Color", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        ANNOT_COLORS_ARGB.forEach { argb ->
                            val sel = argb == colorArgb
                            Box(
                                modifier = Modifier
                                    .size(if (sel) 30.dp else 23.dp)
                                    .shadow(if (sel) 6.dp else 0.dp, CircleShape, spotColor = Color(argb).copy(0.6f))
                                    .border(if (sel) 2.dp else 0.8.dp, if (sel) Color.White else Color.White.copy(0.25f), CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .clickable { colorArgb = argb }
                            )
                        }
                    }
                }

                // ── Style toggles ─────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Style", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val boldSel = isBold
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(if (boldSel) 6.dp else 0.dp, RoundedCornerShape(10.dp), spotColor = NeonCyan.copy(0.4f))
                                .background(if (boldSel) NeonCyan.copy(0.22f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                                .border(if (boldSel) 1.dp else 0.5.dp, if (boldSel) NeonCyan.copy(0.8f) else Color.White.copy(0.15f), RoundedCornerShape(10.dp))
                                .clickable { isBold = !isBold },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("B", color = if (boldSel) NeonCyan else Color.White.copy(0.5f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        val italicSel = isItalic
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(if (italicSel) 6.dp else 0.dp, RoundedCornerShape(10.dp), spotColor = NeonCyan.copy(0.4f))
                                .background(if (italicSel) NeonCyan.copy(0.22f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                                .border(if (italicSel) 1.dp else 0.5.dp, if (italicSel) NeonCyan.copy(0.8f) else Color.White.copy(0.15f), RoundedCornerShape(10.dp))
                                .clickable { isItalic = !isItalic },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I", color = if (italicSel) NeonCyan else Color.White.copy(0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                        }
                    }
                }

                // ── Background ────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Background", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TEXTBOX_BG_OPTIONS.forEach { (argb, label) ->
                            val sel     = bgArgb == argb
                            val bgColor = if (argb == 0) Color.White.copy(0.06f) else Color(argb)
                            Box(
                                modifier = Modifier
                                    .shadow(if (sel) 5.dp else 0.dp, RoundedCornerShape(9.dp), spotColor = NeonCyan.copy(0.3f))
                                    .background(bgColor, RoundedCornerShape(9.dp))
                                    .border(
                                        if (sel) 1.5.dp else 0.5.dp,
                                        if (sel) NeonCyan.copy(0.85f) else Color.White.copy(0.18f),
                                        RoundedCornerShape(9.dp)
                                    )
                                    .clickable { bgArgb = argb }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = if (argb == 0 || bgColor.luminance() > 0.4f) Color(0xFF0A0A1A) else Color.White,
                                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // ── Border toggle ─────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(0.04f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, if (hasBorder) NeonCyan.copy(0.35f) else Color.White.copy(0.08f), RoundedCornerShape(10.dp))
                        .clickable { hasBorder = !hasBorder }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (hasBorder) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        null,
                        tint     = if (hasBorder) NeonCyan else Color.White.copy(0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Show Border", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f))
                }

                // ── Live preview ──────────────────────────────────────────────
                if (text.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Preview", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (bgArgb == 0) Color.Transparent else Color(bgArgb),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .then(
                                        if (hasBorder) Modifier.border(1.5.dp, Color(colorArgb).copy(0.7f), RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text       = text,
                                    color      = Color(colorArgb),
                                    fontSize   = fontSize.sp,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle  = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                    lineHeight = (fontSize * 1.4f).sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(13.dp), spotColor = NeonCyan.copy(0.5f))
                    .background(
                        Brush.linearGradient(listOf(NeonCyan.copy(if (text.isBlank()) 0.10f else 0.32f), NeonCyan.copy(0.08f))),
                        RoundedCornerShape(13.dp)
                    )
                    .border(1.dp, NeonCyan.copy(if (text.isBlank()) 0.2f else 0.65f), RoundedCornerShape(13.dp))
                    .clickable(enabled = text.isNotBlank()) {
                        if (text.isNotBlank()) {
                            onSave(AnnotationTextBox(
                                xNorm      = xNorm,
                                yNorm      = yNorm,
                                text       = text,
                                colorArgb  = colorArgb,
                                fontSizeSp = fontSize,
                                isBold     = isBold,
                                isItalic   = isItalic,
                                bgArgb     = bgArgb,
                                hasBorder  = hasBorder
                            ))
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.TextFields, null,
                        tint     = NeonCyan.copy(if (text.isBlank()) 0.35f else 1f),
                        modifier = Modifier.size(14.dp))
                    Text("Place Label",
                        color      = NeonCyan.copy(if (text.isBlank()) 0.35f else 1f),
                        fontWeight = FontWeight.ExtraBold,
                        style      = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(13.dp))
                    .background(Color.White.copy(0.05f), RoundedCornerShape(13.dp))
                    .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(13.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Cancel", color = Color.White.copy(0.55f), style = MaterialTheme.typography.labelLarge)
            }
        }
    )
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
