package com.voidchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.data.models.Message
import com.voidchat.app.ui.theme.*

@Composable
fun MessageBubble(
    message: Message,
    myDisplayId: String,
    modifier: Modifier = Modifier
) {
    val isMine = message.senderId == myDisplayId
    var showMetadata by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }

    // Automated trigger for fadeOut when destroyed is flagged
    LaunchedEffect(message.destroyed) {
        if (message.destroyed) {
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(600)) + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 12.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomStart = if (isMine) 8.dp else 0.dp,
                    bottomEnd = if (isMine) 0.dp else 8.dp
                ),
                color = if (isMine) VoidBlack else VoidDarkNavy,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isMine) NeonCyan else BorderDark
                ),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clickable { showMetadata = !showMetadata }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        // Since this is encrypted in production models, we output standard UI payload securely
                        text = if (message.destroyed) "[SEAL RECOILED - ZEROED OUT]" else message.encryptedPayload.take(32) + "...",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMine) "ME" else message.senderId.take(9),
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        if (message.selfDestructSeconds > 0) {
                            SelfDestructIndicator(
                                secondsRemaining = calculateSecondsRemaining(message),
                                totalSeconds = message.selfDestructSeconds
                            )
                        }
                    }
                }
            }

            if (showMetadata) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = VoidBlack.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "SECURE PROTOCOL METADATA:",
                            style = MaterialTheme.typography.labelSmall,
                            color = HotPinkLight
                        )
                        Text(
                            text = "IV: ${message.iv}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "PAYLOAD: ${message.encryptedPayload.take(24)}[...]",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "ALG: AES-256-GCM / ECDH SECP256R1",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen
                        )
                    }
                }
            }
        }
    }
}

private fun calculateSecondsRemaining(message: Message): Int {
    val age = (System.currentTimeMillis() - message.timestamp) / 1000
    val rem = message.selfDestructSeconds - age.toInt()
    return if (rem > 0) rem else 0
}
