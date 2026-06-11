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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.crypto.TransferManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.data.remote.FirestoreManager
import com.voidchat.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferOutScreen(
    displayId: String,
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var transferCode by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(true) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var timeLeftSeconds by remember { mutableStateOf(600) } // 10 minutes
    var isClaimed by remember { mutableStateOf(false) }
    var hasCleanedUp by remember { mutableStateOf(false) }

    // Generate and upload loop
    LaunchedEffect(Unit) {
        try {
            val code = TransferManager.generateTransferCode()
            transferCode = code
            val data = TransferManager.encryptIdentityForTransfer(code)
            TransferManager.uploadTransfer(code, data)
            isUploading = false

            // Listen for document to be deleted (claimed)
            val listenerReg = FirestoreManager.listenForTransferClaimed(code) {
                isClaimed = true
            }

            // Expiry countdown
            while (timeLeftSeconds > 0 && !isClaimed) {
                delay(1000L)
                timeLeftSeconds--
            }

            // Once expired or claimed, clean up listener
            listenerReg.remove()

            if (timeLeftSeconds <= 0 && !isClaimed) {
                // Delete expired transfer document
                try {
                    TransferManager.deleteTransfer(code)
                } catch (e: Exception) {
                    android.util.Log.e("VoidTransfer", "Cleanup of expired transfer failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            isUploading = false
            uploadError = e.localizedMessage ?: "Failed to initialize secure transfer pipeline"
            android.util.Log.e("VoidTransfer", "Init failed", e)
        }
    }

    // Wiping mechanism
    fun executeWipe() {
        if (hasCleanedUp) return
        hasCleanedUp = true
        scope.launch {
            try {
                // Delete local databases, prefs, and private keys
                val db = AppDatabase.getDatabase(context)
                val prefs = PreferencesManager(context)

                db.identityDao().deleteIdentity()
                db.messageDao().deleteExpiredMessages()
                
                // Clear any local contacts
                val contacts = db.contactDao().getAllContacts().first()
                contacts.forEach { contact ->
                    db.contactDao().deleteContact(contact.displayId)
                }

                prefs.clearAll()
                IdentityManager.deleteIdentity()

                Toast.makeText(context, "Node terminal context purges complete.", Toast.LENGTH_LONG).show()
                onNavigateToOnboarding()
            } catch (e: Exception) {
                android.util.Log.e("VoidTransfer", "Wipe failed: ${e.message}", e)
                hasCleanedUp = false
                Toast.makeText(context, "Terminal wipe failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Cancel and delete document
    fun cancelTransfer() {
        scope.launch {
            if (transferCode.isNotEmpty()) {
                try {
                    TransferManager.deleteTransfer(transferCode)
                } catch (e: Exception) {
                    android.util.Log.e("VoidTransfer", "Failed to delete transfer on manual cancel: ${e.message}")
                }
            }
            onNavigateBack()
        }
    }

    // Copy to clipboard
    fun copyToClipboard() {
        if (transferCode.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Void Chat Transfer Code", transferCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Transfer code copied.", Toast.LENGTH_SHORT).show()
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
                        IconButton(
                            onClick = { cancelTransfer() },
                            modifier = Modifier.testTag("transfer_out_back_button")
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

            if (isClaimed) {
                // Claimed screen: "Transfer complete. This device will now forget your identity."
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "MIGRATION CLAIM DETECTED",
                                color = NeonCyan,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Your cryptographic identity layers have been securely received and imported by the new node terminal.\n\nTo prevent key duplication and maintain private communication integrity, this host terminal must now wipe its locally persisted memory structures.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { executeWipe() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("wipe_this_device_button")
                    ) {
                        Text(
                            text = "NUCLEAR PURGE & WIPE THIS DEVICE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Standard code generate with countdown timer
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = "MIGRATE TO NEW PEER NODE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = HotPinkLight,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "On your new device, open Void and tap 'I Already Have an Identity' then 'Enter Transfer Code' to reclaim your peer address.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = ErrorRed)
                            Text(
                                text = "Keep this code private. Anyone with this code can access your account.",
                                color = ErrorRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Display code
                    if (isUploading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                            Text(
                                text = "SECURI-CHANNEL NEGOTIATION...",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (uploadError != null) {
                        Text(
                            text = "ERROR: $uploadError",
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Display the 6 digit code in boxes
                        Text(
                            text = "ACTIVE TERMINAL CODE:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            transferCode.forEach { char ->
                                Surface(
                                    color = VoidDarkBlue,
                                    border = BorderStroke(1.dp, NeonCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = char.toString(),
                                            color = TextPrimary,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { copyToClipboard() },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                            border = BorderStroke(1.dp, NeonCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("copy_transfer_code_button")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan)
                                Text(
                                    text = "COPY TRANSFER CODE",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Timer countdown
                        val min = timeLeftSeconds / 60
                        val sec = timeLeftSeconds % 60
                        val timeStr = "%02d:%02d".format(min, sec)

                        Text(
                            text = if (timeLeftSeconds > 0) "Code expires in: $timeStr" else "Code expired. Restart migration.",
                            color = if (timeLeftSeconds > 60) NeonCyan else ErrorRed,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = { cancelTransfer() },
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("cancel_transfer_button")
                    ) {
                        Text(
                            text = "ABORT TRANSFER",
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
