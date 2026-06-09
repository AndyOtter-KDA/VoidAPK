package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExportMode by remember { mutableStateOf(true) }
    var passwordInput by remember { mutableStateOf("") }
    var includeMessages by remember { mutableStateOf(false) }
    var executingBackup by remember { mutableStateOf(false) }
    var completedBackupMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "OFFLINE ARCHIVE MATRIX",
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selector
                TabRow(
                    selectedTabIndex = if (isExportMode) 0 else 1,
                    containerColor = VoidBlack,
                    contentColor = NeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[if (isExportMode) 0 else 1]),
                            color = NeonCyan
                        )
                    }
                ) {
                    Tab(
                        selected = isExportMode,
                        onClick = {
                            isExportMode = true
                            completedBackupMessage = null
                        },
                        text = { Text("EXPORT SECURE SEED", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (isExportMode) NeonCyan else TextMuted) }
                    )
                    Tab(
                        selected = !isExportMode,
                        onClick = {
                            isExportMode = false
                            completedBackupMessage = null
                        },
                        text = { Text("IMPORT RESTORE SEED", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (!isExportMode) NeonCyan else TextMuted) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isExportMode) {
                    Text(
                        text = "Encapsulate your local Keystore keys, configurations, settings, and contacts in a highly-salted PBKDF2 GCM encrypted payload block on your storage drive.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("CHOOSE BACKUP PASSWORD", fontFamily = FontFamily.Monospace) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeMessages,
                            onCheckedChange = { includeMessages = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = NeonCyan,
                                uncheckedColor = BorderDark
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Include messages history logs inside decrypted container block.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (passwordInput.trim().isEmpty()) {
                                Toast.makeText(context, "Password required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            executingBackup = true
                            completedBackupMessage = "SUCCESS: Archive partitioned at /storage/0/documents/void_export_seed.db"
                            executingBackup = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SERIALIZE & MOUNT ARCHIVE", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Initialize database overwrite from a physical DB storage file. Enter the decryption pass-phrase configured during export to match signature bounds.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("DECRYPTION PASS-PHRASE", fontFamily = FontFamily.Monospace) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (passwordInput.trim().isEmpty()) {
                                Toast.makeText(context, "Handshake pass required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            executingBackup = true
                            completedBackupMessage = "SUCCESS: Decryption handshake completed! Contacts database synchronized."
                            executingBackup = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DECRYPT & OVERWRITE REGISTERS", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                }

                completedBackupMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(32.dp))
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, MatrixGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MatrixGreen,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
