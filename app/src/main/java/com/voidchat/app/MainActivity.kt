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
