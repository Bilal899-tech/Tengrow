package com.safeguard.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView

/**
 * Passive "shame alert" — shown whenever a blocked habit is detected.
 *
 * A full-screen red-tinted screen with a cursive, passive-shame message
 * ("You are trying to involve yourself in bad habits."), followed by a
 * forced return to the home screen. It cannot be dismissed early (no back,
 * no buttons). Uses the built-in "cursive" font family with an italic SERIF
 * fallback on OEMs that omit the handwriting font.
 */
class ShameAlertActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { goHome() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_shame_alert)
        findViewById<TextView>(R.id.tvShameMessage).typeface = SHAME_FONT
        handler.postDelayed(dismissRunnable, SHAME_DISPLAY_MS)
    }

    private fun goHome() {
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        finishAffinity()
    }

    override fun onBackPressed() {
        // Intentionally non-dismissible during the shame window.
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(dismissRunnable)
    }

    companion object {
        private const val SHAME_DISPLAY_MS = 14_000L

        /** "cursive" handwriting family with a SERIF italic fallback. */
        private val SHAME_FONT: Typeface by lazy {
            runCatching { Typeface.create("cursive", Typeface.BOLD_ITALIC) }
                .getOrNull() ?: Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        }

        @JvmStatic
        fun show(context: Context) {
            val i = Intent(context, ShameAlertActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(i)
        }
    }
}