package com.voidchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidchat.app.ui.theme.*
import com.voidchat.app.viewmodel.OnboardingState
import com.voidchat.app.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToRecovery: (displayId: String, List<String>) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(state) {
        if (state is OnboardingState.Created) {
            val s = state as OnboardingState.Created
            onNavigateToRecovery(s.displayId, s.phrase)
        } else if (state is OnboardingState.Restored) {
            onNavigateToHome()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Cyberpunk Logo
                Text(
                    text = "[ VOID ]",
                    color = NeonCyan,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "EPHEMERAL QUANTUM TRANSMISSIONS",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Initialize an independent cryptographic identity kernel. No email, no telephone, no phone book correlation. Only local client E2E tunnels.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = { viewModel.createIdentity() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_identity_button")
                ) {
                    Text(
                        text = "CREATE NEW IDENTITY",
                        color = VoidBlack,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onNavigateToRestore,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("already_have_identity_button")
                ) {
                    Text(
                        text = "I ALREADY HAVE AN IDENTITY",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                // States indicators
                when (state) {
                    is OnboardingState.Creating -> {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(color = NeonCyan)
                    }
                    is OnboardingState.Restoring -> {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(color = HotPink)
                    }
                    is OnboardingState.Error -> {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = (state as OnboardingState.Error).message,
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
