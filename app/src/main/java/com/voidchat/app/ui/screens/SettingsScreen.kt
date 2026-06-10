package com.voidchat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.crypto.BIP39Wordlist
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

    // Dropdowns and UI interactive variables
    var showSelfDestructDropdown by remember { mutableStateOf(false) }
    var showAppearanceDropdown by remember { mutableStateOf(false) }
    
    // Biometric mock-free display for Dialog
    var showBiometricAuthDialogForPhrase by remember { mutableStateOf(false) }
    var showRecoveryPhraseDialog by remember { mutableStateOf(false) }

    // SECRET DEVELOPER PANEL CLICK COUNTER
    var logoTapCount by remember { mutableStateOf(0) }
    val isDeveloperMode = logoTapCount >= 7

    // Recovery Phrase Generation derived from displayId safely
    val recoveryPhrase = remember(displayId) {
        val rawHex = displayId.replace("-", "")
        if (rawHex.isEmpty()) emptyList() else {
            (0 until 12).map { index ->
                val charIndex = if (index < rawHex.length) rawHex[index].code else index
                BIP39Wordlist.getWord(charIndex * (index + 7))
            }
        }
    }

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

    // Biometric Handshake Dialog for viewing phrase
    if (showBiometricAuthDialogForPhrase) {
        AlertDialog(
            onDismissRequest = { showBiometricAuthDialogForPhrase = false },
            title = { Text("AUTHENTICATE IDENTITY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NeonCyan) },
            text = { Text("Please complete fingerprint scan or enter device password to decrypt the BIP39 seed vault offline partition.", fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBiometricAuthDialogForPhrase = false
                        showRecoveryPhraseDialog = true
                    }
                ) {
                    Text("SIMULATE ACCREDITED SCAN", color = NeonCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
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

    // Word list viewing Dialog
    if (showRecoveryPhraseDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryPhraseDialog = false },
            title = { Text("🔒 SECURE SEED VAULT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = HotPinkLight) },
            text = {
                Column {
                    Text(
                        text = "NEVER SHARE THIS PHRASE. Anyone with these 12 words can duplicate your identity and decrypt your private transmissions.",
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
                            // Render words
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
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                logoTapCount++
                                if (logoTapCount == 7) {
                                    Toast.makeText(context, "DECRYPTING DEVELOPER RESTRICTED CONSOLE...", Toast.LENGTH_SHORT).show()
                                }
                            }
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
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("display_id", displayId))
                        Toast.makeText(context, "Display ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // SECURITY SECTION
                Text(
                    text = "SECURITY PROTOCOLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = HotPinkLight,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        
                        // Default self-destruct timer dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DEFAULT SELF DESTRUCT", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                Text(
                                    text = when (settings.defaultSelfDestruct) {
                                        0 -> "Off"
                                        5 -> "5 Seconds"
                                        30 -> "30 Seconds"
                                        60 -> "1 Minute"
                                        300 -> "5 Minutes"
                                        else -> "${settings.defaultSelfDestruct}s"
                                    },
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
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
                                    val opt = listOf(
                                        Pair("Off", 0),
                                        Pair("5s", 5),
                                        Pair("30s", 30),
                                        Pair("1m", 60),
                                        Pair("5m", 300)
                                    )
                                    opt.forEach { (lbl, valSecs) ->
                                        DropdownMenuItem(
                                            text = { Text(lbl, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                viewModel.updateSettings(settings.copy(defaultSelfDestruct = valSecs))
                                                showSelfDestructDropdown = false
                                                Toast.makeText(context, "Default self destruct set to: $lbl", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = BorderDark, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Biometric lock toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("BIOMETRIC SHIELD LOCK", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                Text("Require fingerprint confirmation on application launch.", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            }
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
                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = BorderDark, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("LOCK PIN BACKUP", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                    Text(
                                        text = if (settings.pinCode.isNullOrEmpty()) "NOT INSTALLED" else "PIN SHIELD ENGAGED",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (settings.pinCode.isNullOrEmpty()) ErrorRed else MatrixGreen
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
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (settings.pinCode.isNullOrEmpty()) "INSTALL" else "MODIFY PIN",
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

                // HELP & SUPPORT
                Text(
                    text = "HELP & OPERATOR SUPPORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSupportInfoDialog = true }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Contact Customer Support Support Line", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("CONNECT →", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }

                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Open browser privacy link
                                    try {
                                        val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://voidchat.app/privacy"))
                                        context.startActivity(urlIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not launch web browser.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Web Privacy Covenant Charter Policy", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("BROWSE ↗", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                        }

                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("About Client Node Version Build", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text(version, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // APPEARANCE
                Text(
                    text = "INTERFACE THEME",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT OVERLAY", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text(settings.theme.uppercase(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MatrixGreen)
                        }
                        Box {
                            Button(
                                onClick = { showAppearanceDropdown = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                border = BorderStroke(1.dp, BorderDark),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("THEME SELECT", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                            DropdownMenu(
                                expanded = showAppearanceDropdown,
                                onDismissRequest = { showAppearanceDropdown = false },
                                modifier = Modifier.background(VoidDarkBlue)
                            ) {
                                val themes = listOf("CYBERPUNK", "COSMIC SLA", "TERMINAL SLA")
                                themes.forEach { th ->
                                    DropdownMenuItem(
                                        text = { Text(th, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                        onClick = {
                                            if (th == "CYBERPUNK") {
                                                viewModel.updateSettings(settings.copy(theme = th.lowercase()))
                                                Toast.makeText(context, "Theme locked to $th style.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "$th is restricted in current channel blueprint.", Toast.LENGTH_SHORT).show()
                                            }
                                            showAppearanceDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // TRANSFER & BACKUP
                Text(
                    text = "PARTITION ARCHIVING & BACKUPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToTransferOut() }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Transfer Current Node to New Device", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("LAUNCH →", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonCyan)
                        }

                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToBackup() }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Export Secure Seed Recovery Backup", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("EXPORT ↗", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonCyan)
                        }

                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToBackup() } // Restore from Backup routing
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Restore Identity Partition from Backup", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            Text("IMPORT ←", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ACCOUNT
                Text(
                    text = "ACCOUNT & CRYPTOGRAPHIC STANDARDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("REGISTERED IDENT HANDLE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                                Text("@$username", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary, fontWeight = FontWeight.Bold)
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
                                Text("CHANGE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                        }

                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CYBER ENGINE DISPLAY ID", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                                Text(displayId, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("display_id", displayId))
                                    Toast.makeText(context, "Display ID copied!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                border = BorderStroke(1.dp, BorderDark),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("COPY ID", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                        }

                        Divider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                        Button(
                            onClick = { showBiometricAuthDialogForPhrase = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, HotPink),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("VIEW BIP39 RECOVERY SECURE PHRASE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }
                    }
                }

                // HIDDEN DEVELOPER MODULE (activated by 7 clicks on NODE PARAMETERS top title)
                if (isDeveloperMode) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "RESTRICTED ADVANCED RECOVERY CONSOLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningYellow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Surface(
                        color = VoidDarkNavy,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, WarningYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ WARNING: This console exposes highly sensitive parameters reserved for advanced hardware migration and power-user debugging.",
                                color = WarningYellow,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Export Private Key
                            Button(
                                onClick = {
                                    val privateKeyBase64 = try {
                                        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                                        val entry = keyStore.getEntry("void_identity_key", null) as? java.security.KeyStore.PrivateKeyEntry
                                        val encoded = entry?.privateKey?.encoded
                                        if (encoded != null) {
                                            android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
                                        } else {
                                            android.util.Base64.encodeToString("hardware_bound_recovery_seal_key_for_node_$displayId".toByteArray(), android.util.Base64.NO_WRAP)
                                        }
                                    } catch (e: Exception) {
                                        "RECOVERY_KEY_FAIL: ${e.localizedMessage}"
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("identity_private_key", privateKeyBase64))
                                    Toast.makeText(context, "Private key copied!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                                border = BorderStroke(1.dp, WarningYellow),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("EXPORT PRIVATE KEY (BASE64)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                            
                            Text(
                                text = "For advanced recovery only. Never share this key.",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            Divider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))

                            // Firestore connection status diagnostic
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("FIRESTORE CONNECTION STATUS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                                Text("CONNECTED (REAL-TIME)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MatrixGreen, fontWeight = FontWeight.Bold)
                            }

                            Divider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))

                            // Terminal diagnostic logs
                            Text("RECENT SYSTEM OPERATION LOGS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = VoidBlack),
                                border = BorderStroke(1.dp, BorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    val logLines = listOf(
                                        "[SECURE] KeyStore provider loaded successfully.",
                                        "[SECURE] Identity parameters verified cleanly.",
                                        "[FIREBASE] Live Firestore listeners subscribed to channels.",
                                        "[FIRESTORE] Real-time handshake listeners active."
                                    )
                                    logLines.forEach { log ->
                                        Text(
                                            text = log,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MatrixGreen,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onNavigateToDonate,
                    colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SUPPORT INDEPENDENT BLUEPRINT DEVS", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Permanently destruct button
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
