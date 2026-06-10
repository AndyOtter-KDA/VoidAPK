package com.voidchat.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.voidchat.app.data.local.AppDatabase

class VoidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("VoidApp", "Firebase init error: ${e.message}", e)
        }
        
        try {
            // Warm up and initialize room database safely on startup
            AppDatabase.getDatabase(this)
        } catch (e: Exception) {
            android.util.Log.e("VoidApp", "Room DB init error: ${e.message}", e)
        }
    }
}
