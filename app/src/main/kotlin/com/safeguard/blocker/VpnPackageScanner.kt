package com.safeguard.blocker

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.util.Log
import java.security.MessageDigest
import java.util.Locale

/** Result of scanning the device for installed VPN apps. */
data class InstalledVpnApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSignedBuild: Boolean,
    val signatureTrusted: Boolean
)

/**
 * Scans the installed packages with [PackageManager] (requires the
 * QUERY_ALL_PACKAGES permission) and returns every known VPN client present
 * on the device, flagging whether the signing certificate matches the
 * fingerprint we expect for that vendor.
 */
object VpnPackageScanner {

    private const val TAG = "VPNScan"

    fun scan(context: Context): List<InstalledVpnApp> {
        val pm = context.packageManager
        val installed: List<PackageInfo> = try {
            pm.getInstalledPackages(0)
        } catch (se: SecurityException) {
            Log.e(TAG, "QUERY_ALL_PACKAGES missing — scan aborted", se)
            return emptyList()
        }

        val known = VpnPackageDatabase.KNOWN_VPN_PACKAGES
        val result = ArrayList<InstalledVpnApp>(4)

        for (info in installed) {
            if (info.packageName !in known) continue
            val label = runCatching {
                info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
            }.getOrElse { info.packageName }
            val hash = try {
                signingHash(info, pm)
            } catch (e: Exception) {
                Log.w(TAG, "signature read failed for ${info.packageName}", e)
                null
            }
            if (hash != null) {
                Log.i(TAG, "signature ${info.packageName} = $hash")
            }
            val expected = VpnPackageDatabase.KNOWN_SIGNATURES[info.packageName]
            val trusted = expected != null && expected.equals(hash, ignoreCase = true)

            result += InstalledVpnApp(
                packageName = info.packageName,
                label = label,
                versionName = info.versionName,
                isSignedBuild = hash != null,
                signatureTrusted = trusted
            )
        }
        return result.sortedBy { it.packageName }
    }

    /** True when the package exists on the device, by any means. */
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * SHA-256 of the signing certificate, hex-encoded lowercase.
     * Uses the (deprecated but universal) GET_SIGNATURES path so it works on
     * every API level from 21 up.
     */
    private fun signingHash(info: PackageInfo, pm: PackageManager): String? {
        @Suppress("DEPRECATION")
        val raw: Array<Signature>? = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw?.firstOrNull()?.toByteArray() ?: return null)
        return digest.joinToString("") { "%02x".format(Locale.US, it) }
    }
}
