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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import com.voidchat.app.data.models.GroupMessage
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
import com.voidchat.app.viewmodel.GroupChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    viewModel: GroupChatViewModel,
    myDisplayId: String,
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (groupId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val currentMembers by viewModel.groupMembers.collectAsState()

    val isOwner = groupInfo?.createdBy == myDisplayId
    val isUserAdmin = isOwner || currentMembers.any { it.displayId == myDisplayId && it.role == "ADMIN" }

    var textInput by remember { mutableStateOf("") }
    var selectedTimerSeconds by remember { mutableStateOf(0) }
    var expandedDropdown by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) {
        viewModel.loadMessages(groupId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToGroupInfo(groupId) }
                        ) {
                            Text(
                                text = groupInfo?.name ?: "MULTI-NODE SYSTEM CHANNEL",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "NODES ACTIVE: ${groupInfo?.members?.split(",")?.size ?: 1}",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (!groupInfo?.description.isNullOrEmpty()) {
                                Text(
                                    text = "DESC: ${groupInfo?.description}",
                                    color = NeonCyan,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val linkId = viewModel.createInviteLink(0L, 100)
                                        val code = "void://group/$groupId/$linkId"
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Void Group invite", code)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied secure E2E join-link to clipboard", Toast.LENGTH_SHORT).show()
                                    } catch(e: Exception) {
                                        Toast.makeText(context, "Link creation error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Invite link", tint = NeonCyan)
                        }
                        IconButton(onClick = { onNavigateToGroupInfo(groupId) }) {
                            Icon(Icons.Default.Info, contentDescription = "Group Info", tint = TextPrimary)
                        }
                    }
                )
                if (!groupInfo?.pinnedMessageText.isNullOrEmpty()) {
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📌 GROUP ANNOUNCEMENT (PINNED)",
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = groupInfo?.pinnedMessageText ?: "",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            // Button to unpin if user is admin/owner
                            if (isUserAdmin) {
                                IconButton(onClick = { viewModel.unpinMessage() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Unpin", tint = TextMuted)
                                }
                            }
                        }
                    }
                }
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    val reversedList = messages.asReversed()
                    items(reversedList) { msg ->
                        GroupMessageBubble(
                            msg = msg,
                            myDisplayId = myDisplayId,
                            isOwner = isOwner,
                            isAdmin = isUserAdmin,
                            onPin = { decryptedText -> viewModel.pinMessage(msg.messageId, decryptedText) },
                            onDelete = { viewModel.deleteGroupMessage(msg.messageId) }
                        )
                    }
                }

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
                                        Pair("5m", 300)
                                    )
                                    choices.forEach { (lbl, dur) ->
                                        DropdownMenuItem(
                                            text = { Text(lbl, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                selectedTimerSeconds = dur
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("SEND MULTI-PAYLOAD...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (textInput.trim().isNotEmpty()) {
                                        viewModel.sendMessage(textInput, selectedTimerSeconds)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(NeonCyan, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = VoidBlack
                                )
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
fun GroupMessageBubble(
    msg: GroupMessage,
    myDisplayId: String,
    isOwner: Boolean,
    isAdmin: Boolean,
    onPin: (String) -> Unit,
    onDelete: () -> Unit
) {
    val isMine = msg.senderId == myDisplayId
    var showMenu by remember { mutableStateOf(false) }

    val decryptedText = remember(msg.messageId, msg.encryptedPayload, msg.iv) {
        try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val keyBytes = md.digest(msg.groupId.toByteArray(Charsets.UTF_8))
            val aesKey = com.voidchat.app.crypto.CryptoManager.secretKeyFromBytes(keyBytes)
            val result = com.voidchat.app.crypto.CryptoManager.decrypt(msg.encryptedPayload, msg.iv, aesKey)
            result.getOrDefault("[Unable to decrypt]")
        } catch (e: Exception) {
            "[Decryption Error]"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isMine) VoidBlack else VoidDarkBlue,
                border = BorderStroke(1.dp, if (isMine) NeonCyan else BorderDark),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { showMenu = true },
                        onLongClick = { showMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isMine) {
                        Text(
                            text = "NODE-${msg.senderId.take(4).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HotPinkLight,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = decryptedText,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SEQ ${msg.keyGeneration} // UTC",
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(VoidDarkBlue)
            ) {
                if (isAdmin) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📌 PIN AS ANNOUNCEMENT", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onPin(decryptedText)
                        }
                    )
                }
                if (isMine || isAdmin) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = HotPink, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🗑️ DELETE MESSAGE", color = HotPink, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
