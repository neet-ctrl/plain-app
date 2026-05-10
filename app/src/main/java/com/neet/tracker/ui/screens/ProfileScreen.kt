package com.neet.tracker.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.fileViewerRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.DialogTextField
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.ProfileViewModel
import java.net.URLEncoder

@Composable
fun ProfileScreen(navController: NavController, vm: ProfileViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsState()
    var editing by remember { mutableStateOf(false) }

    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var dob by remember(profile) { mutableStateOf(profile?.dob ?: "") }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var mobile by remember(profile) { mutableStateOf(profile?.mobile ?: "") }
    var aadhar by remember(profile) { mutableStateOf(profile?.aadharNo ?: "") }
    var tenth by remember(profile) { mutableStateOf(profile?.tenthPercentage ?: "") }
    var tenthUri by remember(profile) { mutableStateOf(profile?.tenthMarksheetUri ?: "") }
    var twelfth by remember(profile) { mutableStateOf(profile?.twelfthPercentage ?: "") }
    var twelfthUri by remember(profile) { mutableStateOf(profile?.twelfthMarksheetUri ?: "") }
    var photoUri by remember(profile) { mutableStateOf(profile?.photoUri ?: "") }
    var target by remember(profile) { mutableStateOf(profile?.targetScore ?: "700/720") }
    var attempts by remember(profile) { mutableStateOf(profile?.neetAttempts ?: emptyList()) }
    var showAddAttempt by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            photoUri = it.toString()
        }
    }

    SpaceBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            NEETTopBar(
                title = "Student Profile",
                breadcrumb = "Home",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = {
                        if (editing) {
                            vm.save(
                                (profile ?: StudentProfile()).copy(
                                    name = name, dob = dob, email = email, mobile = mobile,
                                    aadharNo = aadhar, tenthPercentage = tenth, tenthMarksheetUri = tenthUri,
                                    twelfthPercentage = twelfth, twelfthMarksheetUri = twelfthUri,
                                    photoUri = photoUri, targetScore = target, neetAttempts = attempts
                                )
                            )
                        }
                        editing = !editing
                    }) {
                        Icon(
                            if (editing) Icons.Default.Save else Icons.Default.Edit,
                            null,
                            tint = NeonCyan
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Profile Photo
                item {
                    GlassCard(glowColor = NeonCyan) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .border(3.dp, Brush.sweepGradient(listOf(NeonCyan, NeonPurple, NeonGold, NeonCyan)), CircleShape)
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(CosmicBlue, CircleShape)
                                        .clip(CircleShape)
                                        .clickable(enabled = editing) { photoLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (photoUri.isNotBlank()) {
                                        coil.compose.AsyncImage(
                                            model = photoUri, contentDescription = "Photo",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, null, tint = NeonCyan, modifier = Modifier.size(60.dp))
                                    }
                                }
                                if (editing) {
                                    Box(
                                        modifier = Modifier.size(32.dp).align(Alignment.BottomEnd)
                                            .background(NeonCyan, CircleShape).border(2.dp, CosmicBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CameraAlt, null, tint = CosmicBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            // Target Score
                            Row(
                                modifier = Modifier
                                    .background(NeonGold.copy(0.15f), RoundedCornerShape(16.dp))
                                    .border(1.dp, NeonGold.copy(0.5f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.EmojiEvents, null, tint = NeonGold, modifier = Modifier.size(20.dp))
                                Text("NEET Target: $target", style = MaterialTheme.typography.headlineSmall, color = NeonGold, fontWeight = FontWeight.ExtraBold)
                                Text("· MBBS Doctor 🩺", style = MaterialTheme.typography.bodySmall, color = NeonGold.copy(0.7f))
                            }
                        }
                    }
                }

                // Basic Info
                item {
                    ProfileSection(title = "Personal Details", accentColor = NeonCyan) {
                        ProfileField("Full Name", name, editing, Icons.Default.Person) { name = it }
                        ProfileField("Date of Birth", dob, editing, Icons.Default.Cake) { dob = it }
                        ProfileField("Email", email, editing, Icons.Default.Email) { email = it }
                        ProfileField("Mobile No.", mobile, editing, Icons.Default.Phone) { mobile = it }
                        ProfileField("Aadhar No.", aadhar, editing, Icons.Default.Badge) { aadhar = it }
                        if (editing) ProfileField("Target Score", target, editing, Icons.Default.EmojiEvents) { target = it }
                    }
                }

                // Qualifications
                item {
                    ProfileSection(title = "Academic Qualifications", accentColor = NeonPurple) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileField("10th %", tenth, editing, Icons.Default.School) { tenth = it }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileField("12th %", twelfth, editing, Icons.Default.School) { twelfth = it }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FileUploadButton("10th Marksheet", tenthUri, editing, NeonPurple,
                                onUpload = { tenthUri = it },
                                onView = { navController.navigate(fileViewerRoute(tenthUri, "10th Marksheet")) }
                            )
                            FileUploadButton("12th Marksheet", twelfthUri, editing, NeonPurple,
                                onUpload = { twelfthUri = it },
                                onView = { navController.navigate(fileViewerRoute(twelfthUri, "12th Marksheet")) }
                            )
                        }
                    }
                }

                // NEET Attempts
                item {
                    ProfileSection(title = "NEET Attempts (${attempts.size})", accentColor = NeonGold) {
                        attempts.forEachIndexed { i, attempt ->
                            NEETAttemptCard(
                                attempt = attempt,
                                index = i + 1,
                                editing = editing,
                                onUpdate = { updated -> attempts = attempts.toMutableList().also { it[i] = updated } },
                                onDelete = { attempts = attempts.toMutableList().also { it.removeAt(i) } },
                                onViewFile = { uri, title -> navController.navigate(fileViewerRoute(uri, title)) }
                            )
                        }
                        if (editing) {
                            Button(
                                onClick = {
                                    attempts = attempts + NeetAttempt()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGold.copy(0.12f)),
                                border = BorderStroke(1.dp, NeonGold.copy(0.4f))
                            ) {
                                Icon(Icons.Default.Add, null, tint = NeonGold)
                                Spacer(Modifier.width(8.dp))
                                Text("Add NEET Attempt", color = NeonGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSection(title: String, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(glowColor = accentColor) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(4.dp, 20.dp).background(accentColor, RoundedCornerShape(2.dp)))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = accentColor, fontWeight = FontWeight.Bold)
            }
            NeonDivider(accentColor)
            content()
        }
    }
}

@Composable
fun ProfileField(label: String, value: String, editing: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onValueChange: (String) -> Unit) {
    if (editing) {
        DialogTextField(value = value, onValueChange = onValueChange, label = label, icon = icon, accentColor = NeonCyan)
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(18.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun RowScope.FileUploadButton(label: String, uri: String, editing: Boolean, color: Color, onUpload: (String) -> Unit, onView: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        u?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            onUpload(it.toString())
        }
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (uri.isNotBlank()) color.copy(0.12f) else Color.White.copy(0.04f))
            .border(1.dp, if (uri.isNotBlank()) color.copy(0.4f) else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
            .clickable { if (uri.isNotBlank()) onView() else if (editing) launcher.launch("*/*") }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                if (uri.isNotBlank()) Icons.Default.FileOpen else Icons.Default.UploadFile,
                null,
                tint = if (uri.isNotBlank()) color else Color.White.copy(0.3f),
                modifier = Modifier.size(24.dp)
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (uri.isNotBlank()) color else Color.White.copy(0.4f))
            if (uri.isNotBlank()) {
                Text("Tap to view", style = MaterialTheme.typography.labelSmall, color = color.copy(0.6f))
            } else if (editing) {
                Text("Tap to upload", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.25f))
            }
        }
    }
}

