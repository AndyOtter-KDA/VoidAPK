package com.voidchat.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.voidchat.app.data.local.AppDatabase

class VoidApp : Application() {
    companion object {
        lateinit var instance: VoidApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            
            // Test connection to Firestore
            com.voidchat.app.data.remote.FirestoreManager.testConnection { success ->
                if (success) {
                    android.util.Log.d("VoidFirestore", "Real Firestore online and connected successfully on startup.")
                } else {
                    android.util.Log.e("VoidFirestore", "Real Firestore failed connection test on startup.")
                }
            }
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
