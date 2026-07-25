package com.safeguard.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safeguard.blocker.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private var currentStep = 1
    private var accEnabled = false
    private var adminEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        showStep(1)

        b.btnGetStarted.setOnClickListener { showStep(2) }

        b.btnContinue.setOnClickListener {
            val pwd = b.etPassword.text?.toString() ?: ""
            val confirm = b.etConfirmPassword.text?.toString() ?: ""
            if (pwd.length < 4) {
                Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pwd != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PasswordManager.set(this, pwd)
            showStep(3)
        }

        b.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        b.btnEnableDeviceAdmin.setOnClickListener {
            val comp = ComponentName(this, DeviceAdminReceiver::class.java)
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to prevent uninstallation without password")
            })
        }

        b.btnFinishSetup.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Password strength listener
        b.etPassword.setOnTextChanged { s ->
            val len = s?.length ?: 0
            val pct = when {
                len == 0 -> 0; len < 4 -> 25; len < 8 -> 50; len < 12 -> 75; else -> 100
            }
            val label = when {
                len == 0 -> ""; len < 4 -> "Weak"; len < 8 -> "Fair"; len < 12 -> "Strong"; else -> "Excellent"
            }
            b.strengthBar.progress = pct
            b.tvStrengthLabel.text = label
            val color = when {
                len < 4 -> R.color.error; len < 8 -> R.color.tertiary
                len < 12 -> R.color.secondary; else -> R.color.primary
            }
            b.strengthBar.progressTintList = ContextCompat.getColorStateList(this, color)
            b.tvStrengthLabel.setTextColor(ContextCompat.getColor(this, color))
        }
    }

    override fun onResume() {
        super.onResume()
        accEnabled = isAccessibilityActive()
        adminEnabled = isAdminActive()

        if (accEnabled) {
            b.btnEnableAccessibility.text = "Enabled"
            b.btnEnableAccessibility.isEnabled = false
            b.tvAccStatus.text = getString(R.string.status_active)
            b.tvAccStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        }
        if (adminEnabled) {
            b.btnEnableDeviceAdmin.text = "Enabled"
            b.btnEnableDeviceAdmin.isEnabled = false
            b.tvAdminStatus.text = getString(R.string.status_active)
            b.tvAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        }

        b.btnFinishSetup.isEnabled = accEnabled && adminEnabled
        if (accEnabled && adminEnabled) {
            b.btnFinishSetup.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        b.step1Content.visibility = if (step == 1) android.view.View.VISIBLE else android.view.View.GONE
        b.step2Content.visibility = if (step == 2) android.view.View.VISIBLE else android.view.View.GONE
        b.step3Content.visibility = if (step == 3) android.view.View.VISIBLE else android.view.View.GONE

        val active = R.drawable.circle_step_active
        val inactive = R.drawable.circle_step_inactive
        val activeColor = ContextCompat.getColor(this, R.color.primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.on_surface_variant)

        b.step1Circle.setBackgroundResource(if (step >= 1) active else inactive)
        b.step2Circle.setBackgroundResource(if (step >= 2) active else inactive)
        b.step3Circle.setBackgroundResource(if (step >= 3) active else inactive)

        b.step1Label.setTextColor(if (step >= 1) activeColor else inactiveColor)
        b.step2Label.setTextColor(if (step >= 2) activeColor else inactiveColor)
        b.step3Label.setTextColor(if (step >= 3) activeColor else inactiveColor)

        // Check mark for completed steps
        if (step > 1) b.step1Circle.text = "✓"
        if (step > 2) b.step2Circle.text = "✓"
        if (step >= 1 && step <= 1) b.step1Circle.text = "1"
        if (step == 2) b.step2Circle.text = "2"
        if (step == 3) b.step3Circle.text = "3"

        val lineColor = ContextCompat.getColor(this, R.color.primary_container)
        b.line1.setBackgroundColor(if (step >= 2) lineColor else ContextCompat.getColor(this, R.color.outline_variant))
        b.line2.setBackgroundColor(if (step >= 3) lineColor else ContextCompat.getColor(this, R.color.outline_variant))
    }

    private fun isAccessibilityActive(): Boolean {
        val pref = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return pref.contains("$packageName/${LightweightBlockerService::class.java.name}")
    }

    private fun isAdminActive(): Boolean {
        val comp = ComponentName(this, DeviceAdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(comp)
    }

    private fun com.google.android.material.textfield.TextInputEditText.setOnTextChanged(listener: (String?) -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { listener(s?.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
}