@Composable
fun NEETAttemptCard(
    attempt: NeetAttempt,
    index: Int,
    editing: Boolean,
    onUpdate: (NeetAttempt) -> Unit,
    onDelete: () -> Unit,
    onViewFile: (String, String) -> Unit
) {
    var year by remember(attempt) { mutableStateOf(attempt.year) }
    var rollNo by remember(attempt) { mutableStateOf(attempt.rollNo) }
    var marks by remember(attempt) { mutableStateOf(attempt.marksObtained) }
    var lack by remember(attempt) { mutableStateOf(attempt.lackDescription) }
    var marksheetUri by remember(attempt) { mutableStateOf(attempt.marksheetUri) }
    var qpUri by remember(attempt) { mutableStateOf(attempt.questionPaperUri) }
    var solUri by remember(attempt) { mutableStateOf(attempt.solutionPdfUri) }

    val context = LocalContext.current
    val marksheetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            marksheetUri = it.toString(); onUpdate(attempt.copy(marksheetUri = marksheetUri))
        }
    }
    val qpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            qpUri = it.toString(); onUpdate(attempt.copy(questionPaperUri = qpUri))
        }
    }
    val solLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            solUri = it.toString(); onUpdate(attempt.copy(solutionPdfUri = solUri))
        }
    }

    val save = { onUpdate(attempt.copy(year = year, rollNo = rollNo, marksObtained = marks, lackDescription = lack, marksheetUri = marksheetUri, questionPaperUri = qpUri, solutionPdfUri = solUri)) }

    GlassCard(glowColor = NeonGold, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(NeonGold.copy(0.2f), CircleShape).border(1.dp, NeonGold.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$index", style = MaterialTheme.typography.labelLarge, color = NeonGold, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Text("NEET Attempt #$index", style = MaterialTheme.typography.headlineSmall, color = NeonGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (editing) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (editing) {
                DialogTextField(value = year, onValueChange = { year = it; save() }, label = "Year", icon = Icons.Default.CalendarToday, accentColor = NeonGold)
                DialogTextField(value = rollNo, onValueChange = { rollNo = it; save() }, label = "Roll No.", icon = Icons.Default.Numbers, accentColor = NeonGold)
                DialogTextField(value = marks, onValueChange = { marks = it; save() }, label = "Marks Obtained", icon = Icons.Default.Score, accentColor = NeonGold)
                DialogTextField(value = lack, onValueChange = { lack = it; save() }, label = "What was lacking?", icon = Icons.Default.TrendingDown, accentColor = NeonGold, multiline = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFileButton("Marksheet", marksheetUri, NeonGold, { marksheetLauncher.launch("*/*") }, { onViewFile(marksheetUri, "Marksheet $year") }, Modifier.weight(1f))
                    SmallFileButton("Question Paper", qpUri, NeonGold, { qpLauncher.launch("*/*") }, { onViewFile(qpUri, "Question Paper $year") }, Modifier.weight(1f))
                    SmallFileButton("Solution", solUri, NeonGold, { solLauncher.launch("*/*") }, { onViewFile(solUri, "Solution $year") }, Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    InfoPair("Year", year.ifBlank { "—" })
                    InfoPair("Roll No.", rollNo.ifBlank { "—" })
                    InfoPair("Marks", marks.ifBlank { "—" })
                }
                if (lack.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.TrendingDown, null, tint = NeonRed.copy(0.7f), modifier = Modifier.size(14.dp))
                        Text("Lack: $lack", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.6f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (marksheetUri.isNotBlank()) SmallViewButton("Marksheet", NeonGold) { onViewFile(marksheetUri, "Marksheet $year") }
                    if (qpUri.isNotBlank()) SmallViewButton("QP", NeonGold) { onViewFile(qpUri, "Question Paper $year") }
                    if (solUri.isNotBlank()) SmallViewButton("Solution", NeonGold) { onViewFile(solUri, "Solution $year") }
                }
            }
        }
    }
}

@Composable
fun InfoPair(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallFileButton(label: String, uri: String, color: Color, onUpload: () -> Unit, onView: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (uri.isNotBlank()) color.copy(0.15f) else Color.White.copy(0.04f))
            .border(0.5.dp, if (uri.isNotBlank()) color.copy(0.5f) else Color.White.copy(0.1f), RoundedCornerShape(8.dp))
            .clickable { if (uri.isNotBlank()) onView() else onUpload() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (uri.isNotBlank()) "✓ $label" else "+ $label",
            style = MaterialTheme.typography.labelSmall,
            color = if (uri.isNotBlank()) color else Color.White.copy(0.4f)
        )
    }
}

@Composable
fun SmallViewButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.1f))
            .border(0.5.dp, color.copy(0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.OpenInNew, null, tint = color, modifier = Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
