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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.ui.theme.*
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.LocalIdentity
import com.voidchat.app.data.local.PreferencesManager
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
    var fileContentStr by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    // Launcher for selecting TXT or VOID backup files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = uri.path?.substringAfterLast('/') ?: "backup_file"
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val rawStr = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                fileContentStr = rawStr.trim()
                Toast.makeText(context, "Loaded file successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeRestore(code: String) {
        if (isRestoring) return
        isRestoring = true
        scope.launch {
            try {
                // Call IdentityManager.restoreFromRecoveryCode
                val res = IdentityManager.restoreFromRecoveryCode(code)
                res.fold(
                    onSuccess = { restoreResult ->
                        // Log.d("RestoreScreen", "Restore result: Y")
                        android.util.Log.d("RestoreScreen", "Restore result: success")

                        val displayId = restoreResult.displayId
                        val username = restoreResult.username

                        // Write to database so system is completely registered
                        val db = AppDatabase.getDatabase(context)
                        val prefs = PreferencesManager(context)
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

                        Toast.makeText(context, "Identity restored. Welcome back, $username.", Toast.LENGTH_LONG).show()
                        onNavigateToHome()
                    },
                    onFailure = { err ->
                        android.util.Log.d("RestoreScreen", "Restore result: fail")
                        val errMsg = err.localizedMessage ?: err.message ?: "Invalid recovery code"
                        Toast.makeText(context, "Error: $errMsg", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                android.util.Log.d("RestoreScreen", "Restore result: exception")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isRestoring = false
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

                // OPTION 1: Enter Transfer Code
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
                                text = "OPTION 1: ENTER TRANSFER CODE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "On your old device, go to Settings → Transfer to New Device to get a 6-digit active session code.",
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
                                .testTag("enter_transfer_code_button")
                        ) {
                            Text(
                                text = "ENTER TRANSFER CODE",
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
                                text = "OPTION 2: PASTE RECOVERY CODE",
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
                                val cleanInput = recoveryCodeInput.trim()
                                // Log.d("RestoreScreen", "Pasted code length: X")
                                android.util.Log.d("RestoreScreen", "Pasted code length: ${cleanInput.length}")
                                
                                if (cleanInput.isEmpty()) {
                                    Toast.makeText(context, "Please paste a recovery code first.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                executeRestore(cleanInput)
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
                                text = "OPTION 3: RESTORE FROM FILE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Restore from a plain text export file (.void or .txt) downloaded or stored on this system.",
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
                                text = if (selectedFileName.isEmpty()) "CHOOSE FILE" else "FILE: $selectedFileName",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (fileContentStr.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    executeRestore(fileContentStr)
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
                                        text = "RESTORE FROM LOADED FILE",
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
