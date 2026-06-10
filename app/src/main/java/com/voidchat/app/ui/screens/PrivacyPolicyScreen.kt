package com.voidchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SECURE PROTOCOLS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoidBlack
                )
            )
        },
        containerColor = VoidBlack,
        modifier = modifier
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(VoidBlack)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "HOW VOID PROTECTS YOUR PRIVACY",
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HotPink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Section 1: "No Accounts, No Tracking"
            PrivacySection(
                title = "1. NO ACCOUNTS, NO TRACKING",
                content = "• Void has no signup. No email. No phone number. No cloud accounts.\n" +
                          "• Your identity is a cryptographic key pair created on your device.\n" +
                          "• We never see your name, location, or any personal information.\n" +
                          "• There are no ads. No analytics. No third-party trackers."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: "End-to-End Encryption"
            PrivacySection(
                title = "2. END-TO-END ENCRYPTION",
                content = "• Every message is encrypted on your device before it leaves.\n" +
                          "• The encryption key is generated through a secure handshake between you and the recipient.\n" +
                          "• Void's servers only store encrypted data that looks like random characters.\n" +
                          "• Nobody — not even Void's developer — can read your messages without your private key."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: "Self-Destructing Messages"
            PrivacySection(
                title = "3. SELF-DESTRUCTING MESSAGES",
                content = "• Messages can be set to self-destruct after being read.\n" +
                          "• Once the timer expires, the message is deleted from both devices and from Void's servers.\n" +
                          "• Self-destructing notes are one-time only. After reading, they're gone forever."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: "Your Identity, Your Control"
            PrivacySection(
                title = "4. YOUR IDENTITY, YOUR CONTROL",
                content = "• Your identity lives entirely on your device.\n" +
                          "• Transfer it to a new phone via encrypted QR code or backup file.\n" +
                          "• Lose your device AND your recovery phrase? Your identity is gone forever. We cannot recover it. That's by design.\n" +
                          "• You can delete your identity at any time from Settings."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 5: "What Void Stores"
            PrivacySection(
                title = "5. WHAT VOID STORES",
                content = "• Encrypted message payloads (unreadable without your key).\n" +
                          "• Your chosen username and public key (needed for others to find and verify you).\n" +
                          "• That's it. No message history is readable by us. No metadata beyond what's technically required to route messages."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 6: "Questions?"
            PrivacySection(
                title = "6. QUESTIONS?",
                content = "• If you have questions about your privacy, open a support chat from Settings.\n" +
                          "• We're happy to explain how anything works."
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Surface(
                color = VoidDarkBlue,
                border = BorderStroke(1.dp, MatrixGreen),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Void was built on the belief that private communication is a right, not a privilege.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MatrixGreen,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun PrivacySection(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VoidDarkNavy,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, BorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = content,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
