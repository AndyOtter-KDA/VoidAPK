package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.GroupChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    inviteCode: String,
    viewModel: GroupChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupChat: (groupId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf(inviteCode) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "JOIN SEGMENT VIA DEEP LINK",
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Paste a valid VOID invite transmission link. (e.g., void://group/segment_id/inv_id)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("VOID INVITE CODE URI", fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Surface(
                    color = VoidDarkNavy,
                    border = BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PEER VERIFICATION STATUS:",
                            style = MaterialTheme.typography.labelSmall,
                            color = HotPinkLight
                        )
                        Text(
                            text = "Target Link parsed as valid VOID network intent.",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Button(
                            onClick = {
                                val code = inputCode.trim()
                                if (code.isEmpty()) {
                                    Toast.makeText(context, "Please enter an invite code.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.joinGroup(code) { success ->
                                    if (success) {
                                        // Retrieve groupId from segment
                                        val clean = code.removePrefix("void://group/")
                                        val pid = clean.split("/").firstOrNull() ?: "grp_join"
                                        onNavigateToGroupChat(pid)
                                    } else {
                                        Toast.makeText(context, "Channel handshake timed out or rejected.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("MOUNT SECURE SEGMENT BACKPLANE", fontFamily = FontFamily.Monospace, color = VoidBlack)
                        }
                    }
                }
            }
        }
    }
}
