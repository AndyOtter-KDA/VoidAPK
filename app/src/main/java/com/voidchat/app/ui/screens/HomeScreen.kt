package com.voidchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
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
    onNavigateToContactPicker: () -> Unit,
    onNavigateToCreateNote: () -> Unit,
    onNavigateToReadNote: (shareCode: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.chats.collectAsState()
    val announcement by viewModel.announcement.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    var selectedTab by remember { mutableStateOf("chats") }
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
                            .clickable { selectedTab = "chats" },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MailOutline,
                                contentDescription = "Chats",
                                tint = if (selectedTab == "chats") NeonCyan else TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "CHATS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (selectedTab == "chats") NeonCyan else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedTab = "contacts" },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Contacts",
                                tint = if (selectedTab == "contacts") NeonCyan else TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "CONTACTS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (selectedTab == "contacts") NeonCyan else TextMuted,
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
            FloatingActionButton(
                onClick = { onNavigateToJoinChat() },
                containerColor = NeonCyan,
                contentColor = VoidBlack,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Private Chat")
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

                if (selectedTab == "chats") {
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
                            items(chats) { chatUi ->
                                ChatRowItem(
                                    chatUi = chatUi,
                                    onDeleteLocally = { viewModel.deleteChatLocally(chatUi.chat.chatId) },
                                    onClearMessages = { viewModel.clearMessages(chatUi.chat.chatId) },
                                    onClick = { onNavigateToChat(chatUi.chat.chatId) }
                                )
                            }
                        }
                    }
                } else if (selectedTab == "contacts") {
                    if (contacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "[ PEER REGISTRY EMPTY ]\nYour direct contact lists are empty.\nUse direct chat options to register addresses.",
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
                            items(contacts) { contact ->
                                Surface(
                                    color = VoidDarkNavy,
                                    border = BorderStroke(1.dp, BorderDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable {
                                            viewModel.startNewChat(contact.displayId) { chatId ->
                                                onNavigateToChat(chatId)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${contact.nickname}",
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "ADDR ID: ${contact.displayId}",
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteContact(contact.displayId)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Contact",
                                                tint = HotPink
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
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatRowItem(
    chatUi: com.voidchat.app.viewmodel.ChatUiModel,
    onDeleteLocally: () -> Unit,
    onClearMessages: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box {
        Surface(
            color = VoidBlack,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
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
                                color = if (chatUi.keyExchangeComplete) MatrixGreen else WarningYellow,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            val isSupport = chatUi.otherPartyDisplayId == "VOID-SUPP-CHAT-LINE" || chatUi.otherPartyDisplayId.contains("VOID-SUPP") || chatUi.otherPartyDisplayId.contains("support") || chatUi.otherPartyDisplayId.contains("SUPP")
                            val displayName = when {
                                isSupport -> "Void Support"
                                !chatUi.otherPartyUsername.isNullOrEmpty() -> chatUi.otherPartyUsername!!
                                else -> {
                                    val id = chatUi.otherPartyDisplayId
                                    if (id.length >= 8) "${id.take(4)}-${id.takeLast(4)}" else id
                                }
                            }
                            Text(
                                text = displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        // Line 2: last message preview
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = chatUi.lastMessagePreview,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Line 3: Timestamp or self-destruct indicator
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = chatUi.timestampStr,
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (chatUi.keyExchangeComplete) "E2E OK" else "SECURING...",
                            color = if (chatUi.keyExchangeComplete) MatrixGreen else WarningYellow,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
                Divider(color = BorderDark.copy(alpha = 0.5f), thickness = 1.dp)
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(VoidDarkNavy)
        ) {
            DropdownMenuItem(
                text = { Text("DELETE CHAT", fontFamily = FontFamily.Monospace, color = HotPink, fontSize = 12.sp) },
                onClick = {
                    showMenu = false
                    onDeleteLocally()
                    android.widget.Toast.makeText(context, "Chat deleted from this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text("CLEAR MESSAGES", fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 12.sp) },
                onClick = {
                    showMenu = false
                    onClearMessages()
                    android.widget.Toast.makeText(context, "Messages cleared", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// GroupRowItem removed
