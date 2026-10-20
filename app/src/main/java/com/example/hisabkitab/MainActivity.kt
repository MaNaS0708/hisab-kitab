package com.example.hisabkitab

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.hisabkitab.util.AppLog

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("MainActivity", "UNCAUGHT CRASH in thread ${thread.name}: ${throwable.localizedMessage}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        AppLog.i("MainActivity", "onCreate started")
        try {
            installSplashScreen()
            enableEdgeToEdge()
        } catch (e: Exception) {
            AppLog.e("MainActivity", "Error during splash / edgeToEdge setup", e)
        }

        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)
            val root = findViewById<View>(R.id.main_root)
            if (root != null) {
                ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                    try {
                        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                        view.setPadding(0, statusBars.top, 0, navBars.bottom)
                    } catch (e: Exception) {
                        AppLog.w("MainActivity", "Error applying insets", e)
                    }
                    insets
                }
            } else {
                AppLog.w("MainActivity", "main_root View not found")
            }
            AppLog.i("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            AppLog.e("MainActivity", "Error inflating layout or setting view", e)
        }
    }
}
