package com.example.hisabkitab.util

import android.util.Log

object AppLog {
    private const val GLOBAL_TAG = "HisabKitab_Debug"

    fun d(component: String, message: String) {
        Log.d(GLOBAL_TAG, "[$component] $message")
    }

    fun i(component: String, message: String) {
        Log.i(GLOBAL_TAG, "[$component] $message")
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(GLOBAL_TAG, "[$component] $message", throwable)
        } else {
            Log.w(GLOBAL_TAG, "[$component] $message")
        }
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(GLOBAL_TAG, "[$component] CRITICAL ERROR: $message", throwable)
        } else {
            Log.e(GLOBAL_TAG, "[$component] CRITICAL ERROR: $message")
        }
    }
}
