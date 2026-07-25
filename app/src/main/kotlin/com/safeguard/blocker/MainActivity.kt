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

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var keywordAdapter: KeywordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        keywordAdapter = KeywordAdapter(this) { kw -> removeKeyword(kw) }
        b.rvKeywords.adapter = keywordAdapter

        refreshAll()

        b.btnAccessibility.setOnClickListener {
            if (!isAccessibilityActive()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        b.btnDeviceAdmin.setOnClickListener {
            val comp = ComponentName(this, DeviceAdminReceiver::class.java)
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(comp)) {
                val intent = Intent(this, PasswordUnlockActivity::class.java).apply {
                    putExtra("source", "dashboard")
                }
                startActivity(intent)
            } else {
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
            if (kw.isNotBlank()) { saveKeyword(kw); b.etKeyword.text?.clear() }
        }

        b.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        b.btnHelp.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        refreshAccessibilityStatus()
        refreshAdminStatus()
        refreshPasswordStatus()
        refreshKeywordList()
        updateOverallStatus()
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

    private fun loadKeywords(): List<String> {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        return prefs.getStringSet("list", defaultSet())?.toList()?.sorted() ?: defaultSet().toList()
    }

    private fun saveKeyword(kw: String) {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("list", defaultSet())?.toMutableSet() ?: defaultSet().toMutableSet()
        set.add(kw.lowercase())
        prefs.edit().putStringSet("list", set).apply()
        keywordAdapter.submitList(set.sorted())
    }

    private fun removeKeyword(kw: String) {
        val prefs = getSharedPreferences("keywords", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("list", defaultSet())?.toMutableSet() ?: return
        set.remove(kw)
        prefs.edit().putStringSet("list", set).apply()
        keywordAdapter.submitList(set.sorted())
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
            setOnClickListener {
                val pos = tag as? Int ?: return@setOnClickListener
                onDelete(getItem(pos))
            }
            background = ContextCompat.getDrawable(context, R.drawable.circle_step_inactive)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.textView.text = getItem(position)
        holder.textView.tag = position
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }
}

private val Int.dp: Int get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
