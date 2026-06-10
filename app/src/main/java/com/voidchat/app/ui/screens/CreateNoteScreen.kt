package com.voidchat.app.ui.screens

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.NoteUiState
import com.voidchat.app.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteScreen(
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentInput by remember { mutableStateOf("") }
    var passphraseInput by remember { mutableStateOf("") }
    var selectedLifetimeSeconds by remember { mutableStateOf(0) } // 0 = First view
    var maxViews by remember { mutableStateOf(1) }
    var expandedLifetime by remember { mutableStateOf(false) }
    var showGroupDropdown by remember { mutableStateOf(false) }

    val groupChats by viewModel.groupChats.collectAsState()

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
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
                            text = "DEPLOY ONE-TIME SECURE NOTE",
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
                    .padding(24.dp)
            ) {
                if (state is NoteUiState.Created) {
                    val code = (state as NoteUiState.Created).shareCode
                    Text(
                        text = "NOTE CRYPTOGRAPHICALLY DEPLOYED",
                        color = MatrixGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "The payload has been encrypted with a random AES-256 session key and published. Anyone with this URI code can fetch, decrypt and destroy the note payload.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = {},
                        readOnly = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Void share code", code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied Void Share URI", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("COPY SECURE TRANSMISSION CODE", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.resetState()
                            contentInput = ""
                            passphraseInput = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GENERATE ANOTHER NOTE", fontFamily = FontFamily.Monospace, color = TextPrimary)
                    }
                } else {
                    Text(
                        text = "Compose a secure payload. Once viewed the configured amount of times (default: 1), the memory partition is overwritten with physical noise.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                     OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        placeholder = { Text("ENTER SECURE CONTENT...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 240.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box {
                            TextButton(
                                onClick = { showGroupDropdown = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = HotPinkLight)
                            ) {
                                Text(
                                    text = "+ ATTACH GROUP SEGMENT INVITE Link",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            DropdownMenu(
                                expanded = showGroupDropdown,
                                onDismissRequest = { showGroupDropdown = false },
                                modifier = Modifier.background(VoidDarkBlue)
                            ) {
                                if (groupChats.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No Group Channels available", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                        onClick = { showGroupDropdown = false }
                                    )
                                } else {
                                    groupChats.forEach { group ->
                                        DropdownMenuItem(
                                            text = { Text(group.name, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                showGroupDropdown = false
                                                viewModel.createGroupInviteAndAppend(group.groupId) { inviteUrl ->
                                                    val spacing = if (contentInput.isEmpty()) "" else "\n\n"
                                                    contentInput += "${spacing}Group Invite Link: $inviteUrl"
                                                    Toast.makeText(context, "Appended invitation handshake context.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Lifetime settings dropdown
                        Box {
                            Button(
                                onClick = { expandedLifetime = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (selectedLifetimeSeconds == 0) "VIEW LIFETIME: FIRST READ" else "VIEW LIFETIME: ${selectedLifetimeSeconds / 60} min",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                            DropdownMenu(
                                expanded = expandedLifetime,
                                onDismissRequest = { expandedLifetime = false },
                                modifier = Modifier.background(VoidDarkBlue)
                            ) {
                                val lifespans = listOf(
                                    Pair("First read", 0),
                                    Pair("5 min", 300),
                                    Pair("1 hour", 3600)
                                )
                                lifespans.forEach { (lbl, life) ->
                                    DropdownMenuItem(
                                        text = { Text(lbl, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                        onClick = {
                                            selectedLifetimeSeconds = life
                                            expandedLifetime = false
                                        }
                                    )
                                }
                            }
                        }

                        // Max views toggle if first view format
                        if (selectedLifetimeSeconds == 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("VIEWS:", fontFamily = FontFamily.Monospace, color = TextMuted, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { maxViews = if (maxViews == 1) 3 else 1 }) {
                                    Text("$maxViews x", color = HotPink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val expiry = if (selectedLifetimeSeconds == 0) 0L else System.currentTimeMillis() + (selectedLifetimeSeconds * 1000)
                            viewModel.createNote(contentInput, maxViews, expiry)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SEAL NOTE & COMMIT TO REPOSITORY", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
