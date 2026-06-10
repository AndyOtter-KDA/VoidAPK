package com.voidchat.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.data.models.SupportTicket
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.SupportTicketViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTicketScreen(
    viewModel: SupportTicketViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tickets by viewModel.tickets.collectAsState()
    val myId by viewModel.myId.collectAsState()
    val myUsername by viewModel.myUsername.collectAsState()

    var subjectInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val deviceInfo = remember {
        "App Version: v0.0.9-TERMINAL\nOS: Android ${Build.VERSION.RELEASE}\nModel: ${Build.MODEL}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SECURE SUPPORT INTERNET CHANNEL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoidDarkNavy
                )
            )
        },
        containerColor = VoidDarkNavy,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "TRANSMIT TICKET SHEETS",
                    color = HotPinkLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If something is malfunctioning across the network nodes or your cryptographic segments, lodge a record below. All details stay local until sent.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            // Ticket Form
            item {
                Surface(
                    color = VoidDarkBlue,
                    border = BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = subjectInput,
                            onValueChange = { subjectInput = it },
                            label = { Text("TICKET SUBJECT", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            placeholder = { Text("Describe visual/network error...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted) },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            label = { Text("MESSAGE CONTENT", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            placeholder = { Text("Include exact steps to reproduce...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            shape = RoundedCornerShape(8.dp),
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Device Info Block (Read-only)
                        Text(
                            text = "METADATA PACKETS (AUTO-ATTACHED)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = VoidDarkNavy,
                            border = BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = deviceInfo,
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (subjectInput.trim().isEmpty() || messageInput.trim().isEmpty()) {
                                    Toast.makeText(context, "Fill in all fields", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmitting = true
                                viewModel.submitTicket(subjectInput, messageInput) { success, ticketId ->
                                    isSubmitting = false
                                    if (success && ticketId != null) {
                                        Toast.makeText(context, "Ticket #$ticketId submitted. We'll reply within 24h.", Toast.LENGTH_LONG).show()
                                        subjectInput = ""
                                        messageInput = ""
                                    } else {
                                        Toast.makeText(context, "Submission failure. Verify server configuration.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isSubmitting) "COMMITTING..." else "SUBMIT TICKET",
                                color = VoidBlack,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Active Tickets Section Header
            item {
                Text(
                    text = "ACTIVE REPORT CHANNELS",
                    color = HotPinkLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            if (tickets.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = "NO HISTORIC TICKET RECORDS EXTRACTED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                items(tickets) { ticket ->
                    TicketItem(ticket = ticket)
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun TicketItem(ticket: SupportTicket) {
    var expanded by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateStr = formatter.format(Date(ticket.createdAt))

    Surface(
        color = VoidDarkBlue,
        border = BorderStroke(1.dp, BorderDark),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (ticket.status == "open") NeonCyan.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = ticket.status.uppercase(),
                                color = if (ticket.status == "open") NeonCyan else ErrorRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "#${ticket.ticketId.uppercase()}",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ticket.subject,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextMuted
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Divider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))
                    Text(
                        text = "TIMESTAMP: $dateStr",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ticket.message,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "SYSTEM NETWORK RESPONSES",
                        color = HotPinkLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (ticket.replies.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No replies yet. Admins typically review transmissions within 24 hours.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    } else {
                        ticket.replies.forEach { reply ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = if (reply.senderId == "admin") MatrixGreen.copy(alpha = 0.08f) else VoidDarkNavy,
                                border = BorderStroke(1.dp, if (reply.senderId == "admin") MatrixGreen.copy(alpha = 0.3f) else BorderDark),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (reply.senderId == "admin") "ADMIN_RESPONSE" else "USER",
                                            color = if (reply.senderId == "admin") MatrixGreen else NeonCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        val replyDate = formatter.format(Date(reply.createdAt))
                                        Text(
                                            text = replyDate,
                                            color = TextMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = reply.message,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
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
