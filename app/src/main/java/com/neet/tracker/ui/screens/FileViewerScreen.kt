package com.neet.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.theme.*
import coil.compose.AsyncImage

@Composable
fun FileViewerScreen(navController: NavController, fileUri: String, title: String) {
    val context = LocalContext.current
    val uri = remember(fileUri) { try { Uri.parse(fileUri) } catch (e: Exception) { null } }
    val extension = remember(fileUri) {
        MimeTypeMap.getFileExtensionFromUrl(fileUri)?.lowercase()
            ?: fileUri.substringAfterLast('.', "").lowercase()
    }
    val isImage = extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val isPdf = extension == "pdf" || fileUri.contains("pdf", ignoreCase = true)

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(
                title = title,
                breadcrumb = "Viewer",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (isPdf) "application/pdf" else "image/*")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }) {
                        Icon(Icons.Default.OpenInNew, null, tint = NeonCyan)
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (isPdf) "application/pdf" else "image/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share $title"))
                    }) {
                        Icon(Icons.Default.Share, null, tint = NeonCyan)
                    }
                }
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    uri == null || fileUri.isBlank() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.BrokenImage, null, tint = NeonRed.copy(0.6f), modifier = Modifier.size(80.dp))
                            Text("File not found", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(0.5f))
                        }
                    }
                    isImage -> {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassCard(glowColor = NeonCyan, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 500.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    isPdf -> {
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            GlassCard(glowColor = NeonOrange) {
                                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Box(
                                        modifier = Modifier.size(100.dp).background(NeonOrange.copy(0.15f), CircleShape).border(2.dp, NeonOrange.copy(0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, null, tint = NeonOrange, modifier = Modifier.size(52.dp))
                                    }
                                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Text("PDF Document", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.4f))
                                    NeonDivider(NeonOrange)
                                    Text(
                                        "Tap 'Open Externally' to view this PDF in your device's PDF reader with full features including search, zoom, and annotations.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(0.6f),
                                        textAlign = TextAlign.Center
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
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange.copy(0.2f)),
                                        border = BorderStroke(1.dp, NeonOrange.copy(0.6f))
                                    ) {
                                        Icon(Icons.Default.OpenInNew, null, tint = NeonOrange)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Open in PDF Reader", color = NeonOrange, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, NeonCyan.copy(0.4f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                                    ) {
                                        Icon(Icons.Default.Share, null, tint = NeonCyan)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Share PDF", color = NeonCyan)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            GlassCard(glowColor = NeonCyan) {
                                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Icon(Icons.Default.AttachFile, null, tint = NeonCyan, modifier = Modifier.size(72.dp))
                                    Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Text("File type: ${extension.uppercase().ifBlank { "Unknown" }}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.4f))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = uri
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            try { context.startActivity(intent) } catch (e: Exception) {}
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(0.2f)),
                                        border = BorderStroke(1.dp, NeonCyan.copy(0.6f))
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
            }
        }
    }
}
