package com.voidchat.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(com.voidchat.app.ui.theme.VoidBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            com.voidchat.app.ui.theme.ScanlineOverlay()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "VOID SECURE COGNITIVE SHIELD",
                                    fontFamily = FontFamily.Monospace,
                                    color = com.voidchat.app.ui.theme.NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(
                                    color = com.voidchat.app.ui.theme.HotPink,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "DECRYPTING SYSTEM KEYSTORE SEGMENTS...",
                                    fontFamily = FontFamily.Monospace,
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
