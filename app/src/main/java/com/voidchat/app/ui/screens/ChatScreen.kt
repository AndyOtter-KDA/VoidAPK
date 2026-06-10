package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
    }

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
                                text = if (isSupport) "Void Support" else "NODE-${chatId.replace("-", "").take(6).uppercase()}",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ADDR: $chatId",
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
                        IconButton(onClick = { viewModel.performKeyExchange() }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Active Exchange Tunnel Status",
                                tint = if (state is ChatState.Encrypted) MatrixGreen else WarningYellow
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

            Column(modifier = Modifier.fillMaxSize()) {
                // Warning panel if not completed Crypt state
                if (state is ChatState.KeyExchange || state is ChatState.Loading) {
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, WarningYellow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = WarningYellow,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "⏳ Securing chat...",
                                color = WarningYellow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
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

                // Controls and Input board
                Divider(color = BorderDark, thickness = 1.dp)
                
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
                            // Self-destruct menu selector
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

                            val isSecure = state is ChatState.Encrypted
                            Text(
                                text = if (isSecure) "🔐 Encrypted" else "⏳ Securing chat...",
                                color = if (isSecure) MatrixGreen else WarningYellow,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isKeyExchangeInProgress = state is ChatState.KeyExchange || state is ChatState.Loading

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                enabled = !isKeyExchangeInProgress,
                                placeholder = { Text(
                                    text = if (isKeyExchangeInProgress) "TUNNEL SECURING..." else "SEND SECURED PAYLOAD...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                ) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    disabledBorderColor = BorderDark,
                                    disabledTextColor = TextMuted
                                ),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (isKeyExchangeInProgress) {
                                        Toast.makeText(context, "Waiting for secure connection...", Toast.LENGTH_SHORT).show()
                                    } else if (textInput.trim().isNotEmpty()) {
                                        viewModel.sendMessage(textInput, selectedTimerSeconds)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isKeyExchangeInProgress) BorderDark else NeonCyan,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (isKeyExchangeInProgress) "Waiting for secure connection..." else "Send",
                                    tint = if (isKeyExchangeInProgress) TextMuted else VoidBlack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
