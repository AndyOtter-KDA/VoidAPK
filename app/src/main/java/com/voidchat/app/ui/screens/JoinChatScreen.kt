package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.data.remote.FirestoreManager
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinChatScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (chatId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayIdInput by remember { mutableStateOf("") }
    var isStarting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "NEW CHAT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = TextPrimary,
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ESTABLISH cryptographic TUNNEL",
                    color = HotPinkLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Input a destination display identifier code to establish a direct connection path. The path is fully end-to-end encrypted under ECDH handshake.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = displayIdInput,
                        onValueChange = { displayIdInput = it.uppercase().trim() },
                        label = { Text("ENTER DISPLAY ID", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val clipboardText = clipboardManager.getText()?.text
                            if (!clipboardText.isNullOrEmpty()) {
                                displayIdInput = clipboardText.uppercase().trim()
                                Toast.makeText(context, "ID pasted from clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VoidDarkBlue),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(
                            text = "PASTE",
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val input = displayIdInput.trim()
                        
                        // Validate format: 19 characters with hyphens
                        val idPattern = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
                        if (!idPattern.matches(input)) {
                            Toast.makeText(context, "Format error: Account ID must be exactly in XXXX-XXXX-XXXX-XXXX format.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val myDisplayId = com.voidchat.app.crypto.IdentityManager.getDisplayId() ?: ""
                        if (input == myDisplayId) {
                            Toast.makeText(context, "Tunnel loopback forbidden. You cannot start a chat with yourself.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        isStarting = true
                        scope.launch {
                            try {
                                val chatId = if (myDisplayId < input) "${myDisplayId}_${input}" else "${input}_${myDisplayId}"
                                val existingChat = FirestoreManager.getChat(chatId)
                                
                                if (existingChat != null) {
                                    // Chat already exists, navigate to it directly
                                    isStarting = false
                                    onNavigateToChat(chatId)
                                } else {
                                    // Chat not exists, create new one
                                    viewModel.startNewChat(input) { pathChatId ->
                                        isStarting = false
                                        onNavigateToChat(pathChatId)
                                    }
                                }
                            } catch (e: Exception) {
                                isStarting = false
                                Toast.makeText(context, "Connection failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = displayIdInput.isNotEmpty() && !isStarting,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isStarting) "ESTABLISHING..." else "START CHAT",
                        fontFamily = FontFamily.Monospace,
                        color = VoidBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (isStarting) {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
        }
    }
}
