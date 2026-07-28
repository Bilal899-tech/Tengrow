package com.safeguard.blocker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivityTrustBinding

class TrustActivity : AppCompatActivity() {

    private lateinit var b: ActivityTrustBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTrustBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnGetStarted.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }
}