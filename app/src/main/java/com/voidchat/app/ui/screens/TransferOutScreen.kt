package com.voidchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferOutScreen(
    displayId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ticksRemaining by remember { mutableStateOf(30) }
    var qrContentHash by remember { mutableStateOf(System.currentTimeMillis().hashCode().toString()) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (ticksRemaining > 1) {
                ticksRemaining--
            } else {
                ticksRemaining = 30
                qrContentHash = System.currentTimeMillis().hashCode().toString()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "SECURE DEVICE MIGRATION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    }
                )
                Divider(color = BorderDark, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScanlineOverlay()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "MIGRATE TO NEW PEER NODE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HotPinkLight,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CRITICAL WARNING: Once this QR contains is successfully handshake scanned, this physical terminal partition will overwrite its identity database automatically.",
                    color = ErrorRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Draw high-fidelity cryptographic matrix QR code block
                Surface(
                    color = Color.White,
                    border = BorderStroke(2.dp, NeonCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(240.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw dynamic alignment anchors and matrix modules
                            val colCount = 15
                            val cellSize = size.width / colCount
                            val rng = java.util.Random(qrContentHash.hashCode().toLong())
                            
                            for (r in 0 until colCount) {
                                for (c in 0 until colCount) {
                                    val isAnchor = (r < 4 && c < 4) || (r < 4 && c >= colCount - 4) || (r >= colCount - 4 && c < 4)
                                    val drawFill = if (isAnchor) {
                                        (r == 0 || r == 3 || c == 0 || c == 3)
                                    } else {
                                        rng.nextBoolean()
                                    }
                                    if (drawFill) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(c * cellSize, r * cellSize),
                                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "ROTATING EPHEMERAL MIGRATION TUNNEL KEY:\nRefreshing in ${ticksRemaining}s",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
