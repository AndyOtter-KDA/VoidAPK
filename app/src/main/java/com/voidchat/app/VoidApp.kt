package com.voidchat.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.voidchat.app.data.local.AppDatabase

class VoidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Warm up and initialize room database safely on startup
        AppDatabase.getDatabase(this)
    }
}
