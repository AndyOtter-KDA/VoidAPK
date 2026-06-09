package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.components.IdentityCard
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToTransferIn: () -> Unit,
    onNavigateToTransferOut: () -> Unit,
    onNavigateToDonate: () -> Unit,
    onLogOut: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val displayId by viewModel.displayId.collectAsState()
    val username by viewModel.username.collectAsState()
    val version = viewModel.getAppVersion()

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var newUsernameInput by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValueInput by remember { mutableStateOf("") }
    var showSupportInfoDialog by remember { mutableStateOf(false) }

    if (showSupportInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSupportInfoDialog = false },
            title = {
                Text(
                    text = "ESTABLISH DIRECT SECURITY TUNNEL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 14.sp
                )
            },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkBlue),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "This action initializes an encrypted 1-on-1 private connection with the official Void Support operator.\n\nAll subsequent logs, transmissions, and responses routing across this link are fully end-to-end encrypted with public keys.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSupportInfoDialog = false
                        onContactSupport()
                    }
                ) {
                    Text(
                        text = "CONN_LAUNCH()",
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupportInfoDialog = false }) {
                    Text("ABORT", fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            },
            containerColor = VoidDarkNavy
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("CONFIRM DESTRUCT COMMAND", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = { Text("This will permanently overwrite the local cryptographic keys partition and delete identity files. This action is IRREVERSIBLE. Confirm destruction of node @$username?", fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteIdentity {
                            onLogOut()
                        }
                    }
                ) {
                    Text("YES, EXECUTE PURGE", color = ErrorRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("ABORT OPERATION", fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            },
            containerColor = VoidDarkNavy
        )
    }

    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text("RE-REGISTER IDENT HANDLE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NeonCyan) },
            text = {
                Column {
                    Text("Set a new globally unique routing address handle.", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newUsernameInput,
                        onValueChange = { newUsernameInput = it },
                        label = { Text("IDENT HANDLE", fontFamily = FontFamily.Monospace) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newUsernameInput.trim()
                        if (trimmed.isEmpty()) {
                            Toast.makeText(context, "Handle cannot be empty", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        viewModel.updateUsername(trimmed) { success ->
                            if (success) {
                                Toast.makeText(context, "Handle successfully updated", Toast.LENGTH_SHORT).show()
                                showUsernameDialog = false
                            } else {
                                Toast.makeText(context, "Handle taken or registration failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("COMMIT REGISTER", color = NeonCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("ABORT", fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            },
            containerColor = VoidDarkNavy
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("SET SECURE PIN LOCKCODE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NeonCyan) },
            text = {
                Column {
                    Text("Input a 4-digit PIN to securely authorize identity decryption on launch.", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinValueInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                pinValueInput = input
                            }
                        },
                        label = { Text("4-DIGIT PASSCODE", fontFamily = FontFamily.Monospace) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinValueInput.isNotEmpty() && pinValueInput.length < 4) {
                            Toast.makeText(context, "PIN must be exactly 4 digits or empty to clear", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val finalPin = pinValueInput.ifEmpty { null }
                        viewModel.updateSettings(settings.copy(pinCode = finalPin))
                        Toast.makeText(
                            context,
                            if (finalPin == null) "PIN Code cleared" else "PIN Code registered successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        showPinDialog = false
                    }
                ) {
                    Text("COMMIT REGISTER", color = NeonCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            },
            containerColor = VoidDarkNavy
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "NODE PARAMETERS",
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
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                // Profile Section
                IdentityCard(
                    displayId = displayId,
                    username = username.ifEmpty { "void_operative" },
                    onShareQr = {
                        Toast.makeText(context, "QR code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        newUsernameInput = username
                        showUsernameDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("REREGISTER IDENT HANDLE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Security Section
                Text(
                    text = "HARDWARE SECURE COMPARTMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = HotPinkLight,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("ENABLE BIOMETRICS", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Switch(
                                checked = settings.biometricLock,
                                onCheckedChange = {
                                    viewModel.updateSettings(settings.copy(biometricLock = it))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = VoidDarkBlue
                                )
                            )
                        }

                        if (settings.biometricLock) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = BorderDark, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PIN PASSCODE", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = if (settings.pinCode.isNullOrEmpty()) "NOT INSTALLED (SYSTEM BYPASS)" else "CRYPT-SHIELD ENGAGED",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (settings.pinCode.isNullOrEmpty()) HotPinkLight else NeonCyan
                                    )
                                }
                                Button(
                                    onClick = {
                                        pinValueInput = settings.pinCode ?: ""
                                        showPinDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                    border = BorderStroke(1.dp, BorderDark),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (settings.pinCode.isNullOrEmpty()) "CONFIGURE" else "MODIFY PIN",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Backup & Migrate Section
                Text(
                    text = "PARTITION ARCHIVING & MIGRATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("BACKUPS", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                    }

                    Button(
                        onClick = onNavigateToTransferOut,
                        colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("MIGRATE OUT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                    }
                    
                    Button(
                        onClick = onNavigateToTransferIn,
                        colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("IMPORT IN", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Help & Support Section
                Text(
                    text = "HELP & CLIENT SUPPORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSupportInfoDialog = true }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Contact Operator Support →", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }

                        Divider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "Void Policy: Zero logs. Zero metadata. Encrypted storage.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Privacy Covenant Policy →", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // About section
                Text(
                    text = "VOID DEPLOYMENT DIAGNOSTICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("VERSION: $version", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                        Text("E2E COMPLIANCE: 100% SECURE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixGreen)
                        
                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))
                        
                        Button(
                            onClick = onNavigateToDonate,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SUPPORT DEVELOPMENT INFRA", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = VoidBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Destruct button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PERMANENTLY DESTRUCT NODE IDENTITY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
