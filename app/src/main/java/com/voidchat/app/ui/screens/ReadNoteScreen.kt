package com.voidchat.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
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
import com.voidchat.app.viewmodel.NoteUiState
import com.voidchat.app.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadNoteScreen(
    shareCode: String,
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupChat: (groupId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rawInputCode by remember { mutableStateOf(shareCode) }
    var inputPassword by remember { mutableStateOf("") }
    var showPasswordRequiredPrompt by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
        }
    }

    // Automatically check if note needs password or trigger decrypt on fetch
    LaunchedEffect(state) {
        if (state is NoteUiState.Error && (state as NoteUiState.Error).message.contains("protected with a cognitive password")) {
            showPasswordRequiredPrompt = true
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
                            text = "DECRYPT NOTE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (state is NoteUiState.Read) {
                    val body = (state as NoteUiState.Read).content
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "🔓 DECRYPTED CONTENT",
                        color = MatrixGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.5.dp, MatrixGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 320.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = body,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    val inviteUrl = remember(body) {
                        val regex = "void://group/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+".toRegex()
                        regex.find(body)?.value
                    }

                    inviteUrl?.let { url ->
                        var isJoining by remember { mutableStateOf(false) }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isJoining = true
                                viewModel.joinGroupFromNote(url) { realGroupId ->
                                    isJoining = false
                                    if (realGroupId != null) {
                                        Toast.makeText(context, "Handshake verified! Joined group channel.", Toast.LENGTH_SHORT).show()
                                        onNavigateToGroupChat(realGroupId)
                                    } else {
                                        Toast.makeText(context, "Handshake rejection or banned from sector.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isJoining,
                            colors = ButtonDefaults.buttonColors(containerColor = HotPinkLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isJoining) "RESOLVING ROUTING SEGMENT..." else "CONNECT TO GROUP CHANNEL",
                                fontFamily = FontFamily.Monospace,
                                color = VoidBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, ErrorRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Security alert",
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "⚠️ This note was deleted immediately after decryption. Closing this view will sever your only copy.",
                                color = ErrorRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Decrypted Note", body)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Note content copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "COPY CONTENT",
                            fontFamily = FontFamily.Monospace,
                            color = VoidBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.resetState()
                            showPasswordRequiredPrompt = false
                            inputPassword = ""
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CLOSE",
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }

                } else if (state is NoteUiState.Destroyed) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Destroyed",
                        tint = ErrorRed,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "[ NOTE ENVELOPE SEVERED ]",
                        color = ErrorRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This note has expired, peaked user view threshold limits, or was manually zeroed-out by client request. No trace records remain in the Cloud or database caches.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            viewModel.resetState()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BACK", fontFamily = FontFamily.Monospace, color = TextPrimary)
                    }

                } else {
                    Text(
                        text = "Enter your decrypted share code transmission package key. Upon successful client-side parsing and handshake confirmation, the note payload displays securely once.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = rawInputCode,
                        onValueChange = { rawInputCode = it },
                        placeholder = { Text("Enter decryption code", fontFamily = FontFamily.Monospace, color = TextMuted) },
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

                    if (showPasswordRequiredPrompt) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VoidDarkNavy, RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, WarningYellow), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = WarningYellow, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "This note is password protected. Enter password below to proceed.",
                                color = WarningYellow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = inputPassword,
                            onValueChange = { inputPassword = it },
                            placeholder = { Text("Enter password", fontFamily = FontFamily.Monospace, color = TextMuted) },
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningYellow,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (state is NoteUiState.Reading) {
                        CircularProgressIndicator(color = WarningYellow, modifier = Modifier.padding(16.dp))
                    } else {
                        Button(
                            onClick = {
                                val password = if (showPasswordRequiredPrompt && inputPassword.isNotEmpty()) inputPassword else null
                                viewModel.readNote(rawInputCode, password)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "DECRYPT NOTE",
                                fontFamily = FontFamily.Monospace,
                                color = VoidBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (state is NoteUiState.Error && !showPasswordRequiredPrompt) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (state as NoteUiState.Error).message,
                            color = ErrorRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
