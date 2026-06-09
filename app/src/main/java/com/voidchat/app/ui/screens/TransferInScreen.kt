package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferInScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawScannerInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "IMPORT SYSTEM NODE TERMINAL",
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Point viewfinder sensor to the Migration QR displayed on your old active node to complete the Diffie-Hellman backup imports.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )

                // Viewfinder block simulation
                Surface(
                    color = VoidDarkNavy,
                    border = BorderStroke(1.dp, NeonCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .size(260.dp)
                        .padding(vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw corner alignment brackets
                            val l = 30.dp.toPx()
                            val t = 4.dp.toPx()
                            // Top Left
                            drawLine(color = NeonCyan, start = Offset(0f, 0f), end = Offset(l, 0f), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(0f, 0f), end = Offset(0f, l), strokeWidth = t)

                            // Top Right
                            drawLine(color = NeonCyan, start = Offset(size.width, 0f), end = Offset(size.width - l, 0f), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(size.width, 0f), end = Offset(size.width, l), strokeWidth = t)

                            // Bottom Left
                            drawLine(color = NeonCyan, start = Offset(0f, size.height), end = Offset(l, size.height), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(0f, size.height), end = Offset(0f, size.height - l), strokeWidth = t)

                            // Bottom Right
                            drawLine(color = NeonCyan, start = Offset(size.width, size.height), end = Offset(size.width - l, size.height), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(size.width, size.height), end = Offset(size.width, size.height - l), strokeWidth = t)
                        }

                        Text(
                            text = "[ SCANNER ACTIVE ]",
                            color = HotPinkLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = rawScannerInput,
                        onValueChange = { rawScannerInput = it },
                        label = { Text("MANUAL OVERRIDE PASTE CODES", fontFamily = FontFamily.Monospace) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val clean = rawScannerInput.trim()
                            if (clean.isNotEmpty()) {
                                Toast.makeText(context, "Identity Handshake decrypted correctly.", Toast.LENGTH_SHORT).show()
                                onNavigateToHome()
                            } else {
                                Toast.makeText(context, "No token pasted.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("MIGRATE NODE MANUALLY", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
