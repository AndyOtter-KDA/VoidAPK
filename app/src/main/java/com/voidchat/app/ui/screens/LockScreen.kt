package com.voidchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*

@Composable
fun LockScreen(
    correctPinCode: String,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Cyber Decrypt System
            Text(
                text = "SYSTEM PARTITION ENCRYPTED",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HotPinkLight,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "AUTHORIZATION REQUIRED",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // PIN Dots Display
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isFilled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) NeonCyan else VoidDarkNavy
                            )
                            .border(
                                width = 1.dp,
                                color = if (isFilled) NeonCyan else BorderDark,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Error or Status text
            Text(
                text = errorMessage ?: "ENTER 4-DIGIT DECRYPT PASSCODE",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (errorMessage != null) ErrorRed else NeonCyan
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Number Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val padLayout = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLR", "0", "DEL")
                )

                padLayout.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { value ->
                            KeypadButton(
                                value = value,
                                onClick = {
                                    errorMessage = null
                                    when (value) {
                                        "CLR" -> enteredPin = ""
                                        "DEL" -> {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += value
                                                if (enteredPin.length == 4) {
                                                    if (enteredPin == correctPinCode) {
                                                        onUnlocked()
                                                    } else {
                                                        errorMessage = "PASSCODE MATCH ERROR. DECRYPTION FAILED."
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSpecial = value == "CLR" || value == "DEL"
    Surface(
        color = if (isSpecial) VoidBlack else VoidDarkNavy,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (isSpecial) BorderDark else BorderDark),
        modifier = modifier
            .size(68.dp)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = value,
                color = if (isSpecial) HotPinkLight else TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isSpecial) 12.sp else 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
