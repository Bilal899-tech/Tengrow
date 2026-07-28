package com.safeguard.blocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private var statusRunnable: Runnable? = null
    private var fallbackRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            b = ActivitySplashBinding.inflate(layoutInflater)
            setContentView(b.root)

            val statuses = listOf(
                "Initializing...", "Checking Security...",
                "Encrypting Session...", "Verifying Assets...", "Finalizing Setup..."
            )

            var index = 0
            statusRunnable = object : Runnable {
                override fun run() {
                    if (index < statuses.size) {
                        b.tvStatus.text = statuses[index]
                        index++
                        handler.postDelayed(this, 700)
                    } else {
                        navigateAway()
                    }
                }
            }
            handler.postDelayed(statusRunnable!!, 500)

            fallbackRunnable = Runnable { navigateAway() }
            handler.postDelayed(fallbackRunnable!!, 4200)
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Crash in onCreate", e)
            try {
                val fos = openFileOutput("crash_log.txt", Context.MODE_PRIVATE)
                fos.write(android.util.Log.getStackTraceString(e).toByteArray())
                fos.close()
            } catch (_: Exception) {}
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statusRunnable?.let { handler.removeCallbacks(it) }
        fallbackRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun navigateAway() {
        try {
            if (hasNavigated) return
            hasNavigated = true
            statusRunnable?.let { handler.removeCallbacks(it) }
            fallbackRunnable?.let { handler.removeCallbacks(it) }
            val target = if (!PasswordManager.isSet(this)) {
                Intent(this, SetupActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(target)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Crash in navigateAway", e)
            try {
                val fos = openFileOutput("crash_log.txt", Context.MODE_PRIVATE)
                fos.write(android.util.Log.getStackTraceString(e).toByteArray())
                fos.close()
            } catch (_: Exception) {}
            finishAffinity()
        }
    }
}