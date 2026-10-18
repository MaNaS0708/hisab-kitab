package com.example.hisabkitab

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

class HisabKitabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.log("HisabKitabApp initialized")
        } catch (e: Exception) {
            Log.w("HisabKitabApp", "Failed to initialize Firebase Crashlytics: ${e.message}")
        }
    }
}
