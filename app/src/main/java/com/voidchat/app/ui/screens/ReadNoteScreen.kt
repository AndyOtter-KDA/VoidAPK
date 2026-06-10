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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.NoteUiState
import com.voidchat.app.viewmodel.NoteViewModel

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
    var revealed by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
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
                            text = "DECRYPT COURIER NOTE",
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
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (state is NoteUiState.Read) {
                    val body = (state as NoteUiState.Read).content
                    Text(
                        text = "DECRYPTED SYSTEM SEED CONTENT:",
                        color = MatrixGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, MatrixGreen),
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
                        val context = androidx.compose.ui.platform.LocalContext.current

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

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "RESOLVE MANUAL HANDSHAKE INVITE LINK",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "If you have received an out-of-band group segment handshake link (e.g., void://group/xyz/abc), paste it below to securely connect.",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            var manualInviteInput by remember { mutableStateOf("") }
                            var isManualJoining by remember { mutableStateOf(false) }
                            val context = androidx.compose.ui.platform.LocalContext.current
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = manualInviteInput,
                                    onValueChange = { manualInviteInput = it },
                                    placeholder = { Text("Paste invite link...", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val code = manualInviteInput.trim()
                                        if (code.isNotEmpty()) {
                                            isManualJoining = true
                                            viewModel.joinGroupFromNote(code) { realGroupId ->
                                                isManualJoining = false
                                                if (realGroupId != null) {
                                                    Toast.makeText(context, "Handshake verified! Joined group channel.", Toast.LENGTH_SHORT).show()
                                                    onNavigateToGroupChat(realGroupId)
                                                } else {
                                                    Toast.makeText(context, "Handshake rejection or banned from sector.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isManualJoining,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isManualJoining) "JOINING" else "JOIN",
                                        color = VoidBlack,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "🔥 NOTE PURGE MANDATED 🔥\nDecryption keys removed. Backing out of this view severs any remaining local trace of this note.",
                        color = ErrorRed,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.resetState()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SEVER & DESTRUCTION PROTOCOL", fontFamily = FontFamily.Monospace, color = TextPrimary)
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This note has expired, peaked user view threshold limits, or was manually zeroed-out by client request. No trace records remain in the Cloud or database caches.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                } else {
                    // Ask for share URI/code to fetch
                    Text(
                        text = "WARNING: Note decryption is active. Once decrypted and displayed, other cached references are destroyed to ensure confidentiality.",
                        color = WarningYellow,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = rawInputCode,
                        onValueChange = { rawInputCode = it },
                        label = { Text("PASTE DECRYPTION SHARE URI", fontFamily = FontFamily.Monospace) },
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

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            viewModel.readNote(rawInputCode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("EXECUTE DECRYPTON PROTOCOL", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
