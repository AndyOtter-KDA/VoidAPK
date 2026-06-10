package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
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

    LaunchedEffect(chatId) {
        viewModel.initChat(chatId, "")
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
                        if (state is ChatState.ENCRYPTED) {
                            Surface(
                                color = VoidDarkBlue,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MatrixGreen),
                                modifier = Modifier.padding(end = 8.dp)
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
                                modifier = Modifier.padding(end = 8.dp).alpha(pulseAlpha)
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                maxLines = 4
                            )

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
