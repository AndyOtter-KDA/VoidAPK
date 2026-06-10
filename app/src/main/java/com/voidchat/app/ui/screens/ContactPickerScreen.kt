package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.Contact
import com.voidchat.app.ui.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)

    var contactsList by remember { mutableStateOf<List<Contact>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        db.contactDao().getAllContacts()
            .catch { error ->
                Toast.makeText(context, "Error loading identities: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            .collect { list ->
                contactsList = list
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SELECT PEER NODES",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedIds.size} NODES TARGETED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
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
        },
        floatingActionButton = {
            if (selectedIds.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val idsString = selectedIds.joinToString(",")
                        onNavigateToCreateGroup(idsString)
                    },
                    containerColor = NeonCyan,
                    contentColor = VoidBlack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "START SESSION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = "Proceed")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScanlineOverlay()

            if (contactsList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "[ NO REGISTRY ENTRIES DETECTED ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Register or communicate with terminal peer nodes directly to store their handshake certificates in your persistent memory database before group routing.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(contactsList) { contact ->
                        val isSelected = selectedIds.contains(contact.displayId)
                        Surface(
                            color = if (isSelected) VoidDarkNavy else VoidDarkBlue,
                            border = BorderStroke(1.dp, if (isSelected) NeonCyan else BorderDark),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        selectedIds.remove(contact.displayId)
                                    } else {
                                        selectedIds.add(contact.displayId)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (isSelected) NeonCyan.copy(alpha = 0.1f) else VoidDarkNavy,
                                            shape = RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Contact Icon",
                                        tint = if (isSelected) NeonCyan else TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.nickname,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "NODE: ${contact.displayId.take(16)}...",
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) {
                                            selectedIds.remove(contact.displayId)
                                        } else {
                                            selectedIds.add(contact.displayId)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonCyan,
                                        uncheckedColor = BorderDark,
                                        checkmarkColor = VoidBlack
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
