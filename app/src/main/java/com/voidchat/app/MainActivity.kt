package com.voidchat.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.ui.navigation.NavGraph
import com.voidchat.app.ui.navigation.Routes
import com.voidchat.app.ui.theme.VoidTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            VoidTheme {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }
                val prefs = remember { com.voidchat.app.data.local.PreferencesManager(applicationContext) }
                var isAppLocked by remember { mutableStateOf(prefs.biometricLock && !prefs.pinCode.isNullOrEmpty()) }

                LaunchedEffect(Unit) {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val identity = db.identityDao().getIdentity()
                        
                        // Parse incoming deep links if configured
                        val action: String? = intent?.action
                        val dataUri = intent?.data
                        
                        if (dataUri != null && action == Intent.ACTION_VIEW) {
                            val host = dataUri.host
                            val path = dataUri.path
                            if (host == "chat") {
                                val targetId = path?.removePrefix("/") ?: ""
                                startDestination = "${Routes.CHAT}/$targetId"
                            } else if (host == "group") {
                                val cleanPath = path?.removePrefix("/") ?: ""
                                startDestination = "${Routes.GROUP_CHAT}/$cleanPath"
                            } else if (host == "note") {
                                val noteCode = path?.removePrefix("/") ?: ""
                                startDestination = "${Routes.READ_NOTE}/$noteCode"
                            }
                        }

                        if (startDestination == null) {
                            startDestination = if (identity != null) {
                                Routes.HOME
                            } else {
                                Routes.ONBOARDING
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VoidMainActivity", "Startup error: ${e.message}", e)
                        if (startDestination == null) {
                            startDestination = Routes.ONBOARDING
                        }
                    }
                }

                if (isAppLocked) {
                    com.voidchat.app.ui.screens.LockScreen(
                        correctPinCode = prefs.pinCode ?: "",
                        onUnlocked = { isAppLocked = false }
                    )
                } else {
                    val currentStartDest = startDestination
                    if (currentStartDest == null) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .androidx.compose.foundation.background(com.voidchat.app.ui.theme.VoidBlack),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            com.voidchat.app.ui.theme.ScanlineOverlay()
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                            ) {
                                androidx.compose.material3.Text(
                                    text = "VOID SECURE COGNITIVE SHIELD",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = com.voidchat.app.ui.theme.NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = com.voidchat.app.ui.theme.HotPink,
                                    strokeWidth = 2.dp
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                                androidx.compose.material3.Text(
                                    text = "DECRYPTING SYSTEM KEYSTORE SEGMENTS...",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = com.voidchat.app.ui.theme.TextMuted,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    } else {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            NavGraph(
                                navController = navController,
                                startDestination = currentStartDest,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
