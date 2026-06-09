package com.voidchat.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*

@Composable
fun SelfDestructIndicator(
    secondsRemaining: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier
) {
    val ratio = secondsRemaining.toFloat() / totalSeconds.toFloat()
    val color = when {
        ratio <= 0.25f -> DestructUrgent
        ratio <= 0.60f -> DestructWarning
        else -> DestructNormal
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = "🔥",
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "${secondsRemaining}s",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
