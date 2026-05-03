package com.ismartcoding.plain.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.helpers.PerAppLockHelper
import kotlinx.coroutines.launch

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
            sendUserHome()
            finish()
            return
        }
        val lockConfig = PerAppLockHelper.getLock(packageName)
        if (lockConfig == null) {
            finish()
            return
        }
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
                    hashedCredential = hashedCredential,
                    errorMsg = errorMsg,
                    onVerify = { credential ->
                        scope.launch {
                            val ok = PerAppLockHelper.verify(credential, hashedCredential)
                            if (ok) {
                                onUnlocked()
                            } else {
                                PerAppLockHelper.recordAttempt(packageName, false)
                                errorMsg = "Wrong pattern. Try again."
                            }
                        }
                    }
                )
            } else {
                PinInput(
                    hashedCredential = hashedCredential,
                    errorMsg = errorMsg,
                    onVerify = { credential ->
                        scope.launch {
                            val ok = PerAppLockHelper.verify(credential, hashedCredential)
                            if (ok) {
                                onUnlocked()
                            } else {
                                PerAppLockHelper.recordAttempt(packageName, false)
                                errorMsg = "Wrong PIN. Try again."
                            }
                        }
                    }
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
    hashedCredential: String,
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
    hashedCredential: String,
    errorMsg: String,
    onVerify: (String) -> Unit,
) {
    val patternSeq = remember { mutableStateListOf<Int>() }
    var localError by remember { mutableStateOf("") }
    val displayError = errorMsg.ifBlank { localError }

    LaunchedEffect(errorMsg) { if (errorMsg.isNotBlank()) { patternSeq.clear(); localError = "" } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (patternSeq.isEmpty()) "Draw your unlock pattern" else patternSeq.joinToString(" → "),
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (col in 0..2) {
                    val n = row * 3 + col + 1
                    val idx = patternSeq.indexOf(n)
                    val selected = idx >= 0
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF7C3AED) else Color(0xFF2D2D4A))
                            .border(2.dp, if (selected) Color(0xFF7C3AED) else Color(0xFF555577), CircleShape)
                            .clickable {
                                if (!selected) {
                                    patternSeq.add(n)
                                } else if (idx == patternSeq.size - 1) {
                                    patternSeq.removeAt(idx)
                                }
                                localError = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$n",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            if (selected) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color(0xFFE0D7FF),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
            if (row < 2) Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { patternSeq.clear(); localError = "" },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
            ) { Text("Clear") }
            Button(
                onClick = {
                    if (patternSeq.size < 4) {
                        localError = "Pattern must have at least 4 dots"
                    } else {
                        onVerify(patternSeq.joinToString(""))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
            ) { Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold) }
        }
        if (displayError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(displayError, color = Color(0xFFEF4444), fontSize = 13.sp)
        }
    }
}
