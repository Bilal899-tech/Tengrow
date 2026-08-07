package com.safeguard.blocker

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** §1 + §2 + §3 combined outcome of a scan. */
sealed class VpnScanResult {
    /** The full picture for the UI / decision layer. */
    data class Report(val report: VpnThreatReport) : VpnScanResult()

    /** ConnectivityManager/UsageStats unavailable — cannot assess. */
    object Inconclusive : VpnScanResult()
}

/** Everything the use-case learned, ready for a policy decision. */
data class VpnThreatReport(
    val installedVpnApps: List<VpnApp>,
    val networkStatus: VpnActiveStatus,
    val usage: VpnUsageVerification,
    /** Apps that are BOTH installed VPNs AND were recently in the foreground. */
    val suspects: List<String>
)

/**
 * Use-case orchestrator (§1→§3).
 *
 *  - discover: BIND_VPN_SERVICE apps (cached),
 *  - network:  is a tunnel currently active?,
 *  - usage:    were any of those apps foregrounded in the last 5 minutes?,
 *  - report:   merges everything into one sealed-typed result.
 *
 * Everything is suspend + coroutine-friendly; nothing touches the main thread.
 */
class VpnScanner(private val context: Context) {

    /** The single aggregated scan. Also usable from MainActivity's resume. */
    suspend fun scan(): VpnScanResult = coroutineScope {
        val discovered = async { VpnAppDiscovery.discover(context) }
        val network = async { VpnActiveDetector.checkNow(context) }

        val apps = discovered.await()
        val status = network.await()
        if (status is VpnActiveStatus.Unavailable) {
            return@coroutineScope VpnScanResult.Inconclusive
        }

        val candidates = apps.map { it.packageName }
        val usage = if (candidates.isEmpty()) {
            VpnUsageVerification.NoRecentUse
        } else {
            VpnUsageConfirmation.confirm(context, candidates)
        }

        when {
            status is VpnActiveStatus.Active -> {
                // Tunnel is up: treat every installed VPN as suspect; usage
                // data refines which was actually driving it.
                val suspects = when (usage) {
                    is VpnUsageVerification.Confirmed -> usage.packageNames
                    else -> candidates
                }
                VpnScanResult.Report(
                    VpnThreatReport(apps, status, usage, suspects)
                )
            }
            usage is VpnUsageVerification.Confirmed && usage.packageNames.isNotEmpty() -> {
                // No live tunnel right now, but a VPN app was recently used.
                VpnScanResult.Report(
                    VpnThreatReport(
                        installedVpnApps = apps,
                        networkStatus = status,
                        usage = usage,
                        suspects = usage.packageNames
                    )
                )
            }
            else -> VpnScanResult.Report(
                VpnThreatReport(apps, status, usage, emptyList())
            )
        }
    }

    /** Just the discovery part (§1). */
    suspend fun discoverOnly(): List<VpnApp> = VpnAppDiscovery.discover(context)
}