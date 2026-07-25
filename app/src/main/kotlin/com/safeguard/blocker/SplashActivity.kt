package com.safeguard.blocker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)

        val statuses = listOf(
            "Initializing...", "Checking Security...",
            "Encrypting Session...", "Verifying Assets...", "Finalizing Setup..."
        )

        var index = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (index < statuses.size) {
                    b.tvStatus.text = statuses[index]
                    index++
                    handler.postDelayed(this, 800)
                } else {
                    navigateAway()
                }
            }
        }
        handler.postDelayed(runnable, 600)

        handler.postDelayed({ navigateAway() }, 3500)
    }

    private fun navigateAway() {
        val intent = if (PasswordManager.isSet(this)) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, SetupActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
