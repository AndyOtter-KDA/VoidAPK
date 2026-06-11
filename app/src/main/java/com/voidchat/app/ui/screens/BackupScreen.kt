package com.voidchat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransferOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var filePassword by remember { mutableStateOf("") }
    var showPasswordProgress by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "BACKUP YOUR IDENTITY",
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
                            modifier = Modifier.testTag("backup_back_button")
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
                // Intro text
                Text(
                    text = "VOID SECURE IDENTITY BACKUP SYSTEMS",
                    color = HotPinkLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your cryptographic identity is stored locally on this terminal node. Backup your private key layers to avoid complete exclusion from the secure chat matrix.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // OPTION 1: Move to New Device
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
                            Text("📱➡️📱", fontSize = 24.sp)
                            Text(
                                text = "MOVE TO NEW DEVICE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transfer your identity to a new phone using a secure, ephemeral QR code matrix scan.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToTransferOut,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("transfer_out_button")
                        ) {
                            Text(
                                text = "TRANSFER IDENTITY",
                                color = VoidBlack,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // OPTION 2: Copy Recovery Code
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
                                text = "COPY RECOVERY CODE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Copy a short secure code you can paste anywhere. Save it in a password manager or offline notes app.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Copy Warning Text
                        Surface(
                            color = VoidDarkNavy,
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Anyone with this code can access your account. Store it securely.",
                                color = ErrorRed,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 14.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val code = IdentityManager.generateRecoveryCode()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Void Identity Recovery Code", code)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Recovery code copied. Save it somewhere safe.", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("copy_recovery_code_button")
                        ) {
                            Text(
                                text = "COPY CODE",
                                color = VoidBlack,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // OPTION 3: Save to Device
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
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "SAVE TO DEVICE",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Save an encrypted, hardware-isolated backup file to your local phone storage drive.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = filePassword,
                            onValueChange = { filePassword = it },
                            label = { Text("ENCRYPTION PASSWORD (REQUIRED)", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
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
                                if (filePassword.trim().isEmpty()) {
                                    Toast.makeText(context, "Password is required to secure the backup file.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                showPasswordProgress = true
                                try {
                                    val bytes = IdentityManager.exportIdentityToFile(filePassword)
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                    if (!downloadsDir.exists()) {
                                        downloadsDir.mkdirs()
                                    }
                                    val file = File(downloadsDir, "VoidBackup_2026_06_11.void")
                                    file.writeBytes(bytes)
                                    Toast.makeText(context, "Backup saved to Downloads folder", Toast.LENGTH_LONG).show()
                                    Toast.makeText(context, "Remember your password. You'll need it to restore.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    try {
                                        val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                        val file = File(fallbackDir, "VoidBackup_2026_06_11.void")
                                        val bytes = IdentityManager.exportIdentityToFile(filePassword)
                                        file.writeBytes(bytes)
                                        Toast.makeText(context, "Backup saved to App Downloads (External Storage Restricted)", Toast.LENGTH_LONG).show()
                                        Toast.makeText(context, "Remember your password. You'll need it to restore.", Toast.LENGTH_LONG).show()
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Backup write exception: ${ex.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    showPasswordProgress = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_identity_file_button")
                        ) {
                            Text(
                                text = "SAVE FILE",
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
