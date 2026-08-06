package com.safeguard.blocker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivityPasswordBinding

class PasswordUnlockActivity : AppCompatActivity() {

    private lateinit var b: ActivityPasswordBinding
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPasswordBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Strict cool-down: while the timer runs, even the correct master
        // password cannot open the app — the lockout must expire.
        if (PanicLockdown.isActive(this)) {
            blockWhileCoolingDown()
            return
        }

        b.btnUnlock.setOnClickListener {
            val pwd = b.etPassword.text?.toString() ?: ""
            if (pwd.isEmpty()) {
                b.tvError.visibility = android.view.View.VISIBLE
                b.tvError.text = "Enter password"
                return@setOnClickListener
            }

            if (PasswordManager.verify(this, pwd)) {
                b.btnUnlock.isEnabled = false
                b.btnUnlock.text = "Verifying..."
                Handler(Looper.getMainLooper()).postDelayed({
                    AppSessionState.unlock()
                    Toast.makeText(this, "Unlocked for 60 seconds", Toast.LENGTH_SHORT).show()
                    val action = intent.getStringExtra("action")
                    if (action == "deactivate_admin") {
                        AppSessionState.tempUnlock(120_000L)
                        startActivity(Intent("android.settings.DEVICE_ADMIN_SETTINGS"))
                    }
                    finish()
                }, 600)
            } else {
                b.tvError.visibility = android.view.View.VISIBLE
                b.tvError.text = "Wrong password"
                b.etPassword.text?.clear()
            }
        }

        b.etPassword.setOnEditorActionListener { _, _, _ ->
            b.btnUnlock.performClick()
            true
        }

        b.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, RecoveryCodeVerifyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    /** Disables input and shows a live mm:ss countdown of the cool-down. */
    private fun blockWhileCoolingDown() {
        b.btnUnlock.isEnabled = false
        b.etPassword.isEnabled = false
        b.tvForgotPassword.visibility = android.view.View.GONE
        b.tvError.visibility = android.view.View.VISIBLE
        b.tvError.text = getString(R.string.cool_down_unlock)

        countdownRunnable?.let { handler.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                val remaining = PanicLockdown.remainingMs(this@PasswordUnlockActivity)
                if (remaining <= 0L) {
                    finish()
                    return
                }
                val totalSeconds = ((remaining + 999) / 1000L).toInt()
                val mm = totalSeconds / 60
                val ss = totalSeconds % 60
                b.tvError.text = String.format(
                    java.util.Locale.US,
                    "%s %02d:%02d",
                    getString(R.string.cool_down_unlock),
                    mm, ss
                )
                handler.postDelayed(this, 1000L)
            }
        }
        countdownRunnable = tick
        handler.post(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onBackPressed() {
        val src = intent.getStringExtra("source")
        if (src == "settings_block") {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        super.onBackPressed()
    }
}
