package com.safeguard.blocker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivityRecoveryCodeBinding

class RecoveryCodeVerifyActivity : AppCompatActivity() {

    private lateinit var b: ActivityRecoveryCodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRecoveryCodeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvSubtitle.text = getString(R.string.recovery_instruction)

        b.btnVerify.setOnClickListener {
            val code = b.etCode.text?.toString()?.trim() ?: ""
            if (code.isEmpty()) {
                b.tvError.visibility = android.view.View.VISIBLE
                b.tvError.text = "Enter recovery code"
                return@setOnClickListener
            }

            if (RecoveryConfig.isRateLimited(this)) {
                b.tvError.visibility = android.view.View.VISIBLE
                b.tvError.text = "Too many attempts. Try again later."
                return@setOnClickListener
            }

            if (RecoveryConfig.checkBackupCode(code)) {
                RecoveryConfig.recordAttempt(this, true)
                b.tvError.visibility = android.view.View.GONE
                b.btnVerify.isEnabled = false
                b.btnVerify.text = "Verifying..."
                Handler(Looper.getMainLooper()).postDelayed({
                    RecoveryConfig.markRecoveryGranted(this)
                    AppSessionState.tempUnlock(45_000L)
                    Toast.makeText(this, "Recovery verified. You can now set a new password.", Toast.LENGTH_LONG).show()
                    val i = Intent(this, SetupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(SetupActivity.EXTRA_RESET_PASSWORD, true)
                    }
                    startActivity(i)
                    finishAffinity()
                }, 500)
            } else {
                RecoveryConfig.recordAttempt(this, false)
                b.tvError.visibility = android.view.View.VISIBLE
                b.tvError.text = "Invalid recovery code"
                b.etCode.text?.clear()
            }
        }

        b.etCode.setOnEditorActionListener { _, _, _ ->
            b.btnVerify.performClick()
            true
        }

        b.tvBack.setOnClickListener {
            super.onBackPressed()
        }
    }
}
