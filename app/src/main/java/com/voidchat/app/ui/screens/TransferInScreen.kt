package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.crypto.TransferManager
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.Contact
import com.voidchat.app.data.models.LocalIdentity
import com.voidchat.app.data.local.PreferencesManager
import com.voidchat.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TransferInScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val codeDigits = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    var isRestoring by remember { mutableStateOf(false) }
    var restorationError by remember { mutableStateOf<String?>(null) }

    val isCodeComplete = codeDigits.all { it.isNotEmpty() }
    val enteredCode = codeDigits.joinToString("")

    fun handleCodeRestoration() {
        if (!isCodeComplete || isRestoring) return
        isRestoring = true
        restorationError = null
        keyboardController?.hide()

        android.util.Log.d("VoidTransfer", "Initiating 6-digit identity restoration for code: $enteredCode")

        TransferManager.fetchTransfer(enteredCode) { transferData ->
            if (transferData == null) {
                scope.launch {
                    isRestoring = false
                    restorationError = "Invalid code or code expired. Try again."
                }
            } else {
                val decryptRes = TransferManager.decryptIdentityFromTransfer(
                    enteredCode,
                    transferData.encryptedData,
                    transferData.iv
                )

                decryptRes.fold(
                    onSuccess = { restoreData ->
                        scope.launch {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val prefs = PreferencesManager(context)

                                // 1. Reconstruct and import RSA/EC key store values
                                val privateKeyBytes = android.util.Base64.decode(restoreData.privateKey, android.util.Base64.NO_WRAP)
                                val publicKeyBytes = android.util.Base64.decode(restoreData.publicKey, android.util.Base64.NO_WRAP)

                                val keyFactory = java.security.KeyFactory.getInstance("EC")
                                val pubKeySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
                                val publicKey = keyFactory.generatePublic(pubKeySpec)

                                val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
                                val privateKey = keyFactory.generatePrivate(privKeySpec)

                                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                                val cert = com.voidchat.app.crypto.MockX509Certificate(publicKey)
                                val entry = java.security.KeyStore.PrivateKeyEntry(privateKey, arrayOf(cert))
                                keyStore.setEntry(
                                    "void_identity",
                                    entry,
                                    android.security.keystore.KeyProtection.Builder(
                                        android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
                                    ).setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256).build()
                                )

                                val securePrefs = context.getSharedPreferences("voidchat_secure_preferences", android.content.Context.MODE_PRIVATE)
                                securePrefs.edit()
                                    .putString("identity_priv_b64", restoreData.privateKey)
                                    .putString("identity_pub_b64", restoreData.publicKey)
                                    .apply()

                                // 2. Write to Room Database local identity and contacts list
                                val localIdentity = LocalIdentity(
                                    id = UUID.randomUUID().toString(),
                                    keyPairAlias = "void_identity",
                                    publicKeyBase64 = restoreData.publicKey,
                                    displayId = restoreData.displayId,
                                    username = restoreData.username,
                                    recoveryPhraseHash = UUID.randomUUID().toString().hashCode().toString(),
                                    createdAt = System.currentTimeMillis(),
                                    deviceName = android.os.Build.MODEL
                                )
                                db.identityDao().insertIdentity(localIdentity)
                                prefs.username = restoreData.username

                                // Restore contacts display IDs
                                restoreData.contacts.forEach { contactId ->
                                    val parts = contactId.split("-")
                                    val name = if (parts.isNotEmpty()) "Terminal Node ${parts.last()}" else "Terminal Channel"
                                    db.contactDao().insertContact(
                                        Contact(contactId, name, "", System.currentTimeMillis(), false)
                                    )
                                }

                                // 3. Delete transfer document from Firestore to complete claim
                                try {
                                    TransferManager.deleteTransfer(enteredCode)
                                } catch (ex: Exception) {
                                    android.util.Log.e("VoidTransfer", "Could not delete transfer from repository: ${ex.message}")
                                }

                                Toast.makeText(context, "Identity restored. Welcome back, ${restoreData.username}.", Toast.LENGTH_LONG).show()
                                onNavigateToHome()
                            } catch (e: Exception) {
                                android.util.Log.e("VoidTransfer", "Keystore import failure", e)
                                restorationError = "Security hardware module import failed: ${e.localizedMessage}"
                                isRestoring = false
                            }
                        }
                    },
                    onFailure = { err ->
                        scope.launch {
                            // Increment failed attempts for this document
                            TransferManager.incrementFailedAttempts(enteredCode)
                            restorationError = "Decryption failed: Verification error. Incorrect transfer payload decryption key."
                            isRestoring = false
                        }
                    }
                )
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
                            text = "IMPORT SYSTEM NODE TERMINAL",
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
                            modifier = Modifier.testTag("transfer_in_back_button")
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
                    text = "ENTER TRANSFER CHANNEL CODE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HotPinkLight,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Go to Settings → Transfer to New Device on your old terminal and enter the active 6-digit session code below.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // SMS Verification block boxes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    for (i in 0 until 6) {
                        OutlinedTextField(
                            value = codeDigits[i],
                            onValueChange = { value ->
                                if (value.isNotEmpty()) {
                                    val lastChar = value.last().toString()
                                    if (lastChar.first().isDigit()) {
                                        codeDigits[i] = lastChar
                                        if (i < 5) {
                                            focusRequesters[i + 1].requestFocus()
                                        }
                                    }
                                } else {
                                    codeDigits[i] = ""
                                    if (i > 0) {
                                        focusRequesters[i - 1].requestFocus()
                                    }
                                }
                            },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = VoidDarkBlue,
                                unfocusedContainerColor = VoidDarkBlue,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderDark,
                                disabledBorderColor = BorderDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.85f)
                                .focusRequester(focusRequesters[i])
                                .testTag("transfer_code_digit_$i")
                        )
                    }
                }

                // Initial request focus
                LaunchedEffect(Unit) {
                    focusRequesters[0].requestFocus()
                }

                if (isRestoring) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                        Text(
                            text = "FETCHING SECURE IDENTITY PARCEL...",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Button(
                        onClick = { handleCodeRestoration() },
                        enabled = isCodeComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            disabledContainerColor = BorderDark
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("restore_identity_button")
                    ) {
                        Text(
                            text = "RESTORE SECURE IDENTITY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isCodeComplete) VoidBlack else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                restorationError?.let { err ->
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, ErrorRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = "X ERROR: $err",
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(14.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
