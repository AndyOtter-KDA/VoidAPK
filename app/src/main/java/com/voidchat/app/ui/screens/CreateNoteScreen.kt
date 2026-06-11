package com.voidchat.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.NoteUiState
import com.voidchat.app.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteScreen(
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentInput by remember { mutableStateOf("") }
    
    // 0 = First read, 300 = 5 minutes, 3600 = 1 hour, 86400 = 24 hours
    var selectedLifetimeOption by remember { mutableStateOf(0) }
    var expandedDropdown by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "NEW NOTE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
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
                if (state is NoteUiState.Created) {
                    val createdState = state as NoteUiState.Created
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Success header components (green checkmark or lock)
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Created",
                        tint = MatrixGreen,
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "NOTE CREATED",
                        color = MatrixGreen,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Give this code to the recipient.\nThey can open it on our website.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Large, bold, monospace card displaying the code
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(2.dp, NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TRANSMISSION CODE",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = createdState.code,
                                color = NeonCyan,
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Void Share Code", createdState.code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "COPY CODE",
                            fontFamily = FontFamily.Monospace,
                            color = VoidBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }



                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.resetState()
                            contentInput = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CREATE ANOTHER", fontFamily = FontFamily.Monospace, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = VoidDarkNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DONE", fontFamily = FontFamily.Monospace, color = NeonCyan)
                    }

                } else {
                    // Content writing area
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        placeholder = { Text("Write your note...", fontFamily = FontFamily.Monospace, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )

                    // Word and character count at the bottom right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${contentInput.length} chars",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }



                    // Self-destruct options drop / radio buttons
                    Surface(
                        color = VoidDarkNavy,
                        border = BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "SELF-DESTRUCT TIMELINE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Clean Select option from dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { expandedDropdown = true },
                                    border = BorderStroke(1.dp, BorderDark),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when(selectedLifetimeOption) {
                                                0 -> "After first read"
                                                300 -> "After 5 minutes"
                                                3600 -> "After 1 hour"
                                                86400 -> "After 24 hours"
                                                else -> "After first read"
                                            },
                                            color = NeonCyan,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        )
                                        Text(text = "▼", color = NeonCyan, fontSize = 10.sp)
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .background(VoidDarkBlue)
                                ) {
                                    val dropdownOptions = listOf(
                                        Pair("After first read", 0),
                                        Pair("After 5 minutes", 300),
                                        Pair("After 1 hour", 3600),
                                        Pair("After 24 hours", 86400)
                                    )
                                    dropdownOptions.forEach { (label, duration) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    color = TextPrimary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {
                                                selectedLifetimeOption = duration
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    if (state is NoteUiState.Creating) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.padding(16.dp))
                    } else {
                        Button(
                            onClick = {
                                viewModel.createNote(contentInput, selectedLifetimeOption)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "CREATE NOTE",
                                fontFamily = FontFamily.Monospace,
                                color = VoidBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (state is NoteUiState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (state as NoteUiState.Error).message,
                            color = ErrorRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
