package com.ismartcoding.plain.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.helpers.IntruderCaptureHelper
import com.ismartcoding.plain.helpers.IntruderFrontCamera
import com.ismartcoding.plain.helpers.PerAppLockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.compose.foundation.gestures.detectDragGestures

class PerAppLockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        val packageName = intent.getStringExtra("packageName") ?: run {
            sendUserHome(); finish(); return
        }
        val lockConfig = PerAppLockHelper.getLock(packageName)
        if (lockConfig == null) { finish(); return }

        setContent {
            MaterialTheme {
                PerAppLockScreen(
                    packageName = packageName,
                    lockType = lockConfig.lockType,
                    hashedCredential = lockConfig.hashedCredential,
                    onUnlocked = {
                        PerAppLockHelper.markUnlocked(packageName)
                        PerAppLockHelper.recordAttempt(packageName, true)
                        finishAndRemoveTask()
                    },
                    onWrongAttempt = {
                        PerAppLockHelper.recordAttempt(packageName, false)
                        IntruderFrontCamera.fireAndForget(
                            trigger = IntruderCaptureHelper.Trigger.PER_APP_LOCK,
                            triggerDetail = "Wrong credential for $packageName (on-device)",
                            scope = CoroutineScope(Dispatchers.IO),
                        )
                    },
                    onCancel = {
                        sendUserHome()
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        sendUserHome()
        finishAndRemoveTask()
    }

    private fun sendUserHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN)
            home.addCategory(Intent.CATEGORY_HOME)
            home.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(home)
        } catch (_: Throwable) {}
    }
}

@Composable
private fun PerAppLockScreen(
    packageName: String,
    lockType: String,
    hashedCredential: String,
    onUnlocked: () -> Unit,
    onWrongAttempt: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var errorMsg by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7C3AED)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔐", fontSize = 32.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "App Locked",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = packageName.substringAfterLast('.'),
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (lockType == "pattern") {
                PatternInput(
                    errorMsg = errorMsg,
                    onVerify = { credential ->
                        scope.launch {
                            val ok = PerAppLockHelper.verify(credential, hashedCredential)
                            if (ok) {
                                onUnlocked()
                            } else {
                                onWrongAttempt()
                                errorMsg = "Wrong pattern. Try again."
                            }
                        }
                    },
                )
            } else {
                PinInput(
                    errorMsg = errorMsg,
                    onVerify = { credential ->
                        scope.launch {
                            val ok = PerAppLockHelper.verify(credential, hashedCredential)
                            if (ok) {
                                onUnlocked()
                            } else {
                                onWrongAttempt()
                                errorMsg = "Wrong PIN. Try again."
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun PinInput(
    errorMsg: String,
    onVerify: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    val displayError = errorMsg.ifBlank { localError }

    LaunchedEffect(errorMsg) { if (errorMsg.isNotBlank()) { pin = ""; localError = "" } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = pin,
            onValueChange = { v ->
                pin = v.filter { it.isDigit() }.take(12)
                localError = ""
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = displayError.isNotEmpty(),
            label = { Text("Enter PIN", color = Color(0xFFAAAAAA)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (displayError.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(displayError, color = Color(0xFFEF4444), fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (pin.isNotBlank()) onVerify(pin) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
        ) {
            Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PatternInput(
    errorMsg: String,
    onVerify: (String) -> Unit,
) {
    val patternSeq = remember { mutableStateListOf<Int>() }
    var localError by remember { mutableStateOf("") }
    val displayError = errorMsg.ifBlank { localError }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(errorMsg) {
        if (errorMsg.isNotBlank()) {
            patternSeq.clear()
            localError = ""
            isDragging = false
        }
    }

    val dotColor = Color(0xFF7C3AED)
    val dotInactive = Color(0xFF3D3D6B)
    val dotRingInactive = Color(0xFF555577)
    val lineColor = Color(0xFF7C3AED)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Status text above the grid
        Text(
            text = if (patternSeq.isEmpty()) "Draw your unlock pattern" else "●".repeat(patternSeq.size),
            color = if (displayError.isNotEmpty()) Color(0xFFEF4444) else Color(0xFFAAAAAA),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(20.dp))

        // Pattern canvas — 280×280 dp with drag gestures
        Box(
            modifier = Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            patternSeq.clear()
                            localError = ""
                            isDragging = true
                            dragPos = offset
                            val cell = size.width / 3f
                            for (i in 0..8) {
                                val cx = ((i % 3) + 0.5f) * cell
                                val cy = ((i / 3) + 0.5f) * cell
                                if (sqrt((offset.x - cx).pow(2) + (offset.y - cy).pow(2)) < cell * 0.38f) {
                                    patternSeq.add(i + 1)
                                    break
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            dragPos = change.position
                            val cell = size.width / 3f
                            for (i in 0..8) {
                                val n = i + 1
                                if (patternSeq.contains(n)) continue
                                val cx = ((i % 3) + 0.5f) * cell
                                val cy = ((i / 3) + 0.5f) * cell
                                if (sqrt((change.position.x - cx).pow(2) + (change.position.y - cy).pow(2)) < cell * 0.38f) {
                                    patternSeq.add(n)
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            when {
                                patternSeq.size >= 4 -> onVerify(patternSeq.joinToString(""))
                                patternSeq.isNotEmpty() -> {
                                    localError = "Connect at least 4 dots"
                                    patternSeq.clear()
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            patternSeq.clear()
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / 3f
                val outerR = cell * 0.22f
                val innerR = cell * 0.09f

                // Lines between connected dots
                for (i in 0 until patternSeq.size - 1) {
                    val a = patternSeq[i] - 1
                    val b = patternSeq[i + 1] - 1
                    drawLine(
                        color = lineColor.copy(alpha = 0.75f),
                        start = Offset(((a % 3) + 0.5f) * cell, ((a / 3) + 0.5f) * cell),
                        end   = Offset(((b % 3) + 0.5f) * cell, ((b / 3) + 0.5f) * cell),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round,
                    )
                }

                // Trailing line from last dot to current finger
                if (isDragging && patternSeq.isNotEmpty()) {
                    val last = patternSeq.last() - 1
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(((last % 3) + 0.5f) * cell, ((last / 3) + 0.5f) * cell),
                        end   = dragPos,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }

                // Dots
                for (i in 0..8) {
                    val cx = ((i % 3) + 0.5f) * cell
                    val cy = ((i / 3) + 0.5f) * cell
                    val selected = patternSeq.contains(i + 1)

                    // Outer glow ring when selected
                    if (selected) {
                        drawCircle(
                            color = dotColor.copy(alpha = 0.18f),
                            radius = outerR,
                            center = Offset(cx, cy),
                        )
                    }
                    // Ring border
                    drawCircle(
                        color = if (selected) dotColor else dotRingInactive,
                        radius = outerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.5f),
                    )
                    // Filled inner dot
                    drawCircle(
                        color = if (selected) dotColor else dotInactive,
                        radius = innerR,
                        center = Offset(cx, cy),
                    )
                }
            }
        }

        // Number sequence shown below the grid
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (patternSeq.isEmpty()) "— — —" else patternSeq.joinToString(" → "),
            color = Color(0xFF888899),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )

        if (displayError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                displayError,
                color = Color(0xFFEF4444),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { patternSeq.clear(); localError = ""; isDragging = false },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
        ) {
            Text("Clear")
        }
    }
}
