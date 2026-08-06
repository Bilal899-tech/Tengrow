package com.safeguard.blocker

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.blocker.databinding.ActivityPanicAlertBinding
import java.util.Locale

class PanicAlertActivity : AppCompatActivity() {

    private lateinit var b: ActivityPanicAlertBinding
    private var mediaPlayer: MediaPlayer? = null
    private var alarming = true
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        b = ActivityPanicAlertBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (PanicLockdown.isActive(this)) {
            showLockdown()
        } else {
            showAlert()
        }
    }

    private fun showAlert() {
        b.alertSection.visibility = View.VISIBLE
        b.lockdownSection.visibility = View.GONE

        val name = PanicConfig.contactName(this)
        val phone = PanicConfig.contactPhone(this)

        b.tvPanicMessage.text = if (name.isNotBlank()) {
            "Multiple blocked content detections. Alerting $name."
        } else {
            "Multiple blocked content detections. Please stay calm and reach out for support."
        }

        if (phone.isNotBlank()) {
            b.btnCallContact.visibility = View.VISIBLE
            b.btnCallContact.text = if (name.isNotBlank()) "Call $name" else "Call Emergency Contact"
            b.btnCallContact.setOnClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }
            }
        }

        b.btnPanic.setOnClickListener {
            startLockdown()
        }

        startAlarm()
    }

    private fun startLockdown() {
        stopAlarm()
        PanicLockdown.start(this, PanicConfig.lockdownMs(this))
        showLockdown()
    }

    private fun showLockdown() {
        b.alertSection.visibility = View.GONE
        b.lockdownSection.visibility = View.VISIBLE

        val minutes = PanicConfig.lockdownMs(this) / 60_000L
        b.tvLockdownLabel.text = "Cool-down period ($minutes min). Take a breath and step away."

        countdownRunnable?.let { handler.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                val remaining = PanicLockdown.remainingMs(this@PanicAlertActivity)
                if (remaining <= 0L) {
                    PanicLockdown.clear(this@PanicAlertActivity)
                    goHome()
                    return
                }
                val totalSeconds = ((remaining + 999) / 1000L).toInt()
                val mm = totalSeconds / 60
                val ss = totalSeconds % 60
                b.tvCountdown.text = String.format(Locale.US, "%02d:%02d", mm, ss)
                handler.postDelayed(this, 1000L)
            }
        }
        countdownRunnable = tick
        handler.post(tick)
    }

    override fun onResume() {
        super.onResume()
        if (PanicLockdown.isActive(this)) {
            showLockdown()
        } else if (b.alertSection.visibility == View.VISIBLE && mediaPlayer == null) {
            startAlarm()
        }
    }

    private fun startAlarm() {
        alarming = true
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer.create(this, uri)?.apply {
                isLooping = true
                setVolume(1f, 1f)
                start()
            }
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 800, 400), 0)
                }
            }
        }
    }

    private fun stopAlarm() {
        alarming = false
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finishAffinity()
    }

    override fun onBackPressed() {
        // Strict: cannot be dismissed with back.
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
        if (alarming) stopAlarm()
    }
}
