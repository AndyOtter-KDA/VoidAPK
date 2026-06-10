package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatHeaderScreen(
    groupId: String,
    viewModel: GroupChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groupInfo by viewModel.groupInfo.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Owner checks
    val isOwner = groupInfo?.createdBy == viewModel.myDisplayId

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }

    var isEditingDesc by remember { mutableStateOf(false) }
    var editedDesc by remember { mutableStateOf("") }

    var generatedLink by remember { mutableStateOf("") }
    var pinnedText by remember { mutableStateOf("") }

    LaunchedEffect(groupInfo) {
        groupInfo?.let {
            editedName = it.name
            editedDesc = it.description
        }
    }

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
                        Text(
                            text = "SEGMENT ROUTER CONFIG",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Channel Diagnostics Summary Card
                item {
                    Surface(
                        color = VoidDarkBlue,
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Diagnostics Icon",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SECURE PARADIGM DATA",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isEditingName) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonCyan,
                                            unfocusedBorderColor = BorderDark,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        viewModel.renameGroup(editedName) { ok ->
                                            if (ok) {
                                                isEditingName = false
                                                Toast.makeText(context, "Segment label updated.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Label update error", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Text("SAVE", color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = groupInfo?.name ?: "MULTI-NODE SESSION",
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isOwner) {
                                        TextButton(onClick = { isEditingName = true }) {
                                            Text("EDIT NAME", color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isEditingDesc) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = editedDesc,
                                        onValueChange = { editedDesc = it },
                                        placeholder = { Text("Details...", color = TextMuted) },
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
                                    TextButton(onClick = {
                                        viewModel.updateGroupDescription(editedDesc) { ok ->
                                            if (ok) {
                                                isEditingDesc = false
                                                Toast.makeText(context, "Segment manifest updated.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Manifest update error", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Text("SAVE", color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (groupInfo?.description.isNullOrEmpty()) "No operational layout registered here." else "MEMO: ${groupInfo?.description}",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                        lineHeight = 15.sp
                                    )
                                    if (isOwner) {
                                        TextButton(onClick = { isEditingDesc = true }) {
                                            Text("EDIT DESC", color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "CHANNEL SEGMENT ID: $groupId",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Section 2: Secure Invite / Link Generator
                item {
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "SHAREABLE HANDSHAKE LINKS",
                                style = MaterialTheme.typography.labelSmall,
                                color = HotPinkLight,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Deploy persistent invite handshakes to synchronize offline peer node routing.",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (generatedLink.isNotEmpty()) {
                                OutlinedTextField(
                                    value = generatedLink,
                                    onValueChange = {},
                                    readOnly = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        TextButton(onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Handshake URL", generatedLink)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Encrypted invite URL copied safely.", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("COPY", color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }
                                    }
                                )
                            } else {
                                Button(
                                    onClick = {
                                        val code = viewModel.generateInviteLink(groupId)
                                        generatedLink = code
                                        Toast.makeText(context, "Invite link registered.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("REGISTER NEW INVITE HANDSHAKE", color = VoidBlack, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 3: Pin Announcement Broadcast (Admins Only)
                if (isOwner) {
                    item {
                        Surface(
                            color = VoidDarkBlue,
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "SECURE SEGMENT ANNOUNCEMENT (PIN)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HotPink,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = pinnedText,
                                        onValueChange = { pinnedText = it },
                                        placeholder = { Text("Enter alert broadcast text...", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
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
                                            if (pinnedText.trim().isNotEmpty()) {
                                                viewModel.pinMessage("ann_${java.util.UUID.randomUUID().toString().take(6)}", pinnedText.trim())
                                                pinnedText = ""
                                                Toast.makeText(context, "Announcement broadcasted.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("PIN", color = VoidBlack, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!groupInfo?.pinnedMessageText.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = VoidBlack,
                                        border = BorderStroke(1.dp, HotPink),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "ACTIVE ALERT STRIPE:",
                                                    color = HotPinkLight,
                                                    fontSize = 8.sp,
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
                                            IconButton(onClick = { viewModel.unpinMessage() }) {
                                                Icon(Icons.Default.Close, contentDescription = "Unpin", tint = TextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3.5: Add/Inject Peer Node
                item {
                    var inputMbrId by remember { mutableStateOf("") }
                    Surface(
                        color = VoidDarkBlue,
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "ADD NEW NODE BY DISPLAY ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Inject a new static node directly into this multi-party system segment without requiring an invite handshake.",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = inputMbrId,
                                    onValueChange = { inputMbrId = it },
                                    placeholder = { Text("Enter static Display ID...", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
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
                                        val targetId = inputMbrId.trim()
                                        if (targetId.isNotEmpty()) {
                                            viewModel.addMemberByDisplayId(targetId) { success ->
                                                if (success) {
                                                    Toast.makeText(context, "$targetId registered into segment successfully.", Toast.LENGTH_SHORT).show()
                                                    inputMbrId = ""
                                                } else {
                                                    Toast.makeText(context, "Failed to register peer node.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("INJECT", color = VoidBlack, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 4: Members list title
                item {
                    Text(
                        text = "ACTIVE ROUTING NETWORK NODES",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Section 5: Member List with Admin control
                val creator = groupInfo?.createdBy ?: ""
                val membersList = groupInfo?.members?.split(",")?.filter { it.isNotEmpty() } ?: listOf()

                items(membersList) { mbr ->
                    val isSelf = mbr == viewModel.myDisplayId
                    val isMbrCreator = mbr == creator

                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, if (isMbrCreator) NeonCyan.copy(alpha = 0.5f) else BorderDark),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User info",
                                    tint = if (isMbrCreator) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isSelf) "@you (${mbr.take(8)}...)" else mbr,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isMbrCreator) "[ OPERATIONAL CREATOR / ADMIN ]" else "[ SECURED PEER NODE ]",
                                        color = if (isMbrCreator) HotPinkLight else TextMuted,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row {
                                if (isOwner && !isMbrCreator) {
                                    // Kick action
                                    TextButton(
                                        onClick = {
                                            viewModel.kickMember(mbr) { ok ->
                                                if (ok) {
                                                    Toast.makeText(context, "$mbr successfully decoupled.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text("KICK", color = HotPink, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Promote Admin action (NEW!)
                                    TextButton(
                                        onClick = {
                                            viewModel.promoteMemberToAdmin(mbr) { ok ->
                                                if (ok) {
                                                    Toast.makeText(context, "$mbr promoted to ADMIN successfully.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Promotion error.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text("PROMOTE", color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Ban action
                                    TextButton(
                                        onClick = {
                                            viewModel.banMember(mbr) { ok ->
                                                if (ok) {
                                                    Toast.makeText(context, "$mbr blacklisted and purged.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text("BAN", color = ErrorRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        text = if (isSelf) "ACTIVE" else "SYNCED",
                                        color = if (isSelf) NeonCyan else MatrixGreen,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
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
