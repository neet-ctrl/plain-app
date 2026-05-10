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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.RichTextToolbar
import com.neet.tracker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val STATUS_MARKS = listOf("✅", "❌", "⏳", "🔄", "⭐", "❗", "❓", "💡", "🎯", "🔥")
private val QUICK_EMOJIS_FILE = listOf(
    "😊","😔","💪","🔥","📚","✅","❌","🎯","🌟","💡","⚡","🧠","📝","🏆","😴",
    "🩺","⚗️","🔬","🌿","🐾","💊","🫀","🫁","🦷","👁️","🦴","🧬","📊","📈","📉",
    "🤔","💯","🎉","📖","⏰","🔔","📌","🔑","💎","🚀"
)

@Composable
fun FileViewerScreen(navController: NavController, fileUri: String, title: String) {
    val context = LocalContext.current
    val uri = remember(fileUri) { try { Uri.parse(fileUri) } catch (e: Exception) { null } }
    val extension = remember(fileUri) {
        MimeTypeMap.getFileExtensionFromUrl(fileUri)?.lowercase()
            ?: fileUri.substringAfterLast('.', "").lowercase()
    }
    val isImage = extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val isPdf   = extension == "pdf" || fileUri.contains("pdf", ignoreCase = true)

    // ── Persistent notes via SharedPreferences ─────────────────────────────────
    val prefs   = remember { context.getSharedPreferences("file_annotations", Context.MODE_PRIVATE) }
    val noteKey = remember(fileUri) { "note_${fileUri.hashCode()}" }
    var notesText     by remember(noteKey) { mutableStateOf(prefs.getString(noteKey, "") ?: "") }
    var notesExpanded by remember { mutableStateOf(false) }

    // ── PDF state ──────────────────────────────────────────────────────────────
    val pdfPages  = remember { mutableStateListOf<Bitmap>() }
    var pdfLoading  by remember { mutableStateOf(false) }
    var totalPages  by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var pdfError    by remember { mutableStateOf(false) }

    LaunchedEffect(fileUri) {
        if (isPdf && uri != null) {
            pdfLoading = true
            pdfError   = false
            withContext(Dispatchers.IO) {
                try {
                    val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        val width  = 1080
                        for (i in 0 until renderer.pageCount.coerceAtMost(100)) {
                            val page  = renderer.openPage(i)
                            val ratio = page.height.toFloat() / page.width.toFloat()
                            val bmp   = Bitmap.createBitmap(width, (width * ratio).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            pdfPages.add(bmp)
                        }
                        renderer.close()
                        pfd.close()
                    } else { pdfError = true }
                } catch (e: Exception) { pdfError = true }
            }
            pdfLoading = false
        }
    }

    // Auto-save notes whenever they change
    LaunchedEffect(notesText) {
        prefs.edit().putString(noteKey, notesText).apply()
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            NEETTopBar(
                title      = title,
                breadcrumb = if (isPdf && totalPages > 0) "Page ${currentPage + 1} / $totalPages" else "File Viewer",
                onBack     = { navController.popBackStack() },
                actions    = {
                    // Notes toggle
                    val noteAnim by animateFloatAsState(if (notesExpanded) 1f else 0f, tween(300), label = "note")
                    IconButton(onClick = { notesExpanded = !notesExpanded }) {
                        Box(
                            modifier = Modifier.size(34.dp)
                                .background(
                                    if (notesExpanded) NeonGold.copy(0.22f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(if (notesExpanded) BorderStroke(1.dp, NeonGold.copy(0.6f)) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (notesExpanded) Icons.Default.EditNote else Icons.Default.NoteAdd,
                                null, tint = if (notesExpanded) NeonGold else Color.White.copy(0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Share
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (isPdf) "application/pdf" else if (isImage) "image/*" else "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try { context.startActivity(Intent.createChooser(intent, "Share $title")) } catch (e: Exception) {}
                    }) { Icon(Icons.Default.Share, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                    // Open externally
                    IconButton(onClick = {
                        val mime = if (isPdf) "application/pdf" else if (isImage) "image/*" else "*/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }) { Icon(Icons.Default.OpenInNew, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                }
            )

            // ── File content area ────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uri == null || fileUri.isBlank() -> FileErrorView()
                    isImage  -> ImageViewer(uri = uri, title = title)
                    isPdf    -> {
                        when {
                            pdfLoading            -> PdfLoadingView()
                            pdfPages.isNotEmpty() -> PdfPageViewer(pages = pdfPages, onPageChanged = { currentPage = it })
                            else                  -> PdfFallbackView(uri = uri, title = title, context = context)
                        }
                    }
                    else     -> GenericFileView(uri = uri, title = title, extension = extension, context = context)
                }
            }

            // ── Rich annotation notes panel (collapsible bottom sheet) ────────
            AnimatedVisibility(
                visible = notesExpanded,
                enter   = slideInVertically { it } + fadeIn(tween(250)),
                exit    = slideOutVertically { it } + fadeOut(tween(200))
            ) {
                AnnotationPanel(
                    text       = notesText,
                    onTextChange = { notesText = it }
                )
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
        // Panel handle + header
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
            Text("My Annotations & Notes", style = MaterialTheme.typography.labelLarge, color = NeonGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (text.isNotBlank()) {
                Text("${text.length} ch", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.3f))
            }
            if (text.isNotBlank()) {
                IconButton(onClick = { onTextChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }

        NeonDivider(NeonGold.copy(0.25f))
        Spacer(Modifier.height(4.dp))

        // Status marks quick-insert row
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(STATUS_MARKS) { mark ->
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(0.12f), RoundedCornerShape(8.dp))
                        .clickable { onTextChange(text + mark) },
                    contentAlignment = Alignment.Center
                ) { Text(mark, fontSize = 16.sp) }
            }
            item {
                Box(
                    modifier = Modifier.height(34.dp).width(1.dp).background(Color.White.copy(0.1f))
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(if (showEmojiPanel) NeonGold.copy(0.2f) else Color.White.copy(0.06f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, if (showEmojiPanel) NeonGold.copy(0.5f) else Color.White.copy(0.12f), RoundedCornerShape(8.dp))
                        .clickable { showEmojiPanel = !showEmojiPanel },
                    contentAlignment = Alignment.Center
                ) { Text("😊", fontSize = 16.sp) }
            }
        }

        // Emoji picker panel
        AnimatedVisibility(visible = showEmojiPanel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(QUICK_EMOJIS_FILE) { emoji ->
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

        // Full rich text toolbar (reuses the existing comprehensive toolbar)
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            RichTextToolbar(accentColor = NeonGold, onInsert = { onTextChange(text + it) })
        }

        Spacer(Modifier.height(8.dp))

        // Text editor
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
            textStyle = TextStyle(
                color      = Color.White.copy(0.9f),
                fontSize   = 14.sp,
                lineHeight = 22.sp
            ),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        "Write your notes, annotations, highlights, thoughts about this file…\n\nUse the toolbar above for rich formatting ✏️",
                        style = TextStyle(color = Color.White.copy(0.2f), fontSize = 13.sp, lineHeight = 20.sp)
                    )
                }
                inner()
            }
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ─── PDF Page Viewer (in-app rendering) ───────────────────────────────────────

@Composable
private fun PdfPageViewer(pages: List<Bitmap>, onPageChanged: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val visiblePage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(visiblePage) { onPageChanged(visiblePage) }

    LazyColumn(
        state               = listState,
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding      = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = NeonOrange.copy(0.18f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            ) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = "Page ${index + 1}",
                    modifier           = Modifier.fillMaxWidth()
                )
                Box(
                    modifier          = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(vertical = 3.dp),
                    contentAlignment  = Alignment.Center
                ) {
                    Text("— ${index + 1} —", style = MaterialTheme.typography.labelSmall, color = NeonOrange.copy(0.55f), fontSize = 10.sp)
                }
            }
        }
    }
}

// ─── PDF Loading Indicator ────────────────────────────────────────────────────

@Composable
private fun PdfLoadingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "pdf_load")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(90.dp)
                    .shadow(20.dp, CircleShape, spotColor = NeonOrange.copy(pulse * 0.5f))
                    .background(Brush.radialGradient(listOf(NeonOrange.copy(0.18f), Color.Transparent)), CircleShape)
                    .border(2.dp, Brush.sweepGradient(listOf(NeonOrange, NeonGold, NeonOrange)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonOrange, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            }
            Text("Rendering PDF pages…", color = Color.White.copy(0.55f), style = MaterialTheme.typography.bodySmall)
            Text("This may take a moment for large files", color = Color.White.copy(0.3f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─── PDF Fallback (when PdfRenderer fails) ────────────────────────────────────

@Composable
private fun PdfFallbackView(uri: Uri, title: String, context: Context) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassCard(glowColor = NeonOrange) {
                Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    ThreeDIconBox(icon = Icons.Default.PictureAsPdf, tint = NeonOrange, size = 80.dp, iconSize = 44.dp)
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "This PDF couldn't be rendered in-app (encrypted or corrupted). Open it in your PDF reader for full access.",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.55f), textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(0.18f)),
                        border   = BorderStroke(1.dp, NeonOrange.copy(0.65f))
                    ) {
                        Icon(Icons.Default.OpenInNew, null, tint = NeonOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("Open in PDF Reader", color = NeonOrange, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try { context.startActivity(Intent.createChooser(intent, "Share PDF")) } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border   = BorderStroke(1.dp, NeonCyan.copy(0.4f)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.Share, null, tint = NeonCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Share PDF", color = NeonCyan)
                    }
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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        showHint = false
    }

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
            modifier           = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX       = scale
                    scaleY       = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // Zoom hint badge
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
                Text("Pinch to zoom  •  Drag to pan", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.6f))
            }
        }

        // Zoom level indicator
        if (scale != 1f) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NeonGold)
            }
        }
    }
}

// ─── Generic File View ────────────────────────────────────────────────────────

@Composable
private fun GenericFileView(uri: Uri, title: String, extension: String, context: Context) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassCard(glowColor = NeonCyan) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ThreeDIconBox(icon = Icons.Default.AttachFile, tint = NeonCyan, size = 80.dp, iconSize = 44.dp)
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        ".${extension.uppercase().ifBlank { "FILE" }}",
                        style = MaterialTheme.typography.labelMedium, color = NeonCyan.copy(0.65f),
                        modifier = Modifier
                            .background(NeonCyan.copy(0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Text("Open this file in its native app for full viewing.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.45f), textAlign = TextAlign.Center)
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data  = uri
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.18f)),
                        border   = BorderStroke(1.dp, NeonCyan.copy(0.65f))
                    ) {
                        Icon(Icons.Default.OpenInNew, null, tint = NeonCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Open File", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── File Error View ──────────────────────────────────────────────────────────

@Composable
private fun FileErrorView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ThreeDIconBox(icon = Icons.Default.BrokenImage, tint = NeonRed, size = 80.dp, iconSize = 44.dp)
            Text("File not found", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold)
            Text("The file may have been moved or deleted.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.3f))
        }
    }
}
