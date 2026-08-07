package com.safeguard.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.safeguard.blocker.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var keywordAdapter: KeywordAdapter
    private var panicFieldsInitialized = false
    private var ignoreUninstallSwitch = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict cool-down gate: while the timer runs, the dashboard cannot
        // be opened at all — only the lockout screen is reachable.
        if (PanicLockdown.isActive(this)) {
            startActivity(Intent(this, PanicAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        keywordAdapter = KeywordAdapter(this) { kw -> promptPasswordForDelete(kw) }
        b.rvKeywords.adapter = keywordAdapter

        refreshAll()

        b.btnAccessibility.setOnClickListener {
            AppSessionState.tempUnlock(120_000L)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        b.btnDeviceAdmin.setOnClickListener {
            val comp = ComponentName(this, DeviceAdminReceiver::class.java)
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(comp)) {
                val intent = Intent(this, PasswordUnlockActivity::class.java).apply {
                    putExtra("source", "dashboard")
                    putExtra("action", "deactivate_admin")
                }
                startActivity(intent)
            } else {
                AppSessionState.tempUnlock(20_000L)
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents uninstallation without password")
                })
            }
        }

        b.btnChangePassword.setOnClickListener {
            showPasswordDialog()
        }

        b.inputKeyword.setEndIconOnClickListener {
            val kw = b.etKeyword.text?.toString()?.trim() ?: ""
            if (kw.isNotBlank()) { promptPasswordForAdd(kw); b.etKeyword.text?.clear() }
        }

        b.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        b.btnHelp.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:tengrow@nexagaze.com")
                putExtra(Intent.EXTRA_SUBJECT, "Tengrow Support Request")
            }
            startActivity(intent)
        }

        b.btnPanicSave.setOnClickListener {
            savePanicSettings()
        }

        b.btnPanicTest.setOnClickListener {
            savePanicSettings(showToast = false)
            startActivity(Intent(this, PanicAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        b.btnVpnPanic.setOnClickListener {
            // §5 panic button: stop our blocking VPN immediately (the 10s
            // countdown shield can also be scheduled from the block screen).
            VpnShieldVpnService.stopNow(this)
            Toast.makeText(this, R.string.vpn_panic_toast, Toast.LENGTH_SHORT).show()
        }

        setupUninstallProtectionSwitch()
    }

    /** Strict uninstall protection — turning it OFF requires an unlocked owner session or the master password. */
    private fun setupUninstallProtectionSwitch() {
        syncUninstallSwitch()
        b.switchUninstallProtection.setOnCheckedChangeListener { _, checked ->
            if (ignoreUninstallSwitch) return@setOnCheckedChangeListener
            if (checked) {
                UninstallProtection.setEnabled(this, true)
                Toast.makeText(this, "Strict uninstall protection ON", Toast.LENGTH_SHORT).show()
            } else {
                when {
                    AppSessionState.isValid() -> {
                        UninstallProtection.setEnabled(this, false)
                        Toast.makeText(this, "Strict uninstall protection OFF", Toast.LENGTH_SHORT).show()
                    }
                    PasswordManager.isSet(this) -> confirmTurnOffUninstallProtection()
                    else -> {
                        UninstallProtection.setEnabled(this, false)
                        Toast.makeText(this, "Strict uninstall protection OFF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun syncUninstallSwitch() {
        ignoreUninstallSwitch = true
        b.switchUninstallProtection.isChecked = UninstallProtection.isEnabled(this)
        ignoreUninstallSwitch = false
    }

    private fun confirmTurnOffUninstallProtection() {
        val input = android.widget.EditText(this).apply {
            hint = "Enter master password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Disable Uninstall Protection?")
            .setMessage("Without this, Tengrow can be removed from anywhere. Enter your master password to confirm.")
            .setView(input)
            .setPositiveButton("Confirm", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pwd = input.text.toString()
                if (PasswordManager.verify(this, pwd)) {
                    UninstallProtection.setEnabled(this, false)
                    Toast.makeText(this, "Strict uninstall protection OFF", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.setOnDismissListener { syncUninstallSwitch() }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        refreshVpnCard()
        scope.launch {
            VpnAppDiscovery.discover(this@MainActivity)
            refreshVpnCard()
        }
    }

    private fun refreshVpnCard() {
        val status = VpnActiveDetector.checkNow(this)
        val apps = VpnAppDiscovery.peek()
        val text = when (status) {
            is VpnActiveStatus.Active -> getString(R.string.vpn_status_tunnel)
            is VpnActiveStatus.Unavailable -> getString(R.string.vpn_status_inconclusive)
            else -> getString(R.string.vpn_status_clean)
        } + " · " + resources.getString(R.string.vpn_status_apps, apps.size)
        b.tvVpnStatus.text = text
        b.tvVpnStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (status is VpnActiveStatus.Active) R.color.status_active else R.color.status_inactive
            )
        )
    }

    private fun refreshAll() {
        refreshAccessibilityStatus()
        refreshAdminStatus()
        refreshPasswordStatus()
        refreshPanicStatus()
        refreshKeywordList()
        updateOverallStatus()
    }

    private fun refreshPanicStatus() {
        val enabled = PanicConfig.isEnabled(this)
        b.tvPanicStatus.text = PanicConfig.summary(this)
        b.tvPanicStatus.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.status_active else R.color.status_inactive)
        )
        if (!panicFieldsInitialized) {
            panicFieldsInitialized = true
            b.switchPanic.isChecked = enabled
            b.etPanicWindow.setText((PanicConfig.windowMs(this) / 60_000L).toString())
            b.etPanicCount.setText(PanicConfig.triggerCount(this).toString())
            b.etPanicCooldown.setText((PanicConfig.lockdownMs(this) / 60_000L).toString())
            b.etPanicName.setText(PanicConfig.contactName(this))
            b.etPanicPhone.setText(PanicConfig.contactPhone(this))
        }
    }

    private fun savePanicSettings(showToast: Boolean = true) {
        val minutes = b.etPanicWindow.text?.toString()?.toLongOrNull()
            ?.coerceAtLeast(1L) ?: (PanicConfig.DEFAULT_WINDOW_MS / 60_000L)
        val count = b.etPanicCount.text?.toString()?.toIntOrNull()
            ?.coerceAtLeast(2) ?: PanicConfig.DEFAULT_TRIGGER_COUNT
        val cool = b.etPanicCooldown.text?.toString()?.toLongOrNull()
            ?.coerceAtLeast(1L) ?: (PanicConfig.DEFAULT_LOCKDOWN_MS / 60_000L)
        PanicConfig.update(
            this,
            b.switchPanic.isChecked,
            minutes * 60_000L,
            count,
            cool * 60_000L,
            b.etPanicName.text?.toString().orEmpty(),
            b.etPanicPhone.text?.toString().orEmpty()
        )
        refreshPanicStatus()
        if (showToast) Toast.makeText(this, "Panic Alert settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAccessibilityStatus() {
        val active = isAccessibilityActive()
        b.tvAccStatus.text = if (active) getString(R.string.status_active) else getString(R.string.status_not_enabled)
        b.tvAccStatus.setTextColor(ContextCompat.getColor(this, if (active) R.color.status_active else R.color.status_inactive))
        b.btnAccessibility.text = if (active) "Manage" else "Enable"
    }

    private fun refreshAdminStatus() {
        val comp = ComponentName(this, DeviceAdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val active = dpm.isAdminActive(comp)
        b.tvAdminStatus.text = if (active) getString(R.string.status_active) else getString(R.string.status_not_activated)
        b.tvAdminStatus.setTextColor(ContextCompat.getColor(this, if (active) R.color.status_active else R.color.status_inactive))
        b.btnDeviceAdmin.text = if (active) "Deactivate" else "Activate Admin"
    }

    private fun refreshPasswordStatus() {
        val set = PasswordManager.isSet(this)
        b.tvPassStatus.text = if (set) getString(R.string.status_set) else getString(R.string.status_not_set)
        b.tvPassStatus.setTextColor(ContextCompat.getColor(this, if (set) R.color.status_active else R.color.status_inactive))
        b.btnChangePassword.text = if (set) "Change Password" else "Set Password"
    }

    private fun refreshKeywordList() {
        keywordAdapter.submitList(loadKeywords())
    }

    private fun updateOverallStatus() {
        val acc = isAccessibilityActive()
        val admin = (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java))
        val pwd = PasswordManager.isSet(this)
        val allReady = acc && admin && pwd

        b.statusIndicator.setBackgroundTintList(
            ContextCompat.getColorStateList(this, if (allReady) R.color.status_active else R.color.status_inactive)
        )
        b.tvStatusText.text = if (allReady) "All protections active" else "Setup incomplete"
        b.tvStatusText.setTextColor(
            ContextCompat.getColor(this, if (allReady) R.color.status_active else R.color.status_inactive)
        )
    }

    private fun isAccessibilityActive(): Boolean {
        val pref = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return pref.contains("$packageName/${LightweightBlockerService::class.java.name}")
    }

    private fun showPasswordDialog() {
        if (PasswordManager.isSet(this)) {
            val input = android.widget.EditText(this).apply {
                hint = "Current password"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Enter current password")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    if (PasswordManager.verify(this, input.text.toString())) {
                        showNewPasswordDialog()
                    } else {
                        Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            showNewPasswordDialog()
        }
    }

    private fun showNewPasswordDialog() {
        val pwdInput = android.widget.EditText(this).apply {
            hint = "New password (4+ chars)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Set Master Password")
            .setView(pwdInput)
            .setPositiveButton("Set") { _, _ ->
                val pwd = pwdInput.text.toString()
                if (PasswordManager.isKillSwitch(pwd)) {
                    Toast.makeText(this, "This password is reserved. Choose another.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pwd.length >= 4) {
                    PasswordManager.set(this, pwd)
                    Toast.makeText(this, "Password set", Toast.LENGTH_SHORT).show()
                    refreshPasswordStatus()
                } else {
                    Toast.makeText(this, "Minimum 4 characters", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptPasswordForAdd(kw: String) {
        if (!PasswordManager.isSet(this)) {
            saveKeyword(kw)
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = "Enter master password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Password Required")
            .setMessage("Enter your master password to add a keyword")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (PasswordManager.verify(this, input.text.toString())) {
                    saveKeyword(kw)
                } else {
                    Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptPasswordForDelete(kw: String) {
        if (!PasswordManager.isSet(this)) {
            removeKeyword(kw)
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = "Enter master password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Password Required")
            .setMessage("Enter your master password to delete \"$kw\"")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (PasswordManager.verify(this, input.text.toString())) {
                    removeKeyword(kw)
                } else {
                    onDeletePasswordFailed()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Aggressive-deletion guard: repeated failed password attempts while
     * trying to delete protection keywords auto-starts the strict 5-minute
     * cool-down timer (see [PanicLockdown.registerFailedDeleteAttempt]).
     */
    private fun onDeletePasswordFailed() {
        if (PanicLockdown.registerFailedDeleteAttempt(this)) {
            startActivity(Intent(this, PanicAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        } else {
            Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadKeywords(): List<String> {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        return prefs.getStringSet("list", defaultSet())?.toList()?.sorted() ?: defaultSet().toList()
    }

    private fun saveKeyword(kw: String) {
        val normalized = KeywordNormalizer.normalize(kw)
        if (normalized.isEmpty()) return
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("list", defaultSet())?.toMutableSet() ?: defaultSet().toMutableSet()
        if (set.add(normalized)) {
            val tok = prefs.getLong("__token", 0L) + 1
            prefs.edit().putStringSet("list", set).putLong("__token", tok).apply()
            keywordAdapter.submitList(set.sorted())
        }
    }

    private fun removeKeyword(kw: String) {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("list", defaultSet())?.toMutableSet() ?: return
        if (set.remove(KeywordNormalizer.normalize(kw))) {
            val tok = prefs.getLong("__token", 0L) + 1
            prefs.edit().putStringSet("list", set).putLong("__token", tok).apply()
            keywordAdapter.submitList(set.sorted())
        }
    }

    private fun defaultSet(): Set<String> = setOf(
        "xhamster", "pornhub", "xnxx", "xvideos", "redtube",
        "youporn", "porn", "adultcontent", "xxx", "sex",
        "hentai", "onlyfans", "stripchat", "chaturbate"
    )
}

private class KeywordAdapter(
    private val context: Context,
    private val onDelete: (String) -> Unit
) : ListAdapter<String, KeywordAdapter.VH>(DIFF) {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4.dp, 0, 4.dp) }
            setPadding(12.dp, 8.dp, 48.dp, 8.dp)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.on_surface))
            textSize = 13f
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_close_clear_cancel, 0)
            compoundDrawablePadding = 8.dp
            background = ContextCompat.getDrawable(context, R.drawable.circle_step_inactive)
        }
        val vh = VH(tv)
        tv.setOnClickListener {
            val kw = tv.tag as? String
            if (kw != null) onDelete(kw)
        }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val kw = getItem(position)
        holder.textView.text = kw
        holder.textView.tag = kw
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }
}

private val Int.dp: Int get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
