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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPasswordBinding.inflate(layoutInflater)
        setContentView(b.root)

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
