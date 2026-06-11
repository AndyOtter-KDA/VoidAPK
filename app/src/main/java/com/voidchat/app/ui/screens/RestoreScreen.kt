package com.voidchat.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.ui.theme.*
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.LocalIdentity
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.remote.FirestoreManager
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToTransferIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var recoveryCodeInput by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var filePasswordInput by remember { mutableStateOf("") }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    // Launcher for selecting safe .void backup files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.path?.substringAfterLast('/') ?: "backup_file.void"
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                fileBytes = inputStream?.use { it.readBytes() }
                Toast.makeText(context, "Selected file: $selectedFileName Checkpoint successful.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load file: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            text = "RESTORE YOUR IDENTITY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack),
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("restore_back_button")
                        ) {
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
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "VOID KEY RESTORATION PROTOCOLS",
                    color = HotPinkLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Decrypt and synchronize your cryptographic identity layers from a historic device or a secured backup package.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // OPTION 1: Scan QR Code
                Card(
                     modifier = Modifier.fillMaxWidth(),
                     colors = CardDefaults.cardColors(containerColor = VoidDarkBlue),
                     border = BorderStroke(1.dp, BorderDark),
                     shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "SCAN QR CODE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan the ephemeral QR identity transmission directly from your previous phone's active screen.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToTransferIn,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("scan_qr_restore_button")
                        ) {
                            Text(
                                text = "SCAN QR",
                                color = VoidBlack,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // OPTION 2: Paste Recovery Code
                Card(
                     modifier = Modifier.fillMaxWidth(),
                     colors = CardDefaults.cardColors(containerColor = VoidDarkBlue),
                     border = BorderStroke(1.dp, BorderDark),
                     shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "PASTE RECOVERY CODE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Paste the raw recovery code block you saved during previous sessions.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = recoveryCodeInput,
                            onValueChange = { recoveryCodeInput = it },
                            placeholder = { Text("Paste your recovery code here", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedPlaceholderColor = TextMuted,
                                unfocusedPlaceholderColor = TextMuted
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (recoveryCodeInput.trim().isEmpty()) {
                                    Toast.makeText(context, "Please paste a recovery code first.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isRestoring = true
                                scope.launch {
                                    try {
                                        val res = IdentityManager.restoreFromRecoveryCode(recoveryCodeInput.trim())
                                        res.fold(
                                            onSuccess = { displayId ->
                                                // Success: create LocalIdentity in Room DB
                                                val db = AppDatabase.getDatabase(context)
                                                val prefs = PreferencesManager(context)
                                                val username = prefs.username ?: "recovered_node"
                                                val pubKeyBase64 = IdentityManager.getPublicKeyBase64() ?: ""

                                                val localIdentity = LocalIdentity(
                                                    id = UUID.randomUUID().toString(),
                                                    keyPairAlias = "void_identity",
                                                    publicKeyBase64 = pubKeyBase64,
                                                    displayId = displayId,
                                                    username = username,
                                                    recoveryPhraseHash = UUID.randomUUID().toString().hashCode().toString(),
                                                    createdAt = System.currentTimeMillis(),
                                                    deviceName = android.os.Build.MODEL
                                                )
                                                db.identityDao().insertIdentity(localIdentity)
                                                prefs.username = username

                                                Toast.makeText(context, "Handshake successful! Welcome back $username.", Toast.LENGTH_LONG).show()
                                                onNavigateToHome()
                                            },
                                            onFailure = { err ->
                                                val errMsg = err.localizedMessage ?: err.message ?: "Invalid recovery code. Check the code and try again."
                                                Toast.makeText(context, "Error: $errMsg", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Restoration failure: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isRestoring = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("paste_code_restore_button")
                        ) {
                            Text(
                                text = "RESTORE",
                                color = VoidBlack,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // OPTION 3: Restore from File
                Card(
                     modifier = Modifier.fillMaxWidth(),
                     colors = CardDefaults.cardColors(containerColor = VoidDarkBlue),
                     border = BorderStroke(1.dp, BorderDark),
                     shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "RESTORE FROM FILE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Restore from an encrypted .void identity backup file downloaded or stored on this system.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Button to launch system file picker
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedFileUri == null) "CHOOSE FILE" else "FILE: $selectedFileName",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedFileUri != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = filePasswordInput,
                                onValueChange = { filePasswordInput = it },
                                label = { Text("BACKUP PASSWORD", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val bytes = fileBytes
                                    if (bytes == null) {
                                        Toast.makeText(context, "No file bytes loaded.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (filePasswordInput.trim().isEmpty()) {
                                        Toast.makeText(context, "Backup password is required.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isRestoring = true
                                    scope.launch {
                                        try {
                                            val res = IdentityManager.importIdentityFromFile(bytes, filePasswordInput)
                                            res.fold(
                                                onSuccess = { displayId ->
                                                    // Success: retrieve recovered username
                                                    val db = AppDatabase.getDatabase(context)
                                                    val prefs = PreferencesManager(context)
                                                    val username = prefs.username ?: "recovered_node"
                                                    val pubKeyBase64 = IdentityManager.getPublicKeyBase64() ?: ""

                                                    val localIdentity = LocalIdentity(
                                                        id = UUID.randomUUID().toString(),
                                                        keyPairAlias = "void_identity",
                                                        publicKeyBase64 = pubKeyBase64,
                                                        displayId = displayId,
                                                        username = username,
                                                        recoveryPhraseHash = UUID.randomUUID().toString().hashCode().toString(),
                                                        createdAt = System.currentTimeMillis(),
                                                        deviceName = android.os.Build.MODEL
                                                    )
                                                    db.identityDao().insertIdentity(localIdentity)
                                                    prefs.username = username

                                                    Toast.makeText(context, "Identity imported successfully. Welcome $username.", Toast.LENGTH_LONG).show()
                                                    onNavigateToHome()
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, "Wrong password or invalid backup file", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Wrong password or invalid backup file", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isRestoring = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("file_restore_execute_button")
                            ) {
                                if (isRestoring) {
                                    CircularProgressIndicator(color = VoidBlack, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(
                                        text = "RESTORE",
                                        color = VoidBlack,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
