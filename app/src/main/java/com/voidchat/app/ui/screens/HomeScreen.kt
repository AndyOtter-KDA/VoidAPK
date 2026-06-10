package com.voidchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.data.models.Chat
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    myDisplayId: String,
    onNavigateToChat: (chatId: String) -> Unit,
    onNavigateToJoinChat: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToCreateNote: () -> Unit,
    onNavigateToReadNote: (shareCode: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.chats.collectAsState()
    val announcement by viewModel.announcement.collectAsState()
    var showFabMenu by remember { mutableStateOf(false) }
    var showOpenNoteDialog by remember { mutableStateOf(false) }
    var openNoteCodeInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "VOID // CHANNELS",
                                color = NeonCyan,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ADDR: $myDisplayId",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack),
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                        }
                    }
                )
                Divider(color = BorderDark, thickness = 1.dp)
            }
        },
        bottomBar = {
            Surface(
                color = VoidBlack,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { /* Already on Chats */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MailOutline,
                                contentDescription = "Chats",
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "CHATS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onNavigateToCreateNote() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Write Note",
                                tint = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "WRITE NOTE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { showOpenNoteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Open Note",
                                tint = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "OPEN NOTE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showFabMenu) {
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            DropdownMenuItem(
                                text = { Text("NEW PRIVATE HANDSHAKE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan) },
                                onClick = {
                                    showFabMenu = false
                                    onNavigateToJoinChat()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("NEW GROUP CHANNEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HotPinkLight) },
                                onClick = {
                                    showFabMenu = false
                                    onNavigateToCreateGroup()
                                }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = NeonCyan,
                    contentColor = VoidBlack,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add options")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScanlineOverlay()

            if (showOpenNoteDialog) {
                AlertDialog(
                    onDismissRequest = { showOpenNoteDialog = false },
                    title = {
                        Text(
                            text = "DECRYPT SECURE NOTE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Enter the 6-character note verification index handle or key URI to sync and decrypt the remote payload.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = openNoteCodeInput,
                                onValueChange = { openNoteCodeInput = it },
                                placeholder = { Text("NOTE CODE / HANDLE", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val clean = openNoteCodeInput.trim()
                                if (clean.isNotEmpty()) {
                                    showOpenNoteDialog = false
                                    onNavigateToReadNote(clean)
                                    openNoteCodeInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("DECRYPT", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOpenNoteDialog = false }) {
                            Text("ABORT", fontFamily = FontFamily.Monospace, color = HotPink)
                        }
                    },
                    containerColor = VoidDarkNavy,
                    textContentColor = TextPrimary,
                    titleContentColor = NeonCyan,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Announcement banner block
                announcement?.let { ann ->
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, HotPink.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ann.title,
                                    color = HotPinkLight,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ann.body,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            IconButton(onClick = { viewModel.dismissAnnouncement() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }
                    }
                }

                if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ NO ACTIVE TRANSMISSIONS ]\nInitialize a E2E terminal link to communicate.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        items(chats) { chat ->
                            val otherParty = if (chat.participantA == myDisplayId) chat.participantB else chat.participantA
                            ChatRowItem(
                                chat = chat,
                                otherParty = otherParty,
                                onClick = { onNavigateToChat(chat.chatId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatRowItem(
    chat: Chat,
    otherParty: String,
    onClick: () -> Unit
) {
    Surface(
        color = VoidBlack,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BorderDark,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        val isSupport = otherParty == "VOID-SUPP-CHAT-LINE" || otherParty.contains("VOID-SUPP") || otherParty.contains("support") || otherParty.contains("SUPP")
                        Text(
                            text = if (isSupport) "Void Support" else "NODE-${otherParty.replace("-", "").take(6).uppercase()}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ADDR ID: $otherParty",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (chat.keyExchangeComplete) "E2E OK" else "SECURING CHAT...",
                        color = if (chat.keyExchangeComplete) MatrixGreen else WarningYellow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            Divider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)
        }
    }
}
