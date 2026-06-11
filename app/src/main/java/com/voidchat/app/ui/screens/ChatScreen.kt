package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.components.MessageBubble
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.ChatViewModel
import com.voidchat.app.viewmodel.ChatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: ChatViewModel,
    myDisplayId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val state by viewModel.chatState.collectAsState()
    val decryptedMessages by viewModel.decryptedMessages.collectAsState()
    val context = LocalContext.current

    var textInput by remember { mutableStateOf("") }
    var selectedTimerSeconds by remember { mutableStateOf(0) } // 0 = Off
    var expandedDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var showChatInfoDialog by remember { mutableStateOf(false) }
    var chatInfoUsername by remember { mutableStateOf("") }
    var chatInfoDisplayId by remember { mutableStateOf("") }
    var chatInfoKeyStatus by remember { mutableStateOf("") }
    var chatInfoCreatedAt by remember { mutableStateOf(0L) }

    var showMessageSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.initChat(chatId, "")
        selectedTimerSeconds = viewModel.getChatSelfDestructDefault()
    }

    // Modern Pulsing logic for WAITING_FOR_KEY_EXCHANGE
    val infiniteTransition = rememberInfiniteTransition(label = "Securing pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            val isSupport = chatId == "VOID-SUPP-CHAT-LINE" || chatId.contains("VOID-SUPP") || chatId.contains("support") || chatId.contains("SUPP")
                            Text(
                                text = if (isSupport) "Void Support Operator" else "SECURE NODE-${chatId.replace("_", "").take(6).uppercase()}",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ROUTING_CHANNEL: $chatId",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    },
                    actions = {
                        var showActionMenu by remember { mutableStateOf(false) }

                        if (state is ChatState.ENCRYPTED) {
                            Surface(
                                color = VoidDarkBlue,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MatrixGreen),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = "🔐 ENCRYPTED",
                                    color = MatrixGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else if (state is ChatState.WAITING_FOR_KEY_EXCHANGE) {
                            Surface(
                                color = VoidDarkBlue,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, WarningYellow),
                                modifier = Modifier.padding(end = 4.dp).alpha(pulseAlpha)
                            ) {
                                Text(
                                    text = "⏳ SECURING...",
                                    color = WarningYellow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(onClick = { showActionMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More Actions", tint = NeonCyan)
                        }

                         DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false },
                            modifier = Modifier.background(VoidDarkNavy)
                        ) {
                            DropdownMenuItem(
                                text = { Text("CHAT INFO", fontFamily = FontFamily.Monospace, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showActionMenu = false
                                    viewModel.getChatInfo { username, displayId, keyStatus, createdAt ->
                                        chatInfoUsername = username
                                        chatInfoDisplayId = displayId
                                        chatInfoKeyStatus = keyStatus
                                        chatInfoCreatedAt = createdAt
                                        showChatInfoDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("MESSAGE SETTINGS", fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    showActionMenu = false
                                    selectedTimerSeconds = viewModel.getChatSelfDestructDefault()
                                    showMessageSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("SAVE TO CONTACTS", fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    showActionMenu = false
                                    viewModel.saveToContacts(chatId) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("CLEAR MESSAGES", fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    showActionMenu = false
                                    viewModel.clearMessages(chatId) {
                                        Toast.makeText(context, "All messages cleared locally", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("DELETE CHAT LOCALLY", fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    showActionMenu = false
                                    viewModel.deleteChatLocally(chatId) {
                                        Toast.makeText(context, "Chat deleted from this device", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("DELETE FOR EVERYONE", fontFamily = FontFamily.Monospace, color = HotPink, fontSize = 11.sp) },
                                onClick = {
                                    showActionMenu = false
                                    showDeleteConfirmation = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("BLOCK USER", fontFamily = FontFamily.Monospace, color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showActionMenu = false
                                    viewModel.blockUser {
                                        Toast.makeText(context, "User blocked. Chat hidden.", Toast.LENGTH_LONG).show()
                                        onNavigateBack()
                                    }
                                }
                            )
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

            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text("TERMINATE SECURE CHANNEL", fontFamily = FontFamily.Monospace, color = HotPink, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    text = {
                        Text(
                            "This will delete the chat and all messages for both users. This cannot be undone.",
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirmation = false
                                viewModel.deleteChatForEveryone {
                                    Toast.makeText(context, "Chat deleted for everyone", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HotPink),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("TERMINATE", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text("ABORT", fontFamily = FontFamily.Monospace, color = NeonCyan)
                        }
                    },
                    containerColor = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            if (showChatInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showChatInfoDialog = false },
                    title = {
                        Text(
                            "SECURE TELEMETRY SHEET",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column {
                                Text("PEER ADDRESS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                                Text(chatInfoUsername.ifEmpty { "Resolving Node..." }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                            }
                            Column {
                                Text("DEVICE ID HASH", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                                Text(chatInfoDisplayId, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                            }
                            Column {
                                Text("ECDH HANDSHAKE STATE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                                Text(chatInfoKeyStatus, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixGreen)
                            }
                            Column {
                                Text("ESTABLISHED TIME", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                                val dateStr = remember(chatInfoCreatedAt) {
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(chatInfoCreatedAt))
                                }
                                Text(dateStr, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showChatInfoDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("CLOSE", fontFamily = FontFamily.Monospace, color = VoidBlack, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            if (showMessageSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showMessageSettingsDialog = false },
                    title = {
                        Text(
                            "DEFAULT SELF-DESTRUCT TIMER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    },
                    text = {
                        Column {
                            Text(
                                "Set the default auto-purge timer for all outgoing transmission payloads inside this channel.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            val choices = listOf(
                                Pair("OFF (Default)", 0),
                                Pair("5 Seconds", 5),
                                Pair("30 Seconds", 30),
                                Pair("1 Minute", 60),
                                Pair("5 Minutes", 300)
                            )
                            choices.forEach { (label, secs) ->
                                Button(
                                    onClick = {
                                        viewModel.setChatSelfDestructDefault(secs)
                                        selectedTimerSeconds = secs
                                        showMessageSettingsDialog = false
                                        Toast.makeText(context, "Default self-destruct configured", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedTimerSeconds == secs) HotPink else VoidDarkBlue
                                    ),
                                    border = BorderStroke(1.dp, if (selectedTimerSeconds == secs) HotPink else BorderDark),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (selectedTimerSeconds == secs) VoidBlack else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showMessageSettingsDialog = false }) {
                            Text("BACK", fontFamily = FontFamily.Monospace, color = TextMuted)
                        }
                    },
                    containerColor = VoidDarkNavy,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. WAITING_FOR_KEY_EXCHANGE UI rendering
                if (state is ChatState.WAITING_FOR_KEY_EXCHANGE) {
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, WarningYellow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .alpha(pulseAlpha),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = WarningYellow,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "⏳ Securing chat... Exchanging security public keys with other terminal node.",
                                color = WarningYellow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. ERROR UI representation with interactive retry button
                if (state is ChatState.ERROR) {
                    val errMsg = (state as ChatState.ERROR).reason
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = ErrorRed)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Connection error: $errMsg",
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.performKeyExchange() },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RETRY CONNECTION", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Messages list area
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    val reversedList = messages.asReversed()
                    items(reversedList) { msg ->
                        MessageBubble(
                            message = msg,
                            myDisplayId = myDisplayId,
                            decryptedText = decryptedMessages[msg.messageId]
                        )
                    }
                }

                Divider(color = BorderDark, thickness = 1.dp)

                // Controls & message input board
                Surface(
                    color = VoidDarkNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            
                            // Ephemeral self-destruct menu selector
                            Box {
                                TextButton(onClick = { expandedDropdown = true }) {
                                    Text(
                                        text = if (selectedTimerSeconds == 0) "🔥 TIMER: OFF" else "🔥 TIMER: ${selectedTimerSeconds}s",
                                        color = if (selectedTimerSeconds == 0) TextMuted else HotPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false },
                                    modifier = Modifier.background(VoidDarkBlue)
                                ) {
                                    val choices = listOf(
                                        Pair("Off", 0),
                                        Pair("5s", 5),
                                        Pair("30s", 30),
                                        Pair("1m", 60),
                                        Pair("5m", 300)
                                    )
                                    choices.forEach { (label, secs) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                selectedTimerSeconds = secs
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            val isSecure = state is ChatState.ENCRYPTED
                            Text(
                                text = if (isSecure) "🔐 SECURED" else "⏳ TUNNEL OFFLINE",
                                color = if (isSecure) MatrixGreen else WarningYellow,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isInputDisabled = state != ChatState.ENCRYPTED

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = if (isInputDisabled) {
                                    Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            Toast.makeText(context, "Waiting for secure connection...", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    Modifier.weight(1f)
                                }
                            ) {
                                OutlinedTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    enabled = !isInputDisabled,
                                    placeholder = {
                                        Text(
                                            text = if (isInputDisabled) "TUNNEL OFFLINE..." else "SEND SECURED PAYLOAD...",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        disabledBorderColor = BorderDark,
                                        disabledTextColor = TextMuted
                                    ),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    maxLines = 4
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (isInputDisabled) {
                                        Toast.makeText(context, "Waiting for secure connection...", Toast.LENGTH_SHORT).show()
                                    } else if (textInput.trim().isNotEmpty()) {
                                        viewModel.sendMessage(textInput, selectedTimerSeconds)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isInputDisabled) BorderDark else NeonCyan,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (isInputDisabled) "Waiting for secure connection..." else "Send",
                                    tint = if (isInputDisabled) TextMuted else VoidBlack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
