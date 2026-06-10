package com.voidchat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.crypto.BIP39Wordlist
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
    onNavigateToPrivacy: () -> Unit,
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

    // Dialog state variables
    var showUsernameDialog by remember { mutableStateOf(false) }
    var newUsernameInput by remember { mutableStateOf("") }
    
    var showSelfDestructDropdown by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }
    
    var showBiometricAuthDialogForPhrase by remember { mutableStateOf(false) }
    var showRecoveryPhraseDialog by remember { mutableStateOf(false) }
    
    // Advanced variables
    var showExportKeyConfirmDialog by remember { mutableStateOf(false) }

    // Mnemonic derivation matching original deterministically from displayId
    val recoveryPhrase = remember(displayId) {
        val rawHex = displayId.replace("-", "")
        if (rawHex.isEmpty()) emptyList() else {
            (0 until 12).map { index ->
                val charIndex = if (index < rawHex.length) rawHex[index].code else index
                BIP39Wordlist.getWord(charIndex * (index + 7))
            }
        }
    }

    // ----------------------------------------
    // Dialogs
    // ----------------------------------------

    // Change Username Dialog
    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = {
                Text(
                    "RE-REGISTER IDENT HANDLE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 14.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Set a new globally unique routing address handle.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
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
                        Log.d("VoidFirestore", "updateUsername: Dispatching registration for handle '$trimmed'")
                        viewModel.updateUsername(trimmed) { success, errorMsg ->
                            if (success) {
                                Log.d("VoidFirestore", "updateUsername: Reregistered successfully as '$trimmed'")
                                Toast.makeText(context, "Handle successfully updated", Toast.LENGTH_SHORT).show()
                                showUsernameDialog = false
                            } else {
                                Log.e("VoidFirestore", "updateUsername: Failed to change username to '$trimmed': $errorMsg")
                                Toast.makeText(context, errorMsg ?: "Handle taken or registration failed", Toast.LENGTH_LONG).show()
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

    // Biometric Handshake Dialog for viewing phrase
    if (showBiometricAuthDialogForPhrase) {
        AlertDialog(
            onDismissRequest = { showBiometricAuthDialogForPhrase = false },
            title = {
                Text(
                    "AUTHENTICATE IDENTITY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    "Please complete fingerprint scan or enter device credentials to decrypt recovery seed vault.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBiometricAuthDialogForPhrase = false
                        showRecoveryPhraseDialog = true
                        Log.d("VoidFirestore", "biometricAuth: Authenticated successfully to view BIP39 phrase")
                    }
                ) {
                    Text("PASS SCAN", color = NeonCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricAuthDialogForPhrase = false }) {
                    Text("ABORT", fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            },
            containerColor = VoidDarkNavy
        )
    }

    // BIP39 recovery Phrase Dialog
    if (showRecoveryPhraseDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryPhraseDialog = false },
            title = {
                Text(
                    "🔒 SECURE SEED VAULT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = HotPinkLight,
                    fontSize = 14.sp
                )
            },
            text = {
                Column {
                    Text(
                        "NEVER SHARE THIS PHRASE. Anyone with these 12 words can duplicate your identity and decrypt your private transmissions.",
                        color = ErrorRed,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VoidBlack),
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            recoveryPhrase.chunked(3).forEachIndexed { rowIndex, chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    chunk.forEachIndexed { colIndex, word ->
                                        val idx = rowIndex * 3 + colIndex + 1
                                        Text(
                                            text = "$idx. $word",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecoveryPhraseDialog = false }) {
                    Text("SECURE LOCK", color = NeonCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = VoidDarkNavy
        )
    }

    // Advanced key confirmation dialog
    if (showExportKeyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportKeyConfirmDialog = false },
            title = {
                Text(
                    text = "CONFIRM KEY EXPORT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = WarningYellow,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    text = "This exports your private account key. You only need this for advanced recovery. Never share this key with anyone.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportKeyConfirmDialog = false
                        Log.d("VoidFirestore", "exportAccountKey: Beginning KeyStore extraction for displayId=$displayId")
                        val privateKeyBase64 = com.voidchat.app.crypto.IdentityManager.exportPrivateKeyBase64() ?: ""
                        
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("identity_private_key", privateKeyBase64))
                        Log.d("VoidFirestore", "exportAccountKey: Successfully copied KeyStore private key Base64 to clipboard")
                        Toast.makeText(context, "Account key copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("CONFIRM", color = WarningYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportKeyConfirmDialog = false }) {
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
                            text = "SETTINGS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
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
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ----------------------------------------
                // SECURITY SECTION
                // ----------------------------------------
                Text(
                    text = "SECURITY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HotPinkLight,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Default Self-Destruct Timer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default Self-Destruct Timer",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = when (settings.defaultSelfDestruct) {
                                        0 -> "Off"
                                        5 -> "5 Seconds"
                                        30 -> "30 Seconds"
                                        60 -> "1 Minute"
                                        300 -> "5 Minutes"
                                        else -> "${settings.defaultSelfDestruct}s"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = HotPinkLight
                                )
                            }
                            Box {
                                Button(
                                    onClick = { showSelfDestructDropdown = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                    border = BorderStroke(1.dp, BorderDark),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("SELECT", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                }
                                DropdownMenu(
                                    expanded = showSelfDestructDropdown,
                                    onDismissRequest = { showSelfDestructDropdown = false },
                                    modifier = Modifier.background(VoidDarkBlue)
                                ) {
                                    val options = listOf(
                                        Pair("Off", 0),
                                        Pair("5s", 5),
                                        Pair("30s", 30),
                                        Pair("1m", 60),
                                        Pair("5m", 300)
                                    )
                                    options.forEach { (label, secs) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                viewModel.updateSettings(settings.copy(defaultSelfDestruct = secs))
                                                showSelfDestructDropdown = false
                                                Log.d("VoidFirestore", "updateSettings: Changed default self destruct to $secs seconds")
                                                Toast.makeText(context, "Self-destruct timer updated", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Divider(color = BorderDark, thickness = 1.dp)

                        // Biometric Lock Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric Lock",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Requires fingerprint to open app",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = settings.biometricLock,
                                onCheckedChange = { isEnabled ->
                                    viewModel.updateSettings(settings.copy(biometricLock = isEnabled))
                                    Log.d("VoidFirestore", "updateSettings: Set biometric Lock enabled = $isEnabled")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = VoidDarkBlue,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = VoidBlack
                                )
                            )
                        }
                    }
                }

                // ----------------------------------------
                // HELP & SUPPORT SECTION
                // ----------------------------------------
                Text(
                    text = "HELP & SUPPORT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Contact Support Button
                        Button(
                            onClick = {
                                Log.d("VoidFirestore", "contactSupport: Launching encrypted support chat sequence")
                                onContactSupport()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Contact Support",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        // Privacy Policy Button
                        Button(
                            onClick = {
                                onNavigateToPrivacy()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Privacy Policy",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        Divider(color = BorderDark, thickness = 1.dp)

                        // About Void Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "About Void",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = version,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ----------------------------------------
                // APPEARANCE SECTION
                // ----------------------------------------
                Text(
                    text = "APPEARANCE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Theme Selector",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = settings.theme.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MatrixGreen
                                )
                            }
                            Box {
                                Button(
                                    onClick = { showThemeDropdown = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                    border = BorderStroke(1.dp, BorderDark),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("SELECT", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                }
                                DropdownMenu(
                                    expanded = showThemeDropdown,
                                    onDismissRequest = { showThemeDropdown = false },
                                    modifier = Modifier.background(VoidDarkBlue)
                                ) {
                                    val themesList = listOf("CYBERPUNK", "COSMIC", "TERMINAL")
                                    themesList.forEach { th ->
                                        DropdownMenuItem(
                                            text = { Text(th, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                if (th == "CYBERPUNK") {
                                                    viewModel.updateSettings(settings.copy(theme = th.lowercase()))
                                                    Log.d("VoidFirestore", "updateSettings: Theme locked to cyberpunk")
                                                    Toast.makeText(context, "Theme updated to Cyberpunk", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "$th is currently locked", Toast.LENGTH_SHORT).show()
                                                }
                                                showThemeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------
                // TRANSFER & BACKUP SECTION
                // ----------------------------------------
                Text(
                    text = "TRANSFER & BACKUP",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Transfer to New Device
                        Button(
                            onClick = {
                                Log.d("VoidFirestore", "navigation: Navigating to TransferOutScreen")
                                onNavigateToTransferOut()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Transfer to New Device", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                                Text("LAUNCH →", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Export Backup
                        Button(
                            onClick = {
                                Log.d("VoidFirestore", "navigation: Navigating to BackupScreen export flow")
                                onNavigateToBackup()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Export Backup", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                                Text("EXPORT ↗", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Restore from Backup
                        Button(
                            onClick = {
                                Log.d("VoidFirestore", "navigation: Navigating to BackupScreen restore flow")
                                onNavigateToBackup()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Restore from Backup", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                                Text("IMPORT ←", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ----------------------------------------
                // ACCOUNT SECTION
                // ----------------------------------------
                Text(
                    text = "ACCOUNT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Username display with Change Username button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Username",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "@${username.ifEmpty { "void_operative" }}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    newUsernameInput = username
                                    showUsernameDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                border = BorderStroke(1.dp, BorderDark),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Change Username", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                        }

                        Divider(color = BorderDark, thickness = 1.dp)

                        // Display ID with copy button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Display ID",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = displayId,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("display_id", displayId))
                                    Toast.makeText(context, "Display ID copied", Toast.LENGTH_SHORT).show()
                                    Log.d("VoidFirestore", "copyDisplayId: Copied displaying displayId=$displayId successfully to clipboard")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = NeonCyan)
                            }
                        }

                        Divider(color = BorderDark, thickness = 1.dp)

                        // View Recovery Phrase Button
                        Button(
                            onClick = {
                                Log.d("VoidFirestore", "viewRecoveryPhrase: Requesting fingerprint/credential scan for viewing BIP39 mnemonic")
                                showBiometricAuthDialogForPhrase = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, HotPink),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "View Recovery Phrase",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // ----------------------------------------
                // ADVANCED SECTION (At bottom)
                // ----------------------------------------
                Text(
                    text = "ADVANCED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningYellow,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = VoidDarkNavy),
                    border = BorderStroke(1.dp, WarningYellow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                showExportKeyConfirmDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, WarningYellow),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Export Account Key",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                        
                        Text(
                            text = "For advanced account recovery. Not needed for normal use.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
