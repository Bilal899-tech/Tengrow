package com.safeguard.blocker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var b: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }

        b.tvFaq1.setOnClickListener {
            b.tvFaq1Ans.visibility = if (b.tvFaq1Ans.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        b.tvFaq2.setOnClickListener {
            b.tvFaq2Ans.visibility = if (b.tvFaq2Ans.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
}
