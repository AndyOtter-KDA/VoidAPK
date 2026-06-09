package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val displayId by viewModel.displayId.collectAsState()
    val version = viewModel.getAppVersion()

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("CONFIRM DESTRUCT COMMAND", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = { Text("This will permanently overwrite the local cryptographic keys partition and delete identity files. This action is IRREVERSIBLE. Confirm destruction of node @${viewModel.settings.value.theme}?", fontFamily = FontFamily.Monospace) },
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
                    username = "void_operative",
                    onShareQr = {
                        Toast.makeText(context, "QR code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

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
                            Text("BIOMETRIC ACCESS DEPLOYED", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("LOCAL ALERTS SOUNDS", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Switch(
                                checked = settings.soundEnabled,
                                onCheckedChange = {
                                    viewModel.updateSettings(settings.copy(soundEnabled = it))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = VoidDarkBlue
                                )
                            )
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
