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
        // Prevent screenshots and screen recordings app-wide on physical devices for absolute security.
        // Bypassed on Emulators to permit the browser streaming preview to display and function.
        val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.HARDWARE.contains("goldfish") ||
                android.os.Build.HARDWARE.contains("ranchu") ||
                android.os.Build.PRODUCT.contains("sdk_gphone")
        
        if (!isEmulator) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        super.onCreate(savedInstanceState)

        setContent {
            VoidTheme {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }
                val prefs = remember { com.voidchat.app.data.local.PreferencesManager(applicationContext) }
                var isAppLocked by remember { mutableStateOf(prefs.biometricLock && !prefs.pinCode.isNullOrEmpty()) }

                LaunchedEffect(Unit) {
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
                }

                if (isAppLocked) {
                    com.voidchat.app.ui.screens.LockScreen(
                        correctPinCode = prefs.pinCode ?: "",
                        onUnlocked = { isAppLocked = false }
                    )
                } else {
                    startDestination?.let { startDest ->
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            NavGraph(
                                navController = navController,
                                startDestination = startDest,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
