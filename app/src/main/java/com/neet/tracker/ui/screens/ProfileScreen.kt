package com.neet.tracker.ui.screens

import android.content.Intent
import kotlinx.coroutines.launch
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.neet.tracker.data.ChapterStore
import com.neet.tracker.data.models.*
import com.neet.tracker.navigation.fileViewerRoute
import com.neet.tracker.ui.components.*
import com.neet.tracker.ui.dialogs.DialogTextField
import com.neet.tracker.ui.dialogs.JsonValidationErrorDialog
import com.neet.tracker.ui.dialogs.NeetDatePickerButton
import com.neet.tracker.ui.theme.*
import com.neet.tracker.ui.viewmodels.BackupState
import com.neet.tracker.ui.viewmodels.BackupViewModel
import com.neet.tracker.ui.viewmodels.ProfileViewModel
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    var dreamRole by remember(profile) { mutableStateOf(profile?.dreamRole ?: "MBBS Doctor") }
    var attempts by remember(profile) { mutableStateOf(profile?.neetAttempts ?: emptyList()) }
    var showTargetEdit by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupVm: BackupViewModel = hiltViewModel()
    val backupState by backupVm.state.collectAsState()
    val backupMessage by backupVm.message.collectAsState()
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { backupVm.createBackup(context, it) }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupVm.restoreBackup(context, it) }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                photoUri = localPath ?: u.toString()
            }
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
                                    photoUri = photoUri, targetScore = target, dreamRole = dreamRole,
                                    neetAttempts = attempts
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
                                        .clickable(enabled = editing) { photoLauncher.launch(arrayOf("image/*")) },
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
                            // Target Score 3D Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = NeonGold.copy(0.4f), ambientColor = NeonGold.copy(0.1f))
                                    .background(
                                        Brush.linearGradient(listOf(NeonGold.copy(0.22f), Color(0xFF0A1428), NeonGold.copy(0.08f))),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(
                                        1.5.dp,
                                        Brush.linearGradient(listOf(NeonGold.copy(0.75f), Color.White.copy(0.12f), NeonGold.copy(0.35f))),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Trophy 3D icon box
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = NeonGold.copy(0.5f))
                                            .background(
                                                Brush.linearGradient(listOf(NeonGold.copy(0.30f), NeonGold.copy(0.08f))),
                                                RoundedCornerShape(14.dp)
                                            )
                                            .border(1.dp, NeonGold.copy(0.55f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🏆", fontSize = 22.sp)
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            "NEET Target",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NeonGold.copy(0.65f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            target,
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = NeonGold,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("🩺", fontSize = 13.sp)
                                            Text(
                                                dreamRole,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White.copy(0.75f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    // 3D Edit button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .shadow(6.dp, RoundedCornerShape(11.dp), spotColor = NeonGold.copy(0.4f))
                                            .background(
                                                Brush.linearGradient(listOf(NeonGold.copy(0.25f), NeonGold.copy(0.08f))),
                                                RoundedCornerShape(11.dp)
                                            )
                                            .border(
                                                1.dp,
                                                Brush.linearGradient(listOf(NeonGold.copy(0.70f), Color.White.copy(0.20f))),
                                                RoundedCornerShape(11.dp)
                                            )
                                            .clickable { showTargetEdit = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Edit, null, tint = NeonGold, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Basic Info
                item {
                    ProfileSection(title = "Personal Details", accentColor = NeonCyan) {
                        ProfileField("Full Name", name, editing, Icons.Default.Person) { name = it }

                        // Date of Birth — 3D Calendar Picker in edit mode
                        if (editing) {
                            NeetDatePickerButton(
                                selectedDate = dob,
                                onDateSelected = { dob = it },
                                accentColor = NeonCyan,
                                label = "Date of Birth"
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cake, null, tint = NeonCyan.copy(0.6f), modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Date of Birth", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                                    Text(dob.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        ProfileField("Email", email, editing, Icons.Default.Email) { email = it }
                        ProfileField("Mobile No.", mobile, editing, Icons.Default.Phone) { mobile = it }
                        ProfileField("Aadhar No.", aadhar, editing, Icons.Default.Badge) { aadhar = it }
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
                                onView = { navController.navigate(fileViewerRoute(tenthUri, "10th Marksheet")) },
                                onRemove = { tenthUri = "" }
                            )
                            FileUploadButton("12th Marksheet", twelfthUri, editing, NeonPurple,
                                onUpload = { twelfthUri = it },
                                onView = { navController.navigate(fileViewerRoute(twelfthUri, "12th Marksheet")) },
                                onRemove = { twelfthUri = "" }
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
                                onClick = { attempts = attempts + NeetAttempt() },
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

                // ── Chapter Library ───────────────────────────────────────────
                item {
                    val clipboardManager = LocalClipboardManager.current
                    var chLibExpanded by remember { mutableStateOf(false) }
                    var jsonInput by remember { mutableStateOf(ChapterStore.loadJson(context)) }
                    val chapCount = remember(jsonInput) { ChapterStore.getChapters(context).size }
                    var showJsonError by remember { mutableStateOf(false) }
                    var jsonErrorMsg by remember { mutableStateOf("") }
                    var jsonAutoFixed by remember { mutableStateOf<String?>(null) }
                    var jsonSaveSuccess by remember { mutableStateOf(false) }
                    val fmt1Example = """{"Physics":["Units and Measurements","Motion in a Straight Line"],"Chemistry":["Some Basic Concepts","Atomic Structure"],"Biology":["The Living World","Biological Classification"]}"""
                    val fmt2Example = """["Units and Measurements","Some Basic Concepts","The Living World"]"""

                    GlassCard(glowColor = NeonGreen) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { chLibExpanded = !chLibExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.size(4.dp, 20.dp).background(NeonGreen, RoundedCornerShape(2.dp)))
                                Icon(Icons.Default.LibraryBooks, null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                Text("Chapter Library", style = MaterialTheme.typography.headlineMedium, color = NeonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (chapCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(NeonGreen.copy(0.18f), RoundedCornerShape(8.dp))
                                            .border(0.5.dp, NeonGreen.copy(0.5f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) { Text("$chapCount chapters", style = MaterialTheme.typography.labelSmall, color = NeonGreen) }
                                }
                                Icon(if (chLibExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                            }

                            AnimatedVisibility(visible = chLibExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NeonDivider(NeonGreen)

                                    // Format guide
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .background(Color.White.copy(0.04f), RoundedCornerShape(12.dp))
                                            .border(0.5.dp, NeonCyan.copy(0.25f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Code, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                                            Text("JSON Format Guide", style = MaterialTheme.typography.labelMedium, color = NeonCyan, fontWeight = FontWeight.Bold)
                                        }

                                        Text("Format 1 — Subject-wise (Recommended):", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.55f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .background(Color(0xFF050E22), RoundedCornerShape(8.dp))
                                                .border(0.5.dp, NeonGreen.copy(0.3f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                "{\n  \"Physics\": [\"Ch1\", \"Ch2\"],\n  \"Chemistry\": [\"Ch3\"],\n  \"Biology\": [\"Ch4\"]\n}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonGreen,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(fmt1Example)) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.ContentCopy, null, tint = NeonCyan.copy(0.7f), modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        Text("Format 2 — Flat list:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.55f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .background(Color(0xFF050E22), RoundedCornerShape(8.dp))
                                                .border(0.5.dp, NeonPurple.copy(0.3f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                "[\"Ch1\", \"Ch2\", \"Ch3\", \"Ch4\"]",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonPurple,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(fmt2Example)) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.ContentCopy, null, tint = NeonCyan.copy(0.7f), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    // JSON input
                                    Text("Paste your chapter JSON below:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                                    OutlinedTextField(
                                        value = jsonInput,
                                        onValueChange = { jsonInput = it; jsonSaveSuccess = false },
                                        placeholder = { Text("Paste JSON here...", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.25f)) },
                                        minLines = 4,
                                        maxLines = 10,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonGreen.copy(0.6f),
                                            unfocusedBorderColor = Color.White.copy(0.15f),
                                            focusedTextColor = NeonGreen,
                                            unfocusedTextColor = Color.White.copy(0.7f),
                                            cursorColor = NeonGreen
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            val trimmed = jsonInput.trim()
                                            if (trimmed.isBlank()) {
                                                ChapterStore.saveJson(context, "")
                                                jsonSaveSuccess = true
                                            } else {
                                                val err = ChapterStore.validateJson(trimmed)
                                                if (err == null) {
                                                    ChapterStore.saveJson(context, trimmed)
                                                    jsonSaveSuccess = true
                                                } else {
                                                    jsonErrorMsg = err
                                                    jsonAutoFixed = ChapterStore.autoCorrectJson(trimmed)
                                                    showJsonError = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (jsonSaveSuccess) StatusCompleted.copy(0.2f) else NeonGreen.copy(0.2f)
                                        ),
                                        border = BorderStroke(1.dp, if (jsonSaveSuccess) StatusCompleted.copy(0.8f) else NeonGreen.copy(0.7f))
                                    ) {
                                        Icon(
                                            if (jsonSaveSuccess) Icons.Default.CheckCircle else Icons.Default.Save,
                                            null,
                                            tint = if (jsonSaveSuccess) StatusCompleted else NeonGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (jsonSaveSuccess) "Saved!" else "Save Chapter Library",
                                            color = if (jsonSaveSuccess) StatusCompleted else NeonGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (showJsonError) {
                                        JsonValidationErrorDialog(
                                            errorMessage = jsonErrorMsg,
                                            autoFixed = jsonAutoFixed,
                                            onAutoCorrect = { fixed ->
                                                jsonInput = fixed
                                                ChapterStore.saveJson(context, fixed)
                                                showJsonError = false
                                                jsonSaveSuccess = true
                                            },
                                            onDismiss = { showJsonError = false }
                                        )
                                    }

                                    NeonDivider(NeonRed.copy(0.35f))

                                    OutlinedButton(
                                        onClick = { ChapterStore.clearChapters(context); jsonInput = "" },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, NeonRed.copy(0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Clear Chapter Library", style = MaterialTheme.typography.labelMedium)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            try { context.cacheDir.deleteRecursively() } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, NeonOrange.copy(0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange)
                                    ) {
                                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Clear App Cache", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Backup & Restore ──────────────────────────────────────
                item {
                    BackupRestoreCard(
                        state = backupState,
                        message = backupMessage,
                        onBackup  = { folderPickerLauncher.launch(null) },
                        onRestore = { filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream")) },
                        onDismiss = { backupVm.resetState() }
                    )
                }
            }
        }
    }

    // Target Edit Dialog
    if (showTargetEdit) {
        var editTarget by remember { mutableStateOf(target) }
        var editRole by remember { mutableStateOf(dreamRole) }
        AlertDialog(
            onDismissRequest = { showTargetEdit = false },
            containerColor = Color(0xFF08122A),
            shape = RoundedCornerShape(24.dp),
            title = null,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = NeonGold.copy(0.5f))
                                .background(Brush.linearGradient(listOf(NeonGold.copy(0.30f), NeonGold.copy(0.08f))), RoundedCornerShape(14.dp))
                                .border(1.dp, NeonGold.copy(0.55f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("🏆", fontSize = 22.sp) }
                        Column {
                            Text("Edit Target", style = MaterialTheme.typography.headlineMedium, color = NeonGold, fontWeight = FontWeight.ExtraBold)
                            Text("Set your NEET goal & dream role", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.45f))
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, NeonGold.copy(0.4f), Color.Transparent))))

                    // Target score field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🎯", fontSize = 13.sp)
                            Text("NEET Target Score", style = MaterialTheme.typography.labelMedium, color = NeonGold.copy(0.75f), fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = NeonGold.copy(0.2f))
                                .background(Brush.linearGradient(listOf(NeonGold.copy(0.10f), Color(0xFF050E22))), RoundedCornerShape(14.dp))
                                .border(1.dp, NeonGold.copy(0.35f), RoundedCornerShape(14.dp))
                        ) {
                            TextField(
                                value = editTarget,
                                onValueChange = { editTarget = it },
                                placeholder = { Text("e.g. 700/720", color = Color.White.copy(0.25f)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = NeonGold,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = NeonGold
                                ),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Dream role field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🩺", fontSize = 13.sp)
                            Text("Dream Role", style = MaterialTheme.typography.labelMedium, color = NeonGold.copy(0.75f), fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = NeonGold.copy(0.2f))
                                .background(Brush.linearGradient(listOf(NeonGold.copy(0.10f), Color(0xFF050E22))), RoundedCornerShape(14.dp))
                                .border(1.dp, NeonGold.copy(0.35f), RoundedCornerShape(14.dp))
                        ) {
                            TextField(
                                value = editRole,
                                onValueChange = { editRole = it },
                                placeholder = { Text("e.g. MBBS Doctor", color = Color.White.copy(0.25f)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = NeonGold
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .shadow(8.dp, RoundedCornerShape(13.dp), spotColor = NeonGold.copy(0.5f))
                        .background(Brush.linearGradient(listOf(NeonGold.copy(0.35f), NeonGold.copy(0.12f))), RoundedCornerShape(13.dp))
                        .border(1.dp, NeonGold.copy(0.65f), RoundedCornerShape(13.dp))
                        .clickable {
                            target = editTarget
                            dreamRole = editRole
                            vm.save(
                                (profile ?: StudentProfile()).copy(
                                    name = name, dob = dob, email = email, mobile = mobile,
                                    aadharNo = aadhar, tenthPercentage = tenth, tenthMarksheetUri = tenthUri,
                                    twelfthPercentage = twelfth, twelfthMarksheetUri = twelfthUri,
                                    photoUri = photoUri, targetScore = editTarget, dreamRole = editRole,
                                    neetAttempts = attempts
                                )
                            )
                            showTargetEdit = false
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Save, null, tint = NeonGold, modifier = Modifier.size(15.dp))
                        Text("Save", color = NeonGold, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(13.dp))
                        .background(Color.White.copy(0.05f), RoundedCornerShape(13.dp))
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(13.dp))
                        .clickable { showTargetEdit = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Cancel", color = Color.White.copy(0.55f), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
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
fun RowScope.FileUploadButton(label: String, uri: String, editing: Boolean, color: Color, onUpload: (String) -> Unit, onView: () -> Unit, onRemove: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { picked ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, picked)
                onUpload(localPath ?: picked.toString())
            }
        }
    }
    Box(modifier = Modifier.weight(1f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (uri.isNotBlank()) color.copy(0.12f) else Color.White.copy(0.04f))
                .border(1.dp, if (uri.isNotBlank()) color.copy(0.4f) else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                .clickable { if (uri.isNotBlank()) onView() else if (editing) launcher.launch(arrayOf("*/*")) }
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
        if (uri.isNotBlank() && onRemove != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .background(NeonRed, CircleShape)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(11.dp))
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
    val scope = rememberCoroutineScope()
    val marksheetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                marksheetUri = localPath ?: u.toString()
                onUpdate(attempt.copy(marksheetUri = marksheetUri))
            }
        }
    }
    val qpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                qpUri = localPath ?: u.toString()
                onUpdate(attempt.copy(questionPaperUri = qpUri))
            }
        }
    }
    val solLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            scope.launch {
                val localPath = com.neet.tracker.util.copyUriToAppFiles(context, u)
                solUri = localPath ?: u.toString()
                onUpdate(attempt.copy(solutionPdfUri = solUri))
            }
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
                    SmallFileButton("Marksheet", marksheetUri, NeonGold, { marksheetLauncher.launch(arrayOf("*/*")) }, { onViewFile(marksheetUri, "Marksheet $year") }, Modifier.weight(1f), onRemove = { marksheetUri = ""; onUpdate(attempt.copy(marksheetUri = "")) })
                    SmallFileButton("Question Paper", qpUri, NeonGold, { qpLauncher.launch(arrayOf("*/*")) }, { onViewFile(qpUri, "Question Paper $year") }, Modifier.weight(1f), onRemove = { qpUri = ""; onUpdate(attempt.copy(questionPaperUri = "")) })
                    SmallFileButton("Solution", solUri, NeonGold, { solLauncher.launch(arrayOf("*/*")) }, { onViewFile(solUri, "Solution $year") }, Modifier.weight(1f), onRemove = { solUri = ""; onUpdate(attempt.copy(solutionPdfUri = "")) })
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
fun SmallFileButton(label: String, uri: String, color: Color, onUpload: () -> Unit, onView: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
        if (uri.isNotBlank() && onRemove != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .background(NeonRed, CircleShape)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
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

// ── Backup & Restore Card ─────────────────────────────────────────────────────

@Composable
fun BackupRestoreCard(
    state: BackupState,
    message: String,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = Color(0xFF00E5FF)
    val restoreColor = NeonGold

    GlassCard(glowColor = accentColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Brush.linearGradient(listOf(accentColor.copy(0.30f), accentColor.copy(0.07f))),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, accentColor.copy(0.50f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudSync, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        "Backup & Restore",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Export all data as .neet file · Merge-restore safely",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.45f)
                    )
                }
            }

            NeonDivider(accentColor.copy(0.35f))

            // ── Buttons ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Backup button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(0.12f))
                        .border(1.dp, accentColor.copy(0.55f), RoundedCornerShape(12.dp))
                        .clickable(enabled = state != BackupState.RUNNING) { onBackup() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Backup",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Save .neet file",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor.copy(0.55f)
                        )
                    }
                }

                // Restore button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(restoreColor.copy(0.12f))
                        .border(1.dp, restoreColor.copy(0.55f), RoundedCornerShape(12.dp))
                        .clickable(enabled = state != BackupState.RUNNING) { onRestore() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            null,
                            tint = restoreColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Restore",
                            style = MaterialTheme.typography.labelMedium,
                            color = restoreColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Open .neet file",
                            style = MaterialTheme.typography.labelSmall,
                            color = restoreColor.copy(0.55f)
                        )
                    }
                }
            }

            // ── Status area ───────────────────────────────────────────────
            AnimatedVisibility(visible = state != BackupState.IDLE) {
                val (statusColor, statusIcon, statusBg) = when (state) {
                    BackupState.RUNNING -> Triple(accentColor, Icons.Default.HourglassTop, accentColor.copy(0.08f))
                    BackupState.SUCCESS -> Triple(Color(0xFF00E676), Icons.Default.CheckCircle, Color(0xFF00E676).copy(0.08f))
                    BackupState.ERROR   -> Triple(NeonRed, Icons.Default.ErrorOutline, NeonRed.copy(0.08f))
                    BackupState.IDLE    -> Triple(accentColor, Icons.Default.Info, accentColor.copy(0.08f))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusBg)
                        .border(0.5.dp, statusColor.copy(0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state == BackupState.RUNNING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = statusColor,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            message,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (state == BackupState.SUCCESS || state == BackupState.ERROR) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = statusColor.copy(0.6f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onDismiss() }
                            )
                        }
                    }
                }
            }

            // ── Info note ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = Color.White.copy(0.25f),
                    modifier = Modifier.size(12.dp).padding(top = 1.dp)
                )
                Text(
                    "Backup saves your entire database, PDFs, annotations and settings. " +
                    "Restore merges backup data with existing data without deleting anything.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.28f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
